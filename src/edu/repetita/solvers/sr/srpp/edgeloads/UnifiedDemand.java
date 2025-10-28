package edu.repetita.solvers.sr.srpp.edgeloads;

public class UnifiedDemand {
    public final double[] amounts; // Amount for each demand matrix (0 if not present)
    public final int[] demandIndices; // Original demand index in each matrix (-1 if not present)

    public UnifiedDemand(int numMatrices) {
        this.amounts = new double[numMatrices];
        this.demandIndices = new int[numMatrices];
        for (int i = 0; i < numMatrices; i++) {
            this.demandIndices[i] = -1; // -1 means demand not present in this matrix
        }
    }
}
