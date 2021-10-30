package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.io.RepetitaWriter;
import edu.repetita.paths.ShortestPaths;
import edu.repetita.solvers.SRSolver;
import edu.repetita.solvers.sr.srpp.ComparableIntPair;
import edu.repetita.solvers.sr.srpp.segmenttree.SegmentTreeRoot;

import java.util.Arrays;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

/**
 * Solver that implements preprocessing techniques to eliminate dominated paths in Segment Routing.
 * It then sends this smaller set of paths to an ILP solver that will solve it optimally.
 */
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

        long start = System.currentTimeMillis();

        Topology topology = setting.getTopology();
        int nEdges = topology.nEdges;
        int nNodes = topology.nNodes;
        Demands demands = setting.getDemands();
        int maxSegments = setting.getMaxSegments();

        SegmentTreeRoot root = new SegmentTreeRoot(topology, maxSegments);
        root.createODPaths();

        long finish = System.currentTimeMillis();
        long timeElapsed = finish - start;

        // It is also possible to get all paths through root.getAllPaths(), this would be slightly faster, but the paths
        // would not be ordered  in a convenient way. This is why root.createODPaths() is used
        StringBuilder builder = new StringBuilder();
        int[][] paths;
        for (int originNumber = 0; originNumber < nNodes; originNumber++) {
            for (int destNumber = 0; destNumber < nNodes; destNumber++) {
                paths = root.getODPaths(originNumber, destNumber);
                for (int i = 0; i < paths.length; i++) {
                    builder.append(Arrays.toString(paths[i]));
                    builder.append("\n");
                }
            }
        }
        RepetitaWriter.writeToPathFile(builder.toString());
        System.out.println("Time elapsed : " + timeElapsed);
        System.out.println("End of SRPP solve function");
    }

    @Override
    public long solveTime(Setting setting) {
        return solveTime;
    }
}
