package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.io.RepetitaParser;
import edu.repetita.io.RepetitaWriter;
import edu.repetita.solvers.SRSolver;
import edu.repetita.solvers.sr.srpp.edgeloads.EdgeLoadsLinkedList;
import edu.repetita.solvers.sr.srpp.edgeloads.EdgePair;
import edu.repetita.solvers.sr.srpp.segmenttree.SegmentTreeRoot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import gurobi.*;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

/**
 * Solver that implements preprocessing techniques to eliminate dominated paths in Segment Routing.
 * It then sends this smaller set of paths to an ILP solver that will solve it optimally.
 */
public class SRPP extends SRSolver {

    private long solveTime = 0;
    boolean writeOutPaths;
    String inpathsFilename;
    String scenarioChoice;

    public SRPP(String inpathsFilename, boolean writeOutPaths, String scenarioChoice) {
        super();
        this.inpathsFilename = inpathsFilename;
        this.writeOutPaths = writeOutPaths;
        this.scenarioChoice = scenarioChoice;
    }

    @Override
    protected void setObjective() {
        objective = SOLVER_OBJVALUES_MINMAXLINKUSAGE;
    }

    @Override
    public String name() {
        return "SRPreProc";
    }

    @Override
    public String getDescription() {
        return "A Segment Routing path optimizer using preprocessing to reduce the amount of SR paths and then" +
                "gives the reduced set of paths to an ILP as parameter";
    }

