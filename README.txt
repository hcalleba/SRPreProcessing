Most of the code and data comes from https://github.com/svissicchio/Repetita
It is slightly modified to make it lighter and executable  under windows;
    All Scala code has been deleted and only the base classes that are used are present.
    Only one solver is available which is the preprocessing of the SR paths that are then given as parameters to an ILP
    solver.

To execute:
gurobi.jar needs to be added to the lib folder. (TODO, is not necessary yet)
commons-lang is also required but should be present if IntellIJ is used due to the SRPreProc.iml file (TODO use maven to facilitate the dependencies)

The program is executed in the following way:

java exec.jar -graph topology_file -demands demands_filename -maxSR maximum_segment_nodes -outpaths path_filename -out output_filename -verbose debugging_level

an example:
java exec.jar -graph data/2016TopologyZooUCL_inverseCapacity/Iris.graph -demands data/2016TopologyZooUCL_inverseCapacity/Iris.0000.demands -maxSR 3 -outpaths out/testout.txt


To see how the topology looks like, it is possible here:
https://editor.p5js.org/hugo.callebaut.sjb/sketches/WuKwacW3A
The topology file can be copied in topology.txt and sketch.js can then be run. (the current topology is the Iris one)
Most topologies do not come with coordinates for the nodes, drawOnCircle should then be set to true to place all nodes on a circle.