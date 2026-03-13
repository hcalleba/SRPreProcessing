package edu.repetita.solvers.sr;

import edu.repetita.core.Demands;
import edu.repetita.core.Topology;
import edu.repetita.solvers.common.AdversarialMatrix;
import edu.repetita.solvers.common.DemandContribution;
import edu.repetita.solvers.common.DemandLoad;
import edu.repetita.solvers.common.EdgeWorstCase;

import java.util.*;

/**
 * Generates adversarial demand matrices to stress-test a routing solution.
 *
 * Given a fixed routing (represented as an {@link EdgeUsageFunction}),
 * this class generates worst-case demand matrices by greedily perturbing
 * the demands that maximally increase the Maximum Link Utilization (MLU).
 *
 * This class is routing-scheme agnostic — it only depends on an edge usage
 * function, not on any specific solver.
 */
public class AdversarialMatrices {

    private AdversarialMatrices() {} // utility class, no instantiation

    /**
     * Functional interface for querying edge usage of a routing solution.
     * Returns the fraction of flow from (src, dst) that traverses a given edge.
     */
    @FunctionalInterface
    public interface EdgeUsageFunction {
        double getEdgeUsage(int src, int dst, int edge);
    }

    // Adversarial matrix generation

    /**
     * Generates a worst-case demand matrix given a fixed routing.
     * Greedily perturbs demands that maximally increase MLU.
     *
     * @param topology            the network topology
     * @param baseMatrix          the base demand matrix
     * @param routing             edge usage function for the fixed routing
     * @param maxPerturbedDemands maximum number of demands to perturb
     * @param perturbationPercent perturbation factor (e.g. 0.20 for +20%)
     * @param perturbableDemands  set of demand indices allowed to be perturbed (null = all)
     * @return the adversarial matrix with MLU metrics
     */
    public static AdversarialMatrix generateWorstMatrix(
            Topology topology, Demands baseMatrix, EdgeUsageFunction routing,
            int maxPerturbedDemands, double perturbationPercent, Set<Integer> perturbableDemands) {

        int budgetRemaining = maxPerturbedDemands;
        Set<Integer> perturbedDemands = new HashSet<>();
        Demands worstMatrix = copyDemands(baseMatrix);

        while (budgetRemaining > 0) {
            EdgeWorstCase worstCase = findWorstEdge(
                    topology, worstMatrix, routing, perturbedDemands,
                    perturbationPercent, budgetRemaining, perturbableDemands
            );

            if (worstCase == null || worstCase.demands.isEmpty()) {
                System.out.println("No more demands to perturb (budget remaining: " + budgetRemaining + ")");
                break;
            }

            int nbToPerturb = Math.min(worstCase.demands.size(), budgetRemaining);
            for (int i = 0; i < nbToPerturb; i++) {
                int demandIdx = worstCase.demands.get(i);
                perturbedDemands.add(demandIdx);
                worstMatrix.amount[demandIdx] = baseMatrix.amount[demandIdx] * (1.0 + perturbationPercent);
            }

            budgetRemaining -= nbToPerturb;

            System.out.println("  Perturbed " + nbToPerturb + " demands on edge " +
                    worstCase.edgeIdx + " (MLU would be " + worstCase.mlu + ")");
        }

        double actualMLU = calculateMLU(topology, worstMatrix, routing);
        return new AdversarialMatrix(worstMatrix, perturbedDemands, actualMLU);
    }

    /**
     * Generates a random adversarial matrix by randomly perturbing demands.
     */
    public static AdversarialMatrix generateRandomMatrix(
            Topology topology, Demands baseMatrix, EdgeUsageFunction routing,
            int maxPerturbedDemands, double perturbationPercent, Set<Integer> perturbableDemands) {

        Demands randomMatrix = copyDemands(baseMatrix);

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < baseMatrix.nDemands; i++) {
            if (perturbableDemands == null || perturbableDemands.contains(i)) {
                candidates.add(i);
            }
        }

        Collections.shuffle(candidates);
        int nbToPerturb = Math.min(maxPerturbedDemands, candidates.size());

