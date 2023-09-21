package edu.repetita.solvers.sr.srpp.linearproblem;

public class Tuple implements Comparable<Tuple> {
    int start;
    int end;

    public Tuple(int startNode, int endNode) {
        this.start = startNode;
        this.end = endNode;
    }

    public int getStart() {
        return this.start;
    }

    public int getEnd() {
        return this.end;
    }

    @Override
    public int compareTo(Tuple o) {
        int res = this.start - o.start;
        if (res != 0) {
            return res;
        } else {
            return this.end - o.end;
        }
    }

    public boolean equals(Tuple other) {
        return this.start == other.start && this.end == other.end;
    }
}
