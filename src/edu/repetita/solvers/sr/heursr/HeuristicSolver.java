package edu.repetita.solvers.sr.heursr;

import edu.repetita.core.Demands;
import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;

import java.util.HashMap;
import java.util.Map;

/**
 * A heuristic solver for the SR problem with at most 2 segments (1 intermediate node).
 * The heuristic is a local search that starts from the direct shortest paths for each demand,
 * and iteratively tries to improve the objective function by changing the intermediate node
 * of one demand at a time.
 */
public class HeuristicSolver {
    Topology topology;
    Demands demands;
    int maxSegments; // TODO use this
    long startTime;

    private final EdgeFlowVector[][] shortestPathsCache;
    private final double[] linkLoads;
    private final double[] bestLinkLoads;
    private final int[][] currentSrPaths; // For now limited to 2-SR
    private final int[][] bestSrPaths; // For now limited to 2-SR
    private final int P_NORM = 36;

    /* Local variables for the local search */
    double sumPowerP = 0.0;

    public HeuristicSolver(Topology topology, Demands demands, int maxSegments) {
        this.topology = topology;
        this.demands = demands;
        this.maxSegments = maxSegments;
        this.shortestPathsCache = new EdgeFlowVector[topology.nNodes][topology.nNodes];
        this.linkLoads = new double[topology.nEdges];
        this.bestLinkLoads = new double[topology.nEdges];
        this.currentSrPaths = new int[topology.nNodes][topology.nNodes];
        this.bestSrPaths = new int[topology.nNodes][topology.nNodes];
        this.startTime = System.currentTimeMillis();
    }

    public double solve(long endTime) {
        computeShortestPaths(topology);
        initLinkLoads();
        try {
            simulatedAnnealing(endTime);
        } catch (IntermediateNodeIsEndException e) {
            System.err.println("Error: Intermediate node cannot be the destination");
            System.exit(0);
        }
        updateBestLinkLoads();
        return computeBestUMax();
    }

    private void initLinkLoads() {
        /* Initialize link loads to zero */
        for (int e = 0; e < topology.nEdges; e++) {
            linkLoads[e] = 0.0;
        }
        /* Initialize SR paths to direct shortest paths for each demand */
        for (int dem = 0; dem < demands.nDemands; dem++) {
            int s = demands.source[dem];
            int t = demands.dest[dem];
            double d = demands.amount[dem];
            currentSrPaths[s][t] = s; // Direct path
            bestSrPaths[s][t] = s;
            try {
                addLoad(s, currentSrPaths[s][t], t, d, true);
            } catch (IntermediateNodeIsEndException e) {
                System.err.println("Error: Intermediate node cannot be the destination");
                System.exit(0);
            }
        }
        /* Initialize best link loads */
        System.arraycopy(linkLoads, 0, bestLinkLoads, 0, topology.nEdges);
    }

