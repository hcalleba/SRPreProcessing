package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.io.RepetitaParser;
import edu.repetita.io.RepetitaWriter;
import edu.repetita.solvers.SRSolver;
import edu.repetita.solvers.sr.srpp.edgeloads.EdgeLoadsFullArray;
import edu.repetita.solvers.sr.srpp.edgeloads.EdgePair;
import edu.repetita.solvers.sr.srpp.segmenttree.SegmentTreeRoot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import gurobi.*;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

/**
 * Solver that implements preprocessing techniques to eliminate dominated paths in Segment Routing.
 * It then sends this smaller set of paths to an ILP solver that will solve it optimally.
 */
public class SRPP extends SRSolver {

    private long solveTime = 0;
    boolean createOnlyPaths;
    boolean writeOutPaths;
    String inpathsFilename;

    public SRPP(String inpathsFilename, boolean createPaths, boolean writeOutPaths) {
        super();
        this.inpathsFilename = inpathsFilename;
        this.createOnlyPaths = createPaths;
        this.writeOutPaths = writeOutPaths;
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

    @Override
    public void solve(Setting setting, long milliseconds) {

        long start = System.currentTimeMillis();

        Topology topology = setting.getTopology();
        int nEdges = topology.nEdges;
        int nNodes = topology.nNodes;
        Demands demands = setting.getDemands();
        int maxSegments = setting.getMaxSegments();

        SegmentTreeRoot root = new SegmentTreeRoot(topology, maxSegments, Demands.toTrafficMatrix(demands, nNodes));

        ArrayList<int[]> paths = new ArrayList<int[]>();
        if (inpathsFilename == null) {  // Create SR-paths
            root.createODPaths();
            for (int originNumber = 0; originNumber < nNodes; originNumber++) {
                for (int destNumber = 0; destNumber < nNodes; destNumber++) {
                    Collections.addAll(paths, root.getODPaths(originNumber, destNumber));
                }
            }
            root.freeMemory();
        }
        else {  // Load SR-paths from file
            try {
                paths = RepetitaParser.parseSRPaths(inpathsFilename);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        if (inpathsFilename == null) {
            StringBuilder builder = new StringBuilder();
            for (int[] path : paths) {
                builder.append(Arrays.toString(path));
                builder.append("\n");
            }
            RepetitaWriter.writeToPathFile(builder.toString());
        }

        if (createOnlyPaths) {  // We do not solve the ILP
            return;
        }

        /* Here we solve the ILP */
        if (!createOnlyPaths) {
            solveILP(paths, root, topology);
        }






        long finish = System.currentTimeMillis();
        long timeElapsed = finish - start;





        System.out.println("Topology : " + setting.getTopologyFilename());
        System.out.println("Segments : " + setting.getMaxSegments());
        System.out.println("Time elapsed : " + (double)timeElapsed/1000 + " seconds");
    }

    @Override
    public long solveTime(Setting setting) {
        return solveTime;
    }

    private void solveILP (ArrayList<int[]> paths, SegmentTreeRoot root, Topology topology) {
        try {
            /* Create empty environment, set options, and start */
            GRBEnv env = new GRBEnv(true);
            env.set("logFile", "mip1.log");
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
                        model.addConstr(uniquePathExpr[i][j], GRB.EQUAL, 1.0, "unique SR-path-" + i + "-" + j);
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
                EdgeLoadsFullArray edgeLoads = root.getEdgeLoads(path);
                Iterator<EdgePair> it = edgeLoads.iterator();
                while (it.hasNext()) {
                    EdgePair edgePair = it.next();
                    if (edgePair.getLoad() != 0) {
                        uMaxExpr[edgePair.getKey()].addTerm(
                                root.trafficMatrix[path[0]][path[path.length-1]], SRPaths[i]);
                    }
                }
            }
            for (int i =0; i < topology.nEdges; i++) {
                uMaxExpr[i].addTerm(-topology.edgeCapacity[i], uMax);
                model.addConstr(uMaxExpr[i], GRB.LESS_EQUAL, 0.0, "uMax-edge-"+i);
            }

            /* Optimize model */
            model.optimize();
            model.write("mip2.sol");

            /* PRINT RESULT
            for (int i=0; i < paths.size(); i++) {
                System.out.println(SRPaths[i].get(GRB.StringAttr.VarName)+ " " +SRPaths[i].get(GRB.DoubleAttr.X));
            }*/


            /* Dispose of model and environment */
            model.dispose();
            env.dispose();

        } catch(GRBException e) {
            System.out.println("Error code : " + e.getErrorCode() + ". " + e.getMessage());
        }
    }
}
