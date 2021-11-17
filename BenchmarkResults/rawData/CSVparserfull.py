import csv
import os

if __name__ == "__main__":
    header = ["Topology", "DemandFile", "Exit code", "Preprocessing time", "ILP solve time", "Total  time", "uMax"]
    directory = "full"

    for SRdirectory in os.listdir(directory):
        csvfile = open (directory + "." + SRdirectory + ".result.csv", 'w', newline='')
        csvwriter = csv.writer(csvfile)
        csvwriter.writerow(header)

        for filename in os.listdir(directory + "/" + SRdirectory):
            if (len(filename.split(".")) == 3):
                data = []
                with open(directory + "/" + SRdirectory + "/" + filename) as f:
                    lines = f.readlines()
                    data.append(filename.split(".")[0])      # Topo namme
                    data.append(filename.split(".")[1])      # Demand number
                    if len(lines) == 0 or lines[0] != "OK\n":# Error code (0/2)
                        data.extend([2, "-", "-", "-", "-"])
                    else:
                        data.append(0)
                        data.append(float(lines[1].split()[3]))  # Preprocessing time
                        data.append(float(lines[2].split()[4]))  # ILP solve time
                        data.append(float(lines[3].split()[4]))  # Total time
                        data.append(float(lines[5].split()[4]))  # Umax
                        if (data[5] > 1800):                     # Error code (1)
                            data[2] = 1
                    csvwriter.writerow(data)
        csvfile.close()