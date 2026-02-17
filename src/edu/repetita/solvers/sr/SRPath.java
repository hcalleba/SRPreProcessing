package edu.repetita.solvers.sr;

import edu.repetita.core.Topology;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Segment Routing path.
 *
 * A path consists of:
 * - A source node (first element)
 * - A list of intermediate segments which can be:
 *   - Node segments: values in [0, N-1] represent intermediate nodes
 *   - Adjacency segments (edges): values >= N represent edges (edge number = value - N)
 *
 * The destination is implicitly the node reached by following the segments.
 */
public class SRPath {

    /**
     * The source node of this path
     */
    public final int source;

    /**
     * The destination node of this path (derived from segments)
     */
    public final int destination;

    /**
     * The raw segment list (as parsed from file).
     * Values in [0, N-1] are node segments, values >= N are edge segments (edge = value - N)
     */
    public final int[] segments;

    /**
     * Number of intermediate segments (excluding source)
     */
    public final int numSegments;

    /**
     * Number of nodes in the topology (used to distinguish node vs edge segments)
     */
    private final int nNodes;

    /**
     * Whether this path uses any adjacency (edge) segments
     */
    private final boolean usesAdjacencySegments;

    /**
     * Creates an SR path from raw segment data.
     *
     * @param rawPath Array where first element is source, rest are intermediate segments
     * @param nNodes Number of nodes in the topology (to distinguish node vs edge segments)
     * @param topology The topology (needed to resolve edge destinations)
     */
    public SRPath(int[] rawPath, int nNodes, Topology topology) {
        if (rawPath == null || rawPath.length < 1) {
            throw new IllegalArgumentException("SR path must have at least a source node");
        }

        this.nNodes = nNodes;
        this.source = rawPath[0];
        this.numSegments = rawPath.length - 1;
        this.segments = new int[numSegments];
        System.arraycopy(rawPath, 1, this.segments, 0, numSegments);

        // Determine if path uses adjacency segments and compute destination
        boolean hasAdjacency = false;
        int dest = source;

        for (int i = 0; i < numSegments; i++) {
            int seg = segments[i];
            if (seg >= nNodes) {
                // This is an edge segment
                hasAdjacency = true;
                int edgeIndex = seg - nNodes;
                dest = topology.edgeDest[edgeIndex];
            } else {
                // This is a node segment
                dest = seg;
            }
        }

        this.destination = dest;
        this.usesAdjacencySegments = hasAdjacency;
    }

    /**
     * Returns whether this path uses any adjacency (edge) segments.
     */
    public boolean usesAdjacencySegments() {
        return usesAdjacencySegments;
    }

    /**
     * Checks if a given segment is a node segment.
     *
     * @param segmentIndex Index in the segments array
     * @return true if the segment is a node segment
     */
    public boolean isNodeSegment(int segmentIndex) {
        return segments[segmentIndex] < nNodes;
    }

    /**
     * Checks if a given segment is an adjacency (edge) segment.
     *
     * @param segmentIndex Index in the segments array
     * @return true if the segment is an edge segment
     */
    public boolean isAdjacencySegment(int segmentIndex) {
        return segments[segmentIndex] >= nNodes;
    }

    /**
     * Gets the node ID for a node segment.
     *
     * @param segmentIndex Index in the segments array
     * @return The node ID
     * @throws IllegalArgumentException if the segment is not a node segment
     */
    public int getNodeSegment(int segmentIndex) {
        if (!isNodeSegment(segmentIndex)) {
            throw new IllegalArgumentException("Segment at index " + segmentIndex + " is not a node segment");
        }
        return segments[segmentIndex];
    }

    /**
     * Gets the edge ID for an adjacency segment.
     *
     * @param segmentIndex Index in the segments array
     * @return The edge ID (0-based)
     * @throws IllegalArgumentException if the segment is not an edge segment
     */
    public int getEdgeSegment(int segmentIndex) {
        if (!isAdjacencySegment(segmentIndex)) {
            throw new IllegalArgumentException("Segment at index " + segmentIndex + " is not an edge segment");
        }
        return segments[segmentIndex] - nNodes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(source);
        for (int seg : segments) {
            sb.append(", ").append(seg);
        }
        sb.append("]");
        if (usesAdjacencySegments) {
            sb.append(" (uses adjacency)");
        }
        return sb.toString();
    }

    /**
     * Returns a human-readable representation showing segment types.
     *
     * @return String with N for node segments and E for edge segments
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("src=").append(source).append(" -> ");
        for (int i = 0; i < numSegments; i++) {
            if (i > 0) sb.append(" -> ");
            if (isNodeSegment(i)) {
                sb.append("N").append(segments[i]);
            } else {
                sb.append("E").append(getEdgeSegment(i));
            }
        }
        sb.append(" -> dst=").append(destination);
        return sb.toString();
    }
}

