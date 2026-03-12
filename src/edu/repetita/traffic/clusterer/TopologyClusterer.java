package edu.repetita.traffic.clusterer;

/**
 * Common interface for all topology clustering strategies.
 * <p>
 * Usage: call {@link #run()} first, then query results via the other methods.
 */
public interface TopologyClusterer {

    /**
     * Runs the clustering algorithm.
     *
     * @return cluster assignment array: cluster[v] = cluster index for node v
     */
    int[] run();

    /**
     * @return the effective number of clusters after any merging/post-processing
     */
    int getEffectiveK();

    /**
     * @return centers[k] = node index of the center of cluster k
     */
    int[] getCenters();

    /**
     * Prints a human-readable summary of the clustering to stdout.
     */
    void printSummary();
}
