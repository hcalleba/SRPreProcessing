package edu.repetita.solvers.sr.srpp.segmenttree;

// TODO make a branch on git with edge loads not stored for each SR path (should be longer computations, but might resolve memory problem on large instances)
// TODO merge branch and leaf classes

class SegmentTreeLeaf {
    public final int currentNodeNumber;
    public final int originNodeNumber;
    public final SegmentTreeLeaf parent;
    public final SegmentTreeRoot root;
    public final int depth;
    private final SegmentTreeLeaf[] children;

    /* OK */
    public SegmentTreeLeaf(int currentNodeNumber, SegmentTreeRoot root) {
        this.currentNodeNumber = currentNodeNumber;
        this.originNodeNumber = currentNodeNumber; // TODO is this really useful ?
        this.parent = null;
        this.root = root;
        this.depth = 0;
        this.children = new SegmentTreeLeaf[root.nNodes];
        // Create all 1-SR (OSPF) paths
        for (int nodeNumber = 0; nodeNumber < root.nNodes; nodeNumber++) {
            if (nodeNumber != currentNodeNumber) {
                addChild(nodeNumber);
            }
        }
    }

    /* OK */
    public SegmentTreeLeaf(int currentNodeNumber, SegmentTreeLeaf parent) {
        this.currentNodeNumber = currentNodeNumber;
        this.originNodeNumber = parent.originNodeNumber;
        this.parent = parent;
        this.root = parent.root;
        this.depth = parent.depth+1;
        this.children = new SegmentTreeLeaf[root.nNodes];
    }

    /* OK */
    /**
     * Adds a child with node number = childNumber to the current leaf
     * @param childNumber the node number in the topology of the newly added child
     */
    private void addChild(int childNumber) {
        children[childNumber] = new SegmentTreeLeaf(childNumber, this);
        root.addLeafToList(children[childNumber]);
    }

    /* OK */
    public float[] getEdgeLoads() {
        float[] edgeLoads = new float[root.nEdges];
        SegmentTreeLeaf destLeaf = this;
        SegmentTreeLeaf originLeaf = this.parent;
        while (originLeaf != null ) {
            float[] resultLoads = root.getODLoads(originLeaf.currentNodeNumber, destLeaf.currentNodeNumber);
            addLoadArrays(edgeLoads, resultLoads, edgeLoads);
            destLeaf = destLeaf.parent;
            originLeaf = originLeaf.parent;
        }
        return edgeLoads;
    }

    /* OK */
    public static void addLoadArrays(float[] firstArray, float[] secondArray, float[] resultArray) {
        for (int i = 0; i < firstArray.length; i++) {
            resultArray[i] = firstArray[i] + secondArray[i];
        }
    }

    /**
     * Tries to extend the current leaf with SR paths if possible by trying to add every node at the end and testing
     * if the obtained path is dominated.
     */
    protected void extendSRPath() {
        float[] edgeLoads = getEdgeLoads();
        float[] container = new float[root.nEdges];
        for (int lastNode = 0; lastNode < root.nNodes; lastNode++) {
            addLoadArrays(edgeLoads, root.getODLoads(currentNodeNumber, lastNode), container);
            // test if container is dominated by any of the paths
            // originNodeNumber -> lastNode:
        }
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
        // TODO can do better since I know I will call tryAddChild() multiple times on the same leaf
        float[] edgeLoadsLastSegment = branch.root.getODLoads(currentNodeNumber, childNumber);
        float[] edgeLoadsAllButLastSegment = getEdgeLoads();
        for (int edgeNumber = 0; edgeNumber < branch.root.nEdges; edgeNumber++) {
            edgeLoadContainer[edgeNumber] = edgeLoadsLastSegment[edgeNumber] + edgeLoadsAllButLastSegment[edgeNumber];
        }
        if (branch.isDominated(childNumber, edgeLoadContainer, depth)) {
            return false;
        }
        addChild(childNumber);
        return true;
    }

    /**
     * Creates an array of integers each corresponding to a node in the topology.
     * The array corresponds to the SR path of this leaf plus the origin (remember that in principle the origin is not
     * part of the SR path).
     * @return an array of int corresponding to the SR path of the current leaf with the origin.
     */
    public int[] getPath() {
        int length = depth+1;
        // Create the container for the SR path
        int[] path = new int[length];
        // Add origin (branch) node
        path[0] = branch.currentNodeNumber;
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
        int length = depth+1;
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
     * Get the leaf corresponding to childNumber as next node.
     * Will return null if not existing.
     * @param childNumber the node number corresponding to the leaf
     * @return The requested leaf
     */
    protected SegmentTreeLeaf getChild(int childNumber) {
        return children[childNumber];
    }

    /**
     * Deletes a child from the leaf by replacing it by null
     * @param childNumber  the node number of the child to be deleted
     */
    protected void deleteChild(int childNumber) {
        children[childNumber] = null;
    }
}
