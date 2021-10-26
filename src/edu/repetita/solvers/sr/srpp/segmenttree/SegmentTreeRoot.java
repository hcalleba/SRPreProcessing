package edu.repetita.solvers.sr.srpp.segmenttree;

import edu.repetita.core.Demands;

/**
 * Root of the tree containing all non-dominated n-SR paths of a Topology up till n = maxSegments
 */
public class SegmentTreeRoot {
    public final int nNodes;
    public final int nEdges;
    public final int maxSegments;
    private final SegmentTreeBranch[] branches;

    public SegmentTreeRoot(int nNodes, int nEdges, int maxSegments, float[][][] edgeLoadPerPair) {
        this.nNodes = nNodes;
        this.nEdges = nEdges;
        this.maxSegments = maxSegments;
        branches = new SegmentTreeBranch[nNodes];

        for (int nodeNumber  = 0; nodeNumber < nNodes; nodeNumber++) {
            branches[nodeNumber] = new SegmentTreeBranch(this, nodeNumber, edgeLoadPerPair);
        }
    }

    /**
     * Returns a branch of the tree.
     * A branch corresponds to an origin node
     * @param branchNumber the number of the origin node in the Topology
     * @return the branch corresponding to the said origin node
     */
    protected SegmentTreeBranch getBranch(int branchNumber) {
        return branches[branchNumber];
    }

    /**
     * Returns an array composed of all created SR paths between originNode and destinationNode
     * @param originNode the origin node number in the Topology
     * @param destinationNode the destination node number in the Topology
     * @return an array of paths between the said origin and destination nodes.
     */
    public int[][] getODPaths(int originNode, int destinationNode) {
        return branches[originNode].getODPaths(destinationNode);
    }

    /**
     * Browses through the tree in a depth first manner to create an array of arrays containing all non-dominated paths
     * @return the array of arrays each corresponding to a non dominated path
     */
    public int[][] getAllPaths() {
        int numberOfPaths = 0;
        for (int branchNumber = 0; branchNumber < nNodes; branchNumber++) {
            numberOfPaths += branches[branchNumber].getNumberOfPaths();
        }
        int[][] allPaths = new int[numberOfPaths][];  // Array containing all the paths
        int[] allPathsIndex = new int[1];  // Array of size 1 to pass int by reference
        // Integer instead of int to pass by reference
        int[] currentPath = new int[20+1];  // Array containing the nodes of the path we are currently processing
        int currentPathIndex = 0;  // Index to remember how many nodes are in the currentPath
        // Depth-first search in the tree to get the all the SR-paths
        for (int branchNumber = 0; branchNumber < nNodes; branchNumber++) {
            // Add the origin (branch) node as first element of the current path
            currentPath[currentPathIndex] = branchNumber;
            currentPathIndex++;
            for (int leafNumber = 0; leafNumber < nNodes; leafNumber++) {
                SegmentTreeLeaf nextLeaf = branches[branchNumber].getLeaf(leafNumber);
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

    /**
     * Creates all SR paths up to maxSegments for all origin destination pairs in the Topology.
     */
    public void createODPaths() {
        // By default, depth 1 was already constructed by the constructor.
        for (int depth = 2; depth <= maxSegments; depth++) {
            for (int branchNumber = 0; branchNumber < nNodes; branchNumber++) {
                // TODO maybe add demands parameter ?
                branches[branchNumber].addDepth(depth);
            }
        }
    }

    /**
     * returns if a path is present in the tree or not
     * @param path the path whose presence is to be tested
     * @return true if the path exist, false if not
     */
    public boolean pathInTree(int[] path) {
        SegmentTreeLeaf nextLeaf = branches[path[0]].getLeaf(path[1]);  // I know this leaf will always exist as it is
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
        branches[leaf.branch.currentNodeNumber].deleteLeaf(leaf);
    }
}
