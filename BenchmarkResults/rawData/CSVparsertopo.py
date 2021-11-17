import csv
import os

def computeNbMaxPaths(nNodes, SR):
    sum_=0
    for i in range(2, SR+2):
        product=1
        for j in range(i):
            product = product * (nNodes-j)
        sum_+=product
    return sum_


if __name__ == "__main__":
    header = ["Topology", "Exit code", "Total time", "Resulting paths", "Nodes", "Edges", "Maximum paths", "percentage of resulting paths"]
    directory = "topologies"

    for SRdirectory in os.listdir(directory):
        SR = int(SRdirectory[0])
        csvfile = open (directory + "." + SRdirectory + ".result.csv", 'w', newline='')
        csvwriter = csv.writer(csvfile)
        csvwriter.writerow(header)

        for filename in os.listdir(directory + "/" + SRdirectory):
            if (len(filename.split(".")) == 2 and filename.split(".")[1] == "log"):
                data = []
                with open(directory + "/" + SRdirectory + "/" + filename) as f:
                    lines = f.readlines()
                    data.append(filename.split(".")[0])          # Topo name
                    if len(lines) == 0 or lines[0] != "OK\n":    # Error code (0/2)
                        data.extend([2, "-", "-", "-", "-"])
                    else:
                        data.append(0)
                        data.append(float(lines[3].split()[4]))  # Total time
                        data.append(int(lines[4].split()[7]))    # number of resulting paths
                        data.append(int(lines[5].split()[4]))    # number of nodes
                        data.append(int(lines[6].split()[4]))    # number of edges
                        data.append(computeNbMaxPaths(data[4], SR))  # maximum number of paths
                        data.append(str(data[3]/data[6]*100)+"%")# Percentage of left paths after pre proc
                        if (data[2] > 3600):                     # Error code (1)
                            data[1] = 1
                    csvwriter.writerow(data)
        csvfile.close()
