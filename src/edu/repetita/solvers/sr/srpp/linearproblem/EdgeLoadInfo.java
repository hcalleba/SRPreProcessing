package edu.repetita.solvers.sr.srpp.linearproblem;

public class EdgeLoadInfo implements Comparable<EdgeLoadInfo> {
    double load;
    int startNode;
    int endNode;
    public EdgeLoadInfo(double load, int startNode, int endNode){
        this.load = load;
        this.startNode = startNode;
        this.endNode = endNode;
    }

    @Override
    public int compareTo(EdgeLoadInfo o) {
        return Double.compare(this.load, o.load);
    }
}