    private void simulatedAnnealing(long endTime) throws IntermediateNodeIsEndException {
        double initialTemperature = computeInitialTemperature();
        double temperature = initialTemperature;
        double coolingRate = 0.98;
        double minTemperature = initialTemperature * 1e-5;
        int iterationsPerTemp = Math.max(100, demands.nDemands * topology.nNodes / 256);

        double bestUMax = computeCurrentUMax();
        double currentObjectiveValue = computeObjFctSumPowerP();
        double bestObjectiveValue = currentObjectiveValue;
        java.util.Random rand = new java.util.Random();

        while (System.currentTimeMillis() < endTime && temperature > minTemperature) {
            for (int iter = 0; iter < iterationsPerTemp; iter++) {
                /* Randomly select a demand */
                int dem = rand.nextInt(demands.nDemands);
                int s = demands.source[dem];
                int t = demands.dest[dem];
                double d = demands.amount[dem];

                /* Randomly select a new intermediate node */
                int newIntermediate = rand.nextInt(topology.nNodes);
                while (newIntermediate == currentSrPaths[s][t] || newIntermediate == t) {
                    newIntermediate = rand.nextInt(topology.nNodes);
                }

                /* Load the new solution */
                removeLoad(s, currentSrPaths[s][t], t, d, true);
                addLoad(s, newIntermediate, t, d, true);
                /* Compute diff */
                double newObjectiveValue = computeObjFctSumPowerP();
                double delta = currentObjectiveValue - newObjectiveValue;
                if (delta > 0 || Math.exp(delta / temperature) > rand.nextDouble()) {
                    /* Accept the new solution */
                    //System.out.println("Accept w/ old objective value: " + currentObjectiveValue + " -> " + newObjectiveValue + " after seconds: " + ((System.currentTimeMillis() - startTime)/1000.0));
                    currentSrPaths[s][t] = newIntermediate;
                    currentObjectiveValue = newObjectiveValue;

                    /* Update the best solution if needed */
                    if (currentObjectiveValue < bestObjectiveValue) {
                        bestObjectiveValue = currentObjectiveValue;
                        double currentUMax = computeCurrentUMax();
                        if (currentUMax < bestUMax) {
                            /* Update best paths */
                            bestUMax = currentUMax;
                            for (int i = 0; i < topology.nNodes; i++) {
                                System.arraycopy(currentSrPaths[i], 0, bestSrPaths[i], 0, topology.nNodes);
                            }
                            /* Update best link loads */
                            System.arraycopy(linkLoads, 0, bestLinkLoads, 0, topology.nEdges);
                        }

                    }
                } else {
                    /* Revert to the old solution */
                    removeLoad(s, newIntermediate, t, d, true);
                    addLoad(s, currentSrPaths[s][t], t, d, true);
                }
            }
            temperature *= coolingRate;
        }
    }

    private double computeInitialTemperature() {
        return Math.pow(2, -10); // TODO tune this
    }

    private void firstFitLocalSearch(long endTime) throws IntermediateNodeIsEndException {
        int lastDemandImproved = 0;
        int currentDemand = 0;
        int currentNode = 0;
        double lastObjectiveValue = computeObjFctFromScratch();
        double currentObjectiveValue;
        do {
            int s = demands.source[currentDemand];
            int t = demands.dest[currentDemand];
            double demand = demands.amount[currentDemand];
            removeLoad(s, currentSrPaths[s][t], t, demand, false);
            do {
                if (currentNode == currentSrPaths[s][t] || currentNode == t) {
                    currentNode++;
                    continue;
                }
                addLoad(s, currentNode, t, demand, false);
                currentObjectiveValue = computeObjFctFromScratch();
                if (currentObjectiveValue < lastObjectiveValue) {
                    System.out.println("Improved objective value: " + lastObjectiveValue + " -> " + currentObjectiveValue + "after seconds: " + ((System.currentTimeMillis() - startTime)/1000.0));
                    System.out.println("Demand " + currentDemand + " (" + s + "->" + t + "), new intermediate node: " + currentNode);
                    lastObjectiveValue = currentObjectiveValue;
                    currentSrPaths[s][t] = currentNode;
                    lastDemandImproved = currentDemand;
                }
                removeLoad(s, currentNode, t, demand, false);
                currentNode++;
            } while(currentNode < topology.nNodes);
            // Restore the load for the current demand with its (possibly) new path
            addLoad(s, currentSrPaths[s][t], t, demand, false);
            currentNode = 0;
            currentDemand = (currentDemand + 1) % demands.nDemands;
        } while(System.currentTimeMillis() < endTime && currentDemand != lastDemandImproved);
    }

    private double computeCurrentUMax() {
        double uMax = 0.0;
        for (int e = 0; e < topology.nEdges; e++) {
            double util = linkLoads[e] / topology.edgeCapacity[e];
            if (util > uMax) {
                uMax = util;
            }
        }
        return uMax;
    }

    private double computeBestUMax() {
        double UMax = 0.0;
        for (int e = 0; e < topology.nEdges; e++) {
            double util = bestLinkLoads[e] / topology.edgeCapacity[e];
            if (util > UMax) {
                UMax = util;
            }
        }
        return UMax;
    }

