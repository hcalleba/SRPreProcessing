package edu.repetita.traffic.clusterer;

import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Balanced clustering: same center selection as {@link FirstClusterer}
 * (farthest-first among high-capacity nodes), but uses a capacity-constrained
 * assignment that prevents any single cluster from holding more than
 * {@code ceil(nNodes / K) + 1} nodes.
 * <p>
 * This avoids the "first cluster gets half the graph" problem that occurs
 * when the highest-capacity node is also the most central hub.
 */
public class SecondClusterer implements TopologyClusterer {

    private final Topology topology;
    private final int K;

    /** cluster[v] = cluster index assigned to node v */
    private int[] cluster;
    /** centers[k] = node index of the center of cluster k */
    private int[] centers;
    /** effective number of clusters after merging degenerate ones */
    private int effectiveK;

    public SecondClusterer(Topology topology, int K) {
        this.topology = topology;
        this.K = K;
    }

    @Override
    public int[] run() {
        double[] score = computeNodeCapacityScores();
        int[][] dist = new ShortestPaths(topology).distance;
        centers = selectCenters(score, dist);
        cluster = assignNodesBalanced(dist);
        effectiveK = mergeDegenerateClusters(dist);
        return cluster;
    }

    @Override
    public int getEffectiveK() {
        return effectiveK;
    }

    @Override
    public int[] getCenters() {
        return centers;
    }

    // ---- Step 1: node capacity score = sum of capacities of all incident edges ----

    private double[] computeNodeCapacityScores() {
        double[] score = new double[topology.nNodes];
        for (int e = 0; e < topology.nEdges; e++) {
            score[topology.edgeSrc[e]]  += topology.edgeCapacity[e];
            score[topology.edgeDest[e]] += topology.edgeCapacity[e];
        }
        return score;
    }

    // ---- Step 2: select K centers – identical to FirstClusterer (farthest-first) ----

    private int[] selectCenters(double[] score, int[][] dist) {
        int nNodes = topology.nNodes;

        double totalScore = 0.0;
        for (int i = 0; i < nNodes; i++) totalScore += score[i];
        double avgScore = totalScore / nNodes;

        Integer[] ranked = new Integer[nNodes];
        for (int i = 0; i < nNodes; i++) ranked[i] = i;
        Arrays.sort(ranked, (a, b) -> Double.compare(score[b], score[a]));

        boolean[] isCandidate = new boolean[nNodes];
        int numCandidates = 0;
        for (int i = 0; i < nNodes; i++) {
            if (score[i] >= avgScore) {
                isCandidate[i] = true;
                numCandidates++;
            }
        }

        // Print the names of all candidates
        for (int pok = 0; pok < nNodes; pok++) {
            int i = ranked[pok];
            if (isCandidate[i]) {
                System.out.printf("Candidate %d: node=%s, score=%.0f%n", pok, topology.nodeLabel[i], score[i]);
            }
        }

        // ensure at least K candidates
        if (numCandidates < K) {
            for (int i = 0; i < nNodes && numCandidates < K; i++) {
                if (!isCandidate[ranked[i]]) {
                    isCandidate[ranked[i]] = true;
                    numCandidates++;
                }
            }
        }

        int[] chosen = new int[K];
        boolean[] isCenterChosen = new boolean[nNodes];

        // first center: highest-score candidate
        chosen[0] = ranked[0];
        isCenterChosen[chosen[0]] = true;

        // subsequent centers: candidate maximizing min-distance to already chosen centers
        for (int k = 1; k < K; k++) {
            int bestMinDist = -1;
            int bestNode = -1;
            for (int v = 0; v < nNodes; v++) {
                if (!isCandidate[v] || isCenterChosen[v]) continue;
                int minDist = Integer.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    minDist = Math.min(minDist, dist[v][chosen[j]]);
                }
                if (minDist > bestMinDist) {
                    bestMinDist = minDist;
                    bestNode = v;
                }
            }
            chosen[k] = bestNode;
            isCenterChosen[bestNode] = true;
        }

