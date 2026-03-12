package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Solver;
import edu.repetita.core.Topology;
import edu.repetita.io.RepetitaParser;
import edu.repetita.paths.ShortestPaths;
import edu.repetita.solvers.common.AdversarialMatrix;

import java.io.IOException;
import java.util.*;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

/**
 * Adversarial matrix generation solver for Segment Routing.
 *
 * Iteratively:
 * 1. Solves the SRTEP for the current set of demand matrices
 * 2. Generates a worst-case adversarial matrix that maximizes MLU given the current routing
 * 3. Re-optimizes for all matrices including the new adversarial one
 *
 * Bridges the SRTEP solver and the AdversarialMatrices generator.
 */
public class AdversarialSolver extends Solver {

    private long solveTime = 0;
    private long maxTime;

    // Parameters for adversarial matrix generation
    private int numAdversarialMatrices;
    private int maxPerturbedDemands;
    private double perturbationPercent;
    private double demandLoadThreshold = 0.80;
    private boolean useDemandLoadThreshold = false;
    private boolean minimizeAverageMLU = true;

    private final double TARGETMLU = 1.0;

    // SR paths configuration
    private String srPathsFile = null;
    private boolean excludeAdjacencyPaths = false;

    // Configuration setters

    public void setSRPathsFile(String filename) {
        this.srPathsFile = filename;
    }

    public void setExcludeAdjacencyPaths(boolean exclude) {
        this.excludeAdjacencyPaths = exclude;
    }

    public void setNumAdversarialMatrices(int num) {
        this.numAdversarialMatrices = num;
    }

    public void setMaxPerturbedDemands(int max) {
        this.maxPerturbedDemands = max;
    }

    public void setPerturbationPercent(double percent) {
        this.perturbationPercent = percent;
    }

    public void setDemandLoadThreshold(double threshold) {
        this.demandLoadThreshold = threshold;
    }

    public void setUseDemandLoadThreshold(boolean use) {
        this.useDemandLoadThreshold = use;
    }

    public void setMinimizeAverageMLU(boolean minimize) {
        this.minimizeAverageMLU = minimize;
    }

    // Solver interface

    @Override
    protected void setObjective() {
        objective = SOLVER_OBJVALUES_MINMAXLINKUSAGE;
    }

    @Override
    public String name() {
        return "AdversarialSRTEP";
    }

    @Override
    public String getDescription() {
        return "Iteratively solves SRTEP and generates adversarial demand matrices.";
    }

