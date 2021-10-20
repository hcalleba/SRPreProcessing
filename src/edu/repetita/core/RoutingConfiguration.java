package edu.repetita.core;

import edu.repetita.paths.SRPaths;

public class RoutingConfiguration {
    private SRPaths srpaths;

    public SRPaths getSRPaths() {
        return this.srpaths;
    }

    public void setSRPaths(SRPaths srpaths) {
        this.srpaths = srpaths;
    }

    public RoutingConfiguration clone(){
        RoutingConfiguration copy = new RoutingConfiguration();
        copy.setSRPaths(this.getSRPaths());
        return copy;
    }
}
