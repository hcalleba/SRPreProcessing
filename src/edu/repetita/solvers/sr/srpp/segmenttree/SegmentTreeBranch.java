package edu.repetita.solvers.sr.srpp.segmenttree;

import java.util.LinkedList;

/**
 * Branch of the tree containing all non-dominated n-SR paths of a Topology up till n = maxSegments.
 * A branch corresponds to an origin node; it will therefore only contain OD SR-paths where the origin node
 * is the currentNodeNumber in the topology.
 */
public class SegmentTreeBranch {
    // Parent root structure
    public final SegmentTreeRoot root;
    // Number corresponding to the (origin) node of this branch in the topology
    public final int currentNodeNumber;
    private final SegmentTreeLeaf[] leaves;
    // For each destination node, the list will contain all paths to that node
    private final LinkedList<SegmentTreeLeaf>[] pathsToDestination;

    public SegmentTreeBranch(SegmentTreeRoot root, int currentNodeNumber, float[][][] edgeLoadPerPair) {
        this.root = root;
        this.currentNodeNumber = currentNodeNumber;
        leaves = new SegmentTreeLeaf[root.nNodes];
        pathsToDestination = new LinkedList[root.nNodes];

        // Initialisation of 1-SR (=OSPF) path for each destination node.
        for (int nodeNumber = 0; nodeNumber < root.nNodes; nodeNumber++) {
            if (nodeNumber == currentNodeNumber) {
                leaves[nodeNumber] = null;
                // We still create the empty LinkedList for coherence in the code
                pathsToDestination[nodeNumber] = new LinkedList<SegmentTreeLeaf>();
            }
            else {
                // Remember that edgeLoadPerPair works in the following way: [dest][origin][edge]
                leaves[nodeNumber] = new SegmentTreeLeaf(this, null, nodeNumber, edgeLoadPerPair[nodeNumber][currentNodeNumber]);
                pathsToDestination[nodeNumber] = new LinkedList<SegmentTreeLeaf>();
                pathsToDestination[nodeNumber].add(leaves[nodeNumber]);
            }
        }
    }

    /**
     * Adds a leaf to the list of all paths ending on the destination node of the path
     * @param path the leaf corresponding to the path to be added
     */
    protected void addLeafToLinkedList(SegmentTreeLeaf path) {
        pathsToDestination[path.currentNodeNumber].add(path);
    }

    /**
     * Returns the leaf corresponding to leafNumber. This leaf will then correspond to a path:
     * this.currentNodeNumber -> leafNumber
     * @param leafNumber the number corresponding to the node in the topology.
     * @return The said leaf
     */
    public SegmentTreeLeaf getLeaf(int leafNumber) {
        return leaves[leafNumber];
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
     * Computes the number of non dominated paths on this branch
     * @return the number of non dominated paths of the branch
     */
    public int getNumberOfPaths() {
        int nbPaths = 0;
        for (int leafNumber = 0; leafNumber < root.nNodes; leafNumber++) {
            nbPaths += pathsToDestination[leafNumber].size();
        }
        return nbPaths;
    }
}
