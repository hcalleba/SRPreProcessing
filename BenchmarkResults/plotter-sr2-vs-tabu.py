import csv
import matplotlib.pyplot as plt

if __name__ == "__main__":
    with open("OSPF/tabuIGPWO.csv", "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_tabu = list(reader)

    with open("InverseCapacity/SRPP_solve.csv", "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_sr2 = list(reader)

    # I know both files have the same topology/demand in the same order so I can just compare the results in the order they arrive.
    plot_sr2 = []
    plot_tabu = []
    for i in range(len(data_tabu)):
        # uMax index for 2-sr is [-2]
        # uMax index for tabu is [-1]
        # I also know that in both cases all I got each time an optimal solution so I do not need to look at the time taken.
        valtabu = float(data_tabu[i][-1])
        valsr2 = float(data_sr2[i][-2])
        if abs(valsr2 - valtabu) > 0.01:
            plot_tabu.append(valtabu)
            plot_sr2.append(valsr2)
    a, b = zip(*sorted(zip(plot_sr2, plot_tabu), key=lambda pair : pair[0]))

    ind = list(range(len(a)))
    width = 0.4
    plt.bar(ind, a, width, label="2-SRPP-adj")
    plt.bar([x+width for x in ind], b, width, label="TabuIGPWO")
    plt.legend()
    plt.show()