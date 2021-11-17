Most of the code and data comes from https://github.com/svissicchio/Repetita
It is slightly modified to make it lighter and executable under windows;
    All Scala code has been deleted and only the base classes that are used are present.
    Only one solver is available which is the preprocessing of the SR paths that are then given as parameters to an ILP
    solver.

To execute:
gurobi.jar needs to be added to the lib folder.
commons-lang is also required but should be present if IntellIJ is used due to the SRPreProc.iml file

The program is executed in the following way:

java exec.jar -scenario scenario -graph topology_file -demands demands_filename -maxSR maximum_segment_nodes -outpaths path_filename -inpaths inpaths_filename -out output_filename -verbose debugging_level
(Note that -inpaths is only used with scenario loadFromFile, it must be used with this scenario and has no effect when used with another scenario)

an example:
java exec.jar -scenario SRPP -graph data/2016TopologyZooUCL_inverseCapacity/Iris.graph -demands data/2016TopologyZooUCL_inverseCapacity/Iris.0000.demands -maxSR 3 -outpaths out/testout.txt

The three available scenarios are:
    -SRPP: Use the preprocessing algorithm to eliminate dominated paths, and then give these paths as binary variables to the ILP solver.
    -loadFromFile: Must be used with -inpaths; Here the SR-paths will be loaded from the inpaths file and the ILP will be solved with the SR-paths from this file).
                   This can be useful since the SR-preprocessing step will always be the same for a topology regardless od the demands. The SR-paths could then
                   be saved into a file to be reused later without going through the process of recomputing non-dominated paths.
    -full: Here we do not preprocess the SR-paths and simply give the N * N-1 * ... * N-maxSegments-1 possible paths directly to the ILP solver.
           This scenario serves as a comparison with SRPP
    -preprocess: Generates all non-dominated paths of the topology, and writes them to the -outpaths file. The ILP program is not solved here, and
                 therefore, no demand file is needed. If a demand file is given, it is simply ignored.



To see how the topology looks like, it is possible here:
https://editor.p5js.org/hugo.callebaut.sjb/sketches/WuKwacW3A
The topology file can be copied in topology.txt and sketch.js can then be run. (the current topology is the Iris one)
Most topologies do not come with coordinates for the nodes, drawOnCircle should then be set to true to place all nodes on a circle.