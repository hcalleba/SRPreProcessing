package edu.repetita.solvers.sr.srpp.segmenttree;

import edu.repetita.core.Demands;
import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;
import edu.repetita.solvers.sr.srpp.ComparableIntPair;
import edu.repetita.solvers.sr.srpp.edgeloads.EdgeLoadsLinkedList;
import edu.repetita.utils.datastructures.Pair;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Class corresponding to the root of the tree containing the leaves (each leaf corresponding to an SR-path) and
 * information over the topology.
 * The name "root" is probably poorly chosen as it is just a container for some information of the topology and
 * the SegmentTreeLeaf objects.
 * The origin node of a sr-path is itself a SegmentTreeLeaf object and not a SegmentTreeRoot object.
 */
public class SegmentTreeRoot {
    public final int nNodes;
    public final int nEdges;
    public final int maxSegments;
    private SegmentTreeLeaf[] leaves;
    public final EdgeLoadsLinkedList[][] edgeLoadPerPair;
    public final double[][] trafficMatrix;
    public final int[] edgeSrc;
    public final int[] edgeDest;
    public final int[] edgeWeights;
    public final double[] edgeCapacity;
    public final int[][] distancePerPair;
    /*
     For each origin destination pair, the list ODPaths[origin][destination] contains pointers to all the non-dominated
     leaves having origin and destination respectively as origin and destination nodes
    */
    private LinkedList<SegmentTreeLeaf>[][] ODPaths;

    /**
     * Constructor for the root of the SegmentTree
     * @param topology the topology on which the tree will be built
     * @param maxSegments the maximum number of (node) segments of each SR-path
     */
    public SegmentTreeRoot(Topology topology, int maxSegments, Demands demands) {
        this.nNodes = topology.nNodes;
        this.nEdges = topology.nEdges;
        this.maxSegments = maxSegments;
        this.leaves = new SegmentTreeLeaf[nNodes];
        this.ODPaths = new LinkedList[nNodes][nNodes];
        if (demands != null) {
            this.trafficMatrix = Demands.toTrafficMatrix(demands, nNodes);
        } else {
            this.trafficMatrix = null;
        }

        Pair<double[][][], int[][]> tempPair = makeEdgeLoadPerPair(topology);
        double [][][] tempLoads = tempPair.a;
        distancePerPair = tempPair.b;
        this.edgeLoadPerPair = new EdgeLoadsLinkedList[nNodes][nNodes];
        for (int destNode = 0; destNode < nNodes; destNode++) {
            for (int originNode = 0; originNode < nNodes; originNode++) {
                this.edgeLoadPerPair[originNode][destNode] = new EdgeLoadsLinkedList(tempLoads[destNode][originNode]);
            }
        }

        for (int originNode  = 0; originNode < nNodes; originNode++) {
            for (int destNode = 0; destNode < nNodes; destNode++) {
                ODPaths[originNode][destNode] = new LinkedList<>();
            }
        }
        this.edgeSrc = topology.edgeSrc;
        this.edgeDest = topology.edgeDest;
        this.edgeWeights = topology.edgeWeight;
        this.edgeCapacity = topology.edgeCapacity;
    }

    /**
     * Creates the matrix edgeLoadPair[|N|][|N|][|A|] of double
     * For each triplet (U,V,e); U,V nodes and e an edge;
     * edgeLoadPerPair[U][V][e] is the load on edge e when there is a demand of 1 from V to U.
     * Be careful; it is represented as edgeLoadPerPair[destination][origin][edge], with destination as first index and
     * origin as second index
     * @param topology the topology of the network
     * @return edgeLoadPair[][][] as explained above
     */
    public Pair<double[][][], int[][]> makeEdgeLoadPerPair(Topology topology) {

        int nEdges = topology.nEdges;
        int nNodes = topology.nNodes;
        double[][][] edgeLoadPerPair = new double[nNodes][nNodes][nEdges];

        /* Compute the shortest paths in the graph, from there we get the forwarding graph of each node */
        ShortestPaths sp = new ShortestPaths(topology);
        sp.computeShortestPaths();

        /* Loop over all (destination) nodes */
        for (int dest = 0; dest < nNodes; dest++) {
            /* Sort the indices of sp.distance[dest] */
            ComparableIntPair[] nodesSortedByDistance = new ComparableIntPair[nNodes];
            for (int i = 0; i < nNodes; i++) {
                nodesSortedByDistance[i] = new ComparableIntPair(i, sp.distance[i][dest]);
            }
            Arrays.sort(nodesSortedByDistance);

            /* Starting from the closest node origin we will now fill edgeLoadPerPair[dest][origin][] for all edges */
            for (int i = 1; i < nNodes; i++) {
                int origin = nodesSortedByDistance[i].index;
                fillEdgeUsage(dest, origin, edgeLoadPerPair[dest], sp, nEdges);
            }
        }
        return new Pair(edgeLoadPerPair, sp.distance);
    }

