package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.io.RepetitaParser;
import edu.repetita.io.RepetitaWriter;
import edu.repetita.solvers.SRSolver;
import edu.repetita.solvers.sr.srpp.linearproblem.LinearProblem;

import java.io.IOException;
import java.util.ArrayList;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

/**
 * Solver that implements preprocessing techniques to eliminate dominated paths in Segment Routing.
 * It then sends this smaller set of paths to an ILP solver that will solve it optimally.
 */
public class SRPP extends SRSolver {

    private static long MAXEXECTIME = 86400000;  // In ms (= 24 hours)
    private long preprocessingTime;
    private long ILPSolveTime;
    boolean writeOutPaths;
    String inpathsFilename;
    double uMax = 0.0;

    public SRPP(String inpathsFilename, boolean writeOutPaths) {
        super();
        this.inpathsFilename = inpathsFilename;
        this.writeOutPaths = writeOutPaths;
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
        long startTime = System.currentTimeMillis();
        long endTime;
        if (milliseconds > 0) {
            endTime = startTime + milliseconds;
        } else {
            endTime = startTime + MAXEXECTIME;
        }
        Topology topology = setting.getTopology();

        /* preprocessing */
        ArrayList<int[]> paths = new ArrayList<>();
        int nbPaths = preprocessTopology(topology.nNodes, topology, paths, endTime);

        /* Solve the ILP */
        startTime = System.currentTimeMillis();
        if (System.currentTimeMillis() < endTime) {
            LinearProblem lp = new LinearProblem(paths, setting);
            uMax = lp.execute(endTime, false);
            RepetitaWriter.writeToPathFile(lp.getSolution());
            lp.dispose();
        }
        ILPSolveTime = System.currentTimeMillis() - startTime;

        /* Log output */
        RepetitaWriter.appendToOutput("OK");
        RepetitaWriter.appendToOutput("ILP solve time : " + (double)ILPSolveTime/1000 + " seconds");
        RepetitaWriter.appendToOutput("Total time elapsed : " + (double)(ILPSolveTime+preprocessingTime)/1000 + " seconds");
        RepetitaWriter.appendToOutput("Total number of paths after preprocessing : " + nbPaths);
        RepetitaWriter.appendToOutput("Objective value (uMax) : " + uMax + "\n");
    }

    /**
     * Preprocesses the topology to generate all non-dominated paths, all paths or load paths from a file depending
     * on the scenario
     * @param nNodes the number of nodes in the topology
     * @param topology the topology
     * @param paths an arraylist that will serve as container for all the resulting paths
     * @return the number of generated paths in case of preprocessing, 0 otherwise (if all demands strictly positive,
     * this is equal to the size of paths)
     */
    private int preprocessTopology(int nNodes, Topology topology, ArrayList<int[]> paths, long endTime) {
        int nbPaths = 0;
        /* Load SR-paths from file if one is given */
        try {
            RepetitaParser.parseSRPaths(inpathsFilename, topology, paths);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return nbPaths;
    }

    @Override
    public long solveTime(Setting setting) {
        return ILPSolveTime+preprocessingTime;
    }
}
