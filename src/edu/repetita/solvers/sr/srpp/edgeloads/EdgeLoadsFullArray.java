package edu.repetita.solvers.sr.srpp.edgeloads;

import java.util.Iterator;

public class EdgeLoadsFullArray implements Cloneable, Iterable<EdgePair> {
    protected final double[] edges;
    private static final double PRECISION = 0.000001;

    public EdgeLoadsFullArray(double[] edgeLoads) {
        this.edges = edgeLoads;
    }

    private EdgeLoadsFullArray(int size) {
        this.edges = new double[size];
    }

    public void add(EdgeLoadsFullArray other) {
        int size = this.edges.length;
        for (int i = 0; i < size; i++) {
            this.edges[i] += other.edges[i];
        }
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

    @Override
    public Iterator<EdgePair> iterator() {
        return new IteratorFullArray(this);
    }
}

class IteratorFullArray implements Iterator<EdgePair> {
    int index;
    EdgeLoadsFullArray edgeLoads;


    public IteratorFullArray(EdgeLoadsFullArray edgeLoads) {
        index = 0;
        this.edgeLoads = edgeLoads;
    }

    @Override
    public boolean hasNext() {
        return index < edgeLoads.edges.length;
    }

    @Override
    public EdgePair next() {
        return new EdgePair(index, edgeLoads.edges[index++]);
    }
}