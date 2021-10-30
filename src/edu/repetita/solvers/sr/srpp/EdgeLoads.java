package edu.repetita.solvers.sr.srpp;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Class containing a set of edges and a (non-zero) load for each of the present edges
 */
public class EdgeLoads implements Cloneable {

    // Pair = <edgeNumber, edgeLoad>
    private final LinkedList<EdgePair> edges;
    private static final double PRECISION = 0.000001;

    /**
     * Constructor of the class
     * @param edgeLoads an array of double corresponding to the loads on each edge.
     */
    public EdgeLoads(double[] edgeLoads) {
        this.edges = new LinkedList<>();
        for (int edge = 0; edge < edgeLoads.length; edge++) {
            if (edgeLoads[edge] != 0) {
                this.edges.add(new EdgePair(edge, edgeLoads[edge]));
            }
        }
    }

    private EdgeLoads() {
        this.edges = new LinkedList<>();
    }

    /**
     * Adds another EdgeLoads object to this one.
     * @param otherLoads the EdgeLoads object to be added
     */
    public void add(EdgeLoads otherLoads) {
        ListIterator<EdgePair> thisIt = edges.listIterator();
        ListIterator<EdgePair> otherIt = otherLoads.edges.listIterator();
        EdgePair thisEdge, otherEdge;
        if (thisIt.hasNext() && otherIt.hasNext()) {
            thisEdge = thisIt.next();
            otherEdge = otherIt.next();
            while (true) {
                if (thisEdge.getKey() < otherEdge.getKey()) {
                    if (thisIt.hasNext()) {
                        thisEdge = thisIt.next();
                    } else {
                        otherIt.previous();
                        break;
                    }
                }
                else if (thisEdge.getKey() > otherEdge.getKey()) {
                    thisIt.previous();
                    thisIt.add(new EdgePair(otherEdge.getKey(), otherEdge.getLoad()));
                    thisIt.next();
                    if (otherIt.hasNext()) {
                        otherEdge = otherIt.next();
                    } else {
                        break;
                    }
                }
                else { // equality case on the getKey() values
                    thisEdge.setLoad(thisEdge.getLoad() + otherEdge.getLoad());
                    if (thisIt.hasNext() && otherIt.hasNext()) {
                        thisEdge = thisIt.next();
                        otherEdge = otherIt.next();
                    } else {
                        break;
                    }
                }
            }
        }
        while (otherIt.hasNext()) {
            otherEdge = otherIt.next();
            thisIt.add(new EdgePair(otherEdge.getKey(), otherEdge.getLoad()));
        }
    }

    /**
     * Adds two EdgeLoads together and returns a new EdgeLoad object
     * @param first the first term
     * @param second the second term
     * @return the newly created EdgeLoads object
     */
    public static EdgeLoads add(EdgeLoads first, EdgeLoads second) {
        EdgeLoads result = new EdgeLoads();
        ListIterator<EdgePair> firstIt = first.edges.listIterator();
        ListIterator<EdgePair> secondIt = second.edges.listIterator();
        EdgePair firstEdge, secondEdge;
        if (firstIt.hasNext() && secondIt.hasNext()) {
            firstEdge = firstIt.next();
            secondEdge = secondIt.next();
            while (true) {
                if (firstEdge.getKey() < secondEdge.getKey()) {
                    result.edges.add(new EdgePair(firstEdge.getKey(), firstEdge.getLoad()));
                    if (firstIt.hasNext()) {
                        firstEdge = firstIt.next();
                    } else {
                        secondIt.previous();
                        break;
                    }
                }
                else if (firstEdge.getKey() > secondEdge.getKey()) {
                    result.edges.add(new EdgePair(secondEdge.getKey(), secondEdge.getLoad()));
                    if (secondIt.hasNext()) {
                        secondEdge = secondIt.next();
                    } else {
                        firstIt.previous();
                        break;
                    }
                }
                else { // equality case on the getKey() values
                    result.edges.add(new EdgePair(firstEdge.getKey(), firstEdge.getLoad() + secondEdge.getLoad()));
                    if (firstIt.hasNext() && secondIt.hasNext()) {
                        firstEdge = firstIt.next();
                        secondEdge = secondIt.next();
                    } else {
                        break;
                    }
                }
            }
        }
        while (firstIt.hasNext()) {
            firstEdge = firstIt.next();
            result.edges.add(new EdgePair(firstEdge.getKey(), firstEdge.getLoad()));
        }
        while (secondIt.hasNext()) {
            secondEdge = secondIt.next();
            result.edges.add(new EdgePair(secondEdge.getKey(), secondEdge.getLoad()));
        }
        return result;
    }

    /**
     * Compares two EdgeLoads and returns true if this (EdgeLoads object) dominates otherLoads
     * @param otherLoads the other EdgeLoads object to be tested for domination
     * @return true if otherLoads are dominated by this
     */
    public boolean dominates(EdgeLoads otherLoads) {
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
                /* otherLoads and this both use the current arc -> we compare the loads */
                if (otherEdge.getLoad()+PRECISION < thisEdge.getLoad()) {
                    return false;
                } else {
                    thisEdge = thisIt.hasNext() ? thisIt.next() : null;
                    otherEdge = otherIt.hasNext() ? otherIt.next() : null;
                }
            }
        }
        if (thisEdge == null && otherEdge == null) {
            /*
             All processed edges from otherEdges are at least worse than the ones from this and there are no edges
             left to process; otherLoads is therefore dominated by this
            */
            return true;
        } else if (thisEdge == null) {
            /*
             All edges from this have been processed, and they are not worse than any of the edges from otherLoads;
             otherLoads is therefore dominated;
            */
            return true;
        } else {
            /*
             There are edges from this not yet processed, but all edges from otherLoads have been processed.
             These edges from this are therefore strictly worse than the ones from otherLoads;
             otherLoads is therefore non-dominated
            */
            return false;
        }
    }

    @Override
    public EdgeLoads clone() {
        EdgeLoads result = new EdgeLoads();
        for (EdgePair pair : this.edges) {
            result.edges.add(new EdgePair(pair.getKey(), pair.getLoad()));
        }
        return result;
    }
}
