package edu.repetita.solvers.sr.srpp.segmenttree;

// TODO make a branch on git with edge loads not stored for each SR path (should be longer computations, but might resolve memory problem on large instances)
// TODO merge branch and leaf classes

class SegmentTreeLeaf {
    public final int currentNodeNumber;
    public final int originNodeNumber;
    public final SegmentTreeLeaf parent;
    public final SegmentTreeRoot root;
    public final int depth;
    protected final SegmentTreeLeaf[] children;

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
            addArcLoadArrays(edgeLoads, resultLoads, edgeLoads);
            destLeaf = destLeaf.parent;
            originLeaf = originLeaf.parent;
        }
        return edgeLoads;
    }

    /* OK */
    public static void addArcLoadArrays(float[] firstArray, float[] secondArray, float[] resultArray) {
        for (int i = 0; i < firstArray.length; i++) {
            resultArray[i] = firstArray[i] + secondArray[i];
        }
    }

    /* OK */
    /**
     * Tries to extend the current leaf with SR paths if possible by trying to add every node at the end and testing
     * if the obtained path is dominated.
     */
    protected void extendSRPath() {
        float[] edgeLoads = getEdgeLoads();
        float[] container = new float[root.nEdges];
        boolean createdLoads = false;
        for (int lastNode = 0; lastNode < root.nNodes; lastNode++) {
            if (!isOnPath(lastNode) && root.pathInTree(getTestingPath(lastNode))) {
                if (!createdLoads) {
                    addArcLoadArrays(edgeLoads, root.getODLoads(currentNodeNumber, lastNode), container);
                    createdLoads = true;
                }
                if (root.testNewPathDomination(container, originNodeNumber, lastNode, depth+1)) {
                    addChild(lastNode);
                }
            }
        }
    }
    /* OK */
    /**
     * Tests if a node is already on the path of this leaf
     * @param nodeNumber
     * @return
     */
    protected boolean isOnPath(int nodeNumber) {
        SegmentTreeLeaf nextNode = this;
        while (nextNode != null) {
            if (nextNode.currentNodeNumber == nodeNumber) {
                return true;
            }
            nextNode = nextNode.parent;
        }
        return false;
    }

    /* OK */
    /**
     * Creates an array of integers each corresponding to a node in the topology.
     * The array corresponds to [origin+1], ..., this, lastNode
     * @param lastNode the node number of the last node
     * @return the corresponding SR-path
     */
    private int[] getTestingPath(int lastNode) {
        int[] path = new int[depth+1];
        path[depth] = lastNode;
        SegmentTreeLeaf nextNode = this;
        for (int varDepth = depth-1; varDepth >= 0; varDepth--) {
            path[varDepth] = nextNode.currentNodeNumber;
            nextNode = nextNode.parent;
        }
        return path;
    }

   /* OK */
    /**
     * Creates an array of integers each corresponding to a node in the topology.
     * The array corresponds to [origin], ..., this; which is the SR-path of this node
     * @return an array of int corresponding to the SR path of the current leaf with the origin.
     */
    public int[] getPath() {
        int[] path = new int[depth+1];
        SegmentTreeLeaf nextNode = this;
        for (int varDepth = depth; varDepth >= 0; varDepth--) {
            path[varDepth] = nextNode.currentNodeNumber;
            nextNode = nextNode.parent;
        }
        return path;
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
     * Deletes a child from the leaf by replacing it by null
     * @param childNumber  the node number of the child to be deleted
     */
    protected void deleteChild(int childNumber) {
        children[childNumber] = null;
    }
}
