package edu.repetita.solvers.sr.srpp.edgeloads;

public class NodePair {
    final int source, dest;

    public NodePair(int source, int dest) {
        this.source = source;
        this.dest = dest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodePair)) return false;
        NodePair nodePair = (NodePair) o;
        return source == nodePair.source && dest == nodePair.dest;
    }

    @Override
    public int hashCode() {
        return source * 31 + dest;
    }
}
