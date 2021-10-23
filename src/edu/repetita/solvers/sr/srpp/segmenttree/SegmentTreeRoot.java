package edu.repetita.solvers.sr.srpp.segmenttree;

import edu.repetita.core.Demands;

import java.util.LinkedList;

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
    public SegmentTreeBranch getBranch(int branchNumber) {
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
        int[][] allPaths = new int[numberOfPaths][];
        // TODO depthfirst search in the tree to get the paths
        return new int[0][0];
    }

    /**
     * Creates all SR paths up to maxSegments for all origin destination pairs in the Topology.
     */
    public void createODPaths() {
        // TODO maybe add demands parameter ?
        // TODO
        // Should maybe add a reference to first item in LinkedList with currentMaxSegmentsProcessed
    }
}
