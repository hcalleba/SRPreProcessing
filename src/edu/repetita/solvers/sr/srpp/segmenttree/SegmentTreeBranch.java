package edu.repetita.solvers.sr.srpp.segmenttree;

import java.util.LinkedList;
import java.util.ListIterator;

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
    // Maximum depth of the SR-paths
    private int maxDepth;
    // int pointing to the first element of size maxDepth in pathsToDestination
    private final int[] firstPathMaxDepth;

    public SegmentTreeBranch(SegmentTreeRoot root, int currentNodeNumber, float[][][] edgeLoadPerPair) {
        this.root = root;
        this.currentNodeNumber = currentNodeNumber;
        leaves = new SegmentTreeLeaf[root.nNodes];
        pathsToDestination = new LinkedList[root.nNodes];
        firstPathMaxDepth = new int[root.nNodes];  // Initialised to 0 which is what we want
        maxDepth = 1;

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
    protected SegmentTreeLeaf getLeaf(int leafNumber) {
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

    /**
     * Tests if a path origin destination is with edge loads edgeLoads is dominated by another path already in the tree.
     * The parameter origin is not needed as it is already part of the branch instance.
     * @param destination the destination node of the tested path
     * @param newEdgeLoads the edge loads of the tested path (remember we assume a flow of one is used to compute the loads)
     * @return true if the path is dominated (or equal to another), false otherwise
     */
    public boolean isDominated(int destination, float[] newEdgeLoads) {
        // TODO boolean newBetterThanOld;
        // TODO better rounding for floating point imprecision
        // TODO need to change if I don't want to compare when same size
        boolean oldBetterThanNew;
        // Loop over all non-dominated OD paths currently in the tree
        for ( SegmentTreeLeaf oldPath : pathsToDestination[destination] ) {
            oldBetterThanNew = true;
            // If it has 1 edge that is not worse, then it cannot be dominated
            for (int edgeNumber = 0; edgeNumber < root.nEdges; edgeNumber++) {
                if (newEdgeLoads[edgeNumber] + 0.00001 < oldPath.edgeLoads[edgeNumber]) {
                    oldBetterThanNew = false;
                    break;
                }
            }
            // If the new path is dominated by at least one path we can instantly return
            if (oldBetterThanNew) {
                return true;
            }
        }
        // If it was dominated by no path, then it is non-dominated
        return false;
    }

    protected void addDepth(int depth) {
        // TODO when I tryAddChild and it succeeds, I should not try to add a child after the node I just added
        float[] edgeContainer = new float[root.nEdges];
        int[] addedPathsPerDest = new int[root.nNodes];
        // Iterate over all destination nodes
        for (int destinationNode = 0; destinationNode < root.nNodes; destinationNode++) {
            // Test if there are SR-paths of size maxDepth
            if (firstPathMaxDepth[destinationNode] < pathsToDestination[destinationNode].size()) {
                // iterate over these SR-paths of size maxDepth
                ListIterator<SegmentTreeLeaf> iterator = pathsToDestination[destinationNode].listIterator(firstPathMaxDepth[destinationNode]);
                while (iterator.hasNext()) {
                    // Try to add all possible nodes at the end
                    SegmentTreeLeaf nextLeaf = iterator.next();
                    // This is needed otherwise I would try to add nodes after paths created at this iteration of addDepth
                    if (nextLeaf.depth >= depth) {
                        break;
                    }
                    for (int lastNode = 0; lastNode < root.nNodes; lastNode++) {
                        if (nextLeaf.tryAddChild(lastNode, edgeContainer)) {
                            addedPathsPerDest[destinationNode]++;
                        }
                    }
                }
            }
        }
        for (int nodeNumber = 0; nodeNumber < root.nNodes; nodeNumber++) {
            firstPathMaxDepth[nodeNumber] += addedPathsPerDest[nodeNumber];
        }
    }
}
