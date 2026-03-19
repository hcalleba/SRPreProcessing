package edu.repetita.traffic.Generator;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;
import edu.repetita.solvers.mcf.MCFAggregated;
import edu.repetita.solvers.mcf.MCFUnaggregated;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Abstract base class for all traffic matrix generators.
 *
 * Shared state and behaviour:
 * - numMatrices — how many stress matrices to produce (excluding the base).
 * - seed — RNG seed for reproducibility.
 * - boostLow / boostHigh — inclusive bounds for the amplification factor α.
 *   Each matrix samples α ~ Uniform[boostLow, boostHigh] independently.
 * - visualize — whether to open topology visualisation windows.
 * - inflate — whether to inflate the base matrix with long-distance traffic
 *   on uncongested edges before generating stress variants.
 * - scaleToTargetMLU — shared MCF-based normalisation helper.
 * - sampleBoost — draws one α from the configured range.
 *
 * Concrete subclasses implement {@link #generate} and define their own default
 * boost range via {@link #setBoostRange} in their constructor.
 */
public abstract class TrafficMatrixGenerator {

    protected static final double TARGET_MLU = 1.0;

    protected int     numMatrices = 15;
    protected long    seed = 42L;
    protected double  boostLow = 1.0;
    protected double  boostHigh = 1.0;
    protected boolean visualize = true;
    protected boolean inflate = false;

    public void setNumMatrices(int n){
        this.numMatrices = n;
    }
    public void setSeed(long seed){
        this.seed = seed;
    }
    public void setBoostRange(double low, double high){
        this.boostLow = low; this.boostHigh = high;
    }
    public void setVisualize(boolean v){
        this.visualize = v;
    }
    public void setInflate(boolean v){
        this.inflate = v;
    }

    /** Generates and returns the list of demand matrices (base matrix first). */
    public abstract List<Demands> generate(Setting setting);

    /** Samples one amplification factor uniformly in [boostLow, boostHigh]. */
    protected double sampleBoost(Random rng) {
        return boostLow + rng.nextDouble() * (boostHigh - boostLow);
    }

    /** Scales demands so that the MCF-optimal MLU equals {@code targetMLU}. */
    protected static Demands scaleToTargetMLU(Topology topology, Demands matrix, double targetMLU) {
        double currentMLU = MCFAggregated.computeOptimalMLU(topology, matrix);
        if (currentMLU <= 0) {
            System.err.println("Warning: MCF MLU=" + currentMLU + ", returning unscaled matrix");
            return matrix;
        }
        double factor = targetMLU / currentMLU;
        Demands result = new Demands(matrix);
        for (int i = 0; i < result.nDemands; i++) {
            result.amount[i] = Math.floor(matrix.amount[i] * factor);
        }
        System.out.println("MCF_SCALE_FACTOR: " + factor);
        return result;
    }

    /**
     * Inflates a demand matrix by adding flow to uncongested edges, favouring
     * long-distance demands. The MCF is solved once to obtain per-demand per-edge
     * flows; subsequent updates are purely additive (no re-solve).
     *
     * For each demand (processed longest-path-first), computes the maximum
     * scaling factor that keeps all of its edges at or below TARGET_MLU, then
     * applies it and updates the utilisation bookkeeping.
     *
     * @param topology the network topology
     * @param matrix   the demand matrix (already scaled to TARGET_MLU)
     * @return a new Demands with increased volumes on uncongested paths
     */
    protected static Demands inflateUncongested(Topology topology, Demands matrix) {
        int nEdges = topology.nEdges;
        int nDemands = matrix.nDemands;

        MCFUnaggregated.MCFResult mcf = MCFUnaggregated.solve(topology, matrix);
        if (mcf.mlu == Double.MAX_VALUE) {
            System.err.println("Warning: MCF solve failed, skipping inflation");
            return matrix;
        }

        // Compute current per-edge utilisation from the MCF solution
        double[] edgeUtil = new double[nEdges];
        for (int e = 0; e < nEdges; e++) {
            double load = 0.0;
            for (int k = 0; k < nDemands; k++) {
                load += mcf.flowPerDemandPerEdge[k][e];
            }
            edgeUtil[e] = load / topology.edgeCapacity[e];
        }

        // Compute per-demand flow fractions: fraction[k][e] = flow[k][e] / amount[k]
        // This is the frozen MCF routing — tells us how each unit of demand k distributes
        double[][] fraction = new double[nDemands][nEdges];
        for (int k = 0; k < nDemands; k++) {
            if (matrix.amount[k] <= 0) continue;
            for (int e = 0; e < nEdges; e++) {
                fraction[k][e] = mcf.flowPerDemandPerEdge[k][e] / matrix.amount[k];
            }
        }

        // Compute hop count per demand (number of edges with nonzero flow)
        int[] hopCount = new int[nDemands];
        for (int k = 0; k < nDemands; k++) {
            for (int e = 0; e < nEdges; e++) {
                if (fraction[k][e] > 1e-10) hopCount[k]++;
            }
        }

        // Sort demand indices by hop count descending (long-distance first)
        Integer[] order = new Integer[nDemands];
        for (int i = 0; i < nDemands; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Integer.compare(hopCount[b], hopCount[a]));

        Demands inflated = new Demands(matrix);
        int inflatedCount = 0;
        double totalAdded = 0.0;

        for (int k : order) {
            if (matrix.amount[k] <= 0) continue;

            // Find max additional absolute flow δ such that for every edge e used by k:
            //   edgeUtil[e] + δ * fraction[k][e] / capacity[e] <= TARGET_MLU
            double maxDelta = Double.MAX_VALUE;
            for (int e = 0; e < nEdges; e++) {
                if (fraction[k][e] < 1e-10) continue;
                double slack = (TARGET_MLU - edgeUtil[e]) * topology.edgeCapacity[e];
                if (slack <= 0) {
                    maxDelta = 0;
                    break;
                }
                double delta = slack / fraction[k][e];
                maxDelta = Math.min(maxDelta, delta);
            }

            if (maxDelta < 1.0) continue; // not worth adding less than 1 unit

            maxDelta = Math.floor(maxDelta);
            inflated.amount[k] += maxDelta;
            totalAdded += maxDelta;
            inflatedCount++;

            // Update edge utilisation bookkeeping
            for (int e = 0; e < nEdges; e++) {
                if (fraction[k][e] < 1e-10) continue;
                edgeUtil[e] += maxDelta * fraction[k][e] / topology.edgeCapacity[e];
            }
        }

        System.out.printf("Inflation: increased %d demands, added %.0f total flow units%n",
                inflatedCount, totalAdded);
        return inflated;
    }
}
