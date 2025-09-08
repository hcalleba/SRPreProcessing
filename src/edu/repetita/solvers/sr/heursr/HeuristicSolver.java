package edu.repetita.solvers.sr.heursr;

import edu.repetita.core.Demands;
import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HeuristicSolver {
    Topology topology;
    Demands demands;
    int maxSegments; // TODO use this
    double uMax;

    private final EdgeFlowVector[][] shortestPathsCache;
    private final double[] linkLoad;
    private final int[][] srPaths; // For now limited to 2-SR

    public HeuristicSolver(Topology topology, Demands demands, int maxSegments) {
        this.topology = topology;
        this.demands = demands;
        this.maxSegments = maxSegments;
        this.shortestPathsCache = new EdgeFlowVector[topology.nNodes][topology.nNodes];
        this.linkLoad = new double[topology.nEdges];
        this.srPaths = new int[topology.nNodes][topology.nNodes];
    }

    public double solve(long endTime) {
        /* Compute the shortest paths */
        computeShortestPaths(topology);
        /* Initialize link loads to zero */
        for (int e = 0; e < topology.nEdges; e++) {
            linkLoad[e] = 0.0;
        }
        /* Initialize SR paths to direct shortest paths for each demand */
        for (int dem = 0; dem < demands.nDemands; dem++) {
            int s = demands.source[dem];
            int t = demands.dest[dem];
            if (s == t) continue;
            double d = demands.amount[dem];
            EdgeFlowVector spVec = shortestPathsCache[s][t];
            if (spVec != null) {
                spVec.axpy(d, linkLoad);
            }
            srPaths[s][t] = -1; // -1 means direct path
        }
        /* Compute Umax */
        computeUmax();
        /* Start local search */
        return 0.0;
    }

    private void computeUmax() {
        uMax = 0.0;
        for (int e = 0; e < topology.nEdges; e++) {
            double util = linkLoad[e] / topology.edgeCapacity[e];
            if (util > uMax) {
                uMax = util;
            }
        }
    }

    private void computeShortestPaths(Topology topology) {
        ShortestPaths sp = new ShortestPaths(topology);
        sp.computeShortestPaths();
        int numNodes = topology.nNodes;

        for (int dest = 0; dest < numNodes; dest++) {
            sp.makeTopologicalOrdering(dest);
            int[] order = sp.topologicalOrdering;

            // Base case for destination itself
            shortestPathsCache[dest][dest] = new EdgeFlowVector(new int[0], new double[0]);

            // Process nodes in topological order
            for (int i_source = 0; i_source < numNodes; i_source++) {
                int source = order[i_source];

                // Edge cases
                if (source == dest) continue;
                if (sp.distance[source][dest] == Topology.INFINITE_DISTANCE) {
                    shortestPathsCache[source][dest] = null;
                    continue;
                }

                int k = sp.nSuccessors[dest][source];
                if (k == 0) { // Should not happen as it is already handled above
                    System.err.println("Error: No successors found for node " + source + " to destination " + dest);
                    System.exit(0);
                }
                int[] succEdges = sp.successorEdges[dest][source];
                int[] succNodes = sp.successorNodes[dest][source];
                double fracShare = 1.0 / k;
                Map<Integer, Double> fracOnEdgeMap = new HashMap<>();

                for (int p = 0; p < k; p++) {
                    int edgeId = succEdges[p];
                    int nextNode = succNodes[p];

                    fracOnEdgeMap.merge(edgeId, fracShare, Double::sum);

                    if (nextNode == dest) continue;

                    EdgeFlowVector nextVec = shortestPathsCache[nextNode][dest];
                    if (nextVec == null) { // Should not happen unless there is a link with cost <= 0 or else there was an error in the topological ordering
                        System.err.println("Error: No path from node " + nextNode + " to destination " + dest);
                        System.exit(0);
                    }
                    int len = nextVec.edgeIds.length;
                    for (int j = 0; j < len; j++) {
                        int nextEdgeId = nextVec.edgeIds[j];
                        double nextFrac = nextVec.frac[j];
                        fracOnEdgeMap.merge(nextEdgeId, fracShare * nextFrac, Double::sum);
                    }
                }
                // Convert Map to EdgeFlowVector
                int size = fracOnEdgeMap.size();
                int[] edgeIds = new int[size];
                double[] fracs = new double[size];
                int index = 0;
                for (Map.Entry<Integer, Double> entry : fracOnEdgeMap.entrySet()) {
                    edgeIds[index] = entry.getKey();
                    fracs[index] = entry.getValue();
                    index++;
                }
                shortestPathsCache[source][dest] = new EdgeFlowVector(edgeIds, fracs);
            }
        }
    }
}