    private void updateBestLinkLoads(){
        // Update best link loads based on bestSrPaths
        for (int e = 0; e < topology.nEdges; e++) {
            bestLinkLoads[e] = 0.0;
        }
        for (int dem = 0; dem < demands.nDemands; dem++) {
            int s = demands.source[dem];
            int t = demands.dest[dem];
            int intermediate = bestSrPaths[s][t];
            double d = demands.amount[dem];
            if (intermediate == s) { // Direct path
                EdgeFlowVector spVec = shortestPathsCache[s][t];
                for (int i = 0; i < spVec.edgeIds.length; i++) {
                    bestLinkLoads[spVec.edgeIds[i]] += d * spVec.frac[i];
                }
            } else if (intermediate == t) {
                System.err.println("Error: Intermediate node cannot be the destination");
                System.exit(0);
            } else {
                EdgeFlowVector spVec1 = shortestPathsCache[s][intermediate];
                EdgeFlowVector spVec2 = shortestPathsCache[intermediate][t];
                for (int i = 0; i < spVec1.edgeIds.length; i++) {
                    bestLinkLoads[spVec1.edgeIds[i]] += d * spVec1.frac[i];
                }
                for (int i = 0; i < spVec2.edgeIds.length; i++) {
                    bestLinkLoads[spVec2.edgeIds[i]] += d * spVec2.frac[i];
                }
            }
        }
    }

    /**
     * Computes the objective function value as the p-norm of the link utilizations,
     * Be careful that it is based on sumPowerP, which must be updated when linkLoad changes
     * @return the objective function value
     */
    double computeObjFctSumPowerP() {
        return Math.pow(sumPowerP, 1.0/P_NORM);
    }

    double computeObjFctFromScratch() {
        sumPowerP = 0.0;
        for (int e = 0; e < topology.nEdges; e++) {
            double util = linkLoads[e] / topology.edgeCapacity[e];
            if (util != 0.0) {
                sumPowerP += Math.pow(util, P_NORM);
            }
        }
        return Math.pow(sumPowerP, 1.0/P_NORM);
    }


    private void removeLoad(int s, int intermediate, int t, double demand, boolean updateSumPowerP) throws IntermediateNodeIsEndException {
        applyPaths(s, intermediate, t, -demand, updateSumPowerP);
    }

    private void addLoad(int s, int intermediate, int t, double demand, boolean updateSumPowerP) throws IntermediateNodeIsEndException {
        applyPaths(s, intermediate, t, demand, updateSumPowerP);
    }

    private void applyPaths(int s, int intermediate, int t, double demand, boolean updateSumPowerP) throws IntermediateNodeIsEndException {
        if (intermediate == s) { // Direct path
            EdgeFlowVector spVec = shortestPathsCache[s][t];
            for (int i = 0; i < spVec.edgeIds.length; i++) {
                updateEdge(spVec.edgeIds[i], demand * spVec.frac[i], updateSumPowerP);
            }
        } else if (intermediate == t) {
            throw new IntermediateNodeIsEndException("");
        } else {
            EdgeFlowVector spVec1 = shortestPathsCache[s][intermediate];
            EdgeFlowVector spVec2 = shortestPathsCache[intermediate][t];
            for (int i = 0; i < spVec1.edgeIds.length; i++) {
                updateEdge(spVec1.edgeIds[i], demand * spVec1.frac[i], updateSumPowerP);
            }
            for (int i = 0; i < spVec2.edgeIds.length; i++) {
                updateEdge(spVec2.edgeIds[i], demand * spVec2.frac[i], updateSumPowerP);
            }
        }
    }

    private void updateEdge(int edgeId, double deltaLoad, boolean updateSumPowerP) {
        if (deltaLoad == 0.0) return;
        double capacity = topology.edgeCapacity[edgeId];
        double oldUtil = linkLoads[edgeId] / capacity;
        if (oldUtil != 0.0 && updateSumPowerP) {
            sumPowerP -= Math.pow(oldUtil, P_NORM);
        }
        linkLoads[edgeId] += deltaLoad;
        double newUtil = linkLoads[edgeId] / capacity;
        if (newUtil != 0.0 && updateSumPowerP) {
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
