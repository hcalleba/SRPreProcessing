import csv
import matplotlib.pyplot as plt

if __name__ == "__main__":
    treshold = 50
    filenames = ["topologies.2-SR.result.csv", "topologies.3-SR.result.csv", "topologies.4-SR.result.csv"]
    data = []
    fig, axs = plt.subplots(3, 2)

    for filenb in range(len(filenames)):
        file = filenames[filenb]
        csvfile = open (file, 'r')
        SR = int(file.split(".")[1][0])

        csvreader = csv.reader(csvfile)
        next(csvreader)  # Skip header

        nodes = [[], []]
        edges = [[], []]
        percentageplus = [[], []]
        ratioplus = [[], []]
        percentageless = [[], []]
        ratioless = [[], []]
        for row in csvreader:
            if row[1] == "0":
                nodes[0].append(int(row[4]))
                edges[0].append(int(row[5]))
                ratio = edges[0][-1]/nodes[0][-1]
                if (nodes[0][-1] > treshold):
                    percentageplus[0].append(float(row[7][:-1]))
                    ratioplus[0].append(ratio)
                    if (ratio < 4 and ratio >= 2):
                        percentageplus[1].append(float(row[7][:-1]))
                        ratioplus[1].append(ratio)
                else:
                    percentageless[0].append(float(row[7][:-1]))
                    ratioless[0].append(ratio)
                    if (ratio) < 4 and (ratio) >= 2:
                        percentageless[1].append(float(row[7][:-1]))
                        ratioless[1].append(ratio)
        axs[filenb, 0].scatter(ratioless[0], percentageless[0], s=3, c='blue')
        axs[filenb, 0].scatter(ratioplus[0], percentageplus[0], s=3, c='red')
        axs[filenb, 1].scatter(ratioless[1], percentageless[1], s=3, c='blue')
        axs[filenb, 1].scatter(ratioplus[1], percentageplus[1], s=3, c='red')
        axs[filenb, 0].set_title(str(SR) + "-SR; All")
        axs[filenb, 1].set_title(str(SR) + "-SR; 2 <= ratio < 4")
    fig.suptitle("Influence of ratio edges/nodes on resulting non-dominated paths")
    fig.supxlabel("Ratio edges/nodes")
    fig.supylabel("Percentage of resulting paths")
    plt.show()
