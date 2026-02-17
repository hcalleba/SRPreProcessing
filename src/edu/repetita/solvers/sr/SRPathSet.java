package edu.repetita.solvers.sr;

import edu.repetita.core.Topology;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Container for all SR paths in a network.
 *
 * Paths are organized by source-destination pairs, where each pair can have
 * multiple non-dominated paths.
 *
 * Supports filtering to exclude paths that use adjacency (edge) segments.
 */
public class SRPathSet {

    /**
     * All paths indexed by [source][destination]
     */
    private final List<SRPath>[][] pathsByPair;

    /**
     * Number of nodes in the topology
     */
    public final int nNodes;

    /**
     * Number of edges in the topology
     */
    public final int nEdges;

    /**
     * Total number of paths
     */
    private int totalPaths;

    /**
     * Number of paths using adjacency segments
     */
    private int pathsWithAdjacency;

    /**
     * Creates an empty SRPathSet for a given topology.
     *
     * @param nNodes Number of nodes in the topology
     * @param nEdges Number of edges in the topology
     */
    @SuppressWarnings("unchecked")
    public SRPathSet(int nNodes, int nEdges) {
        this.nNodes = nNodes;
        this.nEdges = nEdges;
        this.totalPaths = 0;
        this.pathsWithAdjacency = 0;

        // Initialize path storage
        this.pathsByPair = (List<SRPath>[][]) new List[nNodes][nNodes];
        for (int i = 0; i < nNodes; i++) {
            for (int j = 0; j < nNodes; j++) {
                pathsByPair[i][j] = new ArrayList<>();
            }
        }
    }

    /**
     * Creates an SRPathSet from a topology.
     */
    public SRPathSet(Topology topology) {
        this(topology.nNodes, topology.nEdges);
    }

    /**
     * Adds a path to the set.
     *
     * @param path The SR path to add
     */
    public void addPath(SRPath path) {
        pathsByPair[path.source][path.destination].add(path);
        totalPaths++;
        if (path.usesAdjacencySegments()) {
            pathsWithAdjacency++;
        }
    }

    /**
     * Gets all paths from source to destination.
     *
     * @param source Source node
     * @param destination Destination node
     * @return List of SR paths (may be empty)
     */
    public List<SRPath> getPaths(int source, int destination) {
        return Collections.unmodifiableList(pathsByPair[source][destination]);
    }

    /**
     * Gets all paths from source to destination that don't use adjacency segments.
     *
     * @param source Source node
     * @param destination Destination node
     * @return List of SR paths without adjacency segments
     */
    public List<SRPath> getPathsWithoutAdjacency(int source, int destination) {
        return pathsByPair[source][destination].stream()
                .filter(p -> !p.usesAdjacencySegments())
                .collect(Collectors.toList());
    }

    /**
     * Gets the total number of paths.
     */
    public int getTotalPaths() {
        return totalPaths;
    }

    /**
     * Gets the number of paths that use adjacency segments.
     */
    public int getPathsWithAdjacencyCount() {
        return pathsWithAdjacency;
    }

    /**
     * Gets the number of paths that don't use adjacency segments.
     */
    public int getPathsWithoutAdjacencyCount() {
        return totalPaths - pathsWithAdjacency;
    }

    /**
     * Gets all paths in the set.
     *
     * @return List of all SR paths
     */
    public List<SRPath> getAllPaths() {
        List<SRPath> all = new ArrayList<>();
        for (int i = 0; i < nNodes; i++) {
            for (int j = 0; j < nNodes; j++) {
                all.addAll(pathsByPair[i][j]);
            }
        }
        return all;
    }

    /**
     * Gets all paths that don't use adjacency segments.
     *
     * @return List of SR paths without adjacency segments
     */
    public List<SRPath> getAllPathsWithoutAdjacency() {
        return getAllPaths().stream()
                .filter(p -> !p.usesAdjacencySegments())
                .collect(Collectors.toList());
    }

    /**
     * Creates a new SRPathSet containing only paths without adjacency segments.
     *
     * @return New SRPathSet with filtered paths
     */
    public SRPathSet filterOutAdjacencyPaths() {
        SRPathSet filtered = new SRPathSet(nNodes, nEdges);
        for (int i = 0; i < nNodes; i++) {
            for (int j = 0; j < nNodes; j++) {
                for (SRPath path : pathsByPair[i][j]) {
                    if (!path.usesAdjacencySegments()) {
                        filtered.addPath(path);
                    }
                }
            }
        }
        return filtered;
    }

    /**
     * Checks if there is at least one path from source to destination.
     */
    public boolean hasPath(int source, int destination) {
        return !pathsByPair[source][destination].isEmpty();
    }

    /**
     * Checks if there is at least one path from source to destination
     * that doesn't use adjacency segments.
     */
    public boolean hasPathWithoutAdjacency(int source, int destination) {
        return pathsByPair[source][destination].stream()
                .anyMatch(p -> !p.usesAdjacencySegments());
    }

    /**
     * Gets the number of paths for a specific source-destination pair.
     */
    public int getPathCount(int source, int destination) {
        return pathsByPair[source][destination].size();
    }

    @Override
    public String toString() {
        return "SRPathSet{" +
                "nNodes=" + nNodes +
                ", totalPaths=" + totalPaths +
                ", pathsWithAdjacency=" + pathsWithAdjacency +
                ", pathsWithoutAdjacency=" + (totalPaths - pathsWithAdjacency) +
                '}';
    }
}

