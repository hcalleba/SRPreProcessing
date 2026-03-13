package edu.repetita.traffic.Generator;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.solvers.mcf.MCF;

import java.util.List;
import java.util.Random;

/**
 * Abstract base class for all traffic matrix generators.
 *
 * Shared state and behaviour:
 * - numMatrices — how many stress matrices to produce (excluding the base).
 * - seed — RNG seed for reproducibility.
 * - boostLow / boostHigh — inclusive bounds for the amplification factor α.
 *   Each matrix samples α ~ Uniform[boostLow, boostHigh] independently.
 * - visualize — whether to open topology visualisation windows.
 * - scaleToTargetMLU — shared MCF-based normalisation helper.
 * - sampleBoost — draws one α from the configured range.
 *
 * Concrete subclasses implement {@link #generate} and define their own default
 * boost range via {@link #setBoostRange} in their constructor.
 */
public abstract class TrafficMatrixGenerator {

    protected static final double TARGET_MLU = 1.0;

    protected int     numMatrices = 15;
    protected long    seed        = 42L;
    protected double  boostLow    = 2.0;
    protected double  boostHigh   = 5.0;
    protected boolean visualize   = true;

    public void setNumMatrices(int n)                    { this.numMatrices = n; }
    public void setSeed(long seed)                       { this.seed = seed; }
    public void setBoostRange(double low, double high)   { this.boostLow = low; this.boostHigh = high; }
    public void setVisualize(boolean v)                  { this.visualize = v; }

    /** Generates and returns the list of demand matrices (base matrix first). */
    public abstract List<Demands> generate(Setting setting);

    /** Samples one amplification factor uniformly in [boostLow, boostHigh]. */
    protected double sampleBoost(Random rng) {
        return boostLow + rng.nextDouble() * (boostHigh - boostLow);
    }

    /** Scales demands so that the MCF-optimal MLU equals {@code targetMLU}. */
    protected static Demands scaleToTargetMLU(Topology topology, Demands matrix, double targetMLU) {
        double currentMLU = MCF.computeOptimalMLU(topology, matrix);
        if (currentMLU <= 0) {
            System.err.println("Warning: MCF MLU=" + currentMLU + ", returning unscaled matrix");
            return matrix;
        }
        double factor = targetMLU / currentMLU;
        Demands result = new Demands(matrix);
        for (int i = 0; i < result.nDemands; i++) {
            result.amount[i] = Math.floor(matrix.amount[i] * factor);
        }
        System.out.println("MCF_SCALE_FACTOR: " + factor);
        return result;
    }
}
