import csv
import matplotlib.pyplot as plt
import matplotlib.rcsetup

if __name__ == "__main__":
    font = {'family' : 'DejaVu Sans',
        'weight' : 'normal',
        'size'   : 22}
    matplotlib.rc('font', **font)
    
    with open("OSPF/tabuIGPWO.csv", "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_tabu = list(reader)

    with open("OSPF_SR/SRPP_solve.csv", "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_tabusr2 = list(reader)

    # I know both files have the same topology/demand in the same order so I can just compare the results in the order they arrive.
    plot_tabu = []
    plot_tabusr2 = []
    diff = []
    for i in range(len(data_tabu)):
        # uMax index for 2-sr is [-2]
        # uMax index for tabu_2sr is [-2]
        # I also know that in both cases all I got each time an optimal solution so I do not need to look at the time taken.
        valtabu = float(data_tabu[i][-1])
        valtabusr2 = float(data_tabusr2[i][-2])
        if abs(valtabusr2 - valtabu) > 0.01:
            plot_tabu.append(valtabu)
            plot_tabusr2.append(valtabusr2)
            diff.append(valtabu - valtabusr2)

    a, b, c = zip(*sorted(zip(plot_tabusr2, plot_tabu, diff), key=lambda pair : pair[2]))

    ind = list(range(len(a)))
    width = 0.4
    plt.bar(ind, a, width, label="TabuIGPWO + 2-SR", color="goldenrod")
    plt.bar([x+width for x in ind], b, width, label="TabuIGPWO", color="tab:brown")
    plt.legend()
    plt.xlim(-0.6, len(a))
    plt.show()