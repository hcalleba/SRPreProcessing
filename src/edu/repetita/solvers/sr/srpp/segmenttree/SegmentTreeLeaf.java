package edu.repetita.solvers.sr.srpp.segmenttree;

import edu.repetita.solvers.sr.srpp.edgeloads.EdgeLoadsLinkedList;
import edu.repetita.solvers.sr.srpp.edgeloads.EdgePair;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Class corresponding to a leaf of the tree containing all SR-paths.
 * Each leaf corresponds to an SR-path (except the first leaf which corresponds to the origin node).
 * The SR-path can be found as follows;
 * Starting from "this" leaf, currentNodeNumber is the destination node, parent.currentNodeNumber is the previous node
 * in the SR-path and we continue like this until parent.parent. ... .parent is null; then this means that the last
 * currentNodeNumber found was in fact the origin of the SR-path.
 */
class SegmentTreeLeaf {
    public final int currentNodeNumber;  // If >= nNodes, then corresponds to edge currentNodeNumber - nNodes
    public final int originNodeNumber;
    public final SegmentTreeLeaf parent;
    public final SegmentTreeRoot root;
    public final int depth;
    protected final SegmentTreeLeaf[] children;
    protected final EdgeLoadsLinkedList edgeLoads;
    protected final LinkedList<SegmentTreeLeaf> adjacencyChildren;

    /**
     * Constructor of a leaf. This constructor is called from the root and therefore creates an "origin leaf" for the
     * following SR-paths.
     * @param currentNodeNumber The node number assigned to this leaf (the node number refers to the number in the Topology)
     * @param root The root of the segment path's tree
     */
    protected SegmentTreeLeaf(int currentNodeNumber, SegmentTreeRoot root) {
        this.currentNodeNumber = currentNodeNumber;
        this.originNodeNumber = currentNodeNumber;
        this.parent = null;
        this.root = root;
        this.depth = 0;
        this.children = new SegmentTreeLeaf[root.nNodes];
        this.edgeLoads = null;
        // Create all 1-SR (OSPF) paths
        for (int nodeNumber = 0; nodeNumber < root.nNodes; nodeNumber++) {
            if (nodeNumber != currentNodeNumber) {
                addChild(nodeNumber, root.edgeLoadPerPair[currentNodeNumber][nodeNumber]);
            }
        }
        /* Add all non dominated adjacency segments that start in currentNodeNumber */
        this.adjacencyChildren = new LinkedList<>();
        LinkedList<Integer>[] parallelLinksHelper = new LinkedList[root.nNodes]; // Helper structure to test if some links
            // are parallel with same weight and capacity and which are the only links on the shortest path between origin
            // and destination. They can then be removed despite not being dominated because ECMP will always be better.
        for (int edgeNumber = 0; edgeNumber < root.nEdges; edgeNumber++){
            if (root.edgeSrc[edgeNumber] == currentNodeNumber) {
                int destNode = root.edgeDest[edgeNumber];
                EdgeLoadsLinkedList nodeSegmentLoads = root.edgeLoadPerPair[currentNodeNumber][destNode];
                EdgeLoadsLinkedList adjacencySegmentLoads = new EdgeLoadsLinkedList(edgeNumber);
                if (!nodeSegmentLoads.dominates(adjacencySegmentLoads)) {
                    if (parallelLinksHelper[destNode] == null){
                        parallelLinksHelper[destNode] = new LinkedList<>();
                    }
                    parallelLinksHelper[destNode].add(edgeNumber);
                }
            }
        }
        for (int destNode = 0; destNode < root.nNodes; destNode++){
            if (parallelLinksHelper[destNode] != null) {
                if (parallelLinksHelper[destNode].size() == 1) {
                    // If there is only 1 we add it because it means the node segment takes a path different from the unique adjacency segment
                    int edgeNumber = parallelLinksHelper[destNode].getFirst();
                    addChild(edgeNumber + root.nNodes, new EdgeLoadsLinkedList(edgeNumber));
                } else if (parallelLinksHelper[destNode].size() > 1) {
                    // If there are multiple we must test if they are all equal and uniquely on the shortest path, if not we add all of them
                    int firstEdgeNumber = parallelLinksHelper[destNode].getFirst();
                    int firstWeight = root.edgeWeights[firstEdgeNumber];
                    double firstCapacity = root.edgeCapacity[firstEdgeNumber];
                    Iterator<Integer> adjacencyIterator = parallelLinksHelper[destNode].iterator();
                    Iterator<EdgePair> nodeIterator = root.edgeLoadPerPair[currentNodeNumber][destNode].iterator();
                    Integer edgeNumberAdjacency = adjacencyIterator.next();
                    int edgeNumberNode = nodeIterator.next().getKey();
                    boolean allEqual = edgeNumberNode == edgeNumberAdjacency;
                    while (adjacencyIterator.hasNext() && allEqual) {
                        edgeNumberAdjacency = adjacencyIterator.next();
                        edgeNumberNode = nodeIterator.next().getKey();
                        // Test if weight and capacity are equal
                        allEqual = root.edgeWeights[edgeNumberAdjacency] == firstWeight && root.edgeCapacity[edgeNumberAdjacency] == firstCapacity;
                        // Test if the same edges appear in the "node path", if not they are not all on the shortest path or
                        // there is another edge different from the identical adjacent ones that is on the path and we need
                        // to add all the edges.
                        allEqual = allEqual && (edgeNumberNode == edgeNumberAdjacency);
                    }
                    if (!allEqual || nodeIterator.hasNext()) {
                        for (Integer edgeNumber : parallelLinksHelper[destNode]) {
                            addChild(edgeNumber + root.nNodes, new EdgeLoadsLinkedList(edgeNumber));
                        }
                    }
                }
            }
        }
    }

