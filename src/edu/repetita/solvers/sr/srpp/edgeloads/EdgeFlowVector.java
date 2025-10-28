package edu.repetita.solvers.sr.srpp.edgeloads;

public class EdgeFlowVector {
    public final int[] edgeIds;
    public final double[] frac;

    public EdgeFlowVector(int[] edgeIds, double[] frac) {
        this.edgeIds = edgeIds;
        this.frac = frac;
    }

    /**
     * Computes x = x + a * this
     * Basically if you have a vector x of size |E| (number of edges),
     * this method adds to x the values of this vector scaled by a.
     * @param a a scalar
     * @param x a vector to be updated
     */
    public void axpy(double a, double[] x) {
        for (int i = 0; i < edgeIds.length; i++) {
            x[edgeIds[i]] += a * frac[i];
        }
    }
}
