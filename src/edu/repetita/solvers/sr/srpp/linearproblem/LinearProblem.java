package edu.repetita.solvers.sr.srpp.linearproblem;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;
import edu.repetita.solvers.sr.srpp.edgeloads.EdgeFlowVector;
import com.gurobi.gurobi.*;
import edu.repetita.solvers.sr.srpp.edgeloads.NodePair;
import edu.repetita.solvers.sr.srpp.edgeloads.UnifiedDemand;

import java.util.*;

public class LinearProblem {
    int nbThreads = 8;
    DEBUG debug = DEBUG.MODEL;
    double PRECISION = 0.000001;
    private final EdgeFlowVector[][] shortestPathsCache;
    private final Map<NodePair, UnifiedDemand> unifiedDemands;

    Topology topology;
    ArrayList<int[]> paths;
    ArrayList<Demands> demandMatrices;

    GRBEnv env;
    GRBModel model;
    GRBVar[] SRPaths;
    GRBVar uMax;

    public LinearProblem(ArrayList<int[]> paths, Setting setting) {
        this.topology = setting.getTopology();
        this.paths = paths;
        this.demandMatrices = setting.getDemands();
        this.shortestPathsCache = new EdgeFlowVector[topology.nNodes][topology.nNodes];
        this.unifiedDemands = buildUnifiedDemands();
    }

    public double execute (long endTime, boolean continuous) {
            createModel(endTime, continuous);
            return solve();
    }

    private void createModel(long endTime, boolean continuous) {
        try {
            /* Create empty environment, set options, and start */
            env = new GRBEnv(true);
            switch (debug) {
                case NONE:
                    env.set(GRB.IntParam.OutputFlag, 0);
                    env.set(GRB.IntParam.LogToConsole, 0);
                    break;
                case MODEL:
                case FILE:
                    env.set("logFile", "out/gurobi.log");
                    env.set(GRB.IntParam.OutputFlag, 1);
                    env.set(GRB.IntParam.LogToConsole, 0);
                    break;
                case CONSOLE:
                    env.set(GRB.IntParam.OutputFlag, 0);
                    env.set(GRB.IntParam.LogToConsole, 1);
            }
            env.start();

            /* Create empty model */
            model = new GRBModel(env);
            model.set(GRB.DoubleParam.TimeLimit, (double) (endTime-System.currentTimeMillis())/1000);
            model.set(GRB.IntParam.Threads, nbThreads);
            model.set(GRB.DoubleParam.OptimalityTol, PRECISION);

            /* Create variables */
            createDefaultVariables(continuous);

            /* set objective */
            setUMaxObjective();

            /* Adding constraints */
            createUniquePathExpr();
            createUMaxExprTM();

            if (debug == DEBUG.MODEL) {
                model.write("out/model.lp");
            }
        }
        catch (GRBException e) {
            throw new RuntimeException(e);
        }
    }

