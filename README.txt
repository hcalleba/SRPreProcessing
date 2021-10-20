Most of the code and data comes from https://github.com/svissicchio/Repetita
It is slightly modified to make it lighter and executable with under windows;
    All Scala code has been deleted and only the base classes that are used are present.
    Only one solver is available which is the preprocessing of the SR paths that are then given as parameters to an ILP
    solver.

To execute:
gurobi.jar needs to be added to the lib folder.
commons-lang is also required but should be present if IntellIJ is used due to the SRPreProc.iml file

TODO:
use maven to facilitate the dependency.

java exec.jar -graph topology_file -demands demands_filename -outpaths path_filename -out output_filename -verbose debugging_level


