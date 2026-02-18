package edu.repetita.solvers.sr;

import com.gurobi.gurobi.*;
import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Solver;
import edu.repetita.core.Topology;
import edu.repetita.io.RepetitaParser;
import edu.repetita.paths.ShortestPaths;

import java.io.IOException;
import java.util.*;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

/**
 * Segment Routing Traffic Engineering Problem (SRTEP) solver with adversarial matrix generation.
 *
 * This solver minimizes maximum link utilization by selecting exactly one SR path
 * for each source-destination pair from a set of precomputed non-dominated paths.
 *
 * Flow on edges is computed using ECMP (Equal-Cost Multi-Path) splitting
 * between consecutive segments.
 *
 * The solver also generates adversarial demand matrices by perturbing the base matrix
 * to stress-test the routing solution. It iteratively:
 * 1. Solves the SRTEP for the current set of matrices
 * 2. Generates a worst-case matrix that maximizes MLU given the current routing
 * 3. Re-optimizes for all matrices including the new adversarial one
 */
public class SRTEP extends Solver {

    private long solveTime = 0;
    private long maxTime;

    // Parameters for adversarial matrix generation (configurable)
    private int numAdversarialMatrices;
    private int maxPerturbedDemands;
    private double perturbationPercent;
    private double demandLoadThreshold = 0.80;
    private boolean useDemandLoadThreshold = false;

    private final double TARGETMLU = 1.0;

    // SR paths configuration
    private String srPathsFile = null;
    private boolean excludeAdjacencyPaths = false;

    // Precomputed data
    private SRPathSet pathSet = null;
    private ShortestPaths shortestPaths = null;

    // Precomputed edge usage for all paths
    // edgeUsage[pathIndex][edgeIndex] = fraction of path's flow that uses edge
    private double[][] edgeUsage = null;

    // List of all paths (for indexing into edgeUsage)
    private List<SRPath> allPaths = null;

    /**
     * Set the file containing SR paths
     */
    public void setSRPathsFile(String filename) {
        this.srPathsFile = filename;
    }

    /**
     * Set whether to exclude paths with adjacency (edge) segments
     */
    public void setExcludeAdjacencyPaths(boolean exclude) {
        this.excludeAdjacencyPaths = exclude;
    }

    /**
     * Set the number of adversarial matrices to generate
     */
    public void setNumAdversarialMatrices(int num) {
        this.numAdversarialMatrices = num;
    }

    /**
     * Set the maximum number of demands that can be perturbed per matrix
     */
    public void setMaxPerturbedDemands(int max) {
        this.maxPerturbedDemands = max;
    }

    /**
     * Set the perturbation percentage (e.g., 0.20 for 20%)
     */
    public void setPerturbationPercent(double percent) {
        this.perturbationPercent = percent;
    }

    /**
     * Set the demand load threshold (e.g., 0.75 for top 75% of load)
     */
    public void setDemandLoadThreshold(double threshold) {
        this.demandLoadThreshold = threshold;
    }

    /**
     * Enable or disable the demand load threshold filtering
     */
    public void setUseDemandLoadThreshold(boolean use) {
        this.useDemandLoadThreshold = use;
    }

    @Override
    protected void setObjective() {
        objective = SOLVER_OBJVALUES_MINMAXLINKUSAGE;
    }

    @Override
    public String name() {
        return "SRTEP";
    }

    @Override
    public String getDescription() {
        return "Solves the Segment Routing Traffic Engineering problem. " +
                "Selects exactly one SR path per source-destination pair to minimize maximum link utilization.";
    }