    /**
     * Constructor of a leaf. This constructor is called from another leaf passing itself as parent argument.
     * @param currentNodeNumber The node number attributed to this leaf
     * @param parent The leaf that will become the parent of the newly created leaf (should in principle also be the one calling this constructor)
     * @param edgeLoads The loads on the edges
     */
    private SegmentTreeLeaf(int currentNodeNumber, SegmentTreeLeaf parent, EdgeLoadsLinkedList edgeLoads) {
        this.currentNodeNumber = currentNodeNumber;
        this.originNodeNumber = parent.originNodeNumber;
        this.parent = parent;
        this.root = parent.root;
        this.depth = parent.depth+1;
        if (depth == root.maxSegments) {
            this.children = null;
        } else {
            this.children = new SegmentTreeLeaf[root.nNodes];
        }
        this.edgeLoads = edgeLoads;
        this.adjacencyChildren = new LinkedList<>();
    }

    /**
     * Adds a child with node number = childNumber to the current leaf
     * @param childNumber the node number in the topology of the newly added child
     */
    private void addChild(int childNumber, EdgeLoadsLinkedList edgeLoads) {
        SegmentTreeLeaf child = new SegmentTreeLeaf(childNumber, this, edgeLoads);
        if (childNumber < root.nNodes) {
            children[childNumber] = child;
            root.addLeafToList(children[childNumber]);
        }
        else {
            adjacencyChildren.add(child);
            root.addLeafToList(child);
        }
    }

