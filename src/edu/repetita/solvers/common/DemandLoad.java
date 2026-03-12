package edu.repetita.solvers.common;

/**
 * Stores a demand index and its associated load, used for threshold-based filtering.
 */
public class DemandLoad {
    public int demandIdx;
    public double load;

    public DemandLoad(int demandIdx, double load) {
        this.demandIdx = demandIdx;
        this.load = load;
    }
}
