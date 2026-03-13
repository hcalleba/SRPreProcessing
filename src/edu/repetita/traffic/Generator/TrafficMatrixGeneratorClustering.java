package edu.repetita.traffic.Generator;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.solvers.mcf.MCF;
import edu.repetita.traffic.clusterer.ThirdClusterer;
import edu.repetita.traffic.clusterer.TopologyClusterer;
import edu.repetita.viz.TopologyViewer;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;

public class TrafficMatrixGeneratorClustering {
    private int numGeneratedMatrices;
    private int numClusters;
    private boolean visualizeClusters = true;

    private static final double TARGET_MLU = 1.0;
    private double boostFactor = 2.0;

    public void setNumGeneratedMatrices(int numGeneratedMatrices) {
        this.numGeneratedMatrices = numGeneratedMatrices;
    }

    public void setBoostFactor(double boostFactor) {
        this.boostFactor = boostFactor;
    }

    public void setVisualizeClusters(boolean visualizeClusters) {
        this.visualizeClusters = visualizeClusters;
    }

    public List<Demands> generate(Setting setting) {
        Topology topology = setting.getTopology();

        numClusters = max(4, (int) floor(sqrt(topology.nNodes / 2)));

        // Cluster nodes
        TopologyClusterer clusterer = new ThirdClusterer(topology, numClusters);
        int[] cluster = clusterer.run();
        int effectiveK = clusterer.getEffectiveK();
        clusterer.printSummary();

        if (visualizeClusters) {
            System.out.println("Visualizing full clustering");
            TopologyViewer.show(topology, cluster);
            showEachCluster(topology, cluster, effectiveK);
        }

        // Generate base matrix using https://dl.acm.org/doi/10.1145/1070873.1070876
        BaseMatrixGenerator baseGen = new BaseMatrixGenerator(topology, 1.0, 42L);
        Demands baseMatrix = baseGen.generate();

        // Scale base matrix to target MLU using MCF
        Demands scaledBase = scaleToTargetMLU(topology, baseMatrix, TARGET_MLU);

        // Generate cluster-boosted matrices
        List<Demands> matrices = new ArrayList<>();
        matrices.add(scaledBase);

        // Build all ordered cluster pairs
        List<int[]> clusterPairs = new ArrayList<>();
        for (int i = 0; i < effectiveK; i++) {
            for (int j = 0; j < effectiveK; j++) {
                if (i != j) clusterPairs.add(new int[]{i, j});
            }
        }

        int numToGenerate = min(numGeneratedMatrices, clusterPairs.size());
        for (int m = 0; m < numToGenerate; m++) {
            int srcCluster = clusterPairs.get(m)[0];
            int dstCluster = clusterPairs.get(m)[1];

            // Copy base and boost demands from srcCluster -> dstCluster
            Demands boosted = new Demands(scaledBase);
            for (int d = 0; d < boosted.nDemands; d++) {
                if (cluster[boosted.source[d]] == srcCluster &&
                    cluster[boosted.dest[d]]   == dstCluster) {
                    boosted.amount[d] *= boostFactor;
                }
            }
            
            matrices.add(boosted);

            System.out.printf("Generated matrix %d: boosted cluster %d -> %d%n",
                    m + 1, srcCluster, dstCluster);
        }

        System.out.println("Total matrices generated: " + matrices.size() +
                " (1 base + " + numToGenerate + " cluster-boosted)");
        return matrices;
    }

    /**
     * Scales a demand matrix so that its MCF-optimal MLU equals targetMLU.
     */
    private static Demands scaleToTargetMLU(Topology topology, Demands matrix, double targetMLU) {
        double currentMLU = MCF.computeOptimalMLU(topology, matrix);
        if (currentMLU <= 0) {
            System.err.println("Error: MCF MLU is " + currentMLU + ", returning unscaled matrix");
            return matrix;
        }

        double scaleFactor = targetMLU / currentMLU;
        Demands scaled = new Demands(matrix);
        for (int i = 0; i < scaled.nDemands; i++) {
            scaled.amount[i] = Math.floor(matrix.amount[i] * scaleFactor);
        }

        System.out.println("MCF_SCALE_FACTOR: " + scaleFactor);
        return scaled;
    }

    private static void showEachCluster(Topology topology, int[] cluster, int effectiveK) {
        for (int k = 0; k < effectiveK; k++) {
            int[] focus = new int[topology.nNodes];
            for (int v = 0; v < topology.nNodes; v++) {
                focus[v] = (cluster[v] == k) ? 0 : -1;
            }
            System.out.printf("Visualizing cluster %d\n", k);
            TopologyViewer.show(topology, focus);
        }
    }
}