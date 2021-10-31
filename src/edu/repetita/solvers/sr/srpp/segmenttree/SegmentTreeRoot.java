package edu.repetita.solvers.sr.srpp.segmenttree;

import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;
import edu.repetita.solvers.sr.srpp.ComparableIntPair;
import edu.repetita.solvers.sr.srpp.edgeloads.EdgeLoadsFullArray;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Root of the tree containing the leaves (each corresponding to an SR-path) and information over the topology.
 */
public class SegmentTreeRoot {
    public final int nNodes;
    public final int nEdges;
    public final int maxSegments;
    private final EdgeLoadsFullArray[][] edgeLoadPerPair;
    private final SegmentTreeLeaf[] leaves;
    // For each origin destination pair, the list ODPaths[origin][destination] contains pointers to all the leaves
    // having origin and destination respectively as origin and destination nodes
    private final LinkedList<SegmentTreeLeaf>[][] ODPaths;

    /**
     * Constructor for the root of the SegmentTree
     * @param topology the topology on which the tree will be built
     * @param maxSegments the maximum number of (node) segments of each SR-path
     */
    public SegmentTreeRoot(Topology topology, int maxSegments) {
        this.nNodes = topology.nNodes;
        this.nEdges = topology.nEdges;
        this.maxSegments = maxSegments;
        this.leaves = new SegmentTreeLeaf[nNodes];
        this.ODPaths = new LinkedList[nNodes][nNodes];

        double [][][] temporary = makeEdgeLoadPerPair(topology);
        this.edgeLoadPerPair = new EdgeLoadsFullArray[nNodes][nNodes];
        for (int destNode = 0; destNode < nNodes; destNode++) {
            for (int originNode = 0; originNode < nNodes; originNode++) {
                this.edgeLoadPerPair[originNode][destNode] = new EdgeLoadsFullArray(temporary[destNode][originNode]);
            }
        }

        for (int originNode  = 0; originNode < nNodes; originNode++) {
            for (int destNode = 0; destNode < nNodes; destNode++) {
                ODPaths[originNode][destNode] = new LinkedList<>();
            }
            leaves[originNode] = new SegmentTreeLeaf(originNode, this);
        }
    }

    /**
     * Creates the array edgeLoadPair[|N|][|N|][|A|]
     * For each triplet (U,V,a); U,V nodes and a an edge;
     * edgeLoadPerPair[U][V][a] is the load on edge a when there is a demand of 1 from V to U.
     * @param topology the topology of the network
     * @return edgeLoadPair[][][] as explained above
     */
    public static double[][][] makeEdgeLoadPerPair(Topology topology) {

        int nEdges = topology.nEdges;
        int nNodes = topology.nNodes;
        double[][][] edgeLoadPerPair = new double[nNodes][nNodes][nEdges];

        // Compute the shortest paths in the graph, from there we get the forwarding graph of each node
        ShortestPaths sp = new ShortestPaths(topology);
        sp.computeShortestPaths();

        // Loop over all (destination) nodes
        for (int dest = 0; dest < nNodes; dest++) {
            // Sort the indices of sp.distance[dest]
            ComparableIntPair[] nodesSortedByDistance = new ComparableIntPair[nNodes];
            for (int i = 0; i < nNodes; i++) {
                nodesSortedByDistance[i] = new ComparableIntPair(i, sp.distance[dest][i]);
            }
            Arrays.sort(nodesSortedByDistance);

            // Starting from the closest node origin we will now fill edgeLoadPerPair[dest][origin][] for all edges
            for (int i = 1; i < nNodes; i++) {
                int origin = nodesSortedByDistance[i].index;
                fillEdgeUsage(dest, origin, edgeLoadPerPair[dest], sp, nEdges);
            }
        }
        return edgeLoadPerPair;
    }

