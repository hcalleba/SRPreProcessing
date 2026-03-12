package edu.repetita.solvers.common;

import edu.repetita.core.Demands;

import java.util.Set;

/**
 * Stores an adversarial demand matrix and its associated MLU metrics.
 */
public class AdversarialMatrix {
    public Demands matrix;
    public Set<Integer> perturbedDemandIndices;
    public double nonOptimizedMLU;
    public double cumulativeOptimizedMLU;
    public double individualMLU;
    public double mluWithCumulativeRouting;

    public AdversarialMatrix(Demands matrix, Set<Integer> perturbedIndices, double nonOptimizedMLU) {
        this.matrix = matrix;
        this.perturbedDemandIndices = perturbedIndices;
        this.nonOptimizedMLU = nonOptimizedMLU;
        this.cumulativeOptimizedMLU = 0.0;
        this.individualMLU = 0.0;
        this.mluWithCumulativeRouting = 0.0;
    }
}
