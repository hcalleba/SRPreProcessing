package edu.repetita.solvers.sr.srpp.linearproblem;

import edu.repetita.core.Topology;
import edu.repetita.solvers.sr.srpp.edgeloads.EdgeLoadsLinkedList;
import edu.repetita.solvers.sr.srpp.edgeloads.EdgePair;
import edu.repetita.solvers.sr.srpp.segmenttree.SegmentTreeRoot;
import gurobi.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

enum VARS {
    DEFAULT,
    ROBUSTDUAL
}

public class LinearProblem {
    int nbThreads = 8;
    boolean highPrecision = true; // if true sets the optimalityTol parameter to 10^-8, o/w defaults to 10^-4
    DEBUG debug = DEBUG.MODEL;
    OBJECTIVE obj = OBJECTIVE.UMAX;
    ROBUST robustType;
    int robustGamma = 2; // TODO parametrize, should test that is is lower than nb of demands
    double robustDeviation = 1; // TODO parametrize

    Topology topology;
    ArrayList<int[]> paths;
    SegmentTreeRoot root;
    double[][] initialTM;

    GRBEnv env;
    GRBModel model;
    GRBVar[] SRPaths;
    GRBVar uMax;
    GRBVar[] delta;
    GRBVar[][][] lambda;

    ArrayList<BadTM> worstTMs;

    public LinearProblem(ROBUST robust, ArrayList<int[]> paths, SegmentTreeRoot root, Topology topology) {
        this.robustType = robust;
        this.topology = topology;
        this.paths = paths;
        this.root = root;
        this.initialTM = root.trafficMatrix;
    }

    public double execute (long endTime) {
        switch (robustType) {
            case NONE:
                createModel(endTime, false, VARS.DEFAULT, initialTM);
                break;
            case DUAL:
                createModel(endTime, false, VARS.ROBUSTDUAL, initialTM);
                break;
            case ITERATIVE_INTEGER:
                createModel(endTime, false, VARS.DEFAULT, initFirstRobustTM());
                break;
            case ITERATIVE_CONTINUOUS:
            case ITERATIVE_MIXED:
                createModel(endTime, true, VARS.DEFAULT, initFirstRobustTM());
                break;
            default:
                throw new RuntimeException("Robust formulation not implemented");
        }

        switch(robustType) {
            case NONE:
            case DUAL:
                return solve();
            case ITERATIVE_INTEGER:
                return iterativeIntegerLoop();
                // faire un solve ajouter pire matrice et refaire un solve etc.
            case ITERATIVE_CONTINUOUS:
            case ITERATIVE_MIXED:
                // TODO
                return 0.0;
            default:
                throw new RuntimeException("Robust formulation not implemented");
        }
    }

    private void createModel(long endTime, boolean continuous, VARS vars, double[][] initialTM) {
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
            env.set("logFile", "out/gurobi.log");
            env.start();

            /* Create empty model */
            model = new GRBModel(env);
            model.set(GRB.DoubleParam.TimeLimit, (double) (endTime-System.currentTimeMillis())/1000);
            model.set(GRB.IntParam.Threads, nbThreads);
            if (highPrecision) {
                model.set(GRB.DoubleParam.OptimalityTol, 0.00000001);
            }

            /* Create variables */
            createDefaultVariables(continuous);
            if (vars == VARS.ROBUSTDUAL) {
                createRobustVariables();
            }

            /* set objective */
            if (Objects.requireNonNull(obj) == OBJECTIVE.UMAX) {
                setUMaxObjective();
            } else {
                throw new RuntimeException("Objective not implemented");
            }

            /* Adding constraints */
            createUniquePathExpr();
            if (vars == VARS.ROBUSTDUAL) {
                createRobustUMaxExprTM(initialTM);
                createRobustConstraint();
            } else {
                createUMaxExprTM(initialTM);
            }

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

    private void createRobustVariables() throws GRBException {
        delta = new GRBVar[topology.nEdges];
        for (int i = 0; i < topology.nEdges; i++) {
            delta[i] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "delta-"+i);
        }
        lambda = new GRBVar[topology.nEdges][topology.nNodes][topology.nNodes];
        for (int a = 0; a < topology.nEdges; a++) {
            for (int s = 0; s < topology.nNodes; s++) {
                for (int t = 0; t < topology.nNodes; t++) {
                    lambda[a][s][t] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "lambda-"+a+"-"+s+"-"+t);
                }
            }
        }
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
                uniquePathExpr[i][j] = new GRBLinExpr();
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