    @Override
    public void solve(Setting setting, long milliseconds) {
        long startTime = System.currentTimeMillis();
        maxTime = milliseconds;

        Topology topology = setting.getTopology();
        ArrayList<Demands> demands = setting.getDemands();

        if (demands.isEmpty()) {
            System.err.println("No demand matrix provided");
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

        // Create the SRTEP solver (precomputes ECMP edge usage)
        ShortestPaths shortestPaths = new ShortestPaths(topology);
        SRTEP srtep = new SRTEP(topology, pathSet, shortestPaths, excludeAdjacencyPaths, maxTime);

        // Scale the base matrix to MLU = TARGETMLU using MCF
        Demands baseMatrix = scaleMatrixToMLUUsingMCF(srtep, demands.getFirst(), TARGETMLU);

        // Run the iterative adversarial generation loop
        List<AdversarialMatrix> adversarialMatrices = generateAdversarialMatrices(
                topology, srtep, baseMatrix
        );

        // Print results
        AdversarialMatrices.printResults(adversarialMatrices);

        solveTime = System.currentTimeMillis() - startTime;
    }

    @Override
    public long solveTime(Setting setting) {
        return solveTime;
    }

    // Scaling

    private Demands scaleMatrixToMLUUsingMCF(SRTEP srtep, Demands originalMatrix, double targetMLU) {
        double currentMLU = srtep.solveMCF(originalMatrix);

        if (currentMLU <= 0) {
            System.err.println("Error: MCF MLU is " + currentMLU);
            return originalMatrix;
        }

        Demands scaledMatrix = new Demands(originalMatrix.nDemands);
        double scaleFactor = targetMLU / currentMLU;

        for (int i = 0; i < originalMatrix.nDemands; i++) {
            scaledMatrix.source[i] = originalMatrix.source[i];
            scaledMatrix.dest[i] = originalMatrix.dest[i];
            scaledMatrix.amount[i] = Math.floor(originalMatrix.amount[i] * scaleFactor);
        }

        System.out.println("MCF_SCALE_FACTOR: " + scaleFactor);
        return scaledMatrix;
    }

    // -------------------------------------------------------------------------
    // Iterative adversarial generation loop
    // -------------------------------------------------------------------------

    /**
     * Builds an EdgeUsageFunction from SRTEP solve result.
     * This bridges the SRTEP routing representation to the generic interface
     * used by AdversarialMatrices.
     */
    private AdversarialMatrices.EdgeUsageFunction buildEdgeUsageFunction(
            SRTEP.SolveResult result, double[][] edgeUsage) {
        return (src, dst, edge) -> {
            int pathIdx = result.selectedPathIndex[src][dst];
            if (pathIdx < 0) return 0.0;
            return edgeUsage[pathIdx][edge];
        };
    }

    private List<AdversarialMatrix> generateAdversarialMatrices(
            Topology topology, SRTEP srtep, Demands baseMatrix) {

        List<AdversarialMatrix> adversarialMatrices = new ArrayList<>();
        List<Demands> allMatrices = new ArrayList<>();
        allMatrices.add(baseMatrix);

        double[][] edgeUsage = srtep.getEdgeUsage();

        SRTEP.SolveResult currentResult = srtep.solveRouting(allMatrices);
        double currentMLU = currentResult.mlu;
        AdversarialMatrices.EdgeUsageFunction currentRouting = buildEdgeUsageFunction(currentResult, edgeUsage);

        System.out.println("\n=== Generating Adversarial Matrices (SRTE) ===");
        System.out.println("Base matrix MLU (should be ~1.0): " + currentMLU);

        Set<Integer> perturbableDemands = null;
        if (useDemandLoadThreshold) {
            System.out.println("\n--- Computing perturbable demands based on load threshold ---");
            perturbableDemands = AdversarialMatrices.computePerturbableDemands(baseMatrix, demandLoadThreshold);
        } else {
            System.out.println("\n--- Demand load threshold filtering disabled ---");
        }

        for (int i = 0; i < numAdversarialMatrices; i++) {
            System.out.println("\n--- Generating adversarial matrix " + (i + 1) + " ---");
            System.out.println("Current combined MLU: " + currentMLU);

            // Generate worst-case matrix given current routing
            AdversarialMatrix worstMatrix = AdversarialMatrices.generateWorstMatrix(
                    topology, baseMatrix, currentRouting, maxPerturbedDemands,
                    perturbationPercent, perturbableDemands
            );

            adversarialMatrices.add(worstMatrix);
            allMatrices.add(worstMatrix.matrix);

            // Re-optimize routing with all matrices (including the new one)
            boolean isLastIteration = (i == numAdversarialMatrices - 1);
            boolean runPhase2 = isLastIteration && minimizeAverageMLU && allMatrices.size() > 1;

            SRTEP.SolveResult cumulativeResult = srtep.solveRouting(allMatrices, runPhase2);
            worstMatrix.cumulativeOptimizedMLU = cumulativeResult.mlu;

            // Compute individual MLU (optimized for this matrix alone)
            SRTEP.SolveResult individualResult = srtep.solveRouting(Collections.singletonList(worstMatrix.matrix));
            worstMatrix.individualMLU = individualResult.mlu;

            System.out.println("Non-optimized MLU (old routing): " + worstMatrix.nonOptimizedMLU);
            System.out.println("Cumulative optimized MLU (all matrices): " + cumulativeResult.mlu);
            System.out.println("Individual MLU (this matrix alone): " + individualResult.mlu);
            System.out.println("Perturbed " + worstMatrix.perturbedDemandIndices.size() + " demands");

            currentMLU = cumulativeResult.mlu;
            currentRouting = buildEdgeUsageFunction(cumulativeResult, edgeUsage);
        }

        // Compute individual MLUs with the final routing
        System.out.println("\n--- Computing individual MLUs with final routing ---");
        System.out.println("Final cumulative MLU: " + currentMLU);
        for (int i = 0; i < adversarialMatrices.size(); i++) {
            AdversarialMatrix am = adversarialMatrices.get(i);
            am.mluWithCumulativeRouting = AdversarialMatrices.calculateMLU(topology, am.matrix, currentRouting);
            System.out.println("Matrix " + (i + 1) + " MLU with final routing: " + am.mluWithCumulativeRouting);
        }

        return adversarialMatrices;
    }
}
