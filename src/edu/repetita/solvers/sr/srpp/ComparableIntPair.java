package edu.repetita.solvers.sr.srpp;

public class ComparableIntPair implements Comparable<ComparableIntPair> {
    public final int index;
    public final int value;

    public ComparableIntPair(int index, int value) {
        this.index = index;
        this.value = value;
    }

    @Override
    public int compareTo(ComparableIntPair other) {
        return Integer.valueOf(this.value).compareTo(other.value);
    }
}