    /**
     * Solves the unique SR problem with the given setting using preprocessing for the SR-paths and then an ILP to solve
     * the problem with the SR-paths resulting of the preprocessing
     * @param setting the settings of the problem
     * @param milliseconds supposedly the maximum runtime, but is not implemented
     */
    @Override
    public void solve(Setting setting, long milliseconds) {

        long start = System.currentTimeMillis();

        Topology topology = setting.getTopology();
        int nNodes = topology.nNodes;
        Demands demands = setting.getDemands();
        int maxSegments = setting.getMaxSegments();

        SegmentTreeRoot root = new SegmentTreeRoot(topology, maxSegments, Demands.toTrafficMatrix(demands, nNodes));
        ArrayList<int[]> paths = new ArrayList<>();
        /* Preprocess the SR-paths */
        switch (scenarioChoice) {
            case "SRPP":
                root.createODPaths();
                for (int originNumber = 0; originNumber < nNodes; originNumber++) {
                    for (int destNumber = 0; destNumber < nNodes; destNumber++) {
                        if (root.trafficMatrix[originNumber][destNumber] > 0) {
                            Collections.addAll(paths, root.getODPaths(originNumber, destNumber));
                        }
                    }
                }
                root.freeLeavesMemory();
                break;
            /* Load SR-paths from file if one is given */
            case "loadFromFile":
                try {
                    paths = RepetitaParser.parseSRPaths(inpathsFilename);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                break;
            /* Simply create all possible SR-paths */
            case "full":
                for (int depth = 2; depth <= root.maxSegments + 1; depth++) {
                    int[] path = new int[depth];
                    for (int originNode = 0; originNode < nNodes; originNode++) {
                        path[0] = originNode;
                        addSegment(path, 1, nNodes, paths);
                    }
                }
                break;
        }

        solveTime = System.currentTimeMillis() - start;
        System.out.println("Preprocessing time : " + (double)solveTime/1000 + " seconds");

        /*
         Use here preprocessedPathsToFilepaths, fileName) in case one would like to get all preprocessed i.e. all
         non-dominated paths of size <= root.maxSegments to a file.
         Beware that only paths for which a demand exists between an OD pair will be written out.
        */


        /* We solve the ILP */
        start = System.currentTimeMillis();
        solveILP(paths, root, topology);

        long finish = System.currentTimeMillis() - start;
        solveTime += finish;

        System.out.println("ILP solve time : " + (double)finish/1000 + " seconds");
        System.out.println("Topology : " + setting.getTopologyFilename());
        System.out.println("Segments : " + setting.getMaxSegments());
        System.out.println("Total time elapsed : " + (double)solveTime/1000 + " seconds\n");
    }

    /**
     * Function that writes the preprocessed paths to a file
     * @param paths the preprocessed SR-paths
     * @param filename the name of the file to which we should write the SR-paths
     */
    private void preprocessedPathsToFile(ArrayList<int[]> paths, String filename) {
        StringBuilder builder = new StringBuilder();
        for (int[] path : paths) {
            builder.append(Arrays.toString(path));
            builder.append("\n");
        }
        RepetitaWriter.writeToFile(builder.toString(), filename);
    }

    /**
     * helper function used to enumerate all possible SR-paths (without them being preprocessed)
     * @param path array to which we are currently writing the path
     * @param idx index which we are processing in path
     * @param nNodes the number of nodes of the topology
     * @param paths an arraylist to which we should add a new path once it is created
     */
    private void addSegment(int[] path, int idx, int nNodes, ArrayList<int[]> paths) {
        if (idx == path.length) {
            paths.add(path.clone());
            return;
        }
        for (int nextNode = 0; nextNode < nNodes; nextNode++) {
            boolean inside = false;
            for (int i = 0; i < idx; i++) {
                inside = inside || path[i]==nextNode;
            }
            if (!inside) {
                path[idx] = nextNode;
                addSegment(path, idx + 1, nNodes, paths);
            }
        }
    }

    @Override
    public long solveTime(Setting setting) {
        return solveTime;
    }

    /**
     * Creates the ILP and solves it based on the information given
     * @param paths list of all SR-paths that could potentially be used in a solution
     * @param root the root of the segmentTree, it contains the trafficMatrix and edge usage for every OSPF routing
     *             between any OD pair
     * @param topology the topology of the graph, used values are: nNodes, nEdges, and edgeCapacity[]
     */
    private void solveILP (ArrayList<int[]> paths, SegmentTreeRoot root, Topology topology) {
        try {
            /* Create empty environment, set options, and start */
            GRBEnv env = new GRBEnv(true);
            env.set("logFile", "out/gurobi.log");
            env.start();

            /* Create empty model */
            GRBModel model = new GRBModel(env);

            /* Create variables */
            GRBVar[] SRPaths = new GRBVar[paths.size()];
            for (int i = 0; i < paths.size(); i++) {
                SRPaths[i] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "SR-path-"+i);
            }
            GRBVar uMax = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "uMax");

            /* Set objective */
            GRBLinExpr objExpr = new GRBLinExpr();
            objExpr.addTerm(1.0, uMax);
            model.setObjective(objExpr, GRB.MINIMIZE);

            /* Adding constraints */
            /* CONSTRAINT : Sum of SR-paths for an OD pair is equal to one */
            GRBLinExpr[][] uniquePathExpr = new GRBLinExpr[topology.nNodes][topology.nNodes];
            for (int i = 0; i < topology.nNodes; i++) {
                for (int j = 0; j < topology.nNodes; j++) {
                    uniquePathExpr[i][j] = new GRBLinExpr();
                }
            }
            for (int i = 0; i < paths.size(); i++) {
                int[] path = paths.get(i);
                uniquePathExpr[path[0]][path[path.length-1]].addTerm(1.0, SRPaths[i]);
            }
            for (int i = 0; i < topology.nNodes; i++) {
                for (int j = 0; j < topology.nNodes; j++) {
                    if(root.trafficMatrix[i][j] > 0) {
                        model.addConstr(uniquePathExpr[i][j], GRB.EQUAL, 1.0, "unique_SR-path-" + i + "-" + j);
                    }
                }
            }
            /* CONSTRAINT : max utilisation */
            GRBLinExpr[] uMaxExpr = new GRBLinExpr[topology.nEdges];
            for (int i = 0; i < topology.nEdges; i++) {
                uMaxExpr[i] = new GRBLinExpr();
            }
            for (int i = 0; i < paths.size(); i++) {
                int[] path = paths.get(i);
                EdgeLoadsLinkedList edgeLoads = root.getEdgeLoads(path);
                for (EdgePair edgePair : edgeLoads) {
                    if (edgePair.getLoad() != 0) {
                        uMaxExpr[edgePair.getKey()].addTerm(
                                root.trafficMatrix[path[0]][path[path.length - 1]], SRPaths[i]);
                    }
                }
            }
            for (int i =0; i < topology.nEdges; i++) {
                uMaxExpr[i].addTerm(-topology.edgeCapacity[i], uMax);
                model.addConstr(uMaxExpr[i], GRB.LESS_EQUAL, 0.0, "uMax-edge-"+i);
            }

            /* Optimize model */
            model.optimize();

            /* Write solution to file */
            StringBuilder builder = new StringBuilder();
            builder.append("Each row corresponds to an SR-path used for routing in the form: \n");
            builder.append("[originNode, firstSegmentNode, ..., destinationNode]\n");
            for (int i=0; i < paths.size(); i++) {
                if (SRPaths[i].get(GRB.DoubleAttr.X) != 0.0) {
                    builder.append(Arrays.toString(paths.get(i))).append("\n");
                }
            }
            RepetitaWriter.writeToPathFile(builder.toString());

            /* Dispose of model and environment */
            model.dispose();
            env.dispose();

        } catch(GRBException e) {
            System.out.println("Error code : " + e.getErrorCode() + ". " + e.getMessage());
        }
    }
}
