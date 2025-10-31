package edu.repetita.solvers.sr.heursr;

import edu.repetita.core.Demands;
import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;

import java.util.HashMap;
import java.util.Map;

/**
 * A heuristic solver for the SR problem with at most 2 segments (1 intermediate node).
 * Extended to handle multiple demand matrices robustly.
 * The heuristic is a local search that starts from the direct shortest paths for each demand,
 * and iteratively tries to improve the objective function by changing the intermediate node
 * of one demand at a time.
 */
public class HeuristicSolver {
    Topology topology;
    Demands[] demandMatrices;
    int maxSegments; // TODO use this
    long startTime;

    private final EdgeFlowVector[][] shortestPathsCache;
    private final double[][] linkLoads;
    private final double[][] bestLinkLoads;
    private final int[][] currentSrPaths; // For now limited to 2-SR
    private final int[][] bestSrPaths; // For now limited to 2-SR
    private final int P_NORM = 36;

    private final Map<NodePair, UnifiedDemand> unifiedDemands;
    private final NodePair[] demandPairs; // Array for easy iteration

    /* Edge-based selection data structures */
    private final Map<Integer, java.util.Set<NodePair>> edgeToDemands; // Maps edgeId to demands using it
    private final Map<NodePair, java.util.Set<Integer>> demandToEdges; // Maps demand to edges it uses

    /* Local variables for the local search */
    double[] sumPowerP;

    // Configuration for objective function
    private static final ObjectiveType OBJECTIVE_TYPE = ObjectiveType.AGGREGATE_ALL;

    public enum ObjectiveType {
        MAX_ACROSS_MATRICES,    // min-max: minimize max(||matrix_i||_p)
        PNORM_OF_MATRICES,      // minimize (∑||matrix_i||_p^p)^(1/p)
        AGGREGATE_ALL           // minimize (∑∑util[i][e]^p)^(1/p)
    }

