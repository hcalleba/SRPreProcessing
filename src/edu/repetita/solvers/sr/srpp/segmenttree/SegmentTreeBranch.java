package edu.repetita.solvers.sr.srpp.segmenttree;

import java.util.LinkedList;

public class SegmentTreeBranch {
    // Parent root structure
    public final SegmentTreeRoot root;
    // Number corresponding to the (origin) node of this branch in the topology
    public final int currentNodeNumber;
    private final SegmentTreeLeaf[] leaves;
    // For each destination node, the list will contain all paths to that node
    private final LinkedList<SegmentTreeLeaf>[] pathsToDestination;

    public SegmentTreeBranch(SegmentTreeRoot root, int currentNodeNumber, float[][][] arcLoadPerPair) {
        this.root = root;
        this.currentNodeNumber = currentNodeNumber;
        leaves = new SegmentTreeLeaf[root.nNodes];
        pathsToDestination = new LinkedList[root.nNodes];

        // Initialisation of 1-SR (=OSPF) path for each destination node.
        for (int nodeNumber = 0; nodeNumber < root.nNodes; nodeNumber++) {
            if (nodeNumber == currentNodeNumber) {
                leaves[nodeNumber] = null;
            }
            else {
                // Remember that arcLoadPerPair works in the following way: [dest][origin][arc]
                leaves[nodeNumber] = new SegmentTreeLeaf(this, null, nodeNumber, arcLoadPerPair[nodeNumber][currentNodeNumber]);
            }
        }
    }

    public void addPathToDestination(SegmentTreeLeaf path) {
        pathsToDestination[path.currentNodeNumber].add(path);
    }
}
