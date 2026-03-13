package edu.repetita.traffic.Generator;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.solvers.mcf.MCF;
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
 *   are multiplied by a random α ∈ [MIN_ALPHA, MAX_ALPHA].
 * - Normalisation: the boosted matrix is re-scaled to MLU = 1 via MCF.
 *
 * Each call to {@link #generate} produces one base matrix plus numMatrices
 * cut-stressed matrices, each using an independently sampled Karger cut.
 */

public class TrafficMatrixGeneratorKarger {

    private static final double TARGET_MLU  = 1.0;
    /** Minimum fraction of nodes that must be on the smaller side of the cut. */
    private static final double MIN_BALANCE = 0.20;
    /** Maximum number of Karger retries before giving up on a matrix. */
    private static final int    MAX_TRIALS  = 200;
    private static final double MIN_ALPHA   = 1.5;
    private static final double MAX_ALPHA   = 2.5;

    private int  numMatrices = 15;
    private long seed        = 42L;
    private boolean visualizeBipartitions = false;

    public void setNumMatrices(int n) { this.numMatrices = n; }
    public void setSeed(long seed)    { this.seed = seed; }
    public void setVisualizeBipartitions(boolean visualizeBipartitions) {
        this.visualizeBipartitions = visualizeBipartitions;
    }

    public List<Demands> generate(Setting setting) {
        Topology topology = setting.getTopology();

        // Step 1: base matrix scaled to MLU = 1
        Demands base   = new BaseMatrixGenerator(topology, 1.0, seed).generate();
        Demands scaled = scaleToTargetMLU(topology, base, TARGET_MLU);

        Random rng = new Random(seed + 1);
        List<Demands> matrices = new ArrayList<>();
        matrices.add(scaled);

        for (int m = 0; m < numMatrices; m++) {

            // Step 2: find a balanced bipartition via Karger's algorithm
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
            if (visualizeBipartitions) {
                TopologyViewer.show(topology, assignment);
            }

            // Step 3: amplify all cross-cut demands (both directions)
            double alpha    = MIN_ALPHA + rng.nextDouble() * (MAX_ALPHA - MIN_ALPHA);
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

    // Retry Karger until a balanced bipartition is found or MAX_TRIALS is exhausted.

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

            // Sum weights of all cross-component edges (weight = 1/capacity)
            double totalWeight = 0.0;
            for (int e = 0; e < topology.nEdges; e++) {
                int rs = findRoot(parent, topology.edgeSrc[e]);
                int rd = findRoot(parent, topology.edgeDest[e]);
                if (rs != rd) {
                    double cap = topology.edgeCapacity[e];
                    totalWeight += (cap > 0) ? 1.0 / cap : 1.0;
                }
            }
            if (totalWeight <= 0) break; // graph is already disconnected

            // Sample one cross-component edge proportional to its weight
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

        // Identify the two remaining super-node roots
        int root0 = -1, root1 = -1;
        for (int v = 0; v < n; v++) {
            int r = findRoot(parent, v);
            if (root0 == -1) {
                root0 = r;
            } else if (r != root0 && root1 == -1) {
                root1 = r;
            }
        }
        if (root1 == -1) return null; // degenerate: graph contracted to one component

        // Balance check: reject lopsided cuts
        if ((double) Math.min(sz[root0], sz[root1]) / n < MIN_BALANCE) return null;

        // Build assignment
        int[] assignment = new int[n];
        for (int v = 0; v < n; v++) {
            assignment[v] = (findRoot(parent, v) == root0) ? 0 : 1;
        }
        return assignment;
    }

    // Union-Find: find root with full path compression (two-pass)

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

    // Union-Find: merge two components by rank, accumulating size in the new root

    private void mergeComponents(int[] parent, int[] rank, int[] sz, int a, int b) {
        if (a == b) return;
        if (rank[a] < rank[b]) { int tmp = a; a = b; b = tmp; }
        parent[b] = a;
        sz[a] += sz[b];
        if (rank[a] == rank[b]) rank[a]++;
    }

    // Scale demands so that MCF-optimal MLU equals targetMLU

    private static Demands scaleToTargetMLU(Topology topology, Demands matrix, double targetMLU) {
        double currentMLU = MCF.computeOptimalMLU(topology, matrix);
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
}
