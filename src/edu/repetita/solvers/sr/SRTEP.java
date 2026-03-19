package edu.repetita.solvers.sr;

import com.gurobi.gurobi.*;
import edu.repetita.core.Demands;
import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;
import edu.repetita.solvers.mcf.MCFAggregated;

import java.util.*;

/**
 * Segment Routing Traffic Engineering Problem (SRTEP) solver.
 *
 * Minimizes maximum link utilization by selecting exactly one SR path
 * for each source-destination pair from a set of precomputed non-dominated paths.
 *
 * Flow on edges is computed using ECMP (Equal-Cost Multi-Path) splitting
 * between consecutive segments.
 *
 * This class is a pure solver — it contains only MIP formulation and ECMP logic.
 */
public class SRTEP {

    private final Topology topology;
    private final SRPathSet pathSet;
    private final ShortestPaths shortestPaths;
    private final boolean excludeAdjacencyPaths;
    private long maxTime;

    // Precomputed edge usage for all paths
    // edgeUsage[pathIndex][edgeIndex] = fraction of path's flow that uses edge
    private final double[][] edgeUsage;

    // List of all paths (for indexing into edgeUsage)
    private final List<SRPath> allPaths;

    // pathIndicesByPair[src][dst] = list of path indices in allPaths for that OD pair
    private final List<Integer>[][] pathIndicesByPair;

    /**
     * Result of a routing solve: the optimal MLU and the selected path for each (src, dst) pair.
     */
    public static class SolveResult {
        public final double mlu;
        /** selectedPathIndex[src][dst] = index of selected path in allPaths, or -1 if none */
        public final int[][] selectedPathIndex;
        /** Per-matrix MLU values (from Phase 2 if run, otherwise from Phase 1). */
        public final double[] perMatrixMLU;

        public SolveResult(double mlu, int[][] selectedPathIndex, double[] perMatrixMLU) {
            this.mlu = mlu;
            this.selectedPathIndex = selectedPathIndex;
            this.perMatrixMLU = perMatrixMLU;
        }
    }


    // Construction and initialization

    /**
     * Creates a new SRTEP solver, precomputing edge usage for all paths.
     *
     * @param topology              the network topology
     * @param pathSet               the set of precomputed SR paths
     * @param shortestPaths         precomputed shortest paths (for ECMP)
     * @param excludeAdjacencyPaths if true, exclude paths using adjacency segments
     * @param maxTime               maximum solve time in milliseconds
     */
    public SRTEP(Topology topology, SRPathSet pathSet, ShortestPaths shortestPaths,
                 boolean excludeAdjacencyPaths, long maxTime) {
        this.topology = topology;
        this.pathSet = pathSet;
        this.shortestPaths = shortestPaths;
        this.excludeAdjacencyPaths = excludeAdjacencyPaths;
        this.maxTime = maxTime;

        this.allPaths = excludeAdjacencyPaths ? pathSet.getAllPathsWithoutAdjacency() : pathSet.getAllPaths();
        this.edgeUsage = precomputeEdgeUsage();
        this.pathIndicesByPair = buildPathIndicesByPair();

        System.out.println("Precomputed edge usage for " + allPaths.size() + " paths");
    }

    private List<Integer>[][] buildPathIndicesByPair() {
        int nNodes = topology.nNodes;
        List<Integer>[][] indices = new List[nNodes][nNodes];
        for (int i = 0; i < nNodes; i++)
            for (int j = 0; j < nNodes; j++)
                indices[i][j] = new ArrayList<>();
        for (int p = 0; p < allPaths.size(); p++) {
            SRPath path = allPaths.get(p);
            indices[path.source][path.destination].add(p);
        }
        return indices;
    }

    /** Returns the precomputed edge usage table: edgeUsage[pathIndex][edgeIndex]. */
    public double[][] getEdgeUsage() {
        return edgeUsage;
    }


    // Public solve methods

    /**
     * Solves the SR routing problem using MIP.
     */
    public SolveResult solveRouting(List<Demands> demandsList) {
        return solveRouting(demandsList, false);
    }

