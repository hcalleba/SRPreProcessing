import csv
import os

def processCSV():
    while True:
        data = []
        for i in range(len(csvreader)):
            try:
                nextline = next(csvreader[i])
            except StopIteration:
                return
            if i==0:
                data.append(nextline[0])           # Topo namme
                data.append(nextline[1])           # Demand number
            data.append(nextline[2])               # Exit code
            data.append(nextline[5])               # Time
            data.append(nextline[6])               # Umax
        csvwriter.writerow(data)

if __name__ == "__main__":
    header = ["Topology", "DemandFile", "ExitCode_full", "Time_full", "Umax_full", "ExitCode_SRPP", "Time_SRPP", "Umax_SRPP"]
    for SR in ["2", "3"]:
        filenames = ["../rawData/full."+SR+"-SR.result.csv", "../rawData/SRPP."+SR+"-SR.result.csv"]

        csvfilew = open(SR+"-SR.result.csv", 'w', newline='')
        csvwriter = csv.writer(csvfilew)
        csvwriter.writerow(header)

        csvfilesr = []
        csvreader = []
        for f in filenames:
            csvfilesr.append(open(f, 'r'))
            csvreader.append(csv.reader(csvfilesr[-1]))
            next(csvreader[-1])

        processCSV()
        csvfilew.close()
        for f in csvfilesr:
            f.close()