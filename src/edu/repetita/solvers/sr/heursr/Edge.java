package edu.repetita.solvers.sr.heursr;

public class Edge {
    public final int id;
    public double frac;

    public Edge(int from, int to) {
        this.id = from;
        this.frac = to;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Edge)) return false;
        Edge other = (Edge) obj;
        return this.id == other.id && this.frac == other.frac;
    }

    @Override
    public int hashCode() {
        return 31 * id + frac;
    }

    @Override
    public String toString() {
        return id + " -> " + frac;
    }
}
