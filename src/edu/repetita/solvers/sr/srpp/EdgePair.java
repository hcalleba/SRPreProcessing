package edu.repetita.solvers.sr.srpp;

public class EdgePair {
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
}
