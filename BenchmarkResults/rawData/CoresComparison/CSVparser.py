import csv
import os

if __name__ == "__main__":
    header = ["Topology", "DemandFile", "Preprocessing time", "ILP solve time", "Total  time", "uMax"]
    directories = ["1_core", "8_cores", "16_cores"]

    for nbcores in directories:
        csvfile = open (nbcores + ".res.csv", 'w', newline='')

        csvwriter = csv.writer(csvfile)

        csvwriter.writerow(header)

        for filename in os.listdir(nbcores):
            data = []
            with open(nbcores + "/" + filename) as f:
                lines = f.readlines()
                data.append(filename.split(".")[0])
                data.append(filename.split(".")[1])
                data.append(float(lines[1].split()[3]))
                data.append(float(lines[2].split()[4]))
                data.append(float(lines[3].split()[4]))
                data.append(float(lines[5].split()[4]))

                csvwriter.writerow(data)
        csvfile.close()