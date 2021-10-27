package edu.repetita.solvers.sr.srpp.segmenttree;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Branch of the tree containing all non-dominated n-SR paths of a Topology up till n = maxSegments.
 * A branch corresponds to an origin node; it will therefore only contain OD SR-paths where the origin node
 * is the currentNodeNumber in the topology.
 */
class SegmentTreeBranch {
    // Parent root structure
    public final SegmentTreeRoot root;
    // Number corresponding to the (origin) node of this branch in the topology
    public final int currentNodeNumber;
    private final SegmentTreeLeaf[] leaves;
    // For each destination node, the list will contain all paths to that node
    private final LinkedList<SegmentTreeLeaf>[] pathsToDestination;

    public SegmentTreeBranch(SegmentTreeRoot root, int currentNodeNumber) {
        this.root = root;
        this.currentNodeNumber = currentNodeNumber;
        leaves = new SegmentTreeLeaf[root.nNodes];
        pathsToDestination = new LinkedList[root.nNodes];

        // Initialisation of 1-SR (=OSPF) path for each destination node.
        for (int nodeNumber = 0; nodeNumber < root.nNodes; nodeNumber++) {
            if (nodeNumber == currentNodeNumber) {
                leaves[nodeNumber] = null;
                // We still create the empty LinkedList for coherence in the code
                pathsToDestination[nodeNumber] = new LinkedList<>();
            }
            else {
                // Remember that edgeLoadPerPair works in the following way: [dest][origin][edge]
                leaves[nodeNumber] = new SegmentTreeLeaf(this, null, nodeNumber);
                pathsToDestination[nodeNumber] = new LinkedList<>();
                pathsToDestination[nodeNumber].add(leaves[nodeNumber]);
            }
        }
    }

    /**
     * Returns an array composed of all created SR paths to destinationNode
     * @param destinationNode the destination node number in the Topology
     * @return an array of paths between the said origin and destination nodes.
     */
    public int[][] getODPaths(int destinationNode) {
        int length = pathsToDestination[destinationNode].size();
        int[][] ODPaths = new int[length][];
        int index = 0;
        for ( SegmentTreeLeaf path : pathsToDestination[destinationNode] ) {
            ODPaths[index] = path.getPath();
            index++;
        }
        return ODPaths;
    }

    /**
     * Tests if a path origin destination with edge loads edgeLoads is dominated by another path already in the tree.
     * The parameter origin is not needed as it is already part of the branch instance.
     * @param destination the destination node of the tested path
     * @param newEdgeLoads the edge loads of the tested path (remember we assume a flow of one is used to compute the loads)
     * @param depth the current depth of the tree for which all SR-paths have already been processed.
     *              The depth of the node we are trying to add will therefore be depth+1
     * @return true if the path is dominated (or equal to another), false otherwise
     */
    public boolean isDominated(int destination, float[] newEdgeLoads, int depth) {
        // Loop over all non-dominated OD paths currently in the tree
        ListIterator<SegmentTreeLeaf> iterator = pathsToDestination[destination].listIterator();
        while (iterator.hasNext()){
            SegmentTreeLeaf oldPath = iterator.next();
            if (oldPath.depth <= depth) {
                // If the new path is dominated by at least one path we can instantly return
                if (dominates(newEdgeLoads, oldPath.getEdgeLoads())) {
                    return true;
                }
                if (dominates(oldPath.getEdgeLoads(), newEdgeLoads)) {
                    System.out.println("Longer SR-path dominates shorter SR-path; this is a problem.");
                }
            }
            else {
                if (dominates(newEdgeLoads, oldPath.getEdgeLoads())) {
                    return true;
                }
                // Compare to see if it is dominating a path added earlier in this iteration of addDepth()
                if (dominates(oldPath.getEdgeLoads(), newEdgeLoads)) {
                    // If it is dominating a path already added, we then need to delete this path
                    // Remove from the LinkedList
                    iterator.remove();
                    // Remove from its parent children's list
                    oldPath.parent.deleteChild(oldPath.currentNodeNumber);
                }
            }
        }
        // If it was dominated by no path, then it is non-dominated
        return false;
    }

    /**
     * Compares two arrays of edge loads and return true if newEdgeLoads is dominated by oldEdgeLoads
     * @param newEdgeLoads the edge loads that could be dominated
     * @param oldEdgeLoads the edge loads that could be dominating
     * @return true if newEdgeLoads dominated by oldEdgeLoads, false otherwise
     */
    private static boolean dominates(float[] newEdgeLoads, float[] oldEdgeLoads) {
        // If it has 1 edge that is not worse, then it cannot be dominated
        for (int edgeNumber = 0; edgeNumber < newEdgeLoads.length; edgeNumber++) {
            if (newEdgeLoads[edgeNumber] + 0.00001 < oldEdgeLoads[edgeNumber]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds a new depth of SR-paths in the branch.
     * It tries to add every possible node to all paths of leaves of with depth == depth
     * @param depth the depth of the new layer to be added, equivalently called the segment size
     */
    protected void addDepth(int depth) {
        float[] edgeContainer = new float[root.nEdges];
        // Iterate over all destination nodes
        for (int destinationNode = 0; destinationNode < root.nNodes; destinationNode++) {
            // iterate over the SR-paths
            for (SegmentTreeLeaf nextLeaf : pathsToDestination[destinationNode]) {
                // Only try to add nodes to leaves with leaf.depth == depth-1
                if (nextLeaf.depth == depth - 1) {
                    // Try to add all possible nodes at the end
                    for (int lastNode = 0; lastNode < root.nNodes; lastNode++) {
                        nextLeaf.tryAddChild(lastNode, edgeContainer);
                    }
                }
            }
        }
    }

    /**
     * Deletes a leaf from the tree by removing it from the LinkedList and its parent children's list
     * @param leaf a reference to the leaf to be removed
     */
    protected void deleteLeaf(SegmentTreeLeaf leaf) {
        pathsToDestination[leaf.currentNodeNumber].remove(leaf);
        leaf.parent.deleteChild(leaf.currentNodeNumber);
    }
}
