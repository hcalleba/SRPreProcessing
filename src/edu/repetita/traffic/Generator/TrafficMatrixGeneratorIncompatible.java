package edu.repetita.traffic.Generator;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.paths.ShortestPaths;

import java.util.*;

/**
 * Generates "incompatible" traffic matrices designed to stress-test routing.
 *
 * Each matrix amplifies a set of long OD flows whose OSPF paths are nearly
 * edge-disjoint, creating simultaneous heavy load in different parts of the
 * network — a scenario that is hard for any single routing configuration.
 *
 * Algorithm:
 *   Step 1  Generate a base gravitational matrix D (Nucci et al. 2005),
 *           then scale it to MLU = 1 via MCF.
 *   Step 2  Build LONG_OD: OD pairs whose OSPF path length is >= LONG_OD_THRESHOLD
 *           of the network diameter, AND whose capacity-aware amplification cap
 *           is at least boostLow. Pairs failing the capacity check are leaf-like
 *           and are dropped: amplifying them would merely move the bottleneck to
 *           the node egress, and the subsequent MLU normalisation would cancel
 *           most of the gain.
 *
 *           For pair (s,t) with base demand d:
 *             alphaCap(s,t) = NODE_CAP_FRACTION * min(outCap[s], inCap[t]) / d
 *           A pair is kept only if alphaCap >= boostLow.
 *
 *   Step 3  For each matrix k, greedily pick up to targetPairs pairs from
 *           LONG_OD (targetPairs scales with nNodes: max(5, min(20, nNodes/2))).
 *           A candidate is accepted only if its path shares at most
 *           MAX_SHARED_EDGES edges with every already-chosen path.
 *   Step 4  Amplify each chosen demand by min(sampleBoost(), alphaCap).
 *   Step 5  Re-scale the whole matrix so MLU = 1 (via MCF).
 *   Step 6  Repeat for numMatrices matrices.
 */
public class TrafficMatrixGeneratorIncompatible extends TrafficMatrixGenerator {

    private static final double LONG_OD_THRESHOLD = 0.50;
    private static final int    MAX_SHARED_EDGES  = 1;
    private static final double NODE_CAP_FRACTION = 0.80;

    public TrafficMatrixGeneratorIncompatible() {
    }

    @Override
    public List<Demands> generate(Setting setting) {
        Topology topology = setting.getTopology();
        ShortestPaths sp  = new ShortestPaths(topology);

        Demands base   = new BaseMatrixGenerator(topology, 1.0, seed).generate();
        Demands scaled = scaleToTargetMLU(topology, base, TARGET_MLU);

        double[] outCap = computeOutCap(topology);
        double[] inCap  = computeInCap(topology);

        int diameter  = findDiameter(sp, topology.nNodes);
        int threshold = (int) Math.ceil(LONG_OD_THRESHOLD * diameter);
        System.out.printf("Diameter=%d, LONG_OD threshold=%d (%.0f%%)%n",
                diameter, threshold, LONG_OD_THRESHOLD * 100);

        Map<Long, Integer>        demandIdx = buildDemandIndex(scaled, topology.nNodes);
        List<int[]>               longOD    = new ArrayList<>();
        Map<String, Double>       alphaCaps = new HashMap<>();
        Map<String, Set<Integer>> paths     = new HashMap<>();

        int droppedLeaf = 0;
        for (int s = 0; s < topology.nNodes; s++) {
            for (int t = 0; t < topology.nNodes; t++) {
                if (s == t) continue;
                int dist = sp.distance[s][t];
                if (dist < threshold || dist >= Topology.INFINITE_DISTANCE) continue;
                Integer di = demandIdx.get(encodeOD(s, t, topology.nNodes));
                if (di == null) continue;

                double demand = scaled.amount[di];
                if (demand <= 0) continue;

                double alphaCap = NODE_CAP_FRACTION * Math.min(outCap[s], inCap[t]) / demand;
                if (alphaCap < boostLow) {
                    droppedLeaf++;
                    continue;
                }

                String key = odKey(s, t);
                longOD.add(new int[]{s, t, di});
                alphaCaps.put(key, alphaCap);
                paths.put(key, tracePathEdges(s, t, sp));
            }
        }
        System.out.printf("LONG_OD pairs: %d usable, %d dropped (leaf/low-capacity)%n",
                longOD.size(), droppedLeaf);

        int targetPairs = Math.max(5, Math.min(20, topology.nNodes / 2));
        System.out.println("Target amplified pairs per matrix: " + targetPairs);

        Random rng = new Random(seed + 1);
        List<Demands> matrices = new ArrayList<>();
        matrices.add(scaled);

        for (int m = 0; m < numMatrices; m++) {
            List<int[]> chosen = selectDisjointPairs(longOD, paths, rng, targetPairs);
            if (chosen.isEmpty()) {
                System.err.println("Warning: no disjoint pairs found for matrix " + (m + 1));
                continue;
            }

            Demands boosted = new Demands(scaled);
            for (int[] pair : chosen) {
                String key   = odKey(pair[0], pair[1]);
                double cap   = alphaCaps.getOrDefault(key, boostHigh);
                double alpha = Math.min(sampleBoost(rng), cap);
                boosted.amount[pair[2]] *= alpha;
                System.out.printf("  Matrix %d: amplify (%s->%s) x%.2f (cap=%.1f)%n",
                        m + 1, topology.nodeLabel[pair[0]], topology.nodeLabel[pair[1]], alpha, cap);
            }

            matrices.add(scaleToTargetMLU(topology, boosted, TARGET_MLU));
            System.out.println("Generated incompatible matrix " + (m + 1)
                    + " (" + chosen.size() + " amplified pairs)");
        }

        System.out.println("Total matrices: " + matrices.size()
                + " (1 base + " + (matrices.size() - 1) + " incompatible)");
        return matrices;
    }

