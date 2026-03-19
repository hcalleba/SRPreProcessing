package edu.repetita.solvers.mcf;

import com.gurobi.gurobi.*;
import edu.repetita.core.Demands;
import edu.repetita.core.Topology;
import java.util.*;

public class MCFUnaggregated {

    public static class MCFResult {
        public final double mlu;
        /** flowPerDemandPerEdge[k][e] = flow for demand k on edge e */
        public final double[][] flowPerDemandPerEdge;

        public MCFResult(double mlu, double[][] flowPerDemandPerEdge) {
            this.mlu = mlu;
            this.flowPerDemandPerEdge = flowPerDemandPerEdge;
        }
    }

    /**
     * Solves the MCF using the unaggregated (per-demand) formulation.
     *
     * Variables: f[k][e] = flow for demand k on edge e
     * Objective: minimize uMax
     * Constraints:
     *   - Flow conservation per demand per node
     *   - Sum_k f[k][e] <= capacity[e] * uMax for each edge e
     */
    public static MCFResult solve(Topology topology, Demands demands) {
        try {
            GRBEnv env = new GRBEnv(true);
            env.set(GRB.IntParam.OutputFlag, 0);
            env.set(GRB.IntParam.LogToConsole, 0);
            env.start();

            GRBModel model = new GRBModel(env);

            int nNodes = topology.nNodes;
            int nEdges = topology.nEdges;
            int nDemands = demands.nDemands;

            // Flow variables: f[k][e] = flow for demand k on edge e
            GRBVar[][] f = new GRBVar[nDemands][nEdges];
            for (int k = 0; k < nDemands; k++) {
                for (int e = 0; e < nEdges; e++) {
                    f[k][e] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                            "f_" + k + "_" + e);
                }
            }

            GRBVar uMax = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "uMax");

            GRBLinExpr objExpr = new GRBLinExpr();
            objExpr.addTerm(1.0, uMax);
            model.setObjective(objExpr, GRB.MINIMIZE);

            // Precompute adjacency
            List<Integer>[] outEdges = new List[nNodes];
            List<Integer>[] inEdges = new List[nNodes];
            for (int v = 0; v < nNodes; v++) {
                outEdges[v] = new ArrayList<>();
                inEdges[v] = new ArrayList<>();
            }
            for (int e = 0; e < nEdges; e++) {
                outEdges[topology.edgeSrc[e]].add(e);
                inEdges[topology.edgeDest[e]].add(e);
            }

            // Flow conservation: for each demand k and node v
            for (int k = 0; k < nDemands; k++) {
                int src = demands.source[k];
                int dst = demands.dest[k];
                double amount = demands.amount[k];

                for (int v = 0; v < nNodes; v++) {
                    GRBLinExpr flowBalance = new GRBLinExpr();
                    for (int e : outEdges[v]) {
                        flowBalance.addTerm(1.0, f[k][e]);
                    }
                    for (int e : inEdges[v]) {
                        flowBalance.addTerm(-1.0, f[k][e]);
                    }

                    double rhs;
                    if (v == src) {
                        rhs = amount;
                    } else if (v == dst) {
                        rhs = -amount;
                    } else {
                        rhs = 0.0;
                    }
                    model.addConstr(flowBalance, GRB.EQUAL, rhs, "fc_" + k + "_" + v);
                }
            }

            // Capacity constraints: for each edge e, sum_k f[k][e] <= capacity[e] * uMax
            for (int e = 0; e < nEdges; e++) {
                GRBLinExpr edgeLoad = new GRBLinExpr();
                for (int k = 0; k < nDemands; k++) {
                    edgeLoad.addTerm(1.0, f[k][e]);
                }
                edgeLoad.addTerm(-topology.edgeCapacity[e], uMax);
                model.addConstr(edgeLoad, GRB.LESS_EQUAL, 0.0, "cap_" + e);
            }

            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            if (status != GRB.OPTIMAL && status != GRB.SUBOPTIMAL) {
                System.err.println("MCF: no optimal solution. Status: " + status);
                model.dispose();
                env.dispose();
                return new MCFResult(Double.MAX_VALUE, new double[0][0]);
            }

            double mlu = model.get(GRB.DoubleAttr.ObjVal);

            double[][] flowVals = new double[nDemands][nEdges];
            for (int k = 0; k < nDemands; k++) {
                for (int e = 0; e < nEdges; e++) {
                    flowVals[k][e] = f[k][e].get(GRB.DoubleAttr.X);
                }
            }

            model.dispose();
            env.dispose();

            return new MCFResult(mlu, flowVals);

        } catch (GRBException e) {
            System.err.println("MCF Gurobi error: " + e.getErrorCode() + ". " + e.getMessage());
            return new MCFResult(Double.MAX_VALUE, new double[0][0]);
        }
    }
}