    /**
     * Tries to extend the tree with new SR paths by either :
     * 1. Adding every possible node as new segment node at the end of "this" leaf and testing if the obtained path is
     * dominated. (done if this.depth == depth-1).
     * or
     * 2. calling recursively this function on all of its children.
     * @param depth The depth of the new paths we want to try to add
     */
    protected void extendSRPath(int depth) {
        if (this.depth < depth-1) {
            // Recursive call if not at the correct depth
            for (int nextNode = 0; nextNode < root.nNodes; nextNode++) {
                if (children[nextNode] != null) {
                    children[nextNode].extendSRPath(depth);
                }
            }
            for (SegmentTreeLeaf adjacencyChild : adjacencyChildren) {
                adjacencyChild.extendSRPath(depth);
            }
        }
        else { // Try to add all possible segments at the end
            EdgeLoadsLinkedList result;
            SegmentTreeLeaf leafToSubPath = root.getLeafFromPath(getTestingPath());
            for (SegmentTreeLeaf child : leafToSubPath.children) {
                if (child != null && originNodeNumber != child.currentNodeNumber) {
                    result = EdgeLoadsLinkedList.add(edgeLoads, root.getODLoads(currentNodeNumber, child.currentNodeNumber));
                    if (!root.testNewPathDomination(result, originNodeNumber, child.currentNodeNumber, this.depth+1)) {
                        addChild(child.currentNodeNumber, result);
                    }
                }
            }
            for (SegmentTreeLeaf child : leafToSubPath.adjacencyChildren) {
                int edgeNumber = child.currentNodeNumber - root.nNodes;
                int destNode = root.edgeDest[edgeNumber];
                if (originNodeNumber != destNode) {
                    result = EdgeLoadsLinkedList.add(edgeLoads, new EdgeLoadsLinkedList(edgeNumber));
                    if (!root.testNewPathDomination(result, originNodeNumber, destNode, this.depth+1)) {
                        addChild(child.currentNodeNumber, result);
                    }
                }
            }
        }
    }

    /**
     * Returns an EdgeLoadsLinkedList object corresponding to the edge loads if a demand of 1 was routed along "this"
     * leaf's SR-path
     * @return The EdgeLoadsLinkedList object with the corresponding edge loads
     */
    public EdgeLoadsLinkedList getEdgeLoads() {
        return edgeLoads;
    }

    /**
     * Tests if a node segment is on the SR-path corresponding to "this" leaf.
     * @param nodeNumber the node segment to test for existence on the current path.
     * @return true if the node segment is already on the path, false otherwise
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

    /**
     * Creates an array of integers each corresponding to a node in the topology.
     * The array corresponds to [(origin+1).currentNodeNumber, ..., this.currentNodeNumber, lastNode]
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

    /**
     * Creates an array of integers each corresponding to a node in the topology.
     * The array corresponds to [(origin+1).currentNodeNumber, ..., this.currentNodeNumber]
     * @return the corresponding SR-path
     */
    private int[] getTestingPath() {
        int[] path = new int[depth];
        SegmentTreeLeaf nextNode = this;
        for (int varDepth = depth-1; varDepth >= 0; varDepth--) {
            path[varDepth] = nextNode.currentNodeNumber;
            nextNode = nextNode.parent;
        }
        // If first segment is an adjacency segment replace it by corresponding node segment
        if (path[0] >= root.nNodes) {
            path[0] = root.edgeDest[path[0] - root.nNodes];
        }
        return path;
    }

    /**
     * Creates an array of integers each corresponding to a node in the topology.
     * The array corresponds to [origin.currentNodeNumber, ..., this.currentNodeNumber]; which is the SR-path of this node
     * @return an array of int corresponding to the SR path of the current leaf with the origin.
     */
    protected int[] getPath() {
        int[] path = new int[depth+1];
        SegmentTreeLeaf nextNode = this;
        for (int varDepth = depth; varDepth >= 0; varDepth--) {
            path[varDepth] = nextNode.currentNodeNumber;
            nextNode = nextNode.parent;
        }
        return path;
    }

    /**
     * Deletes a leaf and all of its children by setting the corresponding children in the parent leaf to null
     */
    protected void delete() {
        if (currentNodeNumber < root.nNodes) {
            parent.children[currentNodeNumber] = null;
        }
        else {
            parent.adjacencyChildren.remove(this);
        }
    }
}