    // Helper class for unified demand handling
    private record NodePair(int source, int dest) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NodePair)) return false;
            NodePair nodePair = (NodePair) o;
            return source == nodePair.source && dest == nodePair.dest;
        }
    }
    // Helper class for unified demand handling
    private static class UnifiedDemand {
        final double[] amounts; // Amount for each demand matrix (0 if not present)
        final int[] demandIndices; // Original demand index in each matrix (-1 if not present)

        UnifiedDemand(int numMatrices) {
            this.amounts = new double[numMatrices];
            this.demandIndices = new int[numMatrices];
            for (int i = 0; i < numMatrices; i++) {
                this.demandIndices[i] = -1; // -1 means demand not present in this matrix
            }
        }
    }

    public HeuristicSolver(Topology topology, Demands[] demandMatrices, int maxSegments) {
        this.topology = topology;
        this.demandMatrices = demandMatrices;
        this.maxSegments = maxSegments;
        this.shortestPathsCache = new EdgeFlowVector[topology.nNodes][topology.nNodes];
        this.linkLoads = new double[demandMatrices.length][topology.nEdges];
        this.bestLinkLoads = new double[demandMatrices.length][topology.nEdges];
        this.currentSrPaths = new int[topology.nNodes][topology.nNodes];
        this.bestSrPaths = new int[topology.nNodes][topology.nNodes];
        this.sumPowerP = new double[demandMatrices.length];
        this.startTime = System.currentTimeMillis();

        // Build unified demand structure
        this.unifiedDemands = buildUnifiedDemands();
        this.demandPairs = unifiedDemands.keySet().toArray(new NodePair[0]);

        // Initialize edge-to-demand tracking
        this.edgeToDemands = new HashMap<>();
        this.demandToEdges = new HashMap<>();
    }

    /**
     * Builds a unified view of all demands across all matrices
     */
    private Map<NodePair, UnifiedDemand> buildUnifiedDemands() {
        Map<NodePair, UnifiedDemand> unified = new HashMap<>();

        for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
            Demands demands = demandMatrices[matrixIdx];
            for (int demIdx = 0; demIdx < demands.nDemands; demIdx++) {
                NodePair pair = new NodePair(demands.source[demIdx], demands.dest[demIdx]);

                UnifiedDemand unifiedDem = unified.computeIfAbsent(pair,
                        k -> new UnifiedDemand(demandMatrices.length));

                unifiedDem.amounts[matrixIdx] = demands.amount[demIdx];
                unifiedDem.demandIndices[matrixIdx] = demIdx;
            }
        }
        return unified;
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
        /* Initialize link loads to zero for all matrices */
        for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
            for (int e = 0; e < topology.nEdges; e++) {
                linkLoads[matrixIdx][e] = 0.0;
            }
            sumPowerP[matrixIdx] = 0.0;
        }

        /* Initialize SR paths to direct shortest paths for each unified demand */
        for (NodePair pair : demandPairs) {
            currentSrPaths[pair.source][pair.dest] = pair.source; // Direct path
            bestSrPaths[pair.source][pair.dest] = pair.source;

            UnifiedDemand unifiedDem = unifiedDemands.get(pair);
            try {
                for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
                    if (unifiedDem.amounts[matrixIdx] > 0) {
                        addLoad(pair.source, currentSrPaths[pair.source][pair.dest],
                                pair.dest, unifiedDem.amounts[matrixIdx], matrixIdx, true);
                    }
                }
            } catch (IntermediateNodeIsEndException e) {
                System.err.println("Error: Intermediate node cannot be the destination");
                System.exit(0);
            }
        }

        /* Initialize best link loads */
        for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
            System.arraycopy(linkLoads[matrixIdx], 0, bestLinkLoads[matrixIdx], 0, topology.nEdges);
        }

        /* Build edge-to-demand mappings */
        buildEdgeToDemandMappings();
    }

    /**
     * Builds mappings between edges and demands that use them
     */
    private void buildEdgeToDemandMappings() {
        edgeToDemands.clear();
        demandToEdges.clear();

        for (NodePair pair : demandPairs) {
            java.util.Set<Integer> edgesUsed = new java.util.HashSet<>();
            int s = pair.source;
            int t = pair.dest;
            int intermediate = currentSrPaths[s][t];

            // Collect all edges used by this demand
            if (intermediate == s) { // Direct path
                EdgeFlowVector spVec = shortestPathsCache[s][t];
                if (spVec != null) {
                    for (int edgeId : spVec.edgeIds) {
                        edgesUsed.add(edgeId);
                    }
                }
            } else if (intermediate != t) { // Two-segment path
                EdgeFlowVector spVec1 = shortestPathsCache[s][intermediate];
                EdgeFlowVector spVec2 = shortestPathsCache[intermediate][t];
                if (spVec1 != null) {
                    for (int edgeId : spVec1.edgeIds) {
                        edgesUsed.add(edgeId);
                    }
                }
                if (spVec2 != null) {
                    for (int edgeId : spVec2.edgeIds) {
                        edgesUsed.add(edgeId);
                    }
                }
            }

            // Update mappings
            demandToEdges.put(pair, edgesUsed);
            for (int edgeId : edgesUsed) {
                edgeToDemands.computeIfAbsent(edgeId, k -> new java.util.HashSet<>()).add(pair);
            }
        }
    }

    /**
     * Updates edge-to-demand mappings when a demand's path changes
     */
    private void updateEdgeToDemandMappings(NodePair pair, int oldIntermediate, int newIntermediate) {
        int s = pair.source;
        int t = pair.dest;

        // Remove old edges
        java.util.Set<Integer> oldEdges = demandToEdges.getOrDefault(pair, new java.util.HashSet<>());
        for (int edgeId : oldEdges) {
            java.util.Set<NodePair> demands = edgeToDemands.get(edgeId);
            if (demands != null) {
                demands.remove(pair);
                if (demands.isEmpty()) {
                    edgeToDemands.remove(edgeId);
                }
            }
        }

        // Add new edges
        java.util.Set<Integer> newEdges = new java.util.HashSet<>();
        if (newIntermediate == s) { // Direct path
            EdgeFlowVector spVec = shortestPathsCache[s][t];
            if (spVec != null) {
                for (int edgeId : spVec.edgeIds) {
                    newEdges.add(edgeId);
                }
            }
        } else if (newIntermediate != t) { // Two-segment path
            EdgeFlowVector spVec1 = shortestPathsCache[s][newIntermediate];
            EdgeFlowVector spVec2 = shortestPathsCache[newIntermediate][t];
            if (spVec1 != null) {
                for (int edgeId : spVec1.edgeIds) {
                    newEdges.add(edgeId);
                }
            }
            if (spVec2 != null) {
                for (int edgeId : spVec2.edgeIds) {
                    newEdges.add(edgeId);
                }
            }
        }

        demandToEdges.put(pair, newEdges);
        for (int edgeId : newEdges) {
            edgeToDemands.computeIfAbsent(edgeId, k -> new java.util.HashSet<>()).add(pair);
        }
    }

    /**
     * Selects an edge with probability proportional to its aggregated utilization^power
     */
    private int selectEdgeByUtilization(java.util.Random rand) {
        // Compute aggregated utilization for each edge (sum across all matrices)
        double[] edgeUtilPower = new double[topology.nEdges];
        double totalWeight = 0.0;
        final double SELECTION_POWER = 2.0; // Quadratic bias towards high utilization

        for (int e = 0; e < topology.nEdges; e++) {
            double maxUtil = 0.0;
            for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
                double util = linkLoads[matrixIdx][e] / topology.edgeCapacity[e];
                maxUtil = Math.max(maxUtil, util);
            }
            if (maxUtil > 0.0) {
                edgeUtilPower[e] = Math.pow(maxUtil, SELECTION_POWER);
                totalWeight += edgeUtilPower[e];
            }
        }

        if (totalWeight == 0.0) {
            // Fallback: uniform random selection
            return rand.nextInt(topology.nEdges);
        }

        // Weighted random selection
        double r = rand.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (int e = 0; e < topology.nEdges; e++) {
            cumulative += edgeUtilPower[e];
            if (cumulative >= r) {
                return e;
            }
        }

        return topology.nEdges - 1; // Should rarely reach here
    }

    /**
     * Selects a demand from those using a given edge
     */
    private NodePair selectDemandFromEdge(int edgeId, java.util.Random rand) {
        java.util.Set<NodePair> demandsUsingEdge = edgeToDemands.get(edgeId);
        if (demandsUsingEdge == null || demandsUsingEdge.isEmpty()) {
            // Fallback: random demand
            return demandPairs[rand.nextInt(demandPairs.length)];
        }

        // Convert to array for random access
        NodePair[] demandsArray = demandsUsingEdge.toArray(new NodePair[0]);
        return demandsArray[rand.nextInt(demandsArray.length)];
    }

    /**
     * Computes the total load this demand places on a specific edge
     * when routed through the given intermediate node, considering ECMP fractions
     */
    private double computeLoadOnEdge(int s, int t, int intermediate,
                                     int edgeId, UnifiedDemand unifiedDem) {
        double totalLoad = 0.0;

        // Sum demand across all matrices
        for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
            if (unifiedDem.amounts[matrixIdx] <= 0) continue;

            double demand = unifiedDem.amounts[matrixIdx];
            double fraction = 0.0;

            if (intermediate == s) { // Direct path
                EdgeFlowVector spVec = shortestPathsCache[s][t];
                if (spVec != null) {
                    for (int i = 0; i < spVec.edgeIds.length; i++) {
                        if (spVec.edgeIds[i] == edgeId) {
                            fraction = spVec.frac[i];
                            break;
                        }
                    }
                }
            } else if (intermediate != t) { // Two-segment path
                // Check first segment
                EdgeFlowVector spVec1 = shortestPathsCache[s][intermediate];
                if (spVec1 != null) {
                    for (int i = 0; i < spVec1.edgeIds.length; i++) {
                        if (spVec1.edgeIds[i] == edgeId) {
                            fraction += spVec1.frac[i];
                        }
                    }
                }
                // Check second segment
                EdgeFlowVector spVec2 = shortestPathsCache[intermediate][t];
                if (spVec2 != null) {
                    for (int i = 0; i < spVec2.edgeIds.length; i++) {
                        if (spVec2.edgeIds[i] == edgeId) {
                            fraction += spVec2.frac[i];
                        }
                    }
                }
            }

            totalLoad += demand * fraction;
        }

        return totalLoad;
    }

    /**
     * Selects an intermediate node weighted by load reduction on congested edge.
     * Uses candidate sampling to reduce computational cost.
     * Considers actual ECMP load fractions, not just binary edge usage.
     */
    private int selectIntermediateNode(int s, int t, int congestedEdge,
                                       UnifiedDemand unifiedDem,
                                       int currentIntermediate,
                                       java.util.Random rand) {
        // Compute current load on congested edge
        double currentLoad = computeLoadOnEdge(s, t, currentIntermediate, congestedEdge, unifiedDem);

        // Sample candidates to evaluate (trade-off between quality and speed)
        int maxCandidates = Math.min(topology.nNodes / 4, 20); // Sample 25% or max 20 nodes
        maxCandidates = Math.max(maxCandidates, 5); // At least 5 candidates
        java.util.List<Integer> candidates = new java.util.ArrayList<>();

        // Always include the source (direct path) as a candidate
        if (s != t && s != currentIntermediate) {
            candidates.add(s);
        }

        // Sample additional random candidates
        java.util.Set<Integer> sampledSet = new java.util.HashSet<>(candidates);
        int attempts = 0;
        while (candidates.size() < maxCandidates && attempts < maxCandidates * 3) {
            int v = rand.nextInt(topology.nNodes);
            if (v != t && v != currentIntermediate && sampledSet.add(v)) {
                candidates.add(v);
            }
            attempts++;
        }

        if (candidates.isEmpty()) {
            // Fallback: random node (shouldn't happen normally)
            int v = rand.nextInt(topology.nNodes);
            while (v == t || v == currentIntermediate) {
                v = rand.nextInt(topology.nNodes);
            }
            return v;
        }

        // Compute load reduction for each candidate with exponential weighting
        java.util.List<Double> weights = new java.util.ArrayList<>();
        double totalWeight = 0.0;
        final double REDUCTION_SENSITIVITY = 5.0; // Higher = more preference for load reduction

        for (int v : candidates) {
            double newLoad = computeLoadOnEdge(s, t, v, congestedEdge, unifiedDem);
            double reduction = currentLoad - newLoad;

            // Exponential weighting: strongly prefer higher reductions
            // But still allow exploration of non-improving moves (for SA)
            double weight = Math.exp(reduction * REDUCTION_SENSITIVITY);
            weights.add(weight);
            totalWeight += weight;
        }

        // Weighted random selection
        if (totalWeight > 0.0) {
            double r = rand.nextDouble() * totalWeight;
            double cumulative = 0.0;
            for (int i = 0; i < candidates.size(); i++) {
                cumulative += weights.get(i);
                if (cumulative >= r) {
                    return candidates.get(i);
                }
            }
        }

        // Fallback: uniform random from candidates
        return candidates.get(rand.nextInt(candidates.size()));
    }

    private void simulatedAnnealing(long endTime) throws IntermediateNodeIsEndException {
        double initialTemperature = computeInitialTemperature();
        double temperature = initialTemperature;
        double coolingRate = 0.98;
        double minTemperature = initialTemperature * 1e-5;
        int iterationsPerTemp = Math.max(100, demandPairs.length * topology.nNodes / 4096);

        double bestUMax = computeCurrentUMax();
        double currentObjectiveValue = computeObjFctSumPowerP();
        double bestObjectiveValue = currentObjectiveValue;
        java.util.Random rand = new java.util.Random();

        while (System.currentTimeMillis() < endTime && temperature > minTemperature) {
            for (int iter = 0; iter < iterationsPerTemp; iter++) {
                /* Select a congested edge with probability proportional to utilization */
                int congestedEdge = selectEdgeByUtilization(rand);

                /* Select a demand using this edge */
                NodePair selectedPair = selectDemandFromEdge(congestedEdge, rand);
                int s = selectedPair.source;
                int t = selectedPair.dest;
                UnifiedDemand unifiedDem = unifiedDemands.get(selectedPair);

                /* Select a new intermediate node, preferring those that reduce load on congested edge */
                int oldIntermediate = currentSrPaths[s][t];
                int newIntermediate = selectIntermediateNode(s, t, congestedEdge, unifiedDem, oldIntermediate, rand);

                /* Load the new solution for all matrices where this demand exists */
                for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
                    if (unifiedDem.amounts[matrixIdx] > 0) {
                        removeLoad(s, oldIntermediate, t, unifiedDem.amounts[matrixIdx], matrixIdx, true);
                        addLoad(s, newIntermediate, t, unifiedDem.amounts[matrixIdx], matrixIdx, true);
                    }
                }

                /* Compute diff */
                double newObjectiveValue = computeObjFctSumPowerP();
                double delta = currentObjectiveValue - newObjectiveValue;

                if (delta > 0 || Math.exp(delta / temperature) > rand.nextDouble()) {
                    /* Accept the new solution */
                    currentSrPaths[s][t] = newIntermediate;
                    currentObjectiveValue = newObjectiveValue;

                    /* Update edge-to-demand mappings */
                    updateEdgeToDemandMappings(selectedPair, oldIntermediate, newIntermediate);

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
                            for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
                                System.arraycopy(linkLoads[matrixIdx], 0, bestLinkLoads[matrixIdx], 0, topology.nEdges);
                            }
                        }
                    }
                } else {
                    /* Revert to the old solution */
                    for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
                        if (unifiedDem.amounts[matrixIdx] > 0) {
                            removeLoad(s, newIntermediate, t, unifiedDem.amounts[matrixIdx], matrixIdx, true);
                            addLoad(s, oldIntermediate, t, unifiedDem.amounts[matrixIdx], matrixIdx, true);
                        }
                    }
                    /* Edge-to-demand mappings remain unchanged since we rejected */
                }
            }
            temperature *= coolingRate;
        }
    }

    private double computeInitialTemperature() {
        return Math.pow(2, -10); // TODO tune this
    }

    private double computeCurrentUMax() {
        double uMax = 0.0;
        for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
            for (int e = 0; e < topology.nEdges; e++) {
                double util = linkLoads[matrixIdx][e] / topology.edgeCapacity[e];
                if (util > uMax) {
                    uMax = util;
                }
            }
        }
        return uMax;
    }

    private double computeBestUMax() {
        double UMax = 0.0;
        for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
            for (int e = 0; e < topology.nEdges; e++) {
                double util = bestLinkLoads[matrixIdx][e] / topology.edgeCapacity[e];
                if (util > UMax) {
                    UMax = util;
                }
            }
        }
        return UMax;
    }

    private void updateBestLinkLoads() {
        // Reset best link loads
        for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
            for (int e = 0; e < topology.nEdges; e++) {
                bestLinkLoads[matrixIdx][e] = 0.0;
            }
        }

        // Update best link loads based on bestSrPaths for all unified demands
        for (NodePair pair : demandPairs) {
            int s = pair.source;
            int t = pair.dest;
            int intermediate = bestSrPaths[s][t];
            UnifiedDemand unifiedDem = unifiedDemands.get(pair);

            for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
                double d = unifiedDem.amounts[matrixIdx];
                if (d <= 0) continue;

                if (intermediate == s) { // Direct path
                    EdgeFlowVector spVec = shortestPathsCache[s][t];
                    for (int i = 0; i < spVec.edgeIds.length; i++) {
                        bestLinkLoads[matrixIdx][spVec.edgeIds[i]] += d * spVec.frac[i];
                    }
                } else if (intermediate == t) {
                    System.err.println("Error: Intermediate node cannot be the destination");
                    System.exit(0);
                } else {
                    EdgeFlowVector spVec1 = shortestPathsCache[s][intermediate];
                    EdgeFlowVector spVec2 = shortestPathsCache[intermediate][t];
                    for (int i = 0; i < spVec1.edgeIds.length; i++) {
                        bestLinkLoads[matrixIdx][spVec1.edgeIds[i]] += d * spVec1.frac[i];
                    }
                    for (int i = 0; i < spVec2.edgeIds.length; i++) {
                        bestLinkLoads[matrixIdx][spVec2.edgeIds[i]] += d * spVec2.frac[i];
                    }
                }
            }
        }
    }

    /**
     * Computes the objective function value based on the chosen approach
     */
    double computeObjFctSumPowerP() {
        switch (OBJECTIVE_TYPE) {
            case MAX_ACROSS_MATRICES:
                // Minimize max utilization across all matrices (robust optimization)
                double maxPNorm = 0.0;
                for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
                    double pNorm = Math.pow(sumPowerP[matrixIdx], 1.0 / P_NORM);
                    if (pNorm > maxPNorm) {
                        maxPNorm = pNorm;
                    }
                }
                return maxPNorm;

            case PNORM_OF_MATRICES:
                // P-norm of the p-norms (balanced approach)
                double sumOfPNorms = 0.0;
                for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
                    double pNorm = Math.pow(sumPowerP[matrixIdx], 1.0 / P_NORM);
                    sumOfPNorms += Math.pow(pNorm, P_NORM);
                }
                return Math.pow(sumOfPNorms, 1.0 / P_NORM);

            case AGGREGATE_ALL:
                // Single aggregate sum (treat as one big problem)
                double totalSum = 0.0;
                for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
                    totalSum += sumPowerP[matrixIdx];
                }
                return Math.pow(totalSum, 1.0 / P_NORM);

            default:
                throw new IllegalStateException("Unknown objective type");
        }
    }

    double computeObjFctFromScratch() {
        for (int matrixIdx = 0; matrixIdx < demandMatrices.length; matrixIdx++) {
            sumPowerP[matrixIdx] = 0.0;
            for (int e = 0; e < topology.nEdges; e++) {
                double util = linkLoads[matrixIdx][e] / topology.edgeCapacity[e];
                if (util != 0.0) {
                    sumPowerP[matrixIdx] += Math.pow(util, P_NORM);
                }
            }
        }
        return computeObjFctSumPowerP();
    }

    private void removeLoad(int s, int intermediate, int t, double demand, int matrixIdx, boolean updateSumPowerP) throws IntermediateNodeIsEndException {
        applyPaths(s, intermediate, t, -demand, matrixIdx, updateSumPowerP);
    }

    private void addLoad(int s, int intermediate, int t, double demand, int matrixIdx, boolean updateSumPowerP) throws IntermediateNodeIsEndException {
        applyPaths(s, intermediate, t, demand, matrixIdx, updateSumPowerP);
    }

    private void applyPaths(int s, int intermediate, int t, double demand, int matrixIdx, boolean updateSumPowerP) throws IntermediateNodeIsEndException {
        if (intermediate == s) { // Direct path
            EdgeFlowVector spVec = shortestPathsCache[s][t];
            for (int i = 0; i < spVec.edgeIds.length; i++) {
                updateEdge(spVec.edgeIds[i], demand * spVec.frac[i], matrixIdx, updateSumPowerP);
            }
        } else if (intermediate == t) {
            throw new IntermediateNodeIsEndException("");
        } else {
            EdgeFlowVector spVec1 = shortestPathsCache[s][intermediate];
            EdgeFlowVector spVec2 = shortestPathsCache[intermediate][t];
            for (int i = 0; i < spVec1.edgeIds.length; i++) {
                updateEdge(spVec1.edgeIds[i], demand * spVec1.frac[i], matrixIdx, updateSumPowerP);
            }
            for (int i = 0; i < spVec2.edgeIds.length; i++) {
                updateEdge(spVec2.edgeIds[i], demand * spVec2.frac[i], matrixIdx, updateSumPowerP);
            }
        }
    }

    private void updateEdge(int edgeId, double deltaLoad, int matrixIdx, boolean updateSumPowerP) {
        if (deltaLoad == 0.0) return;
        double capacity = topology.edgeCapacity[edgeId];
        double oldUtil = linkLoads[matrixIdx][edgeId] / capacity;
        if (oldUtil != 0.0 && updateSumPowerP) {
            sumPowerP[matrixIdx] -= Math.pow(oldUtil, P_NORM);
        }
        linkLoads[matrixIdx][edgeId] += deltaLoad;
        double newUtil = linkLoads[matrixIdx][edgeId] / capacity;
        if (newUtil != 0.0 && updateSumPowerP) {
            sumPowerP[matrixIdx] += Math.pow(newUtil, P_NORM);
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