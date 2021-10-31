package edu.repetita.solvers.sr.srpp.edgeloads;

public class EdgePair implements Cloneable {
    private final int key;
    private double load;

    EdgePair(int key, double load) {
        this.key = key;
        this.load = load;
    }

    public int getKey() {
        return this.key;
    }

    public double getLoad() {
        return this.load;
    }

    public void setLoad(double load) {
        this.load = load;
    }

    @Override
    public EdgePair clone() {
        return new EdgePair(key, load);
    }
}
