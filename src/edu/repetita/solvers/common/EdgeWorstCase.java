package edu.repetita.solvers.common;

import java.util.List;

/**
 * Stores the worst-case perturbation result for a given edge,
 * including the resulting MLU and the demands sorted by descending contribution.
 */
public class EdgeWorstCase {
    public int edgeIdx;
    public double mlu;
    public List<Integer> demands;

    public EdgeWorstCase(int edgeIdx, double mlu, List<Integer> demands) {
        this.edgeIdx = edgeIdx;
        this.mlu = mlu;
        this.demands = demands;
    }
}
