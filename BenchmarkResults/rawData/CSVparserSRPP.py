import csv
import os

if __name__ == "__main__":
    header = ["Topology", "DemandFile", "Exit code", "Preprocessing time", "ILP solve time", "Total  time", "uMax"]
    directory = "loadFromFile"

    for SRdirectory in os.listdir(directory):
        SR = int(SRdirectory[0])
        csvfile = open("SRPP" + "." + SRdirectory + ".result.csv", 'w', newline='')
        csvwriter = csv.writer(csvfile)
        csvwriter.writerow(header)

        csvfiletopo = open("topologies." + SRdirectory + ".result.csv", 'r')
        csvreader = csv.reader(csvfiletopo)
        topologydata = next(csvreader)  # extract header
        

        for filename in os.listdir(directory + "/" + SRdirectory):
            if (len(filename.split(".")) == 3):
                data = []
                if filename.split(".")[1] == "0000":
                    topologydata = next(csvreader)
                with open(directory + "/" + SRdirectory + "/" + filename) as f:
                    lines = f.readlines()
                    data.append(filename.split(".")[0])          # Topo name
                    data.append(filename.split(".")[1])          # Demand number
                    if topologydata[1] != "0":                   # Error code (1)
                        data.append(topologydata[1])
                        data.extend(["-", "-", "-", "-"])
                    elif len(lines) == 0 or lines[0] != "OK\n":  # Error code (0/2)
                        data.extend([2, "-", "-", "-", "-"])
                    else:
                        data.append(0)
                        data.append(float(topologydata[2]))      # Preprocessing time
                        data.append(float(lines[3].split()[4]))  # ILP solve time
                        data.append(data[3] + data[4])           # Total time
                        data.append(float(lines[5].split()[4]))  # Umax
                        if (data[4] > 1800):                     # Error code (1)
                            data[2] = 1
                    csvwriter.writerow(data)
        csvfile.close()