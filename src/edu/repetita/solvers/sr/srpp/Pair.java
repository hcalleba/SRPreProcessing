package edu.repetita.solvers.sr.srpp;

public class Pair<T,U> {
    private final T key;
    private U value;

    public Pair(T key, U value) {
        this.key = key;
        this.value = value;
    }

    public T getKey() {
        return this.key;
    }

    public U getValue() {
        return this.value;
    }

    public void setValue(U newVal) {
        this.value = newVal;
    }
}
