package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.io.RepetitaWriter;
import edu.repetita.solvers.SRSolver;
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

        int nbPaths = 0;
        StringBuilder builder = new StringBuilder();
        int[][] paths;
        for (int originNumber = 0; originNumber < nNodes; originNumber++) {
            for (int destNumber = 0; destNumber < nNodes; destNumber++) {
                paths = root.getODPaths(originNumber, destNumber);
                for (int i = 0; i < paths.length; i++) {
                    builder.append(Arrays.toString(paths[i]));
                    builder.append("\n");
                    nbPaths++;
                }
            }
        }
        RepetitaWriter.writeToPathFile(builder.toString());
        System.out.println("Topology : " + setting.getTopologyFilename());
        System.out.println("Segments : " + setting.getMaxSegments());
        System.out.println("Time elapsed : " + (double)timeElapsed/1000 + " seconds");
        int maxNbPaths=0;
        int temp;
        for (int i = 2; i <= setting.getMaxSegments()+1; i++) {
            temp = 1;
            for (int j = 0; j < i; j++) {
                temp *= nNodes-j;
            }
            maxNbPaths += temp;
        }
        System.out.println("Number of paths : " + nbPaths);
        System.out.println("Maximum number of paths : " + maxNbPaths);
        System.out.println("Percentage : " + (double)nbPaths/maxNbPaths*100);
        System.out.println("\n\n");
    }

    @Override
    public long solveTime(Setting setting) {
        return solveTime;
    }
}
