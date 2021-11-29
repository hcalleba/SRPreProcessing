import csv
import os
import matplotlib.pyplot as plt

if __name__ == "__main__":
    csvfiler3SR = open("3-SR.result.csv", "r")
    csvreader3SR = csv.reader(csvfiler3SR)
    next(csvreader3SR)

    data01 = []
    data02 = []
    namesTopo = []
    while True:
        try:
            nextline = next(csvreader3SR)
        except StopIteration:
            break
        if (nextline[4] != "-"):
            if (nextline[8] == "0"):
                data01.append(float(nextline[4]))
                data02.append(float(nextline[10]))
                namesTopo.append(nextline[0] + nextline[1])
    diff = []
    for i in range(len(data01)):
        diff.append(data01[i]-data02[i])
    #yx = sorted(zip(diff, namesTopo))    # To see which topology corresponds to which difference
    diff.sort()
    plt.axhline(y=0, color='r', linestyle='-')
    plt.plot(range(len(diff)), diff, label="Difference in maximum utilisation")
    plt.legend()
    plt.title("Difference in maximum link utilisation between SRPP and CG4SR for 3-SR")
    plt.ylabel("SRPP - CG4SR")
    plt.show()
