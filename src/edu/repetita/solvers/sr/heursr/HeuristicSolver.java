package edu.repetita.solvers.sr.heursr;

import edu.repetita.core.Demands;
import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HeuristicSolver {
    Topology topology;
    Demands demands;
    int maxSegments;

    private Map<Pair<Integer, Integer>, List<Edge>> shortestPathsCache;

    public HeuristicSolver(Topology topology, Demands demands, int maxSegments) {
        this.topology = topology;
        this.demands = demands;
        this.maxSegments = maxSegments;
    }

    public double solve(long endTime) {
        /* Compute shortest paths */
        computeShortestPaths(topology);
        return 0;
    }

    private void computeShortestPaths(Topology topology) {
        ShortestPaths sp = new ShortestPaths(topology);
        sp.computeShortestPaths();

        int numNodes = topology.nNodes;

        for (int source = 0; source < numNodes; source++) {
            for (int dest = 0; dest < numNodes; dest++) {
                if (source == dest) continue;

                // What should I do now ?
            }
        }
    }
}