    /**
     * Solves the SR routing problem using MIP.
     *
     * If runPhase2 is true, performs a second phase that minimizes the
     * sum of MLUs while keeping max MLU at or below the Phase 1 optimum.
     *
     * @param demandsList list of demand matrices to optimize for
     * @param runPhase2   if true and multiple matrices, minimize average MLU
     * @return the solve result containing MLU and selected paths
     */
    public SolveResult solveRouting(List<Demands> demandsList, boolean runPhase2) {
        try {
            GRBEnv env = new GRBEnv(true);
            env.set(GRB.IntParam.LogToConsole, 0);
            env.set(GRB.StringParam.LogFile, "out/gurobi.log");
            env.start();

            GRBModel model = new GRBModel(env);
            model.set(GRB.DoubleParam.TimeLimit, (double) (maxTime / 1000));
            model.set(GRB.IntParam.Threads, 4);

            int nNodes = topology.nNodes;
            int nEdges = topology.nEdges;
            int nMatrices = demandsList.size();
            int nPaths = allPaths.size();

            // Binary variables: x[p] = 1 if path p is selected
            GRBVar[] x = new GRBVar[nPaths];
            for (int p = 0; p < nPaths; p++) {
                x[p] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "x_" + p);
            }

            GRBVar uMax = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "uMax");

