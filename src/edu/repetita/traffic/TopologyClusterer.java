package edu.repetita.traffic;

import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;

import java.util.Arrays;

/**
 * Clusters topology nodes into K regions for traffic matrix generation.
 *
 * Algorithm:
 * 1. Score nodes by total incident capacity
 * 2. Select K cluster centers using farthest-first among nodes with capacity >= average capacity
 * 3. Assign every node to its nearest center (by OSPF shortest path)
 * 4. Merge degenerate clusters (fewer than 2 nodes total)
 */
public class TopologyClusterer {

    private final Topology topology;
    private final int K;

    /** cluster[v] = cluster index assigned to node v */
    private int[] cluster;
    /** centers[k] = node index of the center of cluster k */
    private int[] centers;
    /** effective number of clusters after merging degenerate ones */
    private int effectiveK;

    public TopologyClusterer(Topology topology, int K) {
        this.topology = topology;
        this.K = K;
    }

    public int[] run() {
        double[] score = computeNodeCapacityScores();
        int[][] dist = new ShortestPaths(topology).distance;
        centers = selectCenters(score, dist);
        cluster = assignNodes(dist);
        effectiveK = mergeDegenerateClusters(dist);
        return cluster;
    }

    public int getEffectiveK() {
        return effectiveK;
    }

    public int[] getCenters() {
        return centers;
    }

    // Step 1: node capacity score = sum of capacities of all incident edges
    private double[] computeNodeCapacityScores() {
        double[] score = new double[topology.nNodes];
        for (int e = 0; e < topology.nEdges; e++) {
            score[topology.edgeSrc[e]]  += topology.edgeCapacity[e];
            score[topology.edgeDest[e]] += topology.edgeCapacity[e];
        }
        return score;
    }


    // Step 2: select K centers using farthest-first among nodes with capacity >= average
    private int[] selectCenters(double[] score, int[][] dist) {
        int nNodes = topology.nNodes;

        // restrict candidates to nodes with capacity >= average capacity
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

    // Step 3: assign each node to nearest center by OSPF distance
    private int[] assignNodes(int[][] dist) {
        int[] assignment = new int[topology.nNodes];
        for (int v = 0; v < topology.nNodes; v++) {
            int nearest = 0;
            int minDist = dist[v][centers[0]];
            for (int k = 1; k < K; k++) {
                if (dist[v][centers[k]] < minDist) {
                    minDist = dist[v][centers[k]];
                    nearest = k;
                }
            }
            assignment[v] = nearest;
        }
        return assignment;
    }

    // Step 4: merge clusters with fewer than 2 nodes into nearest neighbor cluster
    private int mergeDegenerateClusters(int[][] dist) {
        boolean merged = true;
        int currentK = K;

        while (merged) {
            merged = false;
            int[] clusterSize = new int[currentK];
            for (int v = 0; v < topology.nNodes; v++) clusterSize[cluster[v]]++;

            for (int k = 0; k < currentK; k++) {
                if (clusterSize[k] >= 2) continue;

                // find nearest other cluster center
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

                // reassign all nodes in cluster k to nearestCluster
                for (int v = 0; v < topology.nNodes; v++) {
                    if (cluster[v] == k) cluster[v] = nearestCluster;
                }

                // compact cluster indices: shift down all indices > k
                for (int v = 0; v < topology.nNodes; v++) {
                    if (cluster[v] > k) cluster[v]--;
                }
                // shift centers array
                int[] newCenters = new int[currentK - 1];
                int idx = 0;
                for (int j = 0; j < currentK; j++) {
                    if (j == k) continue;
                    newCenters[idx++] = centers[j];
                }
                centers = newCenters;
                currentK--;
                merged = true;
                break; // restart loop with updated state
            }
        }

        return currentK;
    }

    // Utility: print cluster summary
    public void printSummary() {
        int[] sizes = new int[effectiveK];
        double[] capacities = new double[effectiveK];
        double[] score = computeNodeCapacityScores();

        for (int v = 0; v < topology.nNodes; v++) {
            sizes[cluster[v]]++;
            capacities[cluster[v]] += score[v];
        }

        System.out.println("=== Clustering Summary (K=" + effectiveK + ") ===");
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

