package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.io.RepetitaWriter;
import edu.repetita.solvers.sr.heursr.HeuristicSolver;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

/**
 * Solver that implements preprocessing techniques to eliminate dominated paths in Segment Routing.
 * It then sends this smaller set of paths to an ILP solver that will solve it optimally.
 */
public class HeurSR extends edu.repetita.solvers.HeurSR {

    private static long maxExecTime = 86400000;  // In ms (= 24 hours)
    private long preprocessingTime;
    private long SearchSolveTime;
    double uMax = 0.0;

    public HeurSR() {
        super();
    }

    @Override
    protected void setObjective() {
        objective = SOLVER_OBJVALUES_MINMAXLINKUSAGE;
    }

    @Override
    public String name() {
        return "HeurSR";
    }

    @Override
    public String getDescription() {
        return "A Heuristic Segment Routing path optimizer";
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
            endTime = startTime + maxExecTime;
        }
        Topology topology = setting.getTopology();
        Demands demands = setting.getDemands();
        int maxSegments = setting.getMaxSegments();

//        /* preprocessing */
//        SegmentTreeRoot root = new SegmentTreeRoot(topology, maxSegments, demands);
//        ArrayList<int[]> paths = new ArrayList<>();
//
//        /* Preprocess the SR-paths */
//        int nbPaths = preprocessTopology(topology.nNodes, root, paths, endTime);
//        preprocessingTime = System.currentTimeMillis() - startTime;

        /* Solve the ILP or write the non-dominated paths to -outpaths file */
        startTime = System.currentTimeMillis();
        if (System.currentTimeMillis() < endTime) {
            // Use heuristic here
            HeuristicSolver heuristicSolver = new HeuristicSolver(topology, demands, maxSegments);
            uMax = heuristicSolver.solve(endTime);
//            RepetitaWriter.writeToPathFile(heuristicSolver.getSolution());
        }
        SearchSolveTime = System.currentTimeMillis() - startTime;

        /* Log output */
        RepetitaWriter.appendToOutput("OK");
        RepetitaWriter.appendToOutput("Preprocessing time : " + (double)preprocessingTime/1000 + " seconds");
        RepetitaWriter.appendToOutput("Search solve time : " + (double)SearchSolveTime/1000 + " seconds");
        RepetitaWriter.appendToOutput("Total time elapsed : " + (double)(SearchSolveTime+preprocessingTime)/1000 + " seconds");
//        RepetitaWriter.appendToOutput("Total number of paths after preprocessing : " + nbPaths);
        RepetitaWriter.appendToOutput("Objective value (uMax) : " + uMax + "\n");
    }

    /**
     * @return the time needed by the last call to solve(), in milliseconds
     */
    @Override
    public long solveTime(Setting setting) {
        return SearchSolveTime+preprocessingTime;
    }
}
