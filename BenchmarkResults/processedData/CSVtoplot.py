import csv
import matplotlib.pyplot as plt

if __name__ == "__main__":
    filenames = ["2-SR.result.csv", "3-SR.result.csv"]
    data = []

    for file in filenames:
        csvfile = open (file, 'r')
        csvreader = csv.reader(csvfile)
        next(csvreader)  # Skip header

        fulldata = []
        SRPPdata = []
        for row in csvreader:
            if row[2]=="0":
                fulldata.append(float(row[3]))
            if row[5]=="0":
                if float(row[6]) < 1800:  # For fairness I do not take the topoplogies that took more than 30 minutes in total
                    SRPPdata.append(float(row[6]))
        fulldata.sort()
        SRPPdata.sort()
        plt.plot(fulldata, range(len(fulldata)), label="full")
        plt.plot(SRPPdata, range(len(SRPPdata)), label="SRPP")
        plt.legend()
        plt.title(file.split(".")[0])
        plt.ylabel("Number of topologies resolved")
        plt.xlabel("Solve time")
        plt.show()