            GRBVar[] uMatrix = new GRBVar[nMatrices];
            for (int m = 0; m < nMatrices; m++) {
                uMatrix[m] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "u_matrix_" + m);
            }

            // Objective: minimize uMax
            GRBLinExpr objExpr = new GRBLinExpr();
            objExpr.addTerm(1.0, uMax);
            model.setObjective(objExpr, GRB.MINIMIZE);
            model.set(GRB.DoubleParam.MIPGap, 0.0009765625); // 2**-10

            // Constraint: exactly one path per (src, dst) pair
            for (int src = 0; src < nNodes; src++) {
                for (int dst = 0; dst < nNodes; dst++) {
                    if (src == dst) continue;

                    List<Integer> pathIndices = pathIndicesByPair[src][dst];
                    if (pathIndices.isEmpty()) {
                        System.err.println("WARNING: No path available for pair (" + src + ", " + dst +
                                "). Check SR paths file.");
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
            for (int matrixIdx = 0; matrixIdx < nMatrices; matrixIdx++) {
                Demands demands = demandsList.get(matrixIdx);

                for (int edge = 0; edge < nEdges; edge++) {
                    GRBLinExpr edgeLoad = new GRBLinExpr();

                    for (int demandIdx = 0; demandIdx < demands.nDemands; demandIdx++) {
                        int src = demands.source[demandIdx];
                        int dst = demands.dest[demandIdx];
                        double amount = demands.amount[demandIdx];

                        for (int p : pathIndicesByPair[src][dst]) {
                            double pathEdgeUsage = edgeUsage[p][edge];
                            if (pathEdgeUsage > 1e-10) {
                                edgeLoad.addTerm(amount * pathEdgeUsage, x[p]);
                            }
                        }
                    }

                    GRBLinExpr capacityConstr = new GRBLinExpr();
                    capacityConstr.add(edgeLoad);
                    capacityConstr.addTerm(-topology.edgeCapacity[edge], uMatrix[matrixIdx]);
                    model.addConstr(capacityConstr, GRB.LESS_EQUAL, 0.0,
                            "capacity_" + edge + "_matrix_" + matrixIdx);
                }

                GRBLinExpr linkToMax = new GRBLinExpr();
                linkToMax.addTerm(1.0, uMatrix[matrixIdx]);
                linkToMax.addTerm(-1.0, uMax);
                model.addConstr(linkToMax, GRB.LESS_EQUAL, 0.0,
                        "uMatrix_leq_uMax_" + matrixIdx);
            }

            // Phase 1: minimize max MLU
            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            if (status != GRB.OPTIMAL && status != GRB.SUBOPTIMAL) {
                System.err.println("Gurobi did not find an optimal solution. Status: " + status);
                model.dispose();
                env.dispose();
                return new SolveResult(Double.MAX_VALUE, null, null);
            }

            double optimalMaxMLU = model.get(GRB.DoubleAttr.ObjVal);

            // Phase 2: if enabled minimize average MLU while keeping max MLU bounded
            if (runPhase2 && nMatrices > 1) {
                System.out.println("Phase 1 optimal max MLU: " + optimalMaxMLU);
                System.out.println("Starting Phase 2: minimizing average MLU...");

                // Add constraint: uMax <= optimalMaxMLU (with small tolerance for numerical stability)
                GRBLinExpr maxMLUBound = new GRBLinExpr();
                maxMLUBound.addTerm(1.0, uMax);
                model.addConstr(maxMLUBound, GRB.LESS_EQUAL, optimalMaxMLU * 1.00001,
                        "max_mlu_bound");

                // Change objective to minimize sum of individual MLUs
                GRBLinExpr avgObjExpr = new GRBLinExpr();
                for (int m = 0; m < nMatrices; m++) {
                    avgObjExpr.addTerm(1.0, uMatrix[m]);
                }
                model.setObjective(avgObjExpr, GRB.MINIMIZE);

                // Set a looser optimality tolerance for Phase 2 to speed up solve
                model.set(GRB.DoubleParam.MIPGap, 0.00390625); // 2**-8

                model.optimize();

                status = model.get(GRB.IntAttr.Status);
                if (status != GRB.OPTIMAL && status != GRB.SUBOPTIMAL) {
                    System.err.println("Phase 2: Gurobi did not find optimal solution. Status: " + status);
                } else {
                    double totalMLU = model.get(GRB.DoubleAttr.ObjVal);
                    System.out.println("Phase 2 total MLU: " + totalMLU + ", average MLU: " + totalMLU / nMatrices);
                }
            }

            // Extract per-matrix MLU values
            double[] perMatrixMLU = new double[nMatrices];
            for (int m = 0; m < nMatrices; m++) {
                perMatrixMLU[m] = uMatrix[m].get(GRB.DoubleAttr.X);
            }

            // Extract selected paths
            int[][] selectedPathIndex = new int[nNodes][nNodes];
            for (int[] row : selectedPathIndex) Arrays.fill(row, -1);

            for (int p = 0; p < nPaths; p++) {
                double val = x[p].get(GRB.DoubleAttr.X);
                if (val > 0.5) {
                    SRPath path = allPaths.get(p);
                    selectedPathIndex[path.source][path.destination] = p;
                }
            }

            model.dispose();
            env.dispose();

            return new SolveResult(optimalMaxMLU, selectedPathIndex, perMatrixMLU);

        } catch (GRBException e) {
            System.err.println("Gurobi error: " + e.getErrorCode() + ". " + e.getMessage());
            return new SolveResult(Double.MAX_VALUE, null, null);
        }
    }

    // MCF solve (used for scaling)

    /**
     * Solves the MCF problem to get the optimal MLU (lower bound, used for scaling).
     * Delegates to {@link MCFAggregated#computeOptimalMLU(Topology, Demands)}.
     */
    public double solveMCF(Demands demands) {
        return MCFAggregated.computeOptimalMLU(topology, demands);
    }

    // Edge usage precomputation (ECMP)

    private double[][] precomputeEdgeUsage() {
        int nPaths = allPaths.size();
        int nEdges = topology.nEdges;
        double[][] usage = new double[nPaths][nEdges];

        for (int p = 0; p < nPaths; p++) {
            usage[p] = computePathEdgeUsage(allPaths.get(p));
        }
        return usage;
    }

    private double[] computePathEdgeUsage(SRPath path) {
        double[] usage = new double[topology.nEdges];
        int currentNode = path.source;

        for (int i = 0; i < path.numSegments; i++) {
            if (path.isAdjacencySegment(i)) {
                int edgeIndex = path.getEdgeSegment(i);
                usage[edgeIndex] += 1.0;
                currentNode = topology.edgeDest[edgeIndex];
            } else {
                int nextNode = path.getNodeSegment(i);
                if (currentNode != nextNode) {
                    addECMPEdgeUsage(usage, currentNode, nextNode);
                }
                currentNode = nextNode;
            }
        }
        return usage;
    }

    private void addECMPEdgeUsage(double[] edgeUsage, int source, int destination) {
        if (source == destination) return;

        double[] flowFraction = new double[topology.nNodes];
        flowFraction[source] = 1.0;

        int nOrdering = shortestPaths.makeTopologicalOrdering(source, destination);

        for (int i = nOrdering - 1; i >= 0; i--) {
            int node = shortestPaths.topologicalOrdering[i];
            double nodeFlow = flowFraction[node];
            if (nodeFlow < 1e-10) continue;

            int nSucc = shortestPaths.nSuccessors[destination][node];
            if (nSucc == 0) continue;

            double splitFraction = nodeFlow / nSucc;

            for (int s = 0; s < nSucc; s++) {
                int succNode = shortestPaths.successorNodes[destination][node][s];
                int succEdge = shortestPaths.successorEdges[destination][node][s];

                edgeUsage[succEdge] += splitFraction;
                flowFraction[succNode] += splitFraction;
            }
        }
    }
}