    private void createUMaxExprTM(double[][] TM) throws GRBException {
        GRBLinExpr[] uMaxExpr = getGrbLinExprs(TM);
        addUMaxExpr(uMaxExpr);
    }

    private void createRobustUMaxExprTM(double[][] TM) throws GRBException {
        GRBLinExpr[] uMaxExpr = getGrbLinExprs(TM);

        /* add the robust part to the expressions */
        for (int i = 0; i < topology.nEdges; i++) {
            uMaxExpr[i].addTerm(robustGamma, delta[i]);
            for (int s = 0; s < topology.nNodes; s++) {
                for (int t = 0; t < topology.nNodes; t++) {
                    uMaxExpr[i].addTerm(1.0, lambda[i][s][t]);
                }
            }
        }
        addUMaxExpr(uMaxExpr);
    }

    private void addUMaxExpr(GRBLinExpr[] uMaxExpr) throws GRBException {
        /* subtract uMax * edge capacity to the expressions and add them to the model */
        for (int i =0; i < topology.nEdges; i++) {
            uMaxExpr[i].addTerm(-topology.edgeCapacity[i], uMax);
            model.addConstr(uMaxExpr[i], GRB.LESS_EQUAL, 0.0, "uMax-edge-"+i);
        }
    }

    /**
     * Returns an array of expressions corresponding to
     * $$\sum_{(s,t) \in N \times N} \textbf{D}(s,t) \sum_{p \in \mathcal{P}^k_{(s,t)}} f^p_a x_p \forall a \in A$$
     * @param TM the traffic matrix from which the values for $$\textbf{D}(s,t)$$ are taken
     * @return the array of expressions
     */
    private GRBLinExpr[] getGrbLinExprs(double[][] TM) {
        /* create the expressions */
        GRBLinExpr[] uMaxExpr = new GRBLinExpr[topology.nEdges];
        for (int i = 0; i < topology.nEdges; i++) {
            uMaxExpr[i] = new GRBLinExpr();
        }
        /* for each path, for each edge it uses, adds the path with potential traffic to the expression */
        for (int i = 0; i < paths.size(); i++) {
            int[] path = paths.get(i);
            EdgeLoadsLinkedList edgeLoads = root.getEdgeLoads(path);
            for (EdgePair edgePair : edgeLoads) {
                if (edgePair.getLoad() != 0) {
                    int startNode = getStartNode(path);
                    int endNode = getEndNode(path);
                    uMaxExpr[edgePair.getKey()].addTerm(
                            TM[startNode][endNode]*edgePair.getLoad(), SRPaths[i]);
                }
            }
        }
        return uMaxExpr;
    }

    private GRBLinExpr[] getGrbLinExprs(BadTM worseDemands) {
        double[][] TMcopy = new double[initialTM.length][];
        for (int i = 0; i < initialTM.length; i++) {
            TMcopy[i] = initialTM[i].clone();
        }
        for (int i = 0; i < robustGamma; i++) {
            TMcopy[worseDemands.getStart(i)][worseDemands.getEnd(i)] = TMcopy[worseDemands.getStart(i)][worseDemands.getEnd(i)] + robustDeviation * TMcopy[worseDemands.getStart(i)][worseDemands.getEnd(i)];
        }
        return getGrbLinExprs(TMcopy);
    }

