package edu.repetita.solvers.sr.srpp.segmenttree;

public class SegmentTreeLeaf {
    public final SegmentTreeBranch branch;
    // Number of the node to which this leaf corresponds.
    public final int currentNodeNumber;
    public final SegmentTreeLeaf parent;
    private final SegmentTreeLeaf[] children;
    protected final float[] edgeLoads;
    protected final int depth;

    public SegmentTreeLeaf(SegmentTreeBranch branch, SegmentTreeLeaf parent, int currentNodeNumber, float[] edgeLoads) {
        this(branch, parent, currentNodeNumber, edgeLoads, 1);
    }

    public SegmentTreeLeaf(SegmentTreeBranch branch, SegmentTreeLeaf parent, int currentNodeNumber, float[] edgeLoads, int depth) {
        this.branch = branch;
        this.parent = parent;
        this.currentNodeNumber = currentNodeNumber;
        children = new SegmentTreeLeaf[branch.root.nNodes];
        this.edgeLoads = edgeLoads;
        this.depth = depth;
    }

    /**
     * Adds a child to the current leaf
     * @param childNumber the node number in the topology of the newly added child
     * @param childEdgeLoads the loads on the edges of the newly added child, the array will be cloned.
     */
    private void addChild(int childNumber, float[] childEdgeLoads) {
        float[] newChildEdgeLoads = childEdgeLoads.clone();
        children[childNumber] = new SegmentTreeLeaf(branch, this, childNumber, newChildEdgeLoads, depth+1);
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
        SegmentTreeLeaf nextLeaf = this;
        while (nextLeaf != null) {
            if (childNumber == nextLeaf.currentNodeNumber){
                return false;
            }
            nextLeaf = nextLeaf.parent;
        }
        if (branch.currentNodeNumber == childNumber) {
            return false;
        }

        // Verify that path [(origin+1) ... dest] is already in tree
        int[] testingPath = getTestingPath(childNumber);
        if (!branch.root.pathInTree(testingPath)) {
            return false;
        }

        // Test if the path is dominated
        float[] edgeLoadsLastSegment = branch.root.getBranch(currentNodeNumber).getLeaf(childNumber).edgeLoads;
        for (int edgeNumber = 0; edgeNumber < branch.root.nEdges; edgeNumber++) {
            edgeLoadContainer[edgeNumber] = edgeLoadsLastSegment[edgeNumber] + this.edgeLoads[edgeNumber];
        }
        if (branch.isDominated(childNumber, edgeLoadContainer)) {
            return false;
        }
        addChild(childNumber, edgeLoadContainer);
        return true;
    }

    /**
     * Creates an array of integers each corresponding to a node in the topology.
     * The array corresponds to the SR path of this leaf plus the origin (remember that in principle the origin is not
     * part of the SR path).
     * @return an array of int corresponding to the SR path of the current leaf with the origin.
     */
    public int[] getPath() {
        int length = getPathLength()+1;
        // Create the container for the SR path
        int[] path = new int[length];
        // Add origin (branch) node
        path[0] = branch.currentNodeNumber;
        // Fill the path with the nodes inside it
        return fillPathWithLeavesNumbers(length-1, path);
    }

    /**
     * Creates an array of integers each corresponding to a node in the topology.
     * The array corresponds to the SR path of this leaf.
     * The only difference between this.getPath() and this.getPathNoOrigin() is the fact that the origin is present or not.
     * @return an array of int corresponding to the SR path of the current leaf.
     */
    public int[] getPathNoOrigin() {
        int length = getPathLength();
        // Create the container for the SR path
        int[] path = new int[length];
        // Fill the path with the nodes inside it
        return fillPathWithLeavesNumbers(length-1, path);
    }

    /**
     * Creates an array of integers each corresponding to a node in the topology.
     * The array itself corresponds to the SR path of the current node plus a new node lastNode added et the end.
     * This function is useful when trying to create new SR-paths, this helps us to test if the latter part of a new
     * SR-path we try to add is already itself an existing SR-path.
     * @param lastNode the number of the last node to be added
     * @return the path corresponding to the SR-path of the node + lastNode
     */
    public int[] getTestingPath(int lastNode) {
        int length = getPathLength()+1;
        // Create the container for the SR path
        int[] path = new int[length];
        // Add the last node
        path[length-1] = lastNode;
        // Fill the path with the nodes inside it
        return fillPathWithLeavesNumbers(length-2, path);
    }

    /**
     * Fills an array of int (nodes) with the current node number starting at the end of the array and then looping over
     * the parents adding them until there is no parent left
     * @param start the index of the array at which we start filling it
     * @param path the array to be filled
     * @return the path array after it is filled
     */
    private int[] fillPathWithLeavesNumbers(int start, int[] path) {
        // Add current leaf number to end of path
        path[start] = currentNodeNumber;
        // Loop over all parents and add them at the end
        SegmentTreeLeaf nextParent = parent;
        int index = start-1;
        while (nextParent != null) {
            path[index] = nextParent.currentNodeNumber;
            nextParent = nextParent.parent;
            index--;
        }
        return path;
    }

    /**
     * Get the length in node segments of the SR path of the current leaf.
     * Notice that the origin (branch) is not counted as it is not part of node segments.
     * @return the number of segment nodes on the SR-path corresponding to the current leaf
     */
    private int getPathLength() {
        // Find the size of the SR path
        int length = 1;  // Starts at 2 because we count the origin and current node
        SegmentTreeLeaf nextParent = parent;
        while (nextParent != null) {
            length += 1;
            nextParent = nextParent.parent;
        }
        return length;
    }

    /**
     * Get the leaf corresponding to childNumber as next node.
     * Will return null if not existing.
     * @param childNumber the node number corresponding to the leaf
     * @return The requested leaf
     */
    protected SegmentTreeLeaf getChild(int childNumber) {
        return children[childNumber];
    }
}
