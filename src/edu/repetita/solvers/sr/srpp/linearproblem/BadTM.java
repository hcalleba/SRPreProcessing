package edu.repetita.solvers.sr.srpp.linearproblem;

import java.util.Arrays;
import java.util.Collections;

public class BadTM {
    Tuple[] tuples;

    public BadTM(Tuple[] tuples) {
        java.util.Arrays.sort(tuples);
        this.tuples = tuples;
    }

    public BadTM(int n) {
        this.tuples = new Tuple[n];
    }

    public void add (int start, int end, int index) {
        this.tuples[index] = new Tuple(start, end);
    }

    public int getStart(int index) {
        return this.tuples[index].getStart();
    }

    public int getEnd(int index) {
        return this.tuples[index].getEnd();
    }

    public void sort() {
        java.util.Arrays.sort(this.tuples);
    }

    public boolean equals(BadTM other) {
        for (int i = 0; i < this.tuples.length; i++) {
            if (!this.tuples[i].equals(other.tuples[i])) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Tuple t : this.tuples) {
            sb.append("Demand: " + t.start + " to " + t.end + "\n");
        }
        return sb.toString();
    }
}
