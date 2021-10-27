package edu.repetita.solvers.sr.srpp.segmenttree;

import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;
import edu.repetita.solvers.sr.srpp.ComparableIntPair;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * Root of the tree containing all non-dominated n-SR paths of a Topology up till n = maxSegments
 */
public class SegmentTreeRoot {
    public final int nNodes;
    public final int nEdges;
    public final int maxSegments;
    private final float[][][] edgeLoadPerPair;
    private final SegmentTreeLeaf[] leaves;
    private final LinkedList<SegmentTreeLeaf>[][] ODPaths;

    /* OK */
    public SegmentTreeRoot(Topology topology, int maxSegments) {
        this.nNodes = topology.nNodes;
        this.nEdges = topology.nEdges;
        this.maxSegments = maxSegments;
        this.leaves = new SegmentTreeLeaf[nNodes];
        this.ODPaths = new LinkedList[nNodes][nNodes];

        this.edgeLoadPerPair = makeEdgeLoadPerPair(topology);

        for (int originNode  = 0; originNode < nNodes; originNode++) {
            leaves[originNode] = new SegmentTreeLeaf(originNode, this);
            for (int destNode = 0; destNode < nNodes; destNode++) {
                ODPaths[originNode][destNode] = new LinkedList<>();
            }
        }
    }

    /* OK */
    protected void addLeafToList(SegmentTreeLeaf leaf) {
        ODPaths[leaf.originNodeNumber][leaf.currentNodeNumber].push(leaf);
    }

    /* OK */
    /**
     * Creates all SR paths up to maxSegments for all origin destination pairs in the Topology.
     */
    public void createODPaths() {
        // By default, depth 1 was already constructed by the constructor.
        for (int depth = 2; depth <= maxSegments; depth++) {
            for (int branchNumber = 0; branchNumber < nNodes; branchNumber++) {
                // TODO maybe add demands parameter ?
                addDepth(depth);
            }
        }
    }

    private void addDepth(int depth) {
        for (int originNode = 0; originNode < nNodes; originNode++) {
            for (int destNode = 0; destNode < nNodes; destNode++) {
                for (SegmentTreeLeaf leaf : ODPaths[originNode][destNode]) {
                    if (leaf.depth == depth-1) {
                        // Try to ad all possible nodes at the end
                        leaf.extendSRPath();
                    }
                    else if (leaf.depth == depth) {
                        break;
                    }
                }
            }
        }
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
     * returns if a path is present in the tree or not
     * @param path the path whose presence is to be tested
     * @return true if the path exist, false if not
     */
    public boolean pathInTree(int[] path) {
        SegmentTreeLeaf nextLeaf = leaves[path[0]].getLeaf(path[1]);  // I know this leaf will always exist as it is
        // created at the start and corresponds to 1-SR or equivalently OSPF.
        for (int index = 2; index < path.length; index++) {
            nextLeaf = nextLeaf.getChild(path[index]);
            if (nextLeaf == null) {
                break;
            }
        }
        return (nextLeaf != null);
    }

    /**
     * Deletes a leaf from the tree by removing it from the LinkedList and its parent children's list
     * This method only calls the deleteLeaf() method on the correct branch
     * @param leaf a reference to the leaf to be removed
     */
    private void deleteLeaf(SegmentTreeLeaf leaf) {
        leaves[leaf.branch.currentNodeNumber].deleteLeaf(leaf);
    }

    /**
     * Creates the array edgeLoadPair[|N|][|N|][|A|]
     * For each triplet (U,V,a); U,V nodes and a an edge;
     * edgeLoadPerPair[U][V][a] is the load on edge a when there is a demand of 1 from V to U.
     * @param topology the topology of the network
     * @return edgeLoadPair[][][] as explained above
     */
    public static float[][][] makeEdgeLoadPerPair(Topology topology) {

        int nEdges = topology.nEdges;
        int nNodes = topology.nNodes;
        float[][][] edgeLoadPerPair = new float[nNodes][nNodes][nEdges];

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
    private static void fillEdgeUsage(int dest, int origin, float[][] edgeLoadDest, ShortestPaths sp, int nEdges) {
        int nSuccessors = sp.nSuccessors[dest][origin];
        for (int i = 0; i < nSuccessors; i++) {
            // Add the load on the direct edge to the new node
            int nextEdge = sp.successorEdges[dest][origin][i];
            edgeLoadDest[origin][nextEdge] = 1.0f/nSuccessors;
            // Add the load when routing from nextNode to dest
            int nextNode = sp.successorNodes[dest][origin][i];
            if (nextNode != dest) {
                for (int j = 0; j < nEdges; j++) {
                    edgeLoadDest[origin][j] += 1.0f / nSuccessors * edgeLoadDest[nextNode][j];
                }
            }
        }
    }

    /**
     * Returns a branch of the tree.
     * A branch corresponds to an origin node
     * @param leafNumber the number of the origin node in the Topology
     * @return the branch corresponding to the said origin node
     */
    protected SegmentTreeLeaf getLeaf(int leafNumber) {
        return leaves[leafNumber];
    }

    /**
     * Returns an array composed of all created SR paths between originNode and destinationNode
     * @param originNode the origin node number in the Topology
     * @param destinationNode the destination node number in the Topology
     * @return an array of paths between the said origin and destination nodes.
     */
    public int[][] getODPaths(int originNode, int destinationNode) {
        return leaves[originNode].getODPaths(destinationNode);
    }

    /**
     * Browses through the tree in a depth first manner to create an array of arrays containing all non-dominated paths
     * @return the array of arrays each corresponding to a non dominated path
     */
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

    /**
     * For a leaf currentLeaf, recursively writes all SR-paths corresponding to this leaf and all of its children by
     * doing a depth-first.
     * @param allPaths List containing all the currently processed paths
     * @param allPathsIndex Index for allPaths indicating which part of the array is already processed
     * @param currentPath An array of nodes corresponding to the nodes prior to currentLeaf in the SR-path
     * @param currentPathIndex Index for currentPath indicating which part of the array corresponds to prior nodes of the path
     * @param currentLeaf The leaf we are currently processing
     */
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
    }
}