    private void createDefaultVariables(boolean continuous) throws GRBException {
        SRPaths = new GRBVar[paths.size()];
        char varType = continuous ? GRB.CONTINUOUS : GRB.BINARY;
        for (int i = 0; i < paths.size(); i++) {
            SRPaths[i] = model.addVar(0.0, 1.0, 0.0, varType, "SR-path-"+ Arrays.toString(paths.get(i)));
        }
        uMax = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "uMax");
    }

    private void setUMaxObjective() throws GRBException {
        GRBLinExpr objExpr = new GRBLinExpr();
        objExpr.addTerm(1.0, uMax);
        model.setObjective(objExpr, GRB.MINIMIZE);
    }

    private void createUniquePathExpr() throws GRBException {
        /* create the expressions */
        GRBLinExpr[][] uniquePathExpr = new GRBLinExpr[topology.nNodes][topology.nNodes];
        for (int i = 0; i < topology.nNodes; i++) {
            for (int j = 0; j < topology.nNodes; j++) {
                if (i != j) {
                    uniquePathExpr[i][j] = new GRBLinExpr();
                }
            }
        }
        /* add the variables corresponding to the paths to the expressions */
        for (int i = 0; i < paths.size(); i++) {
            int[] path = paths.get(i);
            int startNode = getStartNode(path);
            int endNode = getEndNode(path);
            uniquePathExpr[startNode][endNode].addTerm(1.0, SRPaths[i]);
        }
        /* add expressions to the model */
        for (int i = 0; i < topology.nNodes; i++) {
            for (int j = 0; j < topology.nNodes; j++) {
                if (i != j) {
                    model.addConstr(uniquePathExpr[i][j], GRB.EQUAL, 1.0, "unique_SR-path-" + i + "-" + j);
                }
            }
        }
    }

    private void createUMaxExprTM() throws GRBException {
        computeShortestPaths(topology);
        GRBLinExpr[][] uMaxExpr = getGrbLinExprs();
        addUMaxExpr(uMaxExpr);
    }

    private void addUMaxExpr(GRBLinExpr[][] uMaxExpr) throws GRBException {
        /* subtract uMax * edge capacity to the expressions and add them to the model */

        for (int tmIdx = 0; tmIdx < demandMatrices.size(); tmIdx++) {
            for (int i = 0; i < topology.nEdges; i++) {
                uMaxExpr[tmIdx][i].addTerm(-topology.edgeCapacity[i], uMax);
                model.addConstr(uMaxExpr[tmIdx][i], GRB.LESS_EQUAL, 0.0, "uMax-TM-" + tmIdx + "-edge-" + i);
            }
        }
    }

    /**
     * Returns an array of expressions corresponding to
     * $$\sum_{(s,t) \in N \times N} \textbf{D}(s,t) \sum_{p \in \mathcal{P}^k_{(s,t)}} f^p_a x_p \forall a \in A \forall demand matrix$$
     * @return the array of expressions
     */
    private GRBLinExpr[][] getGrbLinExprs() {
        /* create the expressions */
        GRBLinExpr[][] uMaxExpr = new GRBLinExpr[demandMatrices.size()][topology.nEdges];
        for (int tmIdx = 0; tmIdx < demandMatrices.size(); tmIdx++) {
            for (int edgeIdx = 0; edgeIdx < topology.nEdges; edgeIdx++) {
                uMaxExpr[tmIdx][edgeIdx] = new GRBLinExpr();
            }
        }

        /* for each path, for each edge it uses, adds the path with potential traffic to the expression */
        for (int pathIdx = 0; pathIdx < paths.size(); pathIdx++) {
            int[] path = paths.get(pathIdx);
            int startNode = getStartNode(path);
            int endNode = getEndNode(path);

            // Compute edge loads for this path
            Map<Integer, Double> edgeLoads = computeEdgeLoadsForPath(path);

            // Add to expressions for each TM
            for (int tmIdx = 0; tmIdx < demandMatrices.size(); tmIdx++) {
                UnifiedDemand unifiedDemand = unifiedDemands.get(new NodePair(startNode, endNode));
                if (unifiedDemand == null) {
                    continue; // Skip this path if no demand exists
                }
                double demandValue = unifiedDemand.amounts[tmIdx];


                for (Map.Entry<Integer, Double> entry : edgeLoads.entrySet()) {
                    int edgeId = entry.getKey();
                    double load = entry.getValue();

                    if (load > 0) {
                        uMaxExpr[tmIdx][edgeId].addTerm(demandValue * load, SRPaths[pathIdx]);
                    }
                }
            }
        }
        return uMaxExpr;
    }

    private double solve() {
        try {
            model.optimize();
            return model.get(GRB.DoubleAttr.ObjVal);
        } catch (GRBException e) {
            throw new RuntimeException(e);
        }
    }

    public void dispose() {
        try {
            model.dispose();
            env.dispose();
        } catch (GRBException e) {
            throw new RuntimeException(e);
        }
    }

    public String getSolution() {
        try {
            StringBuilder builder = new StringBuilder();
            builder.append("Each row corresponds to an SR-path used for routing in the form: \n");
            builder.append("[originNode, firstSegmentNode, ..., destinationNode]\n");
            for (int i=0; i < paths.size(); i++) {
                if (SRPaths[i].get(GRB.DoubleAttr.X) != 0.0) {
                    builder.append(Arrays.toString(paths.get(i))).append("\n");
                }
            }
            return builder.toString();
        } catch (GRBException e) {
            throw new RuntimeException(e);
        }
    }

    private ArrayList<int[]> getPaths() {
        ArrayList<int[]> ret = new ArrayList<>();
        try {
            for (int i=0; i < paths.size(); i++) {
                if (SRPaths[i].get(GRB.DoubleAttr.X) != 0.0) {
                    ret.add(paths.get(i));
                }
            }
        } catch (GRBException e) {
            throw new RuntimeException(e);
        }
        return ret;
    }

    public int getStartNode(int[] path) {
        return path[0];
    }
    public int getEndNode(int[] path) {
        return (path[path.length-1] < topology.nNodes) ? path[path.length-1] : topology.edgeDest[path[path.length-1]-topology.nNodes];
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

    /**
     * Computes the fractional edge loads for a given path by extending it using shortestPathsCache.
     * Path segments < nNodes are node IDs (use shortest path), >= nNodes are direct edge IDs.
     * @param path the SR path
     * @return map of edgeId -> fractional load
     */
    private Map<Integer, Double> computeEdgeLoadsForPath(int[] path) {
        Map<Integer, Double> edgeLoads = new HashMap<>();

        for (int i = 0; i < path.length - 1; i++) {
            int currentSegment = path[i];
            int nextSegment = path[i + 1];

            int fromNode = (currentSegment < topology.nNodes) ? currentSegment : topology.edgeDest[currentSegment - topology.nNodes];

            if (nextSegment < topology.nNodes) {
                // Next segment is a node - use shortest path from cache
                int toNode = nextSegment;
                EdgeFlowVector shortestPath = shortestPathsCache[fromNode][toNode];

                if (shortestPath != null) {
                    for (int j = 0; j < shortestPath.edgeIds.length; j++) {
                        int edgeId = shortestPath.edgeIds[j];
                        double frac = shortestPath.frac[j];
                        edgeLoads.merge(edgeId, frac, Double::sum);
                    }
                }
            } else {
                // Next segment is a direct edge
                int edgeId = nextSegment - topology.nNodes;
                edgeLoads.merge(edgeId, 1.0, Double::sum);
            }
        }

        return edgeLoads;
    }

    private Map<NodePair, UnifiedDemand> buildUnifiedDemands() {
        Map<NodePair, UnifiedDemand> unified = new HashMap<>();

        for (int matrixIdx = 0; matrixIdx < demandMatrices.size(); matrixIdx++) {
            Demands demands = demandMatrices.get(matrixIdx);
            for (int demIdx = 0; demIdx < demands.nDemands; demIdx++) {
                NodePair pair = new NodePair(demands.source[demIdx], demands.dest[demIdx]);

                UnifiedDemand unifiedDem = unified.computeIfAbsent(pair,
                        k -> new UnifiedDemand(demandMatrices.size()));

                unifiedDem.amounts[matrixIdx] = demands.amount[demIdx];
                unifiedDem.demandIndices[matrixIdx] = demIdx;
            }
        }

        return unified;
    }

}
