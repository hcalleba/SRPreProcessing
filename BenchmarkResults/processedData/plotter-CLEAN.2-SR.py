import csv
import matplotlib.pyplot as plt

if __name__ == "__main__":
    filenames = ["2-SR.result.csv"] #, "3-SR.result.csv"]
    data = []

    for file in filenames:
        csvfile = open (file, 'r')
        csvreader = csv.reader(csvfile)
        next(csvreader)  # Skip header

        SRPPdata = []
        MIPdata = []
        fulldata = []
        CG4SRdata = []
        SRPPsolved = 0
        MIPsolved = 0
        fullsolved = 0
        CG4SRsolved = 0
        for row in csvreader:
            if row[2]=="0":
                if float(row[3]) < 1800:
                    SRPPsolved += 1
                    SRPPdata.append(float(row[3]))
            if row[5]=="0":
                if float(row[6]) < 1800:
                    MIPsolved += 1
                    MIPdata.append(float(row[6]))
            if row[8]=="0":
                if float(row[9]) < 1800:
                    fullsolved += 1
                    fulldata.append(float(row[9]))
            if row[11]=="0":
                if float(row[12]) < 1800:
                    CG4SRsolved += 1
                    CG4SRdata.append(float(row[12]))
                
        MIPdata.sort()
        SRPPdata.sort()
        fulldata.sort()
        CG4SRdata.sort()
        plt.plot(SRPPdata, range(len(SRPPdata)), label="SRPP")
        plt.plot(CG4SRdata, range(len(CG4SRdata)), label="CG4SR")
        plt.plot(fulldata, range(len(fulldata)), label="full")
        plt.plot(MIPdata, range(len(MIPdata)), label="MIP")
        plt.legend()
        plt.title(file.split(".")[0])
        plt.ylabel("Number of topologies resolved")
        plt.xlabel("Solve time")
        plt.xscale('log')
        plt.show()
        print("SRPP solved : " + str(SRPPsolved))
        print("MIP solved : " + str(MIPsolved))
        print("full solved : " + str(fullsolved))
        print("CG4SR solved : " + str(CG4SRsolved))
