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

    public void addChild(int childNumber, SegmentTreeLeaf child) {
        assert child.parent == this;
        children[childNumber] = child;
    }

    public boolean tryAddChild(){
        return true;
    }
}
