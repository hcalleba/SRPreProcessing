import os

directory = "../data/2016TopologyZooUCL_inverseCapacity"
def giveTopoNames(dir):
    for file in os.listdir(dir):
        if file.endswith(".graph"):
            yield file[:-6]

lst = []
for topology in giveTopoNames(directory):
    with open(directory + "/" + topology + ".graph", "r") as f:
        nbNodes = f.readline().split()[-1]
        while (True):
            a = f.readline()
            if (a.startswith("EDGES")):
                break
        nbEdges = a.split()[-1]
        lst.append((topology, nbNodes, nbEdges))

lst.sort(key=lambda x: (int(x[1]), int(x[2])))


print("(", end="")
for i in lst[:-30]:
    print("\"" + i[0] + "\" ", end="")
print(")")