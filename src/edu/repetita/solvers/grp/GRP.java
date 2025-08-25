package edu.repetita.solvers.grp;

import com.gurobi.gurobi.*;
import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Solver;
import edu.repetita.core.Topology;
import java.util.ArrayList;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

public class GRP extends Solver {

    private long solveTime = 0;
    private long maxTime;

    @Override
    protected void setObjective() {
        objective = SOLVER_OBJVALUES_MINMAXLINKUSAGE;
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public String getDescription() {
        return "Solves the topology with the requested demand file as seen by the general routing problem." +
                "This is not practically implementable in a network but serves as a baselmine for comparisons.";
    }

    @Override
    public void solve(Setting setting, long milliseconds) {
        /* Set variables */
        long startTime = System.currentTimeMillis();
        maxTime = milliseconds;

        solveILP(setting.getTopology(), setting.getDemands());

        solveTime = System.currentTimeMillis() - startTime;
    }

    private double solveILP (Topology topology, ArrayList<Demands> demands) {
        try {
            // Create empty environment set options and start
            GRBEnv env = new GRBEnv(true);
            env.set(GRB.IntParam.OutputFlag, 1);
            env.set("logFile", "out/gurobi.log");
            env.set(GRB.IntParam.LogToConsole, 0);
            env.start();

            /* Create empty model */
            GRBModel model = new GRBModel(env);
            // model.set(GRB.DoubleParam.TimeLimit, (double) (maxTime/1000));
            model.set(GRB.IntParam.Threads, 8);

            /* Create variables */
            GRBVar[][][] flowVars = new GRBVar[topology.nNodes][topology.nNodes][topology.nEdges]; // technically only need to create them for each demand, but easier this way
            for (int startNode = 0; startNode < topology.nNodes; startNode++) {
                for (int endNode = 0; endNode < topology.nNodes; endNode++) {
                    if (startNode == endNode) continue; // no flow from a node to itself
                    for (int edge = 0; edge < topology.nEdges; edge++) {
                        flowVars[startNode][endNode][edge] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "FlowVar-"+startNode+"-"+endNode+"-"+edge);
                    }
                }
            }
            GRBVar uMax = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "uMax");

            /* Set Objective */
            GRBLinExpr objExpr = new GRBLinExpr();
            objExpr.addTerm(1.0, uMax);
            model.setObjective(objExpr, GRB.MINIMIZE);

            /* Adding constraints */
            /* CONSTRAINT : Flow conservation */
            // lhs
            GRBLinExpr[][][] flowConservation = new GRBLinExpr[topology.nNodes][topology.nNodes][topology.nNodes];
            for (int startNode = 0; startNode < topology.nNodes; startNode++) {
                for (int endNode = 0; endNode < topology.nNodes; endNode++) {
                    for (int midNode = 0; midNode < topology.nNodes; midNode++) {
                        flowConservation[startNode][endNode][midNode] = new GRBLinExpr();
                    }
                    for (int edge = 0; edge < topology.nEdges; edge ++) {
                        int edgeStart = topology.edgeSrc[edge];
                        int edgeEnd = topology.edgeDest[edge];
                        flowConservation[startNode][endNode][edgeStart].addTerm(1.0, flowVars[startNode][endNode][edge]);
                        flowConservation[startNode][endNode][edgeEnd].addTerm(-1.0, flowVars[startNode][endNode][edge]);
                    }
                }
            }
            // rhs
            for (int startNode = 0; startNode < topology.nNodes; startNode++) {
                for (int endNode = 0; endNode < topology.nNodes; endNode++) {
                    if (startNode != endNode) {
                        for (int midNode = 0; midNode < topology.nNodes; midNode++) {
                            if (startNode == midNode) {
                                model.addConstr(flowConservation[startNode][endNode][midNode], GRB.EQUAL, 1.0, "FlowConstr-"+startNode+"-"+endNode+"-"+midNode);
                            }
                            else if (endNode == midNode) {
                                model.addConstr(flowConservation[startNode][endNode][midNode], GRB.EQUAL, -1.0, "FlowConstr-"+startNode+"-"+endNode+"-"+midNode);
                            } else {
                                model.addConstr(flowConservation[startNode][endNode][midNode], GRB.EQUAL, 0.0, "FlowConstr-"+startNode+"-"+endNode+"-"+midNode);
                            }
                        }
                    }
                }
            }
            /* CONSTRAINT : max utilisation */
            for (int demandMatrixIdx = 0; demandMatrixIdx < demands.size(); demandMatrixIdx++) {
                Demands demandMatrix = demands.get(demandMatrixIdx);
                GRBLinExpr[] uMaxExpr = new GRBLinExpr[topology.nEdges];
                for (int edge = 0; edge < topology.nEdges; edge++) {
                    uMaxExpr[edge] = new GRBLinExpr();
                    for (int demandFlowIdx = 0; demandFlowIdx < demandMatrix.nDemands; demandFlowIdx++) {
                        // for each edge add the demand times the proportional flow variable
                        uMaxExpr[edge].addTerm(demandMatrix.amount[demandFlowIdx], flowVars[demandMatrix.source[demandFlowIdx]][demandMatrix.dest[demandFlowIdx]][edge]);
                    }
                    uMaxExpr[edge].addTerm(-topology.edgeCapacity[edge], uMax);
                    model.addConstr(uMaxExpr[edge], GRB.LESS_EQUAL, 0.0, "uMax-edge-" + edge + "-demandMatrix-" + demandMatrixIdx);
                }
            }

            model.write("out/model.lp");

            /* Optimize model */
            model.optimize();

            double result = model.get(GRB.DoubleAttr.ObjVal);
            System.out.println("Objective value = " + result);

            /* Dispose of model and environment */
            model.dispose();
            env.dispose();
            return result;
        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
            return 0.0;
        }
    }

    @Override
    public long solveTime(Setting setting) {
        return 0;
    }
}