    /**
     * Simulates a demand of one from origin to dest and stores the load in edgeLoadDest.
     * The loads for all nodes where the distance to dest is smaller the distance origin-dest should already be computed
     * as it makes use of these loads to compute the new origin-dest load.
     * @param dest The destination node
     * @param origin The origin node
     * @param edgeLoadDest The current computed edge loads for the destination node dest.
     *                    It must already be computed for all nodes closer to dest than origin
     */
    private static void fillEdgeUsage(int dest, int origin, double[][] edgeLoadDest, ShortestPaths sp, int nEdges) {
        int nSuccessors = sp.nSuccessors[dest][origin];
        for (int i = 0; i < nSuccessors; i++) {
            /* Add the load on the direct edge to the new node */
            int nextEdge = sp.successorEdges[dest][origin][i];
            edgeLoadDest[origin][nextEdge] = 1.0/nSuccessors;
            /* Add the load when routing from nextNode to dest */
            int nextNode = sp.successorNodes[dest][origin][i];
            if (nextNode != dest) {
                for (int j = 0; j < nEdges; j++) {
                    edgeLoadDest[origin][j] += 1.0 / nSuccessors * edgeLoadDest[nextNode][j];
                }
            }
        }
    }

    /**
     * Creates all SR paths up to maxSegments for all (origin, destination) pairs in the Topology.
     */
    public void createODPaths(long endTime) {
        /* We create the 0-th depth and the first depth will be created from the 0-th depth constructor */
        for (int i = 0; i < nNodes; i++) {
            leaves[i] = new SegmentTreeLeaf(i, this);
        }
        for (int depth = 2; depth <= maxSegments; depth++) {
            if (System.currentTimeMillis() > endTime) {
                return;
            }
            addDepth(depth, endTime);
        }
    }

    /**
     * Adds a new depth (of leaves) to the SR-paths of the tree
     * @param depth integer representing the new depth to be added; a depth of x means that we will add all x-SR paths.
     *              Note that to add a depth x, all depths from 2 ... x-1 should already have been added previously.
     */
    private void addDepth(int depth, long endTime) {
        for (int originNode = 0; originNode < nNodes; originNode++) {
            for (int nextNode = 0; nextNode < nNodes; nextNode++) {
                if (System.currentTimeMillis() > endTime) {
                    return;
                }
                if (nextNode != originNode) {
                    leaves[originNode].children[nextNode].extendSRPath(depth);
                }
            }
        }
    }

    /**
     * Adds a lead to the correct LinkedList ODPaths.
     * @param leaf the leaf to be added
     */
    protected void addLeafToList(SegmentTreeLeaf leaf) {
        if (leaf.currentNodeNumber < nNodes) {
            ODPaths[leaf.originNodeNumber][leaf.currentNodeNumber].add(leaf);
        }
        else {
            ODPaths[leaf.originNodeNumber][edgeDest[leaf.currentNodeNumber - nNodes]].add(leaf);
        }
    }

    /**
     * Returns the loads on each arc when there is a demand of 1 from originNode to destNode
     * @param originNode the node from which the demand originates
     * @param destNode the node to which the demand is routed
     * @return an EdgeLoadsLinkedList object containing the loads of each edge
     */
    protected EdgeLoadsLinkedList getODLoads(int originNode, int destNode) {
        EdgeLoadsLinkedList tempLoads;
        if (originNode >= nNodes) {
            int edgeNumber = originNode - nNodes;
            originNode = edgeDest[edgeNumber];  // Convert adjacency segment to node segment
        }
        if (destNode < nNodes) {  // Return normal loads
            tempLoads = edgeLoadPerPair[originNode][destNode];
        }
        else {  // Return normal loads plus adjacency segment loads
            int edgeNumber = destNode - nNodes;
            tempLoads = EdgeLoadsLinkedList.add(edgeLoadPerPair[originNode][edgeSrc[edgeNumber]], new EdgeLoadsLinkedList(edgeNumber));
        }
        return tempLoads;
    }

