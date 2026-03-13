package edu.repetita.traffic.clusterer;

import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Connected Weighted-Voronoi clustering.
 *
 * <p>Addresses two failure modes of the earlier clusterers:
 * <ol>
 *   <li><b>Oversized first cluster (FirstClusterer)</b>: avoided by not seeding the
 *       absolute highest-capacity node as the first center. Instead the first center
 *       maximises {@code capacity(v) × min_dist_to_any_other_candidate(v)}, which
 *       prefers an isolated high-capacity node over a central hub.</li>
 *   <li><b>Disconnected clusters (SecondClusterer)</b>: avoided by growing clusters
 *       with a Prim-style BFS. A node is only offered to cluster k when one of its
 *       direct neighbours has already been assigned to k, so every cluster is a
 *       connected subgraph by construction.</li>
 * </ol>
 *
 * <p>Assignment uses a weighted Voronoi score:
 * <pre>  score(v, k) = dOSPF(v, center_k) / capacity(center_k)</pre>
 * High-capacity centers therefore attract nodes over a wider radius without any
 * hard size cap.
 */
public class ThirdClusterer implements TopologyClusterer {

    private final Topology topology;
    private final int K;

    /** cluster[v] = cluster index assigned to node v */
    private int[] cluster;
    /** centers[k] = node index of the center of cluster k */
    private int[] centers;
    /** effective number of clusters after merging degenerate ones */
    private int effectiveK;

    public ThirdClusterer(Topology topology, int K) {
        this.topology = topology;
        this.K = K;
    }

    // -------------------------------------------------------------------------
    // TopologyClusterer interface
    // -------------------------------------------------------------------------

    @Override
    public int[] run() {
        double[] nodeCapacity = computeNodeCapacityScores();
        int[][] dist = new ShortestPaths(topology).distance;
        centers = selectCenters(nodeCapacity, dist);
        cluster = assignNodesConnectedVoronoi(nodeCapacity, dist);
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

    // Step 1 – node capacity score

    private double[] computeNodeCapacityScores() {
        double[] score = new double[topology.nNodes];
        for (int e = 0; e < topology.nEdges; e++) {
            score[topology.edgeSrc[e]]  += topology.edgeCapacity[e];
            score[topology.edgeDest[e]] += topology.edgeCapacity[e];
        }
        return score;
    }

    // Step 2 – center selection
    //
    // Candidates: nodes with capacity >= average (same as before).
    //
    // First center: maximise capacity(v) * min_dist_to_any_other_candidate(v).
    //   This prefers an isolated high-capacity node over the central hub, so the
    //   first cluster does not immediately dominate the entire graph.
    //
    // Subsequent centers: standard farthest-first among candidates.

    private int[] selectCenters(double[] nodeCapacity, int[][] dist) {
        int nNodes = topology.nNodes;

        double totalCap = 0.0;
        for (double c : nodeCapacity) totalCap += c;
        double avgCap = totalCap / nNodes;

        Integer[] ranked = new Integer[nNodes];
        for (int i = 0; i < nNodes; i++) ranked[i] = i;
        Arrays.sort(ranked, (a, b) -> Double.compare(nodeCapacity[b], nodeCapacity[a]));

        boolean[] isCandidate = new boolean[nNodes];
        int numCandidates = 0;
        for (int i = 0; i < nNodes; i++) {
            if (nodeCapacity[i] >= avgCap) {
                isCandidate[i] = true;
                numCandidates++;
            }
        }

        // Ensure at least K candidates
        if (numCandidates < K) {
            for (int i = 0; i < nNodes && numCandidates < K; i++) {
                if (!isCandidate[ranked[i]]) {
                    isCandidate[ranked[i]] = true;
                    numCandidates++;
                }
            }
        }

        // Print candidates
        for (int pok = 0; pok < nNodes; pok++) {
            int i = ranked[pok];
            if (isCandidate[i]) {
                System.out.printf("Candidate %d: node=%s, capacity=%.0f%n",
                        pok, topology.nodeLabel[i], nodeCapacity[i]);
            }
        }

        int[] chosen = new int[K];
        boolean[] isCenterChosen = new boolean[nNodes];

        // First center: maximise isolation score = capacity * min_dist_to_other_candidate
        double bestIsolation = -1.0;
        int firstCenter = ranked[0]; // fallback to highest capacity
        for (int v = 0; v < nNodes; v++) {
            if (!isCandidate[v]) continue;
            int minDistToOther = Integer.MAX_VALUE;
            for (int w = 0; w < nNodes; w++) {
                if (w == v || !isCandidate[w]) continue;
                if (dist[v][w] < minDistToOther) minDistToOther = dist[v][w];
            }
            if (minDistToOther == Integer.MAX_VALUE) minDistToOther = 0;
            double isolationScore = nodeCapacity[v] * minDistToOther;
            if (isolationScore > bestIsolation) {
                bestIsolation = isolationScore;
                firstCenter = v;
            }
        }
        chosen[0] = firstCenter;
        isCenterChosen[firstCenter] = true;
        System.out.printf("First center: node=%s (isolation-score=%.0f)%n",
                topology.nodeLabel[firstCenter], bestIsolation);

        // Remaining centers: farthest-first
        for (int k = 1; k < K; k++) {
            int bestMinDist = -1;
            int best = -1;
            for (int v = 0; v < nNodes; v++) {
                if (!isCandidate[v] || isCenterChosen[v]) continue;
                int minDist = Integer.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    minDist = Math.min(minDist, dist[v][chosen[j]]);
                }
                if (minDist > bestMinDist) {
                    bestMinDist = minDist;
                    best = v;
                }
            }
            chosen[k] = best;
            isCenterChosen[best] = true;
        }

        return chosen;
    }

