package edu.repetita.solvers.sr.srpp.segmenttree;

public class SegmentTreeRoot {
    public final int nNodes;
    public final int nEdges;
    public final int maxSegments;
    private final SegmentTreeBranch[] branches;

    public SegmentTreeRoot(int nNodes, int nEdges, int maxSegments, float[][][] arcLoadPerPair) {
        this.nNodes = nNodes;
        this.nEdges = nEdges;
        this.maxSegments = maxSegments;
        branches = new SegmentTreeBranch[nNodes];

        for (int nodeNumber  = 0; nodeNumber < nNodes; nodeNumber++) {
            branches[nodeNumber] = new SegmentTreeBranch(this, nodeNumber, arcLoadPerPair);
        }
    }
}