    /**
     * returns true if path is an existing SR-path in the tree, false otherwise
     * @param path the path whose presence is to be tested.
     *             This path is given as an array of int corresponding to: [originNode, ..., destinationNode]
     * @return true if the path exist, false if not
     */
    public boolean pathInTree(int[] path) {
        SegmentTreeLeaf nextLeaf = leaves[path[0]];
        for (int index = 1; index < path.length; index++) {
            nextLeaf = nextLeaf.children[path[index]];
            if (nextLeaf == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * returns the leaf corresponding to requested path
     * @param path the requested path
     *             This path is given as an array of int corresponding to: [originNode, ..., destinationNode]
     * @return the requested leaf
     */
    public SegmentTreeLeaf getLeafFromPath(int[] path) {
        SegmentTreeLeaf nextLeaf = leaves[path[0]];
        for (int index = 1; index < path.length; index++) {
            if (path[index] < nNodes) {
                nextLeaf = nextLeaf.children[path[index]];
                if (nextLeaf == null) {
                    return null;
                }
            }
            else {
                for (SegmentTreeLeaf child : nextLeaf.adjacencyChildren) {
                    if (child.currentNodeNumber == path[index]) {
                        nextLeaf = child;
                        break;
                    }
                }
            }
        }
        return nextLeaf;
    }

    /**
     * Tests if a path with origin node originNode, destination node destinationNode and edge loads newPathEdgeLoads
     * is dominated by any path present in the tree.
     * If the path we are currently testing is dominating another path of same depth, the dominated path is then deleted
     * from the tree.
     * @param newPathEdgeLoads The edge loads of the path we should test domination for.
     * @param originNode The origin node of the path we should test domination for.
     * @param destNode The destination node of the path we should test domination for.
     * @param depth The depth of the path we should test domination for.
     * @return true if the path is dominated by another path, false if it is non-dominated.
     */
    protected boolean testNewPathDomination(EdgeLoadsLinkedList newPathEdgeLoads, int originNode, int destNode, int depth) {
        /* Loop over all non-dominated OD paths currently in the tree */
        ListIterator<SegmentTreeLeaf> iterator = ODPaths[originNode][destNode].listIterator();
        SegmentTreeLeaf nextPath;
        EdgeLoadsLinkedList nextPathLoads;
        while (iterator.hasNext()) {
            nextPath = iterator.next();
            nextPathLoads = nextPath.getEdgeLoads();
            int res = nextPathLoads.dominates(newPathEdgeLoads);
            if (nextPath.depth < depth) {
                if (res == 1) {
                    return true;
                }
                else if (res == -1) {
                    nextPath.setDominated();
                    // Remove from LinkedList
                    iterator.remove();
                }
            }
            else {
                if (res == 1) {
                    return true;
                }
                else if (res == -1) {
                    // Remove from LinkedList
                    iterator.remove();
                    // Remove from tree
                    nextPath.delete();
                }
            }
        }
        return false;
    }

    /**
     * Gets all non-dominated SR-paths going from originNode to destinationNode in the form of an array of array of integers,
     * each element of the first array corresponding to a path and each element of the second array corresponding to a node
     * visited by the path.
     * @param originNode the origin node of all the paths to be fetched.
     * @param destNode the destination node of all the paths to be fetched.
     * @return The array of all SR-paths going from originNode to destNode as described above.
     */
    public int[][] getODPaths(int originNode, int destNode) {
        int[][] paths = new int[ODPaths[originNode][destNode].size()][];
        int index = 0;
        for (SegmentTreeLeaf path : ODPaths[originNode][destNode]) {
            paths[index] = path.getPath();
            index++;
        }
        return paths;
    }

    /**
     * Returns an object EdgeLoadLinkedList containing the loads on each edge for an SR-path composed of the nodes in the
     * array path
     * @param path array containing the node segments of which the SR-path is composed
     * @return the edge loads of the requested path.
     */
    public EdgeLoadsLinkedList getEdgeLoads(int[] path) {
        if (path.length == 2) {
            return getODLoads(path[0], path[1]);
        }
        else {
            EdgeLoadsLinkedList res = EdgeLoadsLinkedList.add(getODLoads(path[0], path[1]), getODLoads(path[1],path[2]));
            for (int i = 3; i < path.length; i++) {
                res.add(getODLoads(path[i-1], path[i]));
            }
            return res;
        }
    }

    /**
     * Frees the memory by "deleting" all leaves in the SR-tree
     * All information about the paths will therefore be lost
     */
    public void freeLeavesMemory() {
        this.leaves = null;
        this.ODPaths = null;
        /*
         Call to garbage collector, usually not recommended as the JVM might ignore it, but works much better with it
         in this case
        */
        System.gc();
    }

    public int getDistance(int startNode, int endNode) {
        int distance = 0;
        if (startNode >= nNodes) {
            startNode = edgeDest[startNode-nNodes];
        }
        if (endNode >= nNodes) {
            distance += edgeWeights[endNode-nNodes];
            endNode = edgeSrc[endNode-nNodes];
        }
        distance += distancePerPair[startNode][endNode];
        return distance;
    }
}
