package edu.repetita.solvers.sr.srpp.segmenttree;

import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;
import edu.repetita.solvers.sr.srpp.ComparableIntPair;

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
    private final float[][][] edgeLoadPerPair;
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

        // TODO change to use the correct type
        double [][][] temporary = makeEdgeLoadPerPair(topology);
        this.edgeLoadPerPair = new float[nNodes][nNodes][nEdges];
        for (int i1 = 0; i1 < nNodes; i1++) {
            for (int i2 = 0; i2 < nNodes; i2++) {
                for (int j1 = 0; j1 < nNodes; j1++) {
                    this.edgeLoadPerPair[i1][i2][j1] = (float) temporary[i1][i2][j1];
                }
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
        // By default, depth 1 was already constructed by the constructor.
        for (int depth = 2; depth <= maxSegments; depth++) {
            addDepth(depth);
        }
    }

    // TODO try it out with a depth first search instead of iterating over the LinkedList
    private void addDepth(int depth) {
        for (int originNode = 0; originNode < nNodes; originNode++) {
            for (int destNode = 0; destNode < nNodes; destNode++) {
                for (SegmentTreeLeaf leaf : ODPaths[originNode][destNode]) {
                    if (leaf.depth == depth-1) {
                        leaf.extendSRPath();
                    }
                    else if (leaf.depth == depth) {
                        // break;
                    }
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
    protected float[] getODLoads(int originNode, int destNode) {
        return edgeLoadPerPair[destNode][originNode];
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

    protected boolean testNewPathDomination(float[] newPathEdgeLoads, int originNode, int destNode, int depth) {
        // Loop over all non-dominated OD paths currently in the tree
        ListIterator<SegmentTreeLeaf> iterator = ODPaths[originNode][destNode].listIterator();
        SegmentTreeLeaf nextPath;
        float[] nextPathLoads;
        while (iterator.hasNext()) {
            nextPath = iterator.next();
            nextPathLoads = nextPath.getEdgeLoads();
            if (nextPath.depth < depth) {
                if (dominates(newPathEdgeLoads, nextPathLoads)) {
                    return true;
                }
                if (dominates(nextPathLoads, newPathEdgeLoads)) {
                    System.out.println("ERROR: shorter path dominated by longer path");
                    System.exit(1);
                }
            }
            else {
                if (dominates(newPathEdgeLoads, nextPathLoads)) {
                    return true;
                }
                if (dominates(nextPathLoads, newPathEdgeLoads)) {
                    // Remove from LinkedList
                    iterator.remove();
                    // Remove from tree
                    nextPath.delete();
                }
            }
        }
        return false;
    }

    /**
     * Compares two arrays of edge loads and returns true if maybeDominatedLoads is dominated by maybeDominatingLoads
     * @param maybeDominatedLoads the edge loads that could be dominated
     * @param maybeDominatingLoads the edge loads that could be dominating
     * @return true if newEdgeLoads dominated by oldEdgeLoads, false otherwise
     */
    private static boolean dominates(float[] maybeDominatedLoads, float[] maybeDominatingLoads) {
        // If it has 1 edge that is not worse, then it cannot be dominated
        for (int edgeNumber = 0; edgeNumber < maybeDominatedLoads.length; edgeNumber++) {
            if (maybeDominatedLoads[edgeNumber] + 0.00001 < maybeDominatingLoads[edgeNumber]) {
                return false;
            }
        }
        return true;
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

    /*
    public int[][] getAllPaths() {
        int numberOfPaths = 0;
        for (int branchNumber = 0; branchNumber < nNodes; branchNumber++) {
            numberOfPaths += leaves[branchNumber].getNumberOfPaths();
        }
        int[][] allPaths = new int[numberOfPaths][];  // Array containing all the paths
        int[] allPathsIndex = new int[1];  // Array of size 1 to pass int by reference
        // Integer instead of int to pass by reference
        int[] currentPath = new int[20+1];  // Array containing the nodes of the path we are currently processing
        // TODO replace 20+1 by I think depth+1 (need to check)
        int currentPathIndex = 0;  // Index to remember how many nodes are in the currentPath
        // Depth-first search in the tree to get the all the SR-paths
        for (int branchNumber = 0; branchNumber < nNodes; branchNumber++) {
            // Add the origin (branch) node as first element of the current path
            currentPath[currentPathIndex] = branchNumber;
            currentPathIndex++;
            for (int leafNumber = 0; leafNumber < nNodes; leafNumber++) {
                SegmentTreeLeaf nextLeaf = leaves[branchNumber].getLeaf(leafNumber);
                if (nextLeaf != null) {
                    depthFirstCreation(allPaths, allPathsIndex, currentPath, currentPathIndex, nextLeaf);
                }
            }
            currentPathIndex--;
        }
        return allPaths;
    }

    private void depthFirstCreation(int[][] allPaths, int[] allPathsIndex, int[] currentPath, int currentPathIndex, SegmentTreeLeaf currentLeaf) {
        // We add the path corresponding to the current leaf to allPaths
        currentPath[currentPathIndex] = currentLeaf.currentNodeNumber;
        currentPathIndex++;
        allPaths[allPathsIndex[0]] = new int[currentPathIndex];
        System.arraycopy(currentPath, 0, allPaths[allPathsIndex[0]], 0, currentPathIndex);
        allPathsIndex[0]++;
        for (int leafNumber = 0; leafNumber < nNodes; leafNumber++) {
            if (currentLeaf.getChild(leafNumber) != null) {
                depthFirstCreation(allPaths, allPathsIndex, currentPath, currentPathIndex, currentLeaf.getChild(leafNumber));
            }
        }
    }*/
}
