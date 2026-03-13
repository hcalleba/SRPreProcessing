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

            Demands boosted = new Demands(scaledBase);
            for (int d = 0; d < boosted.nDemands; d++) {
                if (cluster[boosted.source[d]] == srcCluster &&
                    cluster[boosted.dest[d]]   == dstCluster) {
                    boosted.amount[d] *= sampleBoost(rng);
                }
            }

            matrices.add(boosted);
            System.out.printf("Generated matrix %d: boosted cluster %d -> %d%n",
                    m + 1, srcCluster, dstCluster);
        }

        System.out.println("Total matrices generated: " + matrices.size() +
                " (1 base + " + numToGenerate + " cluster-boosted)");

        if (visualize) {
            TopologyViewer.show(topology, cluster);
        }

        return matrices;
    }
}
