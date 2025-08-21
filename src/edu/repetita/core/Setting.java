package edu.repetita.core;

import java.io.IOException;
import java.util.ArrayList;

import edu.repetita.io.RepetitaParser;
import edu.repetita.paths.SRPaths;

public class Setting {
    private String topologyFilename;
    private Topology topology;
    private ArrayList<String> demandsFilename;
    private ArrayList<Demands> demands;
    private RoutingConfiguration config;

    public Setting(){
        this.config = new RoutingConfiguration();
        this.demands = new ArrayList<>();
    }

    public void setTopologyFilename(String topologyFilename) {
        this.topologyFilename = topologyFilename;
        try {
            this.topology = RepetitaParser.parseTopology(this.topologyFilename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setDemandsFilename(ArrayList<String> demandsFilename) {
        this.demandsFilename = demandsFilename;
        try {
            for (String filename : this.demandsFilename) {
                this.demands.add(RepetitaParser.parseDemands(filename));
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

    public ArrayList<String> getDemandsFilename() {
        return this.demandsFilename;
    }

    public ArrayList<Demands> getDemands() { return this.demands; }

    public void setDemands(ArrayList<Demands> newDemands) {
        this.demands = newDemands;
    }

    public RoutingConfiguration getRoutingConfiguration() {
        return this.config;
    }

    public void setRoutingConfiguration(RoutingConfiguration newConfig) {
        this.config = newConfig;
    }

    public Setting clone(){
        Setting copy = new Setting();
        copy.setTopology(this.topology.clone());
        copy.setDemands(new ArrayList<>(this.demands));
        copy.setRoutingConfiguration(this.getRoutingConfiguration().clone());
        return copy;
    }
}
