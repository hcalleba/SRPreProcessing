package edu.repetita.traffic;

import edu.repetita.core.Setting;
import edu.repetita.core.Topology;

import static java.lang.Math.*;

public class TrafficMatrixGenerator {
    private int numGeneratedMatrices;
    private int numClusters; // default

    public void setNumGeneratedMatrices(int numGeneratedMatrices) {
        this.numGeneratedMatrices = numGeneratedMatrices;
    }

    public void generate(Setting setting) {
        Topology topology = setting.getTopology();

        numClusters = max(4, (int)ceil(sqrt(topology.nNodes / 2)));

        // Cluster nodes
        TopologyClusterer clusterer = new TopologyClusterer(topology, numClusters);
        int[] cluster = clusterer.run();
        int effectiveK = clusterer.getEffectiveK();
        clusterer.printSummary();

        // TODO generate base matrix using https://dl.acm.org/doi/10.1145/1070873.1070876
        BaseMatrixGenerator BaseGen = new BaseMatrixGenerator(topology, 1.0, 42L);
        BaseGen.generate();
        // TODO scale matrix

        // TODO: generate matrices using cluster assignments
    }
}