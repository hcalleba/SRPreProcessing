package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Solver;
import edu.repetita.core.Topology;
import edu.repetita.io.RepetitaParser;
import edu.repetita.paths.ShortestPaths;
import edu.repetita.traffic.Generator.TrafficMatrixGeneratorClustering;
import edu.repetita.traffic.Generator.TrafficMatrixGeneratorIncompatible;
import edu.repetita.traffic.Generator.TrafficMatrixGeneratorKarger;

import java.io.IOException;
import java.util.*;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

/**
 * Cluster-based traffic engineering solver.
 *
 * Takes a set of demand matrices (typically one base + several cluster-boosted variants
 * from {@link TrafficMatrixGeneratorClustering}) and:
 *
 * 1. Solves the SRTEP individually for each matrix → individual best MLU
 * 2. Solves the SRTEP jointly for all matrices (with optional Phase 2) → joint MLU
 * 3. Computes each matrix's MLU under the joint routing
 * 4. Prints a summary table
 */
public class ClusterSolver extends Solver {

    private long solveTime = 0;
    private long maxTime;

    private String srPathsFile = null;
    private boolean excludeAdjacencyPaths = true;

    // TrafficMatrixGenerator parameters
    private int numGeneratedMatrices = 10;
    private double boostFactor = 2.0;

    public void setSRPathsFile(String filename) {
        this.srPathsFile = filename;
    }

    public void setExcludeAdjacencyPaths(boolean exclude) {
        this.excludeAdjacencyPaths = exclude;
    }

    public void setNumGeneratedMatrices(int num) {
        this.numGeneratedMatrices = num;
    }

    public void setBoostFactor(double factor) {
        this.boostFactor = factor;
    }

    @Override
    protected void setObjective() {
        objective = SOLVER_OBJVALUES_MINMAXLINKUSAGE;
    }

    @Override
    public String name() {
        return "ClusterSRTEP";
    }

    @Override
    public String getDescription() {
        return "Solves SRTEP jointly for cluster-boosted demand matrices.";
    }

    @Override
    public void solve(Setting setting, long milliseconds) {
        long startTime = System.currentTimeMillis();
        maxTime = milliseconds;

        Topology topology = setting.getTopology();

        // Generate cluster-boosted demand matrices
//        TrafficMatrixGeneratorClustering tmGenerator = new TrafficMatrixGeneratorClustering();
//        tmGenerator.setBoostFactor(boostFactor);
//        TrafficMatrixGeneratorIncompatible tmGenerator = new TrafficMatrixGeneratorIncompatible();
        TrafficMatrixGeneratorKarger tmGenerator = new TrafficMatrixGeneratorKarger();
        tmGenerator.setNumMatrices(numGeneratedMatrices);
        List<Demands> matrices = tmGenerator.generate(setting);

        if (matrices.isEmpty()) {
            System.err.println("No demand matrices generated");
            return;
        }

        if (srPathsFile == null) {
            System.err.println("No SR paths file specified. Use setSRPathsFile() before solving.");
            return;
        }

        // Load SR paths
        SRPathSet pathSet;
        try {
            pathSet = RepetitaParser.parseSRPaths(srPathsFile, topology, excludeAdjacencyPaths);
            System.out.println("Loaded SR paths: " + pathSet);
        } catch (IOException e) {
            System.err.println("Error loading SR paths file: " + e.getMessage());
            return;
        }

        // Create the SRTEP solver
        ShortestPaths shortestPaths = new ShortestPaths(topology);
        SRTEP srtep = new SRTEP(topology, pathSet, shortestPaths, excludeAdjacencyPaths, maxTime);

        int nMatrices = matrices.size();
        double[] individualMLU = new double[nMatrices];

        // Step 1: solve each matrix individually
        System.out.println("\n=== Individual SRTEP Solves ===");
        for (int i = 0; i < nMatrices; i++) {
            SRTEP.SolveResult result = srtep.solveRouting(Collections.singletonList(matrices.get(i)));
            individualMLU[i] = result.mlu;
            System.out.printf("Matrix %d: individual MLU = %.6f%n", i, individualMLU[i]);
        }

        // Step 2: solve jointly for all matrices
        System.out.println("\n=== Joint SRTEP Solve ===");
        SRTEP.SolveResult jointResult = srtep.solveRouting(matrices, true);
        double jointMLU = jointResult.mlu;
        System.out.println("Joint max MLU: " + jointMLU);

        // Step 3: per-matrix MLU values from the model (Phase 2 if run, else Phase 1)
        double[] mluUnderJointRouting = jointResult.perMatrixMLU;

        double sumMLU = 0.0;
        for (double v : mluUnderJointRouting) sumMLU += v;
        double avgMLU = sumMLU / nMatrices;

        // Step 4: print summary table
        System.out.println("\n=== Cluster Solver Results ===");
        System.out.printf("%-10s %-20s %-25s %-15s%n",
                "Matrix", "Individual MLU", "MLU w/ Joint Routing", "Degradation");
        System.out.println("-".repeat(70));
        for (int i = 0; i < nMatrices; i++) {
            double degradation = mluUnderJointRouting[i] / individualMLU[i];
            System.out.printf("%-10s %-20.6f %-25.6f %-15.4f%n",
                    (i == 0 ? "Base" : "C_" + i),
                    individualMLU[i],
                    mluUnderJointRouting[i],
                    degradation);
        }
        System.out.println("-".repeat(70));
        System.out.printf("%-10s %-20.6f (max)  %-25.6f (avg)%n", "Joint", jointMLU, avgMLU);

        solveTime = System.currentTimeMillis() - startTime;
    }

    @Override
    public long solveTime(Setting setting) {
        return solveTime;
    }
}
