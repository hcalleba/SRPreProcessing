package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.io.RepetitaParser;
import edu.repetita.io.RepetitaWriter;
import edu.repetita.solvers.SRSolver;
import edu.repetita.solvers.sr.srpp.linearproblem.LinearProblem;
import edu.repetita.solvers.sr.srpp.segmenttree.SegmentTreeRoot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;
import static edu.repetita.solvers.sr.srpp.linearproblem.ROBUST.*;

/**
 * Solver that implements preprocessing techniques to eliminate dominated paths in Segment Routing.
 * It then sends this smaller set of paths to an ILP solver that will solve it optimally.
 */
public class SRPP extends SRSolver {

    private static long maxExecTime = 86400000;  // In ms (= 24 hours)
    private long preprocessingTime;
    private long ILPSolveTime;
    boolean writeOutPaths;
    String inpathsFilename;
    String scenarioChoice;
    double uMax = 0.0;

    int robustGamma = 2; // TODO parametrize
    double robustDeviation = 1; // TODO parametrize

    public SRPP(String inpathsFilename, boolean writeOutPaths, String scenarioChoice) {
        super();
        this.inpathsFilename = inpathsFilename;
        this.writeOutPaths = writeOutPaths;
        this.scenarioChoice = scenarioChoice;
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
            endTime = startTime + maxExecTime;
        }
        Topology topology = setting.getTopology();
        Demands demands = setting.getDemands();
        int maxSegments = setting.getMaxSegments();

        /* preprocessing */
        SegmentTreeRoot root = new SegmentTreeRoot(topology, maxSegments, demands);
        ArrayList<int[]> paths = new ArrayList<>();

        /* Preprocess the SR-paths */
        int nbPaths = 0;
        nbPaths = preprocessTopology(topology.nNodes, root, paths, endTime);
        preprocessingTime = System.currentTimeMillis() - startTime;

        /* Solve the ILP or write the non-dominated paths to -outpaths file */
        startTime = System.currentTimeMillis();
        if (System.currentTimeMillis() < endTime) {
            if (scenarioChoice.equals("preprocess")) {
                preprocessedPathsToFile(paths);

            } else {
                LinearProblem lp = new LinearProblem(ITERATIVE_INTEGER, paths, root, topology);
                uMax = lp.execute(endTime);
                RepetitaWriter.writeToPathFile(lp.getSolution());
                lp.dispose();
            }
        }
        ILPSolveTime = System.currentTimeMillis() - startTime;

        /* Log output */
        RepetitaWriter.appendToOutput("OK");
        RepetitaWriter.appendToOutput("Preprocessing time : " + (double)preprocessingTime/1000 + " seconds");
        RepetitaWriter.appendToOutput("ILP solve time : " + (double)ILPSolveTime/1000 + " seconds");
        RepetitaWriter.appendToOutput("Total time elapsed : " + (double)(ILPSolveTime+preprocessingTime)/1000 + " seconds");
        RepetitaWriter.appendToOutput("Total number of paths after preprocessing : " + nbPaths);
        RepetitaWriter.appendToOutput("Objective value (uMax) : " + uMax + "\n");
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
        switch (scenarioChoice) {
            case "SRPP":
            case "preprocess":
                root.createODPaths(endTime);
                if (System.currentTimeMillis() > endTime) {
                    return -1;
                }
                for (int originNumber = 0; originNumber < nNodes; originNumber++) {
                    for (int destNumber = 0; destNumber < nNodes; destNumber++) {
                        /* if preprocess we keep all paths, otherwise we only keep OD-paths for which there is a
                        positive demand between the nodes */
                        nbPaths += root.getODPaths(originNumber, destNumber).length;
                        if (scenarioChoice.equals("preprocess")) {
                            if (originNumber != destNumber) {
                                Collections.addAll(paths, root.getODPaths(originNumber, destNumber));
                            }
                        } else {
                            if (root.trafficMatrix[originNumber][destNumber] > 0) {
                                Collections.addAll(paths, root.getODPaths(originNumber, destNumber));
                            }
                        }
                    }
                }
                root.freeLeavesMemory();
                break;
            /* Load SR-paths from file if one is given */
            case "loadFromFile":
                try {
                    RepetitaParser.parseSRPaths(inpathsFilename, root, paths);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                break;
            /* Simply create all possible SR-paths */
            case "full":
                for (int depth = 2; depth <= root.maxSegments + 1; depth++) {
                    int[] path = new int[depth];
                    for (int originNode = 0; originNode < nNodes; originNode++) {
                        path[0] = originNode;
                        addSegment(path, 1, nNodes, paths, root);
                    }
                }
                break;
        }
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

    /**
     * helper function used to enumerate all possible SR-paths (without them being preprocessed)
     * @param path array to which we are currently writing the path
     * @param idx index which we are processing in path
     * @param nNodes the number of nodes of the topology
     * @param paths an arraylist to which we should add a new path once it is created
     */
    private void addSegment(int[] path, int idx, int nNodes, ArrayList<int[]> paths, SegmentTreeRoot root) {
        if (idx == path.length) {
            if (root.trafficMatrix[path[0]][path[path.length-1]] > 0){
                paths.add(path.clone());
            }
            return;
        }
        for (int nextNode = 0; nextNode < nNodes; nextNode++) {
            boolean inside = false;
            for (int i = 0; i < idx; i++) {
                inside = inside || path[i]==nextNode;
            }
            if (!inside) {
                path[idx] = nextNode;
                /* Only add SR-path if there is a demand between the nodes */
                addSegment(path, idx + 1, nNodes, paths, root);
            }
        }
    }

    @Override
    public long solveTime(Setting setting) {
        return ILPSolveTime+preprocessingTime;
    }
}