    /**
     * Simulates a demand of one from origin to dest and stores the load in edgeLoadDest.
     * The loads for all nodes where the distance to dest is smaller the distance origin-dest should already be computed
     * as it makes use of these loads to compute the new origin-dest load.
     * @param dest The destination node
     * @param origin The origin node
     * @param edgeLoadDest The current computed edge loads for the destination node dest.
     *                    It must already be computed for all nodes closer to dest than origin
     */
    private static void fillEdgeUsage(int dest, int origin, double[][] edgeLoadDest, ShortestPaths sp, int nEdges) {
        int nSuccessors = sp.nSuccessors[dest][origin];
        for (int i = 0; i < nSuccessors; i++) {
            // Add the load on the direct edge to the new node
            int nextEdge = sp.successorEdges[dest][origin][i];
            edgeLoadDest[origin][nextEdge] = 1.0/nSuccessors;
            // Add the load when routing from nextNode to dest
            int nextNode = sp.successorNodes[dest][origin][i];
            if (nextNode != dest) {
                for (int j = 0; j < nEdges; j++) {
                    edgeLoadDest[origin][j] += 1.0 / nSuccessors * edgeLoadDest[nextNode][j];
                }
            }
        }
    }

    /**
     * Creates all SR paths up to maxSegments for all origin destination pairs in the Topology.
     */
    public void createODPaths() {
        /* By default, depth 1 was already constructed by the constructor. */
        for (int depth = 2; depth <= maxSegments; depth++) {
            addDepth(depth);
        }
    }

    private void addDepth(int depth) {
        EdgeLoadsFullArray edgeLoads;
        for (int originNode = 0; originNode < nNodes; originNode++) {
            System.err.println("Adding originNode : "+originNode+" (Depth "+depth+")");
            for (int nextNode = 0; nextNode < nNodes; nextNode++) {
                if (nextNode != originNode) {
                    edgeLoads = getODLoads(originNode, nextNode);
                    leaves[originNode].children[nextNode].extendSRPath(depth, edgeLoads);
                }
            }
        }
    }

    protected void addLeafToList(SegmentTreeLeaf leaf) {
        ODPaths[leaf.originNodeNumber][leaf.currentNodeNumber].add(leaf);
    }

    /**
     * Returns the loads on each arc when there is a demand of 1 from originNode to destNode
     * @param originNode the node from which the demand originates
     * @param destNode the node to which the demand is routed
     * @return an array of floats corresponding to each edge's load
     */
    protected EdgeLoadsFullArray getODLoads(int originNode, int destNode) {
        return edgeLoadPerPair[originNode][destNode];
    }

    /**
     * returns true if path is an existing SR-path in the tree, false otherwise
     * @param path the path whose presence is to be tested
     * @return true if the path exist, false if not
     */
    public boolean pathInTree(int[] path) {
        SegmentTreeLeaf nextLeaf = leaves[path[0]];
        for (int index = 1; index < path.length; index++) {
            nextLeaf = nextLeaf.children[path[index]];
            if (nextLeaf == null) {
                return false;
            }
        }
        return true;
    }

    protected boolean testNewPathDomination(EdgeLoadsFullArray newPathEdgeLoads, int originNode, int destNode, int depth) {
        // Loop over all non-dominated OD paths currently in the tree
        ListIterator<SegmentTreeLeaf> iterator = ODPaths[originNode][destNode].listIterator();
        SegmentTreeLeaf nextPath;
        EdgeLoadsFullArray nextPathLoads;
        while (iterator.hasNext()) {
            nextPath = iterator.next();
            nextPathLoads = nextPath.getEdgeLoads();
            if (nextPath.depth < depth) {
                if (nextPathLoads.dominates(newPathEdgeLoads)) {
                    return true;
                }
                /*
                 We do not test the case where a longer path might dominate a shorter path.
                 From experience, this never happens, and we have the intuition that it simply cannot happen.
                 Longer paths can indeed dominate shorter paths, but we think that in such a case, the shorter path
                 would already have been dominated by another path of same size or shorter, meaning that the path would
                 not be in the tree in the first place.
                 Furthermore, because of the tree structure, deleting a dominated shorter path would be rather difficult
                 and we decided to not implement this as even if this case happens, it is extremely rare.
                */
            }
            else {
                if (nextPathLoads.dominates(newPathEdgeLoads)) {
                    return true;
                }
                if (newPathEdgeLoads.dominates(nextPathLoads)) {
                    // Remove from LinkedList
                    iterator.remove();
                    // Remove from tree
                    nextPath.delete();
                }
            }
        }
        return false;
    }

    public int[][] getODPaths(int originNode, int destNode) {
        int[][] paths = new int[ODPaths[originNode][destNode].size()][];
        int index = 0;
        for (SegmentTreeLeaf path : ODPaths[originNode][destNode]) {
            paths[index] = path.getPath();
            index++;
        }
        return paths;
    }
}
