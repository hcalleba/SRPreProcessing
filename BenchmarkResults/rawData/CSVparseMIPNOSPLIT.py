import csv
import os

if __name__ == "__main__":
    header = ["Topology", "DemandFile", "Exit code", "Total  time", "pre-uMax", "post-uMax"]
    directory = "MIP-NO-SPLIT"

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
                    if len(lines) != 8 and len(lines) != 9:   # Error code (0/2)
                        data.extend([1, "-", "-", "-"])
                    else:
                        data.append(0)
                        if (len(lines) == 8):
                            data.append(float(lines[7].split()[4]))  # Total time
                        else:
                            data.append(float(lines[8].split()[4]))  # Total time
                        data.append(float(lines[4].split()[4]))  # pre-uMax
                        data.append(float(lines[5].split()[4]))  # post-uMax
                        if (data[3] > 1800):                     # Error code (1)
                            data[2] = 1
                    csvwriter.writerow(data)
        csvfile.close()