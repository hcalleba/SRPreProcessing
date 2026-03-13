package edu.repetita.traffic.Generator;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.traffic.clusterer.ThirdClusterer;
import edu.repetita.traffic.clusterer.TopologyClusterer;
import edu.repetita.viz.TopologyViewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.lang.Math.*;

public class TrafficMatrixGeneratorClustering extends TrafficMatrixGenerator {

    private int numClusters;

    public TrafficMatrixGeneratorClustering() {
        setBoostRange(2.0, 4.0);
    }

    @Override
    public List<Demands> generate(Setting setting) {
        Topology topology = setting.getTopology();
        Random rng = new Random(seed);

        numClusters = max(4, (int) floor(sqrt(topology.nNodes / 2)));

        TopologyClusterer clusterer = new ThirdClusterer(topology, numClusters);
        int[] cluster = clusterer.run();
        int effectiveK = clusterer.getEffectiveK();
        clusterer.printSummary();

        if (visualize) {
            System.out.println("Visualizing full clustering");
            TopologyViewer.show(topology, cluster);
            showEachCluster(topology, cluster, effectiveK);
        }

        Demands baseMatrix = new BaseMatrixGenerator(topology, 1.0, seed).generate();
        Demands scaledBase = scaleToTargetMLU(topology, baseMatrix, TARGET_MLU);

        List<Demands> matrices = new ArrayList<>();
        matrices.add(scaledBase);

        List<int[]> clusterPairs = new ArrayList<>();
        for (int i = 0; i < effectiveK; i++) {
            for (int j = 0; j < effectiveK; j++) {
                if (i != j) clusterPairs.add(new int[]{i, j});
            }
        }

        int numToGenerate = min(numMatrices, clusterPairs.size());
        for (int m = 0; m < numToGenerate; m++) {
            int srcCluster = clusterPairs.get(m)[0];
            int dstCluster = clusterPairs.get(m)[1];

            double alpha = sampleBoost(rng);
            Demands boosted = new Demands(scaledBase);
            for (int d = 0; d < boosted.nDemands; d++) {
                if (cluster[boosted.source[d]] == srcCluster &&
                    cluster[boosted.dest[d]]   == dstCluster) {
                    boosted.amount[d] *= alpha;
                }
            }

            matrices.add(boosted);
            System.out.printf("Generated matrix %d: boosted cluster %d -> %d (x%.2f)%n",
                    m + 1, srcCluster, dstCluster, alpha);
        }

        System.out.println("Total matrices generated: " + matrices.size() +
                " (1 base + " + numToGenerate + " cluster-boosted)");
        return matrices;
    }

    private static void showEachCluster(Topology topology, int[] cluster, int effectiveK) {
        for (int k = 0; k < effectiveK; k++) {
            int[] focus = new int[topology.nNodes];
            for (int v = 0; v < topology.nNodes; v++) {
                focus[v] = (cluster[v] == k) ? 0 : -1;
            }
            System.out.printf("Visualizing cluster %d%n", k);
            TopologyViewer.show(topology, focus);
        }
    }
}
