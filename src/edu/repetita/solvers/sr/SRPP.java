package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;
import edu.repetita.simulators.specialized.ECMPFlowSimulator;
import edu.repetita.solvers.SRSolver;
import edu.repetita.solvers.sr.srpp.ComparableIntPair;

import java.util.Arrays;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

public class SRPP extends SRSolver {

    private long solveTime = 0;


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

        Topology topology = setting.getTopology();
        int nEdges = topology.nEdges;
        int nNodes = topology.nNodes;
        Demands demands = setting.getDemands();
        int maxSegments = setting.getMaxSegments();

        float[][][] arcLoadPerPair = makeArcLoadPerPair(topology);

    }

    @Override
    public long solveTime(Setting setting) {
        return solveTime;
    }

    /**
     * Creates the array arcLoadPair[|N|][|N|][|A|]
     * For each triplet (U,V,a); U,V nodes and a an edge;
     * arcLoadPerPair[U][V][a] is the load on arc a when there is a demand of 1 from V to U.
     * @param topology the topology of the network
     * @return arcLoadPair[][][] as explained above
     */
    public static float[][][] makeArcLoadPerPair(Topology topology) {

        int nEdges = topology.nEdges;
        int nNodes = topology.nNodes;
        float[][][] arcLoadPerPair = new float[nNodes][nNodes][nEdges];

        // Compute the shortest paths in the graph, from there we get the forwarding graph of each node
        ShortestPaths sp = new ShortestPaths(topology);
        sp.computeShortestPaths();

        // Loop over all (destination) nodes
        for (int dest = 0; dest < nNodes; dest++) {
            // Sort the indices of sp.distance[dest]
            ComparableIntPair[] nodesSortedByDistance = new ComparableIntPair[nNodes];
            for (int i = 0; i < nNodes; i++) {
                nodesSortedByDistance[i] = new ComparableIntPair(i, sp.distance[dest][i]);
            }
            Arrays.sort(nodesSortedByDistance);

            // Starting from the closest node origin we will now fill arcLoadPerPair[dest][origin][] for all arcs
            for (int i = 1; i < nNodes; i++) {
                int origin = nodesSortedByDistance[i].index;
                fillArcUsage(dest, origin, arcLoadPerPair[dest], sp, nEdges);
            }
        }
        return arcLoadPerPair;
    }

    /**
     * Simulates a demand of one from origin to dest and stores the load in arcLoadDest.
     * The loads for all nodes where the distance to dest is smaller the distance origin-dest should already be computed
     * as it makes use of these loads to compute the new origin-dest load.
     * @param dest The destination node
     * @param origin The origin node
     * @param arcLoadDest The current computed arc loads for the destination node dest.
     *                    It must already be computed for all nodes closer to dest than origin
     */
    private static void fillArcUsage(int dest, int origin, float[][] arcLoadDest, ShortestPaths sp, int nEdges) {
        int nSuccessors = sp.nSuccessors[dest][origin];
        for (int i = 0; i < nSuccessors; i++) {
            // Add the load on the direct edge to the new node
            int nextEdge = sp.successorEdges[dest][origin][i];
            arcLoadDest[origin][nextEdge] = 1.0f/nSuccessors;
            // Add the load when routing from nextNode to dest
            int nextNode = sp.successorNodes[dest][origin][i];
            if (nextNode != dest) {
                for (int j = 0; j < nEdges; j++) {
                    arcLoadDest[origin][j] += 1.0f / nSuccessors * arcLoadDest[nextNode][j];
                }
            }
        }
    }
}