        return chosen;
    }

    // ---- Step 3 (balanced): assign nodes to nearest center, capping cluster sizes ----

    /**
     * Assigns every node to a center using a priority-queue sweep that respects
     * a maximum cluster size of {@code ceil(nNodes / K) + 1}.
     * <p>
     * Nodes are processed in increasing distance order. When a node's preferred
     * cluster is full, it falls back to its next-nearest center.
     */
    private int[] assignNodesBalanced(int[][] dist) {
        int nNodes = topology.nNodes;
        int maxSize = (int) Math.ceil((double) nNodes / K) + 1;

        int[] assignment = new int[nNodes];
        Arrays.fill(assignment, -1);
        int[] clusterSize = new int[K];

        // Build a preference list for each node: centers sorted by distance
        int[][] preference = new int[nNodes][K];
        for (int v = 0; v < nNodes; v++) {
            Integer[] order = new Integer[K];
            for (int k = 0; k < K; k++) order[k] = k;
            final int node = v;
            Arrays.sort(order, Comparator.comparingInt(a -> dist[node][centers[a]]));
            for (int r = 0; r < K; r++) preference[v][r] = order[r];
        }

        // rank[v] = which preference index node v is currently trying
        int[] rank = new int[nNodes];

        // PQ entries: {distance, nodeIndex}
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
        for (int v = 0; v < nNodes; v++) {
            pq.offer(new int[]{dist[v][centers[preference[v][0]]], v});
        }

        while (!pq.isEmpty()) {
            int[] entry = pq.poll();
            int v = entry[1];
            if (assignment[v] != -1) continue;

            int targetCluster = preference[v][rank[v]];
            if (clusterSize[targetCluster] < maxSize) {
                assignment[v] = targetCluster;
                clusterSize[targetCluster]++;
            } else {
                // preferred cluster is full – try next preference
                rank[v]++;
                if (rank[v] < K) {
                    pq.offer(new int[]{dist[v][centers[preference[v][rank[v]]]], v});
                } else {
                    // all clusters at cap – force-assign to original nearest (edge case with tight slack)
                    assignment[v] = preference[v][0];
                    clusterSize[preference[v][0]]++;
                }
            }
        }
        return assignment;
    }

    // ---- Step 4: merge clusters with fewer than 2 nodes ----

    private int mergeDegenerateClusters(int[][] dist) {
        boolean merged = true;
        int currentK = K;

        while (merged) {
            merged = false;
            int[] clusterSize = new int[currentK];
            for (int v = 0; v < topology.nNodes; v++) clusterSize[cluster[v]]++;

            for (int k = 0; k < currentK; k++) {
                if (clusterSize[k] >= 2) continue;

                int nearestCluster = -1;
                int minDist = Integer.MAX_VALUE;
                for (int j = 0; j < currentK; j++) {
                    if (j == k) continue;
                    int d = dist[centers[k]][centers[j]];
                    if (d < minDist) {
                        minDist = d;
                        nearestCluster = j;
                    }
                }

                for (int v = 0; v < topology.nNodes; v++) {
                    if (cluster[v] == k) cluster[v] = nearestCluster;
                }

                for (int v = 0; v < topology.nNodes; v++) {
                    if (cluster[v] > k) cluster[v]--;
                }

                int[] newCenters = new int[currentK - 1];
                int idx = 0;
                for (int j = 0; j < currentK; j++) {
                    if (j == k) continue;
                    newCenters[idx++] = centers[j];
                }
                centers = newCenters;
                currentK--;
                merged = true;
                break;
            }
        }

        return currentK;
    }

    // ---- Utility: print cluster summary ----

    @Override
    public void printSummary() {
        int[] sizes = new int[effectiveK];
        double[] capacities = new double[effectiveK];
        double[] score = computeNodeCapacityScores();

        for (int v = 0; v < topology.nNodes; v++) {
            sizes[cluster[v]]++;
            capacities[cluster[v]] += score[v];
        }

        System.out.println("=== Balanced Clustering Summary (K=" + effectiveK + ") ===");
        for (int k = 0; k < effectiveK; k++) {
            System.out.printf("Cluster %d: center=%s, nodes=%d, totalCapacity=%.0f%n",
                    k, topology.nodeLabel[centers[k]], sizes[k], capacities[k]);
        }

        System.out.println("--- Inter-cluster cut capacities ---");
        for (int e = 0; e < topology.nEdges; e++) {
            int src = topology.edgeSrc[e];
            int dst = topology.edgeDest[e];
            if (cluster[src] != cluster[dst]) {
                System.out.printf("  %s -> %s (clusters %d->%d): capacity=%.0f%n",
                        topology.nodeLabel[src], topology.nodeLabel[dst],
                        cluster[src], cluster[dst], topology.edgeCapacity[e]);
            }
        }
    }
}
