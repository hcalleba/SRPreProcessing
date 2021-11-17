import csv
import matplotlib.pyplot as plt

if __name__ == "__main__":
    treshold = 50
    filenames = ["topologies.2-SR.result.csv", "topologies.3-SR.result.csv", "topologies.4-SR.result.csv"]
    data = []
    fig, axs = plt.subplots(3, 1)

    for filenb in range(len(filenames)):
        file = filenames[filenb]
        csvfile = open (file, 'r')
        SR = int(file.split(".")[1][0])

        csvreader = csv.reader(csvfile)
        next(csvreader)  # Skip header

        nodes = []
        edges = []
        percentageplus = []
        ratioplus = []
        percentageless = []
        ratioless = []
        for row in csvreader:
            if row[1] == "0":
                nodes.append(int(row[4]))
                edges.append(int(row[5]))
                ratio = edges[-1]/nodes[-1]/(nodes[-1]-1)
                if (nodes[-1] > treshold):
                    percentageplus.append(float(row[7][:-1]))
                    ratioplus.append(ratio)
                    if (ratio < 4 and ratio >= 2):
                        percentageplus.append(float(row[7][:-1]))
                        ratioplus.append(ratio)
                else:
                    percentageless.append(float(row[7][:-1]))
                    ratioless.append(ratio)
                    if (ratio) < 4 and (ratio) >= 2:
                        percentageless.append(float(row[7][:-1]))
                        ratioless.append(ratio)
        axs[filenb].scatter(ratioless, percentageless, s=3, c='blue')
        axs[filenb].scatter(ratioplus, percentageplus, s=3, c='red')
        axs[filenb].set_title(str(SR) + "-SR; All")
    fig.suptitle("Influence of ratio edges/nodes² on resulting non-dominated paths")
    fig.supxlabel("Ratio edges/(nodes*(nodes-1))")
    fig.supylabel("Percentage of resulting paths")
    plt.show()
