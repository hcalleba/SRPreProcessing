package edu.repetita.solvers.sr.heursr;

import edu.repetita.core.Demands;
import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;

import java.util.HashMap;
import java.util.Map;

public class HeuristicSolver {
    Topology topology;
    Demands demands;
    int maxSegments; // TODO use this
    double uMax;
    double bestObjectiveValue;
    long startTime;

    private final EdgeFlowVector[][] shortestPathsCache;
    private final double[] linkLoad;
    private final int[][] srPaths; // For now limited to 2-SR
    private final int P_NORM = 10;

    /* Local variables for the local search */
    int startDemand = 0;
    int startNode = 0;
    int currentDemand;
    int currentNode;
    double sumPowerP = 0.0;

    public HeuristicSolver(Topology topology, Demands demands, int maxSegments) {
        this.topology = topology;
        this.demands = demands;
        this.maxSegments = maxSegments;
        this.shortestPathsCache = new EdgeFlowVector[topology.nNodes][topology.nNodes];
        this.linkLoad = new double[topology.nEdges];
        this.srPaths = new int[topology.nNodes][topology.nNodes];
        this.startTime = System.currentTimeMillis();
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
            srPaths[s][t] = s; // Direct path
            try {
                addLoad(s, srPaths[s][t], t, d);
            } catch (IntermediateNodeIsEndException e) {
                System.err.println("Error: Intermediate node cannot be the destination");
                System.exit(0);
            }
        }
        /* Compute Umax */
        uMax = computeUmax();
        bestObjectiveValue = computeObjFct();
        /* Start local search */
        try {
            localSearch(endTime);
        } catch (IntermediateNodeIsEndException e) {
            System.err.println("Error: Intermediate node cannot be the destination");
            System.exit(0);
        }
        return computeUmax();
    }

    private void localSearch(long endTime) throws IntermediateNodeIsEndException {
        int lastDemandImproved = 0;
        int lastNodeImproved = 0;
        int currentDemand = 0;
        int currentNode = 0;
        double currentObjectiveValue = bestObjectiveValue;
        do {
            int s = demands.source[currentDemand];
            int t = demands.dest[currentDemand];
            double demand = demands.amount[currentDemand];
            removeLoad(s, srPaths[s][t], t, demand);
            do {
                try {
                    addLoad(s, currentNode, t, demand);
                } catch (IntermediateNodeIsEndException e) {
                    currentNode++;
                    continue;
                }
                currentObjectiveValue = computeObjFct();
                if (currentObjectiveValue < bestObjectiveValue) {
                    System.out.println("Improved objective value: " + bestObjectiveValue + " -> " + currentObjectiveValue + "after seconds: " + ((System.currentTimeMillis() - startTime)/1000.0));
                    System.out.println("Demand " + currentDemand + " (" + s + "->" + t + "), new intermediate node: " + currentNode);
                    bestObjectiveValue = currentObjectiveValue;
                    srPaths[s][t] = currentNode;
                    lastDemandImproved = currentDemand;
                    lastNodeImproved = currentNode;
                }
                removeLoad(s, currentNode, t, demand);
                currentNode++;
            } while(currentNode < topology.nNodes);
            // Restore the load for the current demand with its (possibly) new path
            addLoad(s, srPaths[s][t], t, demand);
            currentNode = 0;
            currentDemand = (currentDemand + 1) % demands.nDemands;
        } while(System.currentTimeMillis() < endTime && currentDemand != lastDemandImproved);
    }

    private double computeUmax() {
        double uMax = 0.0;
        for (int e = 0; e < topology.nEdges; e++) {
            double util = linkLoad[e] / topology.edgeCapacity[e];
            if (util > uMax) {
                uMax = util;
            }
        }
        return uMax;
    }

    /**
     * Computes the objective function value as the p-norm of the link utilizations, with p=64
     * Be careful that it is based on sumPowerP, which must be updated when linkLoad changes
     * @return the objective function value
     */
    double computeObjFct() {
        return Math.pow(sumPowerP, 1.0/P_NORM);
    }


    private void removeLoad(int s, int intermediate, int t, double demand) throws IntermediateNodeIsEndException {
        applyPaths(s, intermediate, t, -demand);
    }

    private void addLoad(int s, int intermediate, int t, double demand) throws IntermediateNodeIsEndException {
        applyPaths(s, intermediate, t, demand);
    }

    private void applyPaths(int s, int intermediate, int t, double demand) throws IntermediateNodeIsEndException {
        if (intermediate == s) { // Direct path
            EdgeFlowVector spVec = shortestPathsCache[s][t];
            for (int i = 0; i < spVec.edgeIds.length; i++) {
                updateEdge(spVec.edgeIds[i], demand * spVec.frac[i]);
            }
        } else if (intermediate == t) {
            throw new IntermediateNodeIsEndException("");
        } else {
            EdgeFlowVector spVec1 = shortestPathsCache[s][intermediate];
            EdgeFlowVector spVec2 = shortestPathsCache[intermediate][t];
            for (int i = 0; i < spVec1.edgeIds.length; i++) {
                updateEdge(spVec1.edgeIds[i], demand * spVec1.frac[i]);
            }
            for (int i = 0; i < spVec2.edgeIds.length; i++) {
                updateEdge(spVec2.edgeIds[i], demand * spVec2.frac[i]);
            }
        }
    }

    private void updateEdge(int edgeId, double deltaLoad) {
        if (deltaLoad == 0.0) return;
        double capacity = topology.edgeCapacity[edgeId];
        double oldUtil = linkLoad[edgeId] / capacity;
        if (oldUtil != 0.0) {
            sumPowerP -= Math.pow(oldUtil, P_NORM);
        }
        linkLoad[edgeId] += deltaLoad;
        double newUtil = linkLoad[edgeId] / capacity;
        if (newUtil != 0.0) {
            sumPowerP += Math.pow(newUtil, P_NORM);
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