    @Override
    public void solve(Setting setting, long milliseconds) {
        long startTime = System.currentTimeMillis();
        maxTime = milliseconds;

        Topology topology = setting.getTopology();
        ArrayList<Demands> demands = setting.getDemands();

        if (demands.isEmpty()) {
            System.err.println("No demand matrix provided");
            return;
        }

        if (srPathsFile == null) {
            System.err.println("No SR paths file specified. Use setSRPathsFile() before solving.");
            return;
        }

        // Load SR paths
        try {
            pathSet = RepetitaParser.parseSRPaths(srPathsFile, topology, excludeAdjacencyPaths);
            System.out.println("Loaded SR paths: " + pathSet);
        } catch (IOException e) {
            System.err.println("Error loading SR paths file: " + e.getMessage());
            return;
        }

        // Initialize shortest paths for ECMP computation
        shortestPaths = new ShortestPaths(topology);

        // Precompute edge usage for all paths
        allPaths = excludeAdjacencyPaths ? pathSet.getAllPathsWithoutAdjacency() : pathSet.getAllPaths();
        precomputeEdgeUsage(topology);

        // Scale the base matrix to MLU = TARGETMLU using MCF (not SRTEP)
        // This ensures consistent scaling across different routing schemes
        Demands baseMatrix = scaleMatrixToMLUUsingMCF(topology, demands.getFirst(), TARGETMLU);
        //Demands baseMatrix = demands.getFirst();

        // Generate adversarial matrices
        List<AdversarialMatrix> adversarialMatrices = generateAdversarialMatrices(
                topology, baseMatrix, numAdversarialMatrices,
                maxPerturbedDemands, perturbationPercent
        );

        // Print results
        printAdversarialResults(adversarialMatrices);

        solveTime = System.currentTimeMillis() - startTime;
    }

    /**
     * Scales a demand matrix to achieve a target MLU using the MCF (Multicommodity Flow) formulation.
     * This provides a consistent baseline for scaling that doesn't depend on the SR path set.
     */
    private Demands scaleMatrixToMLUUsingMCF(Topology topology, Demands originalMatrix, double targetMLU) {
        double currentMLU = solveMCF(topology, originalMatrix);

        if (currentMLU <= 0) {
            System.err.println("Error: MCF MLU is " + currentMLU);
            return originalMatrix;
        }

        Demands scaledMatrix = new Demands(originalMatrix.nDemands);
        double scaleFactor = targetMLU / currentMLU;

        for (int i = 0; i < originalMatrix.nDemands; i++) {
            scaledMatrix.source[i] = originalMatrix.source[i];
            scaledMatrix.dest[i] = originalMatrix.dest[i];
            scaledMatrix.amount[i] = Math.floor(originalMatrix.amount[i] * scaleFactor);
        }

        System.out.println("MCF_SCALE_FACTOR: " + scaleFactor);
        return scaledMatrix;
    }

