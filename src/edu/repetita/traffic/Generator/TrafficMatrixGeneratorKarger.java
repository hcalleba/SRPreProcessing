package edu.repetita.traffic.Generator;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.viz.TopologyViewer;

import java.util.*;

/**
 * Generates traffic matrices that stress a balanced min-cut of the topology,
 * discovered via a weighted variant of Karger's randomised contraction algorithm.
 *
 * Algorithm:
 * - Base matrix: gravitational model (Nucci et al. 2005), scaled to MLU = 1.
 * - Karger cut: repeatedly contract edges until two super-nodes remain.
 *   Each edge is selected with probability proportional to 1/capacity,
 *   so high-capacity edges are contracted first, leaving low-capacity bottleneck
 *   edges as the likely cut. The result is accepted only if the smaller partition
 *   holds at least MIN_BALANCE (20%) of all nodes — this prevents degenerate leaf cuts.
 * - Amplification: all cross-cut OD pairs (i∈S1↔j∈S2 in both directions)
 *   are multiplied by α ~ Uniform[boostLow, boostHigh].
 * - Normalisation: the boosted matrix is re-scaled to MLU = 1 via MCF.
 *
 * Each call to {@link #generate} produces one base matrix plus numMatrices
 * cut-stressed matrices, each using an independently sampled Karger cut.
 */
public class TrafficMatrixGeneratorKarger extends TrafficMatrixGenerator {

    private static final double MIN_BALANCE = 0.20;
    private static final int    MAX_TRIALS  = 200;

    public TrafficMatrixGeneratorKarger() {
        setBoostRange(1.5, 2.5);
    }

    @Override
    public List<Demands> generate(Setting setting) {
        Topology topology = setting.getTopology();

        Demands base   = new BaseMatrixGenerator(topology, 1.0, seed).generate();
        Demands scaled = scaleToTargetMLU(topology, base, TARGET_MLU);

        Random rng = new Random(seed + 1);
        List<Demands> matrices = new ArrayList<>();
        matrices.add(scaled);

        for (int m = 0; m < numMatrices; m++) {

            int[] assignment = findBalancedCut(topology, rng);
            if (assignment == null) {
                System.err.printf("Warning: no balanced cut found for matrix %d after %d trials%n",
                        m + 1, MAX_TRIALS);
                continue;
            }

            int s1Size = 0, s2Size = 0;
            for (int a : assignment) { if (a == 0) s1Size++; else s2Size++; }
            System.out.printf("Matrix %d: bipartition S1=%d nodes, S2=%d nodes%n",
                    m + 1, s1Size, s2Size);
            if (visualize) {
                TopologyViewer.show(topology, assignment);
            }

            double alpha    = sampleBoost(rng);
            Demands boosted = new Demands(scaled);
            int boostedCount = 0;
            for (int d = 0; d < boosted.nDemands; d++) {
                if (assignment[boosted.source[d]] != assignment[boosted.dest[d]]) {
                    boosted.amount[d] *= alpha;
                    boostedCount++;
                }
            }
            System.out.printf("  Amplified %d cross-cut demands by x%.2f%n", boostedCount, alpha);

            matrices.add(boosted);
            System.out.println("Generated Karger matrix " + (m + 1));
        }

        System.out.println("Total matrices: " + matrices.size()
                + " (1 base + " + (matrices.size() - 1) + " Karger-cut)");
        return matrices;
    }

    private int[] findBalancedCut(Topology topology, Random rng) {
        for (int trial = 0; trial < MAX_TRIALS; trial++) {
            int[] result = runKargerTrial(topology, rng);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * One trial of weighted Karger contraction.
     *
     * @return node-to-partition assignment (0 or 1), or {@code null} if the resulting
     *         bipartition violates the balance constraint.
     */
    private int[] runKargerTrial(Topology topology, Random rng) {
        int n = topology.nNodes;

        int[] parent = new int[n];
        int[] rank   = new int[n];
        int[] sz     = new int[n];
        for (int i = 0; i < n; i++) { parent[i] = i; sz[i] = 1; }

        int superNodes = n;

        while (superNodes > 2) {

            double totalWeight = 0.0;
            for (int e = 0; e < topology.nEdges; e++) {
                int rs = findRoot(parent, topology.edgeSrc[e]);
                int rd = findRoot(parent, topology.edgeDest[e]);
                if (rs != rd) {
                    double cap = topology.edgeCapacity[e];
                    totalWeight += (cap > 0) ? 1.0 / cap : 1.0;
                }
            }
            if (totalWeight <= 0) break;

            double pick       = rng.nextDouble() * totalWeight;
            double cumulative = 0.0;
            int contractA = -1, contractB = -1;
            for (int e = 0; e < topology.nEdges; e++) {
                int rs = findRoot(parent, topology.edgeSrc[e]);
                int rd = findRoot(parent, topology.edgeDest[e]);
                if (rs != rd) {
                    double cap = topology.edgeCapacity[e];
                    cumulative += (cap > 0) ? 1.0 / cap : 1.0;
                    if (contractA == -1 && cumulative >= pick) {
                        contractA = rs;
                        contractB = rd;
                    }
                }
            }
            if (contractA == -1) break;

            mergeComponents(parent, rank, sz, contractA, contractB);
            superNodes--;
        }

        int root0 = -1, root1 = -1;
        for (int v = 0; v < n; v++) {
            int r = findRoot(parent, v);
            if (root0 == -1) {
                root0 = r;
            } else if (r != root0 && root1 == -1) {
                root1 = r;
            }
        }
        if (root1 == -1) return null;

        if ((double) Math.min(sz[root0], sz[root1]) / n < MIN_BALANCE) return null;

        int[] assignment = new int[n];
        for (int v = 0; v < n; v++) {
            assignment[v] = (findRoot(parent, v) == root0) ? 0 : 1;
        }
        return assignment;
    }

    private int findRoot(int[] parent, int x) {
        int root = x;
        while (parent[root] != root) root = parent[root];
        while (parent[x] != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
    }

    private void mergeComponents(int[] parent, int[] rank, int[] sz, int a, int b) {
        if (a == b) return;
        if (rank[a] < rank[b]) { int tmp = a; a = b; b = tmp; }
        parent[b] = a;
        sz[a] += sz[b];
        if (rank[a] == rank[b]) rank[a]++;
    }
}
