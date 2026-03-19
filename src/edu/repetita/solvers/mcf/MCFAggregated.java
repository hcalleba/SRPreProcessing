package edu.repetita.solvers.mcf;

import com.gurobi.gurobi.*;
import edu.repetita.core.Demands;
import edu.repetita.core.Topology;


public class MCFAggregated {

    /**
     * Solves the MCF problem for a single demand matrix and returns the optimal MLU.
     * Uses the destination-aggregated formulation: N×E flow variables (one per destination per edge)
     * This is a convenience method usable from other solvers (e.g. for scaling).
     */
    public static double computeOptimalMLU(Topology topology, Demands demands) {
        try {
            GRBEnv env = new GRBEnv(true);
            env.set(GRB.IntParam.OutputFlag, 0);
            env.set(GRB.IntParam.LogToConsole, 0);
            env.start();

            GRBModel model = new GRBModel(env);

            int nNodes = topology.nNodes;
            int nEdges = topology.nEdges;

            // Precompute net supply/demand per destination node:
            // b[d][v] < 0 means v is a source sending to d, b[d][d] > 0 means d absorbs all flow
            double[][] b = new double[nNodes][nNodes];
            for (int i = 0; i < demands.nDemands; i++) {
                int src = demands.source[i];
                int dst = demands.dest[i];
                double amount = demands.amount[i];
                b[dst][src] -= amount;  // supply at source (negative = net outflow)
                b[dst][dst] += amount;  // sink at destination (positive = net inflow)
            }

            // Aggregated flow variables: f[d][e] = total flow destined to node d on edge e
            // Only created for destinations that actually have demand
            GRBVar[][] f = new GRBVar[nNodes][];
            for (int d = 0; d < nNodes; d++) {
                if (b[d][d] <= 0) continue;
                f[d] = new GRBVar[nEdges];
                for (int e = 0; e < nEdges; e++) {
                    f[d][e] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "f_" + d + "_" + e);
                }
            }

            GRBVar uMax = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "uMax");

            GRBLinExpr objExpr = new GRBLinExpr();
            objExpr.addTerm(1.0, uMax);
            model.setObjective(objExpr, GRB.MINIMIZE);

            // Flow conservation: for each destination d and each node v
            for (int d = 0; d < nNodes; d++) {
                if (f[d] == null) continue;
                for (int v = 0; v < nNodes; v++) {
                    GRBLinExpr flowBalance = new GRBLinExpr();
                    for (int e = 0; e < nEdges; e++) {
                        if (topology.edgeSrc[e] == v)
                            flowBalance.addTerm(1.0, f[d][e]);
                        if (topology.edgeDest[e] == v)
                            flowBalance.addTerm(-1.0, f[d][e]);
                    }
                    model.addConstr(flowBalance, GRB.EQUAL, b[d][v], "fc_" + d + "_" + v);
                }
            }

            // Capacity constraints: for each edge e, sum of all destination flows <= capacity * uMax
            for (int e = 0; e < nEdges; e++) {
                GRBLinExpr edgeLoad = new GRBLinExpr();
                for (int d = 0; d < nNodes; d++) {
                    if (f[d] == null) continue;
                    edgeLoad.addTerm(1.0, f[d][e]);
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
}
