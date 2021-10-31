// Warning this class cannot be used as such since it does not have in place addition

package edu.repetita.solvers.sr.srpp.edgeloads;

/**
 * Class containing a set of edges and a (non-zero) load for each of the present edges
 */
public class EdgeLoadsArray implements Cloneable {
    public final int size;
    public final EdgePair[] edges;
    private static final double PRECISION = 0.000001;

    /**
     * Constructor of the class
     * @param edgeLoads an array of double corresponding to the loads on each edge.
     */
    public EdgeLoadsArray(double[] edgeLoads) {
        int usedEdges = 0;
        for (int edgeNumber = 0; edgeNumber < edgeLoads.length; edgeNumber++) {
            if (edgeLoads[edgeNumber] != 0) {
                usedEdges++;
            }
        }
        this.size = usedEdges;
        this.edges = new EdgePair[size];

        usedEdges = 0;
        for (int edgeNumber = 0; edgeNumber < edgeLoads.length; edgeNumber++) {
            if (edgeLoads[edgeNumber] != 0) {
                this.edges[usedEdges] = new EdgePair(edgeNumber, edgeLoads[edgeNumber]);
                usedEdges++;
            }
        }
    }

    private EdgeLoadsArray(int size) {
        this.size = size;
        this.edges = new EdgePair[size];
    }

    public static EdgeLoadsArray add(EdgeLoadsArray first, EdgeLoadsArray second) {
        int usedEdges = getUsedEdges(first, second);
        EdgeLoadsArray result = new EdgeLoadsArray(usedEdges);
        addInContainer(first, second, result);
        return result;
    }

    private static int getUsedEdges(EdgeLoadsArray first, EdgeLoadsArray second) {
        int usedEdges = 0, idx1 = 0, idx2 = 0;
        while (idx1 < first.size && idx2 < second.size) {
            if (first.edges[idx1].getKey() < second.edges[idx2].getKey()) {
                idx1++;
            } else if (first.edges[idx1].getKey() > second.edges[idx2].getKey()) {
                idx2++;
            } else {
                idx1++;
                idx2++;
            }
            usedEdges++;
        }
        while (idx1 < first.size) {
            idx1++;
            usedEdges++;
        }
        while (idx2 < second.size) {
            idx2++;
            usedEdges++;
        }
        return usedEdges;
    }

    private static void addInContainer(EdgeLoadsArray first, EdgeLoadsArray second, EdgeLoadsArray container) {
        int usedEdges = 0, idx1 = 0, idx2 = 0;
        while (idx1 < first.size && idx2 < second.size) {
            if (first.edges[idx1].getKey() < second.edges[idx2].getKey()) {
                container.edges[usedEdges] = first.edges[idx1].clone();
                idx1++;
            } else if (first.edges[idx1].getKey() > second.edges[idx2].getKey()) {
                container.edges[usedEdges] = second.edges[idx2].clone();
                idx2++;
            } else {
                container.edges[usedEdges] = new EdgePair(first.edges[idx1].getKey(),
                        first.edges[idx1].getLoad()+second.edges[idx2].getLoad());
                idx1++;
                idx2++;
            }
            usedEdges++;
        }
        while (idx1 < first.size) {
            container.edges[usedEdges] = first.edges[idx1].clone();
            idx1++;
            usedEdges++;
        }
        while (idx2 < second.size) {
            container.edges[usedEdges] = second.edges[idx2].clone();
            idx2++;
            usedEdges++;
        }
    }

    public boolean dominates(EdgeLoadsArray otherLoads) {
        int idx1 = 0, idx2 = 0;
        while (idx1 < this.size && idx2 < otherLoads.size) {
            if (this.edges[idx1].getKey() < otherLoads.edges[idx2].getKey()) {
                /* this uses an arc that otherLoads does not use -> otherLoads cannot be dominated by this */
                return false;
            }
            else if (this.edges[idx1].getKey() > otherLoads.edges[idx2].getKey()) {
                /* otherLoads uses an arc that this does not exist -> we continue the search */
                idx2++;
            }
            else {
                /* otherLoads and this both use the current arc -> we compare the loads on the arc */
                if (otherLoads.edges[idx2].getLoad()+PRECISION < this.edges[idx1].getLoad()) {
                    return false;
                } else {
                    idx1++;
                    idx2++;
                }
            }
        }
        /*
         If idx1 == this.size, it means we have processed all edges in "this", and that none of them was worse than any
         edge in otherLoads, so otherLoads is dominated, and we return true.
         On the other hand if idx1 == this.size, since we broke out of the loop we know that idx2 == otherLoads.size.
         This means that there exists an edge in "this" that is not used in otherLoads, so it cannot be dominated, and
         we return false
        */
        return idx1 == this.size;
    }

    @Override
    public EdgeLoadsArray clone() {
        EdgeLoadsArray result = new EdgeLoadsArray(this.size);
        for (int i = 0; i < this.size; i++) {
            result.edges[i] = this.edges[i].clone();
        }
        return result;
    }
}
