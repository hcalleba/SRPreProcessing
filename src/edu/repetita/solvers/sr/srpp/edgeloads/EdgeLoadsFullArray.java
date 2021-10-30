package edu.repetita.solvers.sr.srpp.edgeloads;

public class EdgeLoadsFullArray implements Cloneable {
    private final double[] edges;
    private static final double PRECISION = 0.000001;

    public EdgeLoadsFullArray(double[] edgeLoads) {
        this.edges = edgeLoads;
    }

    private EdgeLoadsFullArray(int size) {
        this.edges = new double[size];
    }

    public static EdgeLoadsFullArray add(EdgeLoadsFullArray first, EdgeLoadsFullArray second) {
        int size = first.edges.length;
        EdgeLoadsFullArray result = new EdgeLoadsFullArray(size);
        for (int i = 0; i < size; i++) {
            result.edges[i] = first.edges[i] + second.edges[i];
        }
        return result;
    }

    public boolean dominates(EdgeLoadsFullArray otherLoads) {
        for (int i = 0; i < edges.length; i++) {
            if (otherLoads.edges[i]+PRECISION < this.edges[i]) {
                return false;
            }
        }
        return true;
    }

    public EdgeLoadsFullArray clone() {
        int size = this.edges.length;
        EdgeLoadsFullArray result = new EdgeLoadsFullArray(size);
        System.arraycopy(this.edges, 0, result.edges, 0, size);
        return result;
    }
}