    private void createRobustConstraint() throws GRBException {
        GRBLinExpr[][][] robustExpr = new GRBLinExpr[topology.nEdges][topology.nNodes][topology.nNodes];
        for (int a = 0; a < topology.nEdges; a++) {
            for (int s = 0; s < topology.nNodes; s++) {
                for (int t = 0; t < topology.nNodes; t++) {
                    robustExpr[a][s][t] = new GRBLinExpr();
                    robustExpr[a][s][t].addTerm(1.0, delta[a]);
                    robustExpr[a][s][t].addTerm(1.0, lambda[a][s][t]);
                }
            }
        }
        for (int i = 0; i < paths.size(); i++) {
            int[] path = paths.get(i);
            EdgeLoadsLinkedList edgeLoads = root.getEdgeLoads(path);
            for (EdgePair edgePair : edgeLoads) {
                if (edgePair.getLoad() != 0) {
                    int startNode = path[0];
                    int endNode = (path[path.length-1] < root.nNodes) ? path[path.length-1] : root.edgeDest[path[path.length-1]-root.nNodes];
                    // root.trafficMatrix[startNode][endNode]*robustDeviation corresponds here to e_{st}
                    double lhs = root.trafficMatrix[startNode][endNode] * robustDeviation * edgePair.getLoad();
                    robustExpr[edgePair.getKey()][startNode][endNode].addTerm(-lhs, SRPaths[i]);
                }
            }
        }
        for (int a = 0; a < topology.nEdges; a++) {
            for (int s = 0; s < topology.nNodes; s++) {
                for (int t = 0; t < topology.nNodes; t++) {
                    /* if there are only two terms, the constraint is redundant because we have $\lambda_{ast} + delta_{a} \geq 0$ */
                    if (robustExpr[a][s][t].size() > 2) {
                        model.addConstr(robustExpr[a][s][t], GRB.GREATER_EQUAL, 0.0, "robust-" + a + "-" + s + "-" + t);
                    }
                }
            }
        }
    }

    private double solve() {
        try {
            model.optimize();
            double result = model.get(GRB.DoubleAttr.ObjVal);
            return result;
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

    /**
     * Finds the indices of the robustGamma biggest values in the traffic matrix
     * Since I assume robustGamma is relatively small, I simply iterate a max search robustGamma times
     * instead of sorting the matrix
     * @return an array of robustGamma indices
     */
    private BadTM getInitialWorstTM() {
        double[][] TMcopy = new double[initialTM.length][];
        for (int i = 0; i < initialTM.length; i++) {
            TMcopy[i] = initialTM[i].clone();
        }
        BadTM ret = new BadTM(robustGamma);
        // int[][] ret = new int[2][robustGamma]; // TODO change using tuple and BadTM class
        for (int k=0; k < robustGamma; k++) {
            double max = 0.0;
            for (int i = 0; i < TMcopy.length; i++) {
                for (int j = 0; j < TMcopy.length; j++) {
                    if (TMcopy[i][j] > max) {
                        max = TMcopy[i][j];
                        ret.add(i, j, k);
                    }
                }
            }
            TMcopy[ret.getStart(k)][ret.getEnd(k)] = 0.0;
        }
        return ret;
    }

    private BadTM getWorstTM(ArrayList<int[]> paths) {
        // see GitHub srpp
        return null;
    }

    /**
     *
     * @param worseTM an array of the robustGamma indices of the demands which lead to the worst matrix
     * @return true if the TM was added, false if it was already in the list
     */
    private boolean addNewWorstTM(BadTM worseTM) {
        worseTM.sort();
        for (int i = 0; i < worstTMs.size(); i++) {
            if (worseTM.equals(worstTMs.get(i))) {
                return false;
            }
        }
        worstTMs.add(worseTM);
        return true;
    }

    private double[][] initFirstRobustTM() {
        BadTM worstTM = getInitialWorstTM();
        addNewWorstTM(worstTM);
        double[][] TMcopy = new double[initialTM.length][];
        for (int i = 0; i < initialTM.length; i++) {
            TMcopy[i] = initialTM[i].clone();
        }
        for (int i = 0; i < robustGamma; i++) {
            TMcopy[worstTM.getStart(i)][worstTM.getEnd(i)] = TMcopy[worstTM.getStart(i)][worstTM.getEnd(i)] + robustDeviation * TMcopy[worstTM.getStart(i)][worstTM.getEnd(i)];
        }
        return TMcopy;
    }

    private double iterativeIntegerLoop() {
        double res = solve();
        while(addNewWorstTM(getWorstTM(getPaths()))) {
            try {
                addUMaxExpr(getGrbLinExprs(worstTMs.get(worstTMs.size()-1)));
            } catch (GRBException e) {
                throw new RuntimeException(e);
            }
            res = solve();
        }
        return res;
    }
}