    /**
     * Solves the MCF (Multicommodity Flow) problem to get the optimal MLU.
     * This is used for scaling the demand matrix.
     */
    private double solveMCF(Topology topology, Demands demands) {
        try {
            GRBEnv env = new GRBEnv(true);
            env.set(GRB.IntParam.OutputFlag, 0);
            env.set(GRB.IntParam.LogToConsole, 0);
            env.start();

            GRBModel model = new GRBModel(env);
            model.set(GRB.DoubleParam.TimeLimit, 300.0); // 5 minute timeout for scaling
            model.set(GRB.IntParam.Threads, 4);

            int nNodes = topology.nNodes;
            int nEdges = topology.nEdges;

            // Flow variables: flow[src][dst][edge] = fraction of (src,dst) demand on edge
            GRBVar[][][] flowVars = new GRBVar[nNodes][nNodes][nEdges];
            for (int src = 0; src < nNodes; src++) {
                for (int dst = 0; dst < nNodes; dst++) {
                    if (src == dst) continue;
                    for (int edge = 0; edge < nEdges; edge++) {
                        flowVars[src][dst][edge] = model.addVar(
                                0.0, 1.0, 0.0, GRB.CONTINUOUS,
                                "flow_" + src + "_" + dst + "_" + edge
                        );
                    }
                }
            }

            // Maximum utilization variable
            GRBVar uMax = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "uMax");

            // Objective: minimize uMax
            GRBLinExpr objExpr = new GRBLinExpr();
            objExpr.addTerm(1.0, uMax);
            model.setObjective(objExpr, GRB.MINIMIZE);

            // Flow conservation constraints
            for (int src = 0; src < nNodes; src++) {
                for (int dst = 0; dst < nNodes; dst++) {
                    if (src == dst) continue;

                    for (int node = 0; node < nNodes; node++) {
                        GRBLinExpr flowBalance = new GRBLinExpr();

                        // Outflow - Inflow
                        for (int edge = 0; edge < nEdges; edge++) {
                            if (topology.edgeSrc[edge] == node) {
                                flowBalance.addTerm(1.0, flowVars[src][dst][edge]);
                            }
                            if (topology.edgeDest[edge] == node) {
                                flowBalance.addTerm(-1.0, flowVars[src][dst][edge]);
                            }
                        }

                        // RHS: 1 at source, -1 at destination, 0 elsewhere
                        double rhs = 0.0;
                        if (node == src) rhs = 1.0;
                        else if (node == dst) rhs = -1.0;

                        model.addConstr(flowBalance, GRB.EQUAL, rhs,
                                "flow_conservation_" + src + "_" + dst + "_" + node);
                    }
                }
            }

            // Capacity constraints
            for (int edge = 0; edge < nEdges; edge++) {
                GRBLinExpr edgeLoad = new GRBLinExpr();

                for (int demandIdx = 0; demandIdx < demands.nDemands; demandIdx++) {
                    int src = demands.source[demandIdx];
                    int dst = demands.dest[demandIdx];
                    double amount = demands.amount[demandIdx];

                    edgeLoad.addTerm(amount, flowVars[src][dst][edge]);
                }

                edgeLoad.addTerm(-topology.edgeCapacity[edge], uMax);
                model.addConstr(edgeLoad, GRB.LESS_EQUAL, 0.0, "capacity_" + edge);
            }

            // Solve
            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            if (status != GRB.OPTIMAL && status != GRB.SUBOPTIMAL) {
                System.err.println("MCF: Gurobi did not find an optimal solution. Status: " + status);
                model.dispose();
                env.dispose();
                return Double.MAX_VALUE;
            }

            double result = model.get(GRB.DoubleAttr.ObjVal);

            model.dispose();
            env.dispose();

            return result;

        } catch (GRBException e) {
            System.err.println("MCF Gurobi error: " + e.getErrorCode() + ". " + e.getMessage());
            return Double.MAX_VALUE;
        }
    }

    /**
     * Precomputes edge usage for all paths in allPaths.
     * Populates the edgeUsage array.
     */
    private void precomputeEdgeUsage(Topology topology) {
        int nPaths = allPaths.size();
        int nEdges = topology.nEdges;

        edgeUsage = new double[nPaths][nEdges];

        for (int p = 0; p < nPaths; p++) {
            SRPath path = allPaths.get(p);
            edgeUsage[p] = computePathEdgeUsage(path, topology);
        }

        System.out.println("Precomputed edge usage for " + nPaths + " paths");
    }

    /**
     * Gets the edge usage for a path by its index.
     */
    private double[] getPathEdgeUsage(int pathIndex) {
        return edgeUsage[pathIndex];
    }

    /**
     * Computes the edge usage for a single SR path using ECMP.
     *
     * For each subpath (between consecutive segments), we compute how flow is
     * distributed across edges using ECMP shortest path routing.
     *
     * @param path The SR path
     * @param topology The network topology
     * @return Array where result[edge] = fraction of path's flow that uses edge
     */
    private double[] computePathEdgeUsage(SRPath path, Topology topology) {
        int nEdges = topology.nEdges;
        double[] edgeUsage = new double[nEdges];

        int currentNode = path.source;

        for (int i = 0; i < path.numSegments; i++) {
            if (path.isAdjacencySegment(i)) {
                // Adjacency segment: all flow goes on this specific edge
                int edgeIndex = path.getEdgeSegment(i);
                edgeUsage[edgeIndex] += 1.0;
                currentNode = topology.edgeDest[edgeIndex];
            } else {
                // Node segment: use ECMP to reach this node
                int nextNode = path.getNodeSegment(i);
                if (currentNode != nextNode) {
                    // Compute ECMP edge usage from currentNode to nextNode
                    addECMPEdgeUsage(edgeUsage, currentNode, nextNode, topology);
                }
                currentNode = nextNode;
            }
        }

        return edgeUsage;
    }

    /**
     * Adds the ECMP edge usage for routing from source to destination to the edgeUsage array.
     * Uses the precomputed shortest path DAG to determine flow splitting.
     */
    private void addECMPEdgeUsage(double[] edgeUsage, int source, int destination, Topology topology) {
        if (source == destination) return;

        int nNodes = topology.nNodes;

        // We need to compute flow fractions using the shortest path DAG
        // flowFraction[node] = fraction of flow arriving at node from source
        double[] flowFraction = new double[nNodes];
        flowFraction[source] = 1.0;

        // Process nodes in topological order from source to destination
        // Note: makeTopologicalOrdering uses post-order DFS, so destination is at index 0
        // and source is at index nOrdering-1. We need to iterate in reverse.
        int nOrdering = shortestPaths.makeTopologicalOrdering(source, destination);

        for (int i = nOrdering - 1; i >= 0; i--) {
            int node = shortestPaths.topologicalOrdering[i];
            double nodeFlow = flowFraction[node];

            if (nodeFlow < 1e-10) continue;

            // Get successors in shortest path DAG to destination
            int nSucc = shortestPaths.nSuccessors[destination][node];
            if (nSucc == 0) continue;

            // Split flow equally among successors (ECMP)
            double splitFraction = nodeFlow / nSucc;

            for (int s = 0; s < nSucc; s++) {
                int succNode = shortestPaths.successorNodes[destination][node][s];
                int succEdge = shortestPaths.successorEdges[destination][node][s];

                // Add flow to this edge
                edgeUsage[succEdge] += splitFraction;

                // Propagate flow fraction to successor node
                flowFraction[succNode] += splitFraction;
            }
        }
    }

    /**
     * Class to store an adversarial matrix and its metadata
     */
    private static class AdversarialMatrix {
        Demands matrix;
        Set<Integer> perturbedDemandIndices;
        double nonOptimizedMLU;
        double cumulativeOptimizedMLU;
        double individualMLU;
        double mluWithCumulativeRouting;

        AdversarialMatrix(Demands matrix, Set<Integer> perturbedIndices, double nonOptimizedMLU) {
            this.matrix = matrix;
            this.perturbedDemandIndices = perturbedIndices;
            this.nonOptimizedMLU = nonOptimizedMLU;
            this.cumulativeOptimizedMLU = 0.0;
            this.individualMLU = 0.0;
            this.mluWithCumulativeRouting = 0.0;
        }
    }

    /**
     * Class to store the routing solution (selected path index for each demand)
     */
    private class Routing {
        // selectedPathIndex[src][dst] = index of selected path in allPaths, or -1 if no path
        int[][] selectedPathIndex;
        int nNodes;

        Routing(int nNodes) {
            this.nNodes = nNodes;
            this.selectedPathIndex = new int[nNodes][nNodes];
            // Initialize to -1 (no path)
            for (int i = 0; i < nNodes; i++) {
                for (int j = 0; j < nNodes; j++) {
                    selectedPathIndex[i][j] = -1;
                }
            }
        }

        /**
         * Get the edge usage fraction for a flow from src to dst on a given edge
         */
        double getEdgeUsage(int src, int dst, int edge) {
            int pathIdx = selectedPathIndex[src][dst];
            if (pathIdx < 0) return 0.0;
            return edgeUsage[pathIdx][edge];
        }
    }

    /**
     * Scales a demand matrix to achieve a target MLU
     */
    private Demands scaleMatrixToMLU(Topology topology, Demands originalMatrix, double targetMLU) {
        Routing routing = new Routing(topology.nNodes);
        double currentMLU = solveRouting(topology, Collections.singletonList(originalMatrix), routing);

        if (currentMLU <= 0) {
            System.err.println("Error: current MLU is " + currentMLU);
            return originalMatrix;
        }

        Demands scaledMatrix = new Demands(originalMatrix.nDemands);
        double scaleFactor = targetMLU / currentMLU;

        for (int i = 0; i < originalMatrix.nDemands; i++) {
            scaledMatrix.source[i] = originalMatrix.source[i];
            scaledMatrix.dest[i] = originalMatrix.dest[i];
            scaledMatrix.amount[i] = Math.floor(originalMatrix.amount[i] * scaleFactor);
        }

        System.out.println("SCALE_FACTOR: " + scaleFactor);
        return scaledMatrix;
    }

    /**
     * Generates a list of adversarial matrices
     */
    private List<AdversarialMatrix> generateAdversarialMatrices(
            Topology topology, Demands baseMatrix, int numMatrices,
            int maxPerturbedDemands, double perturbationPercent) {

        List<AdversarialMatrix> adversarialMatrices = new ArrayList<>();
        List<Demands> allMatrices = new ArrayList<>();
        allMatrices.add(baseMatrix);

        Routing currentRouting = new Routing(topology.nNodes);
        double currentMLU = solveRouting(topology, allMatrices, currentRouting);

        System.out.println("\n=== Generating Adversarial Matrices (SRTE) ===");
        System.out.println("Base matrix MLU (should be ~1.0): " + currentMLU);

        Set<Integer> perturbableDemands = null;
        if (useDemandLoadThreshold) {
            System.out.println("\n--- Computing perturbable demands based on load threshold ---");
            perturbableDemands = computePerturbableDemands(baseMatrix, demandLoadThreshold);
        } else {
            System.out.println("\n--- Demand load threshold filtering disabled ---");
        }

        for (int i = 0; i < numMatrices; i++) {
            System.out.println("\n--- Generating adversarial matrix " + (i + 1) + " ---");
            System.out.println("Current combined MLU: " + currentMLU);

            AdversarialMatrix worstMatrix = generateWorstMatrix(
                    topology, baseMatrix, currentRouting, maxPerturbedDemands,
                    perturbationPercent, perturbableDemands
            );

            adversarialMatrices.add(worstMatrix);
            allMatrices.add(worstMatrix.matrix);

            currentRouting = new Routing(topology.nNodes);
            double cumulativeOptimizedMLU = solveRouting(topology, allMatrices, currentRouting);
            worstMatrix.cumulativeOptimizedMLU = cumulativeOptimizedMLU;

            double individualMLU = solveRouting(topology, Collections.singletonList(worstMatrix.matrix), null);
            worstMatrix.individualMLU = individualMLU;

            System.out.println("Non-optimized MLU (old routing): " + worstMatrix.nonOptimizedMLU);
            System.out.println("Cumulative optimized MLU (all matrices): " + cumulativeOptimizedMLU);
            System.out.println("Individual MLU (this matrix alone): " + individualMLU);
            System.out.println("Perturbed " + worstMatrix.perturbedDemandIndices.size() + " demands");

            currentMLU = cumulativeOptimizedMLU;
        }

        System.out.println("\n--- Computing individual MLUs with final routing ---");
        System.out.println("Final cumulative MLU: " + currentMLU);
        for (int i = 0; i < adversarialMatrices.size(); i++) {
            AdversarialMatrix am = adversarialMatrices.get(i);
            am.mluWithCumulativeRouting = calculateMLU(topology, am.matrix, currentRouting);
            System.out.println("Matrix " + (i + 1) + " MLU with final routing: " + am.mluWithCumulativeRouting);
        }

        return adversarialMatrices;
    }

    /**
     * Computes the set of demand indices that are allowed to be perturbed
     */
    private Set<Integer> computePerturbableDemands(Demands baseMatrix, double loadThreshold) {
        double totalLoad = 0.0;
        for (int i = 0; i < baseMatrix.nDemands; i++) {
            totalLoad += baseMatrix.amount[i];
        }

        List<DemandLoad> demandLoads = new ArrayList<>();
        for (int i = 0; i < baseMatrix.nDemands; i++) {
            demandLoads.add(new DemandLoad(i, baseMatrix.amount[i]));
        }
        demandLoads.sort((a, b) -> Double.compare(b.load, a.load));

        double targetLoad = totalLoad * loadThreshold;
        double cumulativeLoad = 0.0;
        Set<Integer> perturbableDemands = new HashSet<>();

        for (DemandLoad dl : demandLoads) {
            perturbableDemands.add(dl.demandIdx);
            cumulativeLoad += dl.load;
            if (cumulativeLoad >= targetLoad) {
                break;
            }
        }

        System.out.println("Total network load: " + totalLoad);
        System.out.println("Target load threshold (" + (loadThreshold * 100) + "%): " + targetLoad);
        System.out.println("Cumulative load of selected demands: " + cumulativeLoad);
        System.out.println("Number of perturbable demands: " + perturbableDemands.size() + "/" + baseMatrix.nDemands);

        return perturbableDemands;
    }

    private static class DemandLoad {
        int demandIdx;
        double load;

        DemandLoad(int demandIdx, double load) {
            this.demandIdx = demandIdx;
            this.load = load;
        }
    }

    /**
     * Generates the worst-case demand matrix given a fixed routing
     */
    private AdversarialMatrix generateWorstMatrix(
            Topology topology, Demands baseMatrix, Routing routing,
            int maxPerturbedDemands, double perturbationPercent, Set<Integer> perturbableDemands) {

        int budgetRestant = maxPerturbedDemands;
        Set<Integer> perturbedDemands = new HashSet<>();
        Demands worstMatrix = copyDemands(baseMatrix);

        while (budgetRestant > 0) {
            EdgeWorstCase worstCase = findWorstEdge(
                    topology, worstMatrix, routing, perturbedDemands,
                    perturbationPercent, budgetRestant, perturbableDemands
            );

            if (worstCase == null || worstCase.demands.isEmpty()) {
                System.out.println("No more demands to perturb (budget remaining: " + budgetRestant + ")");
                break;
            }

            int nbToPerturb = Math.min(worstCase.demands.size(), budgetRestant);
            for (int i = 0; i < nbToPerturb; i++) {
                int demandIdx = worstCase.demands.get(i);
                perturbedDemands.add(demandIdx);
                worstMatrix.amount[demandIdx] = baseMatrix.amount[demandIdx] * (1.0 + perturbationPercent);
            }

            budgetRestant -= nbToPerturb;

            System.out.println("  Perturbed " + nbToPerturb + " demands on edge " +
                    worstCase.edgeIdx + " (MLU would be " + worstCase.mlu + ")");
        }

        double actualMLU = calculateMLU(topology, worstMatrix, routing);
        return new AdversarialMatrix(worstMatrix, perturbedDemands, actualMLU);
    }

    private static class EdgeWorstCase {
        int edgeIdx;
        double mlu;
        List<Integer> demands;

        EdgeWorstCase(int edgeIdx, double mlu, List<Integer> demands) {
            this.edgeIdx = edgeIdx;
            this.mlu = mlu;
            this.demands = demands;
        }
    }

    /**
     * Finds the edge that would give the worst MLU if we perturb demands using it
     */
    private EdgeWorstCase findWorstEdge(
            Topology topology, Demands baseMatrix, Routing routing,
            Set<Integer> alreadyPerturbed, double perturbationPercent, int budgetRestant,
            Set<Integer> perturbableDemands) {

        EdgeWorstCase worstCase = null;
        double worstMLU = 0.0;

        for (int edge = 0; edge < topology.nEdges; edge++) {
            List<DemandContribution> contributions = new ArrayList<>();

            for (int demandIdx = 0; demandIdx < baseMatrix.nDemands; demandIdx++) {
                if (alreadyPerturbed.contains(demandIdx)) continue;
                if (perturbableDemands != null && !perturbableDemands.contains(demandIdx)) continue;

                int src = baseMatrix.source[demandIdx];
                int dst = baseMatrix.dest[demandIdx];
                double flowOnEdge = routing.getEdgeUsage(src, dst, edge);

                if (flowOnEdge > 1e-6) {
                    double contribution = baseMatrix.amount[demandIdx] * flowOnEdge;
                    contributions.add(new DemandContribution(demandIdx, contribution));
                }
            }

            if (contributions.isEmpty()) continue;

            contributions.sort((a, b) -> Double.compare(b.contribution, a.contribution));

            int nbDemandsToPerturb = Math.min(contributions.size(), budgetRestant);
            Set<Integer> demandsToPerturb = new HashSet<>();
            for (int i = 0; i < nbDemandsToPerturb; i++) {
                demandsToPerturb.add(contributions.get(i).demandIdx);
            }

            double edgeLoad = 0.0;
            for (int demandIdx = 0; demandIdx < baseMatrix.nDemands; demandIdx++) {
                int src = baseMatrix.source[demandIdx];
                int dst = baseMatrix.dest[demandIdx];
                double flowOnEdge = routing.getEdgeUsage(src, dst, edge);
                double amount = baseMatrix.amount[demandIdx];

                if (demandsToPerturb.contains(demandIdx)) {
                    amount *= (1.0 + perturbationPercent);
                }

                edgeLoad += amount * flowOnEdge;
            }

            double mlu = edgeLoad / topology.edgeCapacity[edge];

            if (mlu > worstMLU) {
                worstMLU = mlu;
                List<Integer> demandIndices = new ArrayList<>();
                for (int i = 0; i < nbDemandsToPerturb; i++) {
                    demandIndices.add(contributions.get(i).demandIdx);
                }
                worstCase = new EdgeWorstCase(edge, mlu, demandIndices);
            }
        }

        return worstCase;
    }

    private static class DemandContribution {
        int demandIdx;
        double contribution;

        DemandContribution(int demandIdx, double contribution) {
            this.demandIdx = demandIdx;
            this.contribution = contribution;
        }
    }

    /**
     * Calculates the MLU of a demand matrix with a given routing
     */
    private double calculateMLU(Topology topology, Demands demands, Routing routing) {
        double maxUtilization = 0.0;

        for (int edge = 0; edge < topology.nEdges; edge++) {
            double edgeLoad = 0.0;

            for (int demandIdx = 0; demandIdx < demands.nDemands; demandIdx++) {
                int src = demands.source[demandIdx];
                int dst = demands.dest[demandIdx];
                double flowOnEdge = routing.getEdgeUsage(src, dst, edge);
                edgeLoad += demands.amount[demandIdx] * flowOnEdge;
            }

            double utilization = edgeLoad / topology.edgeCapacity[edge];
            maxUtilization = Math.max(maxUtilization, utilization);
        }

        return maxUtilization;
    }

    /**
     * Solves the SR routing problem using MIP.
     * Selects exactly one path per (src, dst) pair to minimize maximum link utilization.
     *
     * @param topology The network topology
     * @param demandsList List of demand matrices to optimize for
     * @param outputRouting If not null, the selected paths are stored here
     * @return The optimal MLU
     */
    private double solveRouting(Topology topology, List<Demands> demandsList, Routing outputRouting) {
        try {
            GRBEnv env = new GRBEnv(true);
            env.set(GRB.IntParam.OutputFlag, 0);
            env.set(GRB.IntParam.LogToConsole, 0);
            env.start();

            GRBModel model = new GRBModel(env);
            model.set(GRB.DoubleParam.TimeLimit, (double) (maxTime / 1000));
            model.set(GRB.IntParam.Threads, 4);

            int nNodes = topology.nNodes;
            int nEdges = topology.nEdges;

            // Prepare the list of all paths
            List<SRPath> allPaths = excludeAdjacencyPaths ? pathSet.getAllPathsWithoutAdjacency() : pathSet.getAllPaths();
            int nPaths = allPaths.size();

            // Build index mapping: for each (src, dst) pair, list of path indices in allPaths
            Map<Integer, Map<Integer, List<Integer>>> pathIndicesByPair = new HashMap<>();
            for (int i = 0; i < topology.nNodes; i++) {
                pathIndicesByPair.put(i, new HashMap<>());
                for (int j = 0; j < topology.nNodes; j++) {
                    pathIndicesByPair.get(i).put(j, new ArrayList<>());
                }
            }
            for (int p = 0; p < nPaths; p++) {
                SRPath path = allPaths.get(p);
                pathIndicesByPair.get(path.source).get(path.destination).add(p);
            }

            // Binary variables: x[p] = 1 if path p is selected
            GRBVar[] x = new GRBVar[nPaths];
            for (int p = 0; p < nPaths; p++) {
                x[p] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "x_" + p);
            }

            // Continuous variable for maximum utilization
            GRBVar uMax = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "uMax");

            // Objective: minimize uMax
            GRBLinExpr objExpr = new GRBLinExpr();
            objExpr.addTerm(1.0, uMax);
            model.setObjective(objExpr, GRB.MINIMIZE);

            // Constraint: exactly one path per (src, dst) pair
            for (int src = 0; src < nNodes; src++) {
                for (int dst = 0; dst < nNodes; dst++) {
                    if (src == dst) continue;

                    List<Integer> pathIndices = pathIndicesByPair.get(src).get(dst);
                    if (pathIndices.isEmpty()) {
                        System.err.println("WARNING: No path available for pair (" + src + ", " + dst +
                                "). This demand will be ignored in routing. Check SR paths file.");
                        continue;
                    }

                    GRBLinExpr pathSelection = new GRBLinExpr();
                    for (int p : pathIndices) {
                        pathSelection.addTerm(1.0, x[p]);
                    }
                    model.addConstr(pathSelection, GRB.EQUAL, 1.0,
                            "path_selection_" + src + "_" + dst);
                }
            }

            // Capacity constraints for each demand matrix
            for (int matrixIdx = 0; matrixIdx < demandsList.size(); matrixIdx++) {
                Demands demands = demandsList.get(matrixIdx);

                for (int edge = 0; edge < nEdges; edge++) {
                    GRBLinExpr edgeLoad = new GRBLinExpr();

                    // For each demand, add its contribution to this edge
                    for (int demandIdx = 0; demandIdx < demands.nDemands; demandIdx++) {
                        int src = demands.source[demandIdx];
                        int dst = demands.dest[demandIdx];
                        double amount = demands.amount[demandIdx];

                        // For each path available for this demand
                        for (int p : pathIndicesByPair.get(src).get(dst)) {
                            double pathEdgeUsage = getPathEdgeUsage(p)[edge];
                            if (pathEdgeUsage > 1e-10) {
                                edgeLoad.addTerm(amount * pathEdgeUsage, x[p]);
                            }
                        }
                    }

                    // edgeLoad <= capacity * uMax
                    edgeLoad.addTerm(-topology.edgeCapacity[edge], uMax);
                    model.addConstr(edgeLoad, GRB.LESS_EQUAL, 0.0,
                            "capacity_" + edge + "_matrix_" + matrixIdx);
                }
            }

            // Solve
            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            if (status != GRB.OPTIMAL && status != GRB.SUBOPTIMAL) {
                System.err.println("Gurobi did not find an optimal solution. Status: " + status);
                model.dispose();
                env.dispose();
                return Double.MAX_VALUE;
            }

            double result = model.get(GRB.DoubleAttr.ObjVal);

            // Extract the selected paths if requested
            if (outputRouting != null) {
                for (int p = 0; p < nPaths; p++) {
                    double val = x[p].get(GRB.DoubleAttr.X);
                    if (val > 0.5) {
                        SRPath path = allPaths.get(p);
                        outputRouting.selectedPathIndex[path.source][path.destination] = p;
                    }
                }
            }

            model.dispose();
            env.dispose();

            return result;

        } catch (GRBException e) {
            System.err.println("Gurobi error: " + e.getErrorCode() + ". " + e.getMessage());
            return Double.MAX_VALUE;
        }
    }

    /**
     * Copies a demand matrix
     */
    private Demands copyDemands(Demands original) {
        Demands copy = new Demands(original.nDemands);
        for (int i = 0; i < original.nDemands; i++) {
            copy.source[i] = original.source[i];
            copy.dest[i] = original.dest[i];
            copy.amount[i] = original.amount[i];
        }
        return copy;
    }

    /**
     * Prints the results of adversarial matrix generation
     */
    private void printAdversarialResults(List<AdversarialMatrix> matrices) {
        System.out.println("\n=== Adversarial Matrix Generation Results (SRTE) ===");

        for (int i = 0; i < matrices.size(); i++) {
            AdversarialMatrix am = matrices.get(i);
            System.out.println("MATRIX: " + (i + 1));
            System.out.println("  NON_OPTIMIZED_MLU: " + am.nonOptimizedMLU);
            System.out.println("  CUMULATIVE_OPTIMIZED_MLU: " + am.cumulativeOptimizedMLU);
            System.out.println("  MLU_WITH_CUMULATIVE_ROUTING: " + am.mluWithCumulativeRouting);
            System.out.println("  INDIVIDUAL_MLU: " + am.individualMLU);
            System.out.println("  NUM_PERTURBED: " + am.perturbedDemandIndices.size());

            List<Integer> sortedIds = new ArrayList<>(am.perturbedDemandIndices);
            Collections.sort(sortedIds);
            System.out.print("  PERTURBED_DEMAND_IDS: ");
            for (int j = 0; j < sortedIds.size(); j++) {
                if (j > 0) System.out.print(",");
                System.out.print(sortedIds.get(j));
            }
            System.out.println();
        }

        System.out.println("\n=== Summary ===");
        System.out.println(String.format("%-10s %-20s %-25s %-25s %-20s %-15s",
                "Matrix", "Non-Opt MLU", "Cumulative-Opt MLU", "MLU w/ Cum. Routing", "Individual MLU", "Num Perturbed"));
        System.out.println("-".repeat(120));
        for (int i = 0; i < matrices.size(); i++) {
            AdversarialMatrix am = matrices.get(i);
            System.out.println(String.format("%-10s %-20.6f %-25.6f %-25.6f %-20.6f %-15d",
                    "M_" + (i+1), am.nonOptimizedMLU, am.cumulativeOptimizedMLU, am.mluWithCumulativeRouting,
                    am.individualMLU, am.perturbedDemandIndices.size()));
        }
    }

    @Override
    public long solveTime(Setting setting) {
        return solveTime;
    }
}