        Set<Integer> perturbedDemands = new HashSet<>();
        for (int i = 0; i < nbToPerturb; i++) {
            int demandIdx = candidates.get(i);
            perturbedDemands.add(demandIdx);
            randomMatrix.amount[demandIdx] = baseMatrix.amount[demandIdx] * (1.0 + perturbationPercent);
        }

        System.out.println("  Randomly perturbed " + nbToPerturb + " demands");

        double actualMLU = calculateMLU(topology, randomMatrix, routing);
        return new AdversarialMatrix(randomMatrix, perturbedDemands, actualMLU);
    }

    // MLU computation

    /**
     * Calculates the Maximum Link Utilization of a demand matrix with a given routing.
     */
    public static double calculateMLU(Topology topology, Demands demands, EdgeUsageFunction routing) {
        double maxUtilization = 0.0;

        for (int edge = 0; edge < topology.nEdges; edge++) {
            double edgeLoad = 0.0;

            for (int demandIdx = 0; demandIdx < demands.nDemands; demandIdx++) {
                int src = demands.source[demandIdx];
                int dst = demands.dest[demandIdx];
                double flowOnEdge = routing.getEdgeUsage(src, dst, edge);
                edgeLoad += demands.amount[demandIdx] * flowOnEdge;
            }

            double utilization = edgeLoad / topology.edgeCapacity[edge];
            maxUtilization = Math.max(maxUtilization, utilization);
        }

        return maxUtilization;
    }

    // Perturbable demand selection

    /**
     * Computes the set of demand indices allowed to be perturbed based on a load threshold.
     * Selects the largest demands that cumulatively contribute to the given percentage of total load.
     */
    public static Set<Integer> computePerturbableDemands(Demands baseMatrix, double loadThreshold) {
        double totalLoad = 0.0;
        for (int i = 0; i < baseMatrix.nDemands; i++) {
            totalLoad += baseMatrix.amount[i];
        }

        List<DemandLoad> demandLoads = new ArrayList<>();
        for (int i = 0; i < baseMatrix.nDemands; i++) {
            demandLoads.add(new DemandLoad(i, baseMatrix.amount[i]));
        }
        demandLoads.sort((a, b) -> Double.compare(b.load, a.load));

        double targetLoad = totalLoad * loadThreshold;
        double cumulativeLoad = 0.0;
        Set<Integer> perturbableDemands = new HashSet<>();

        for (DemandLoad dl : demandLoads) {
            perturbableDemands.add(dl.demandIdx);
            cumulativeLoad += dl.load;
            if (cumulativeLoad >= targetLoad) {
                break;
            }
        }

        System.out.println("Total network load: " + totalLoad);
        System.out.println("Target load threshold (" + (loadThreshold * 100) + "%): " + targetLoad);
        System.out.println("Cumulative load of selected demands: " + cumulativeLoad);
        System.out.println("Number of perturbable demands: " + perturbableDemands.size() + "/" + baseMatrix.nDemands);

        return perturbableDemands;
    }

    // Utility

    /** Creates a deep copy of a demand matrix. */
    public static Demands copyDemands(Demands original) {
        Demands copy = new Demands(original.nDemands);
        for (int i = 0; i < original.nDemands; i++) {
            copy.source[i] = original.source[i];
            copy.dest[i] = original.dest[i];
            copy.amount[i] = original.amount[i];
        }
        return copy;
    }

    /** Prints the results of adversarial matrix generation. */
    public static void printResults(List<AdversarialMatrix> matrices) {
        System.out.println("\n=== Adversarial Matrix Generation Results ===");

        for (int i = 0; i < matrices.size(); i++) {
            AdversarialMatrix am = matrices.get(i);
            System.out.println("MATRIX: " + (i + 1));
            System.out.println("  NON_OPTIMIZED_MLU: " + am.nonOptimizedMLU);
            System.out.println("  CUMULATIVE_OPTIMIZED_MLU: " + am.cumulativeOptimizedMLU);
            System.out.println("  MLU_WITH_CUMULATIVE_ROUTING: " + am.mluWithCumulativeRouting);
            System.out.println("  INDIVIDUAL_MLU: " + am.individualMLU);
            System.out.println("  NUM_PERTURBED: " + am.perturbedDemandIndices.size());

            List<Integer> sortedIds = new ArrayList<>(am.perturbedDemandIndices);
            Collections.sort(sortedIds);
            System.out.print("  PERTURBED_DEMAND_IDS: ");
            for (int j = 0; j < sortedIds.size(); j++) {
                if (j > 0) System.out.print(",");
                System.out.print(sortedIds.get(j));
            }
            System.out.println();
        }

        System.out.println("\n=== Summary ===");
        System.out.println(String.format("%-10s %-20s %-25s %-25s %-20s %-15s",
                "Matrix", "Non-Opt MLU", "Cumulative-Opt MLU", "MLU w/ Cum. Routing", "Individual MLU", "Num Perturbed"));
        System.out.println("-".repeat(120));
        for (int i = 0; i < matrices.size(); i++) {
            AdversarialMatrix am = matrices.get(i);
            System.out.println(String.format("%-10s %-20.6f %-25.6f %-25.6f %-20.6f %-15d",
                    "M_" + (i + 1), am.nonOptimizedMLU, am.cumulativeOptimizedMLU, am.mluWithCumulativeRouting,
                    am.individualMLU, am.perturbedDemandIndices.size()));
        }
    }

    // Private helpers

    /**
     * Finds the edge whose demand perturbation would yield the worst MLU.
     */
    private static EdgeWorstCase findWorstEdge(
            Topology topology, Demands baseMatrix, EdgeUsageFunction routing,
            Set<Integer> alreadyPerturbed, double perturbationPercent, int budgetRemaining,
            Set<Integer> perturbableDemands) {

        EdgeWorstCase worstCase = null;
        double worstMLU = 0.0;

        for (int edge = 0; edge < topology.nEdges; edge++) {
            List<DemandContribution> contributions = new ArrayList<>();

            for (int demandIdx = 0; demandIdx < baseMatrix.nDemands; demandIdx++) {
                if (alreadyPerturbed.contains(demandIdx)) continue;
                if (perturbableDemands != null && !perturbableDemands.contains(demandIdx)) continue;

                int src = baseMatrix.source[demandIdx];
                int dst = baseMatrix.dest[demandIdx];
                double flowOnEdge = routing.getEdgeUsage(src, dst, edge);

                if (flowOnEdge > 1e-6) {
                    double contribution = baseMatrix.amount[demandIdx] * flowOnEdge;
                    contributions.add(new DemandContribution(demandIdx, contribution));
                }
            }

            if (contributions.isEmpty()) continue;

            contributions.sort((a, b) -> Double.compare(b.contribution, a.contribution));

            int nbDemandsToPerturb = Math.min(contributions.size(), budgetRemaining);
            Set<Integer> demandsToPerturb = new HashSet<>();
            for (int i = 0; i < nbDemandsToPerturb; i++) {
                demandsToPerturb.add(contributions.get(i).demandIdx);
            }

            double edgeLoad = 0.0;
            for (int demandIdx = 0; demandIdx < baseMatrix.nDemands; demandIdx++) {
                int src = baseMatrix.source[demandIdx];
                int dst = baseMatrix.dest[demandIdx];
                double flowOnEdge = routing.getEdgeUsage(src, dst, edge);
                double amount = baseMatrix.amount[demandIdx];

                if (demandsToPerturb.contains(demandIdx)) {
                    amount *= (1.0 + perturbationPercent);
                }

                edgeLoad += amount * flowOnEdge;
            }

            double mlu = edgeLoad / topology.edgeCapacity[edge];

            if (mlu > worstMLU) {
                worstMLU = mlu;
                List<Integer> demandIndices = new ArrayList<>();
                for (int i = 0; i < nbDemandsToPerturb; i++) {
                    demandIndices.add(contributions.get(i).demandIdx);
                }
                worstCase = new EdgeWorstCase(edge, mlu, demandIndices);
            }
        }

        return worstCase;
    }
}
