package edu.repetita.core;

import java.io.IOException;
import java.util.List;

import edu.repetita.io.RepetitaParser;
import edu.repetita.paths.SRPaths;

public class Setting {
    private String topologyFilename;
    private Topology topology = null;
    private List<String> demandsFilenames;
    private Demands[] demands;
    private RoutingConfiguration config;
    private int maxSegments;

    public Setting(){
        this.config = new RoutingConfiguration();
    }

    public void setTopologyFilename(String topologyFilename) {
        this.topologyFilename = topologyFilename;
        try {
            this.topology = RepetitaParser.parseTopology(this.topologyFilename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setDemandsFilename(List<String> demandsFilename) {
        this.demandsFilenames = demandsFilename;
        this.demands = new Demands[demandsFilenames.size()];
        try {
            for (int i = 0; i < demandsFilenames.size(); i++) {
                this.demands[i] = RepetitaParser.parseDemands(this.demandsFilenames.get(i));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getTopologyFilename() {
        return this.topologyFilename;
    }

    public Topology getTopology() {
        return this.topology;
    }

    public void setTopology(Topology topology) {
        this.topology = topology;
    }

    public List<String> getDemandsFilename() {
        return this.demandsFilenames;
    }

    public Demands[] getDemands() { return this.demands; }

    public void setDemands(Demands[] newDemands) {
        this.demands = newDemands;
    }

    public RoutingConfiguration getRoutingConfiguration() {
        return this.config;
    }

    public void setRoutingConfiguration(RoutingConfiguration newConfig) {
        this.config = newConfig;
    }

    public void setSRPaths(SRPaths paths) {
        this.config.setSRPaths(paths);
    }

    public SRPaths getSRPaths() {
        return this.config.getSRPaths();
    }

    public void setMaxSegments(int maxSegments) {
        this.maxSegments = maxSegments;
    }

    public int getMaxSegments() {
        return this.maxSegments;
    }

    public Setting clone(){
        Setting copy = new Setting();
        copy.setTopology(this.topology.clone());
        copy.setDemands(this.demands);
        copy.setRoutingConfiguration(this.getRoutingConfiguration().clone());
        copy.setMaxSegments(this.maxSegments);
        return copy;
    }
}
