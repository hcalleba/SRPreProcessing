package edu.repetita.solvers.common;

/**
 * Stores the contribution of a demand to a specific edge's load.
 */
public class DemandContribution {
    public int demandIdx;
    public double contribution;

    public DemandContribution(int demandIdx, double contribution) {
        this.demandIdx = demandIdx;
        this.contribution = contribution;
    }
}
