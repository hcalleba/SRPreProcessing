package edu.repetita.solvers.sr.srpp.edgeloads;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Class containing a set of edges and a (non-zero) load for each of the present edges
 */
public class EdgeLoadsLinkedList implements Cloneable, Iterable<EdgePair> {

    private final LinkedList<EdgePair> edges;
    private static final double PRECISION = 0.000001;

    /**
     * Constructor of the class
     * @param edgeLoads an array of double corresponding to the loads on each edge.
     */
    public EdgeLoadsLinkedList(double[] edgeLoads) {
        this.edges = new LinkedList<>();
        for (int edge = 0; edge < edgeLoads.length; edge++) {
            if (edgeLoads[edge] != 0) {
                this.edges.add(new EdgePair(edge, edgeLoads[edge]));
            }
        }
    }

    /**
     * Constructor of the class
     * @param edgeNumber the unique edge for which the load will be set to one.
     */
    public EdgeLoadsLinkedList(int edgeNumber) {
        this.edges = new LinkedList<>();
        edges.add(new EdgePair(edgeNumber, 1));
    }

    private EdgeLoadsLinkedList() {
        this.edges = new LinkedList<>();
    }

    /**
     * Adds another EdgeLoads object to this one.
     * @param otherLoads the EdgeLoads object to be added
     */
    public void add(EdgeLoadsLinkedList otherLoads) {
        ListIterator<EdgePair> thisIt = edges.listIterator();
        ListIterator<EdgePair> otherIt = otherLoads.edges.listIterator();
        EdgePair thisEdge = thisIt.hasNext() ? thisIt.next() : null;
        EdgePair otherEdge = otherIt.hasNext() ? otherIt.next() : null;
        while (thisEdge != null && otherEdge != null) {
            if (thisEdge.getKey() < otherEdge.getKey()) {
                thisEdge = thisIt.hasNext() ? thisIt.next() : null;
            }
            else if (thisEdge.getKey() > otherEdge.getKey()) {
                thisIt.previous();
                thisIt.add(otherEdge.clone());
                thisIt.next();
                otherEdge = otherIt.hasNext() ? otherIt.next() : null;
            }
            else { // equality case on the getKey() values
                thisEdge.setLoad(thisEdge.getLoad() + otherEdge.getLoad());
                thisEdge = thisIt.hasNext() ? thisIt.next() : null;
                otherEdge = otherIt.hasNext() ? otherIt.next() : null;
            }
        }
        while (otherEdge != null) {
            thisIt.add(otherEdge.clone());
            otherEdge = otherIt.hasNext() ? otherIt.next() : null;
        }
    }

    /**
     * Adds two EdgeLoads together and returns a new EdgeLoad object
     * @param first the first term
     * @param second the second term
     * @return the newly created EdgeLoads object
     */
    public static EdgeLoadsLinkedList add(EdgeLoadsLinkedList first, EdgeLoadsLinkedList second) {
        EdgeLoadsLinkedList result = new EdgeLoadsLinkedList();
        ListIterator<EdgePair> firstIt = first.edges.listIterator();
        ListIterator<EdgePair> secondIt = second.edges.listIterator();
        EdgePair firstEdge = firstIt.hasNext() ? firstIt.next() : null;
        EdgePair secondEdge = secondIt.hasNext() ? secondIt.next() : null;
        while (firstEdge != null && secondEdge != null) {
            if (firstEdge.getKey() < secondEdge.getKey()) {
                result.edges.add(firstEdge.clone());
                firstEdge = firstIt.hasNext() ? firstIt.next() : null;
            }
            else if (firstEdge.getKey() > secondEdge.getKey()) {
                result.edges.add(secondEdge.clone());
                secondEdge = secondIt.hasNext() ? secondIt.next() : null;
            }
            else {
                result.edges.add(new EdgePair(firstEdge.getKey(), firstEdge.getLoad()+secondEdge.getLoad()));
                firstEdge = firstIt.hasNext() ? firstIt.next() : null;
                secondEdge = secondIt.hasNext() ? secondIt.next() : null;
            }
        }
        while (firstEdge != null) {
            result.edges.add(firstEdge.clone());
            firstEdge = firstIt.hasNext() ? firstIt.next() : null;
        }
        while (secondEdge != null) {
            result.edges.add(secondEdge.clone());
            secondEdge = secondIt.hasNext() ? secondIt.next() : null;
        }
        return result;
    }

    /**
     * Compares two EdgeLoads and returns true if this (EdgeLoads object) dominates otherLoads
     * @param otherLoads the other EdgeLoads object to be tested for domination
     * @return true if otherLoads are dominated by this
     */
    public boolean dominates(EdgeLoadsLinkedList otherLoads) {
        ListIterator<EdgePair> thisIt = this.edges.listIterator();
        ListIterator<EdgePair> otherIt = otherLoads.edges.listIterator();
        EdgePair thisEdge = thisIt.hasNext() ? thisIt.next() : null;
        EdgePair otherEdge = otherIt.hasNext() ? otherIt.next() : null;
        while (thisEdge != null && otherEdge != null) {
            if (thisEdge.getKey() < otherEdge.getKey()) {
                /* this uses an arc that otherLoads does not use -> otherLoads cannot be dominated by this */
                return false;
            }
            else if (thisEdge.getKey() > otherEdge.getKey()) {
                /* otherLoads uses an arc that this does not exist -> we continue the search */
                otherEdge = otherIt.hasNext() ? otherIt.next() : null;
            } else {
                /* otherLoads and this both use the current arc -> we compare the loads on the arc */
                if (otherEdge.getLoad()+PRECISION < thisEdge.getLoad()) {
                    return false;
                } else {
                    thisEdge = thisIt.hasNext() ? thisIt.next() : null;
                    otherEdge = otherIt.hasNext() ? otherIt.next() : null;
                }
            }
        }
        /*
         If thisEdge is null, it means we have processed all edges in "this", and that none of them was worse than any
         edge in otherLoads, so otherLoads is dominated, and we return true.
         On the other hand if thisEdge != null, since we broke out of the loop we know that otherEdge == null.
         This means that there exists an edge in "this" that is not used in otherLoads, so it cannot be dominated, and
         we return false
        */
        return thisEdge == null;
    }

    /**
     * Returns the load of an edge
     * @param edgeNumber the edge number
     * @return the load of edge edgeNumber
     */
    public double getLoad(int edgeNumber) {
        for (EdgePair obj : edges) {
            if (obj.getKey() == edgeNumber) {
                return obj.getLoad();
            } else if (obj.getKey() > edgeNumber) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Returns the number of edges used (with load != 0)
     * @return the number of edges used
     */
    public int size() {
        return edges.size();
    }

    @Override
    public EdgeLoadsLinkedList clone() {
        EdgeLoadsLinkedList result = new EdgeLoadsLinkedList();
        for (EdgePair pair : this.edges) {
            result.edges.add(pair.clone());
        }
        return result;
    }

    @Override
    public Iterator<EdgePair> iterator() {
        return edges.iterator();
    }
}
