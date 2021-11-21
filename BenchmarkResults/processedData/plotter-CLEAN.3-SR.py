import csv
import matplotlib.pyplot as plt

if __name__ == "__main__":
    filenames = ["3-SR.result.csv"]
    data = []

    for file in filenames:
        csvfile = open (file, 'r')
        csvreader = csv.reader(csvfile)
        next(csvreader)  # Skip header

        SRPPdata = []
        #MIPdata = []
        fulldata = []
        SRPPsolved = 0
        #MIPsolved = 0
        fulsolved = 0
        for row in csvreader:
            if row[2]=="0":
                if float(row[3]) < 1800:
                    SRPPsolved += 1
                    SRPPdata.append(float(row[3]))
            if row[5]=="0":
                if float(row[6]) < 1800:
                    fulsolved += 1
                    fulldata.append(float(row[6]))
            #if row[8]=="0":
            #    if float(row[9]) < 1800:
            #        fulsolved += 1
            #        fulldata.append(float(row[9]))
                
        #MIPdata.sort()
        SRPPdata.sort()
        fulldata.sort()
        plt.plot(SRPPdata, range(len(SRPPdata)), label="SRPP")
        #plt.plot(MIPdata, range(len(MIPdata)), label="MIP")
        plt.plot(fulldata, range(len(fulldata)), label="full")
        plt.legend()
        plt.title(file.split(".")[0])
        plt.ylabel("Number of topologies resolved")
        plt.xlabel("Solve time")
        plt.xscale('log')
        plt.show()
        print("SRPP solved : " + str(SRPPsolved))
        #print("MIP solved : " + str(MIPsolved))
        print("full solved : " + str(fulsolved))
