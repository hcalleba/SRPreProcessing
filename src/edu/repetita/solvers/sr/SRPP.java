package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.io.RepetitaWriter;
import edu.repetita.solvers.SRSolver;
import edu.repetita.solvers.sr.srpp.segmenttree.SegmentTreeRoot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

/**
 * Solver that implements preprocessing techniques to eliminate dominated paths in Segment Routing.
 * It then sends this smaller set of paths to an ILP solver that will solve it optimally.
 */
public class SRPP extends SRSolver {

    private static long maxExecTime = 86400000;  // In ms (= 24 hours)
    private long preprocessingTime;
    private long ILPSolveTime;
    double uMax = 0.0;

    public SRPP() {
        super();
    }

    @Override
    protected void setObjective() {
        objective = SOLVER_OBJVALUES_MINMAXLINKUSAGE;
    }

    @Override
    public String name() {
        return "SRPP";
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

        /* Set variables */
        Topology topology = setting.getTopology();
        Demands demands = setting.getDemands();
        int maxSegments = setting.getMaxSegments();

        /* preprocessing */
        SegmentTreeRoot root = new SegmentTreeRoot(topology, maxSegments);
        ArrayList<int[]> paths = new ArrayList<>();

        /* Preprocess the SR-paths */
        long startTime = System.currentTimeMillis();
        long endTime;
        if (milliseconds > 0) {
            endTime = startTime + milliseconds;
        } else {
            endTime = startTime + maxExecTime;
        }
        int nbPaths = 0;
        nbPaths = preprocessTopology(topology.nNodes, root, paths, endTime);
        preprocessingTime = System.currentTimeMillis() - startTime;

        /* Solve the ILP or write the non-dominated paths to -outpaths file */
        if (System.currentTimeMillis() < endTime) {
            startTime = System.currentTimeMillis();
            preprocessedPathsToFile(paths);
            ILPSolveTime = System.currentTimeMillis() - startTime;
        }

        /* Log output */
        RepetitaWriter.appendToOutput("OK");
        RepetitaWriter.appendToOutput("Preprocessing time : " + (double)preprocessingTime/1000 + " seconds");
        RepetitaWriter.appendToOutput("ILP solve time : " + (double)ILPSolveTime/1000 + " seconds");
        RepetitaWriter.appendToOutput("Total time elapsed : " + (double)(ILPSolveTime+preprocessingTime)/1000 + " seconds");
        RepetitaWriter.appendToOutput("Total number of paths after preprocessing : " + nbPaths);
        RepetitaWriter.appendToOutput("Number of nodes : " + topology.nNodes);
        RepetitaWriter.appendToOutput("Number of edges : " + topology.nEdges);
    }

    /**
     * Preprocesses the topology to generate all non-dominated paths, all paths or load paths from a file depending
     * on the scenario
     * @param nNodes the number of nodes in the topology
     * @param root the root of the SegmentTree
     * @param paths an arraylist that will serve as container for all the resulting paths
     * @return the number of generated paths in case of preprocessing, 0 otherwise (if all demands strictly positive,
     * this is equal to the size of paths)
     */
    private int preprocessTopology(int nNodes, SegmentTreeRoot root, ArrayList<int[]> paths, long endTime) {
        int nbPaths = 0;
        root.createODPaths(endTime);
        if (System.currentTimeMillis() > endTime) {
            return -1;
        }
        for (int originNumber = 0; originNumber < nNodes; originNumber++) {
            for (int destNumber = 0; destNumber < nNodes; destNumber++) {
                if (originNumber != destNumber) {
                    nbPaths += root.getODPaths(originNumber, destNumber).length;
                    Collections.addAll(paths, root.getODPaths(originNumber, destNumber));
                }
            }
        }
        root.freeLeavesMemory();
        return nbPaths;
    }

    /**
     * Function that writes the preprocessed paths to the -outpaths file
     * @param paths the preprocessed SR-paths
     */
    private void preprocessedPathsToFile(ArrayList<int[]> paths) {
        StringBuilder builder = new StringBuilder();
        for (int[] path : paths) {
            builder.append(Arrays.toString(path));
            builder.append("\n");
        }
        RepetitaWriter.writeToPathFile(builder.toString());
    }

    @Override
    public long solveTime(Setting setting) {
        return ILPSolveTime+preprocessingTime;
    }
}