    // Step 3 – connectivity-preserving weighted Voronoi assignment (Prim-style)
    //
    // score(v, k) = dOSPF(v, center_k) / capacity(center_k)
    //
    // Clusters grow outward from their centers via BFS, restricted to direct
    // neighbours of already-assigned nodes.  Because a node is only offered to
    // cluster k when one of its neighbours belongs to k, every cluster forms a
    // connected subgraph.  High-capacity centers have a smaller score denominator
    // and therefore pull in nodes from a wider area.
    //
    // Both out-edges and in-edges are used so that undirected connectivity is
    // respected (the topology has paired directed edges for each physical link).

    private int[] assignNodesConnectedVoronoi(double[] nodeCapacity, int[][] dist) {
        int nNodes = topology.nNodes;

        int[] assignment = new int[nNodes];
        Arrays.fill(assignment, -1);

        // PQ entries: {score, nodeIndex (as double), clusterIndex (as double)}
        PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[0]));

        // Seed: assign each center and offer its neighbours
        for (int k = 0; k < K; k++) {
            assignment[centers[k]] = k;
            offerNeighbours(centers[k], k, nodeCapacity, dist, assignment, pq);
        }

        while (!pq.isEmpty()) {
            double[] entry = pq.poll();
            int v = (int) entry[1];
            int k = (int) entry[2];

            if (assignment[v] != -1) continue; // already claimed by an earlier (better) offer

            assignment[v] = k;
            offerNeighbours(v, k, nodeCapacity, dist, assignment, pq);
        }

        // Fallback for isolated nodes unreachable by BFS (disconnected graph)
        for (int v = 0; v < nNodes; v++) {
            if (assignment[v] == -1) {
                int nearest = 0;
                int minD = dist[v][centers[0]];
                for (int k = 1; k < K; k++) {
                    if (dist[v][centers[k]] < minD) {
                        minD = dist[v][centers[k]];
                        nearest = k;
                    }
                }
                assignment[v] = nearest;
            }
        }

        return assignment;
    }

    /**
     * Adds all unassigned neighbours of {@code v} to the priority queue as
     * candidates for cluster {@code k}.
     * Both outgoing and incoming edges are considered (undirected growth).
     */
    private void offerNeighbours(int v, int k, double[] nodeCapacity, int[][] dist,
                                  int[] assignment, PriorityQueue<double[]> pq) {
        double centerCap = nodeCapacity[centers[k]];
        if (centerCap <= 0.0) centerCap = 1.0; // guard against zero-capacity centers

        for (int e : topology.outEdges[v]) {
            int w = topology.edgeDest[e];
            if (assignment[w] == -1) {
                double score = dist[w][centers[k]] / centerCap;
                pq.offer(new double[]{score, w, k});
            }
        }
        for (int e : topology.inEdges[v]) {
            int w = topology.edgeSrc[e];
            if (assignment[w] == -1) {
                double score = dist[w][centers[k]] / centerCap;
                pq.offer(new double[]{score, w, k});
            }
        }
    }

    // Step 4 – merge degenerate clusters (< 2 nodes) – same as before

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
                    if (j != k) newCenters[idx++] = centers[j];
                }
                centers = newCenters;
                currentK--;
                merged = true;
                break; // restart with updated state
            }
        }

        return currentK;
    }

    // Utility – print summary

    @Override
    public void printSummary() {
        int[] sizes = new int[effectiveK];
        double[] capacities = new double[effectiveK];
        double[] score = computeNodeCapacityScores();

        for (int v = 0; v < topology.nNodes; v++) {
            sizes[cluster[v]]++;
            capacities[cluster[v]] += score[v];
        }

        System.out.println("=== Connected Voronoi Clustering Summary (K=" + effectiveK + ") ===");
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
