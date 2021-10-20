Framework for repeatable experiments in Traffic Engineering.

Features:
- dataset with most instances from the Topology Zoo
- a collection of traffic engineering algorithms and analyses of their results
- libraries to simulate traffic distribution induced by ECMP, static (MPLS tunnels or OpenFlow rules) and Segment Routing paths, compute Multicommodity Flow solutions, and much more!

Typical usage: repetita -graph topology_file -demands demands_filename -demandchanges list_demands_filename -solver algorithm_id -scenario scenario_id -t max_execution_time -outpaths path_filename -out output_filename -verbose debugging_level


