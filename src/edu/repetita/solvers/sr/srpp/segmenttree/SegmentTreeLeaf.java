package edu.repetita.solvers.sr.srpp.segmenttree;

public class SegmentTreeLeaf {
    public final SegmentTreeBranch branch;
    public final SegmentTreeLeaf parent;
    private final SegmentTreeLeaf[] children;
    private final float[] edgeLoads;
    // Number of the node to which this leaf corresponds.
    public final int currentNodeNumber;

    public SegmentTreeLeaf(SegmentTreeBranch branch, SegmentTreeLeaf parent, int currentNodeNumber, float[] edgeLoads) {
        this.branch = branch;
        this.parent = parent;
        this.currentNodeNumber = currentNodeNumber;
        children = new SegmentTreeLeaf[branch.root.nNodes];
        this.edgeLoads = edgeLoads;
    }

    /**
     * Adds a child to the current leaf
     * @param childNumber the node number in the topology of the newly added child
     * @param childEdgeLoads the loads on the edges of the newly added child, the array will be cloned.
     */
    private void addChild(int childNumber, float[] childEdgeLoads) {
        float[] newChildEdgeLoads = childEdgeLoads.clone();
        children[childNumber] = new SegmentTreeLeaf(branch, this, childNumber, newChildEdgeLoads);
        branch.addLeafToLinkedList(children[childNumber]);
    }

    /**
     * Tries to add a child to the current leaf, creating a new SR path.
     * Multiple test are computed to make sure the path we are trying to add is non dominated.
     * @param childNumber the node number in the topology of the newly added child
     * @param edgeLoadContainer an array of size nEdges that will be used to store computations.
     *                          the array is used to prevent creating many arrays when iterating over all possible
     *                          children to add as this method should be called often in such a case.
     * @return true if the path was non dominated and the node added, false if the path was not added.
     */
    public boolean tryAddChild(int childNumber, float[] edgeLoadContainer){
        // Test if childNumber is not already in the path
        SegmentTreeLeaf nextParent = parent;
        while (nextParent != null) {
            if (childNumber == nextParent.currentNodeNumber){
                return false;
            }
            nextParent = nextParent.parent;
        }

        // TODO Verify that path origin+1 ... dest is already in tree

        // Test if the path is dominated
        float[] edgeLoadsLastSegment = branch.root.getBranch(parent.currentNodeNumber).getLeaf(childNumber).edgeLoads;
        float[] edgeLoadsAllButLastSegment = parent.edgeLoads;
        for (int edgeNumber = 0; edgeNumber < branch.root.nEdges; edgeNumber++) {
            edgeLoadContainer[edgeNumber] = edgeLoadsLastSegment[edgeNumber] + edgeLoadsAllButLastSegment[edgeNumber];
        }
        // TODO Implement method in root/branch to compare
        addChild(childNumber, edgeLoadContainer);
        return true;
    }

    /**
     * Creates an array of integers each corresponding to a node in the topology.
     * The array corresponds to the SR path of this leaf
     * @return an array of int corresponding to the SR path of the current leaf
     */
    public int[] getPath() {
        // Find the size of the SR path
        int length = 2;  // Starts at 2 because there is the current node and the branch node
        SegmentTreeLeaf nextParent = parent;
        while (nextParent != null) {
            length += 1;
            nextParent = nextParent.parent;
        }
        // Create the container for the SR path
        int[] path = new int[length];
        // Add origin (branch) end destination (this) nodes
        path[0] = branch.currentNodeNumber;
        path[length-1] = currentNodeNumber;

        // Fill the path with the nodes inside it
        nextParent = parent;
        int index = length-2;
        while (nextParent != null) {
            path[index] = nextParent.currentNodeNumber;
            nextParent = nextParent.parent;
        }
        return path;
    }
}
