package edu.repetita.traffic;

import edu.repetita.core.Demands;
import edu.repetita.core.Topology;

import java.util.Arrays;
import java.util.Random;

/**
 * Generates a base traffic matrix following Nucci et al. (2005).
 *
 * Step 1: Sample N*(N-1) demand values from a lognormal distribution.
 * Step 2: Rank node pairs by (m1, m2) metrics and assign largest demands
 *         to highest-ranked pairs (Ranking Metrics Heuristic, Section 6.2).
 *         m3 metric is left out for simplicity.
 * Step 3: Scale all demands so that the total volume matches the topology
 *         (normalization to a target load is left to the caller via MCF).
 *
 * Parameters:
 *   sigma - shape of the lognormal distribution (recommended: ~1.0)
 *   seed  - random seed for reproducibility
 */
public class BaseMatrixGenerator {

    private final Topology topology;
    private final double sigma;
    private final long seed;

    public BaseMatrixGenerator(Topology topology, double sigma, long seed) {
        this.topology = topology;
        this.sigma = sigma;
        this.seed = seed;
    }

    // Useful t have a baseline mu and avoid MLU of 0.00...1 with too low loads
    // Is scaled afterward anyway
    private double computeMu() {
        double totalCapacity = 0;
        for (int e = 0; e < topology.nEdges; e++) {
            totalCapacity += topology.edgeCapacity[e];
        }
        int nPairs = topology.nNodes * (topology.nNodes - 1);
        double targetMean = (totalCapacity / nPairs) * 0.1;
        return Math.log(targetMean) - (sigma * sigma) / 2.0;
    }

    public Demands generate() {
        int n = topology.nNodes;
        int nPairs = n * (n - 1); // all ordered pairs except self-loops

        // Step 1: sample lognormal demand values
        double[] sampledValues = sampleLognormal(nPairs);

        // Step 2: rank node pairs by (m1, m2)
        int[][] rankedPairs = rankNodePairs();

        // Step 3: sort sampled values descending, assign to pairs in rank order
        Arrays.sort(sampledValues);
        reverse(sampledValues);

        // Build Demands
        String[] labels  = new String[nPairs];
        int[]    sources = new int[nPairs];
        int[]    dests   = new int[nPairs];
        double[] amounts = new double[nPairs];

        for (int i = 0; i < nPairs; i++) {
            int src = rankedPairs[i][0];
            int dst = rankedPairs[i][1];
            labels[i]  = "d_" + src + "_" + dst;
            sources[i] = src;
            dests[i]   = dst;
            amounts[i] = sampledValues[i];
        }

        return new Demands(labels, sources, dests, amounts);
    }

    // Step 1: lognormal sampling
    private double[] sampleLognormal(int count) {
        Random rng = new Random(seed);
        double[] values = new double[count];
        double mu = computeMu();
        for (int i = 0; i < count; i++) {
            values[i] = Math.exp(mu + sigma * rng.nextGaussian());
        }
        return values;
    }

    // Step 2: rank all (src, dst) pairs by (m1 desc, m2 desc)
    //
    // m1(src,dst) = min(fanOutCapacity(src), fanInCapacity(dst))
    // m2(src,dst) = min(degree(src), degree(dst))
    private int[][] rankNodePairs() {
        int n = topology.nNodes;

        double[] fanOut = new double[n];
        double[] fanIn  = new double[n];
        int[]    degree = new int[n];

        for (int e = 0; e < topology.nEdges; e++) {
            int src = topology.edgeSrc[e];
            int dst = topology.edgeDest[e];
            fanOut[src] += topology.edgeCapacity[e];
            fanIn[dst]  += topology.edgeCapacity[e];
            degree[src]++;
            degree[dst]++;
        }

        // collect all pairs
        int nPairs = n * (n - 1);
        int[][] pairs = new int[nPairs][2];
        double[] m1   = new double[nPairs];
        int[]    m2   = new int[nPairs];

        int idx = 0;
        for (int src = 0; src < n; src++) {
            for (int dst = 0; dst < n; dst++) {
                if (src == dst) continue;
                pairs[idx][0] = src;
                pairs[idx][1] = dst;
                m1[idx] = Math.min(fanOut[src], fanIn[dst]);
                m2[idx] = Math.min(degree[src], degree[dst]);
                idx++;
            }
        }

        // sort by m1 desc, break ties by m2 desc
        Integer[] order = new Integer[nPairs];
        for (int i = 0; i < nPairs; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            if (m1[b] != m1[a]) return Double.compare(m1[b], m1[a]);
            return Integer.compare(m2[b], m2[a]);
        });

        int[][] ranked = new int[nPairs][2];
        for (int i = 0; i < nPairs; i++) {
            ranked[i] = pairs[order[i]];
        }
        return ranked;
    }

    // Utility
    private void reverse(double[] arr) {
        for (int i = 0, j = arr.length - 1; i < j; i++, j--) {
            double tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }
}