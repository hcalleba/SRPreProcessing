package edu.repetita.traffic;

import edu.repetita.core.Setting;
import edu.repetita.core.Topology;

public class TrafficMatrixGenerator {
    private int numGeneratedMatrices;

    public void setNumGeneratedMatrices(int numGeneratedMatrices) {
        this.numGeneratedMatrices = numGeneratedMatrices;
    }

    public void generate(Setting setting) {
        Topology topology = setting.getTopology();

        // Cluster nodes
        
    }
}