    private List<int[]> selectDisjointPairs(List<int[]> longOD,
                                             Map<String, Set<Integer>> paths,
                                             Random rng,
                                             int targetPairs) {
        List<int[]> shuffled = new ArrayList<>(longOD);
        Collections.shuffle(shuffled, rng);

        List<int[]> selected = new ArrayList<>();
        for (int[] candidate : shuffled) {
            if (selected.size() >= targetPairs) break;
            Set<Integer> cEdges = paths.get(odKey(candidate[0], candidate[1]));
            if (cEdges == null) continue;

            boolean compatible = true;
            for (int[] already : selected) {
                Set<Integer> aEdges = paths.get(odKey(already[0], already[1]));
                if (intersectionSize(cEdges, aEdges) > MAX_SHARED_EDGES) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) selected.add(candidate);
        }
        return selected;
    }

    private Set<Integer> tracePathEdges(int src, int dest, ShortestPaths sp) {
        Set<Integer> edges = new HashSet<>();
        int current = src;
        int maxHops = sp.distance.length;
        while (current != dest && maxHops-- > 0) {
            if (sp.nSuccessors[dest][current] == 0) break;
            int edge = sp.successorEdges[dest][current][0];
            edges.add(edge);
            current = sp.successorNodes[dest][current][0];
        }
        return edges;
    }

    private int findDiameter(ShortestPaths sp, int nNodes) {
        int max = 0;
        for (int s = 0; s < nNodes; s++) {
            for (int t = 0; t < nNodes; t++) {
                if (s != t && sp.distance[s][t] < Topology.INFINITE_DISTANCE) {
                    max = Math.max(max, sp.distance[s][t]);
                }
            }
        }
        return max;
    }

    private static double[] computeOutCap(Topology t) {
        double[] cap = new double[t.nNodes];
        for (int e = 0; e < t.nEdges; e++) cap[t.edgeSrc[e]] += t.edgeCapacity[e];
        return cap;
    }

    private static double[] computeInCap(Topology t) {
        double[] cap = new double[t.nNodes];
        for (int e = 0; e < t.nEdges; e++) cap[t.edgeDest[e]] += t.edgeCapacity[e];
        return cap;
    }

    private static Map<Long, Integer> buildDemandIndex(Demands d, int nNodes) {
        Map<Long, Integer> idx = new HashMap<>();
        for (int i = 0; i < d.nDemands; i++) {
            idx.put(encodeOD(d.source[i], d.dest[i], nNodes), i);
        }
        return idx;
    }

    private static long encodeOD(int src, int dest, int nNodes) {
        return (long) src * nNodes + dest;
    }

    private static String odKey(int src, int dest) {
        return src + "_" + dest;
    }

    private static int intersectionSize(Set<Integer> a, Set<Integer> b) {
        int count = 0;
        for (int e : a) {
            if (b.contains(e)) count++;
        }
        return count;
    }
}
