package edu.repetita.solvers.sr.srpp;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Class containing a set of edges and a (non-zero) load for each of the present edges
 */
public class EdgeLoads {

    // Pair = <edgeNumber, edgeLoad>
    public final LinkedList<Pair<Integer, Double>> edges;

    /**
     * Constructor of the class
     * @param edgeLoads an array of double corresponding to the loads on each edge.
     */
    public EdgeLoads(double[] edgeLoads) {
        this.edges = new LinkedList<Pair<Integer, Double>>();
        for (int edge = 0; edge < edgeLoads.length; edge++) {
            if (edgeLoads[edge] != 0) {
                this.edges.add(new Pair<>(edge, edgeLoads[edge]));
            }
        }
    }

    private EdgeLoads() {
        this.edges = new LinkedList<Pair<Integer, Double>>();
    }

    /**
     * Adds another EdgeLoads object to this one.
     * @param otherLoads the EdgeLoads object to be added
     */
    public void add(EdgeLoads otherLoads) {
        ListIterator<Pair<Integer, Double>> thisIt = edges.listIterator();
        ListIterator<Pair<Integer, Double>> otherIt = otherLoads.edges.listIterator();
        Pair<Integer, Double> thisEdge, otherEdge;
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
                    thisIt.add(new Pair<>(otherEdge.getKey(), otherEdge.getValue()));
                    thisIt.next();
                    if (otherIt.hasNext()) {
                        otherEdge = otherIt.next();
                    } else {
                        break;
                    }
                }
                else { // equality case on the getKey() values
                    thisEdge.setValue(thisEdge.getValue() + otherEdge.getValue());
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
            thisIt.add(new Pair<>(otherEdge.getKey(), otherEdge.getValue()));
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
        ListIterator<Pair<Integer, Double>> firstIt = first.edges.listIterator();
        ListIterator<Pair<Integer, Double>> secondIt = second.edges.listIterator();
        Pair<Integer, Double> firstEdge, secondEdge;
        if (firstIt.hasNext() && secondIt.hasNext()) {
            firstEdge = firstIt.next();
            secondEdge = secondIt.next();
            while (true) {
                if (firstEdge.getKey() < secondEdge.getKey()) {
                    result.edges.add(new Pair<>(firstEdge.getKey(), firstEdge.getValue()));
                    if (firstIt.hasNext()) {
                        firstEdge = firstIt.next();
                    } else {
                        secondIt.previous();
                        break;
                    }
                }
                else if (firstEdge.getKey() > secondEdge.getKey()) {
                    result.edges.add(new Pair<>(secondEdge.getKey(), secondEdge.getValue()));
                    if (secondIt.hasNext()) {
                        secondEdge = secondIt.next();
                    } else {
                        firstIt.previous();
                        break;
                    }
                }
                else { // equality case on the getKey() values
                    result.edges.add(new Pair<>(firstEdge.getKey(), firstEdge.getValue() + secondEdge.getValue()));
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
            result.edges.add(new Pair<>(firstEdge.getKey(), firstEdge.getValue()));
        }
        while (secondIt.hasNext()) {
            secondEdge = secondIt.next();
            result.edges.add(new Pair<>(secondEdge.getKey(), secondEdge.getValue()));
        }
        return result;
    }


    public boolean dominates(EdgeLoads otherLoads) {
        return true;
    }
}
