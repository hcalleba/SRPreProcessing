package edu.repetita.traffic.Generator;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.viz.TopologyViewer;

import java.util.*;

/**
 * Generates traffic matrices that stress a balanced min-cut of the topology,
 * discovered via Karger's randomised contraction algorithm.
 *
 * Algorithm:
 * - Base matrix: gravitational model (Nucci et al. 2005), scaled to MLU = 1.
 * - Karger cut: at each step, a uniformly random cross-component edge is
 *   contracted until two super-nodes remain. The result is accepted only if
 *   the smaller partition holds at least MIN_BALANCE (20%) of all nodes —
 *   this prevents degenerate leaf cuts — and if the bipartition has not been
 *   produced before, ensuring each matrix stresses a distinct cut.
 * - Amplification: all cross-cut OD pairs (i∈S1↔j∈S2 in both directions)
 *   are multiplied by α ~ Uniform[boostLow, boostHigh], sampled independently
 *   per demand.
 * - Normalisation: the boosted matrix is re-scaled to MLU = 1 via MCF.
 *
 * Each call to {@link #generate} produces one base matrix plus numMatrices
 * cut-stressed matrices, each using an independently sampled Karger cut.
 */
public class TrafficMatrixGeneratorKarger extends TrafficMatrixGenerator {

    private static final double MIN_BALANCE = 0.20;
    private static final int    MAX_TRIALS  = 10000;

    public TrafficMatrixGeneratorKarger() {
    }

    @Override
    public List<Demands> generate(Setting setting) {
        Topology topology = setting.getTopology();

        Demands base   = new BaseMatrixGenerator(topology, 1.0, seed).generate();
        Demands scaled = scaleToTargetMLU(topology, base, TARGET_MLU);

        Random rng = new Random(seed + 1);
        List<Demands> matrices = new ArrayList<>();
        matrices.add(scaled);

        Set<String> seenCuts = new HashSet<>();

        for (int m = 0; m < numMatrices; m++) {

            int[] assignment = findBalancedCut(topology, rng, seenCuts);
            if (assignment == null) {
                System.err.printf("Warning: no unique balanced cut found for matrix %d after %d trials%n",
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

            Demands boosted = new Demands(scaled);
            int boostedCount = 0;
            for (int d = 0; d < boosted.nDemands; d++) {
                if (assignment[boosted.source[d]] != assignment[boosted.dest[d]]) {
                    boosted.amount[d] *= sampleBoost(rng);
                    boostedCount++;
                }
            }
            System.out.printf("  Amplified %d cross-cut demands (α ∈ [%.2f, %.2f])%n",
                    boostedCount, boostLow, boostHigh);

            matrices.add(boosted);
            System.out.println("Generated Karger matrix " + (m + 1));
        }

        System.out.println("Total matrices: " + matrices.size()
                + " (1 base + " + (matrices.size() - 1) + " Karger-cut)");
        return matrices;
    }

    private int[] findBalancedCut(Topology topology, Random rng, Set<String> seenCuts) {
        for (int trial = 0; trial < MAX_TRIALS; trial++) {
            int[] result = runKargerTrial(topology, rng);
            if (result == null) continue;
            String key = encodeCut(result);
            if (seenCuts.add(key)) return result;
        }
        return null;
    }

    /**
     * Canonical encoding of a bipartition: sorted node indices of the smaller
     * half (or partition-0 nodes when both halves are equal in size). Symmetric —
     * swapping the two sides produces the same key.
     */
    private String encodeCut(int[] assignment) {
        int countZero = 0;
        for (int a : assignment) if (a == 0) countZero++;
        int targetSide = (countZero * 2 <= assignment.length) ? 0 : 1;
        StringBuilder sb = new StringBuilder();
        for (int v = 0; v < assignment.length; v++) {
            if (assignment[v] == targetSide) sb.append(v).append(',');
        }
        return sb.toString();
    }

    /**
     * One trial of Karger contraction with uniform random edge selection.
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

            // Collect all edges that cross between distinct super-nodes
            List<Integer> crossEdges = new ArrayList<>();
            for (int e = 0; e < topology.nEdges; e++) {
                int rs = findRoot(parent, topology.edgeSrc[e]);
                int rd = findRoot(parent, topology.edgeDest[e]);
                if (rs != rd) crossEdges.add(e);
            }
            if (crossEdges.isEmpty()) break;

            // Pick one uniformly at random and contract it
            int chosen = crossEdges.get(rng.nextInt(crossEdges.size()));
            int contractA = findRoot(parent, topology.edgeSrc[chosen]);
            int contractB = findRoot(parent, topology.edgeDest[chosen]);

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
