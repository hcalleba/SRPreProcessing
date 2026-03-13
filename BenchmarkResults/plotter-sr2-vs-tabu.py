import csv
import matplotlib.pyplot as plt
import matplotlib.rcsetup

if __name__ == "__main__":
    font = {'family' : 'DejaVu Sans',
        'weight' : 'normal',
        'size'   : 30}
    matplotlib.rc('font', **font)
    
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
    diff = []
    for i in range(len(data_tabu)):
        # uMax index for 2-sr is [-2]
        # uMax index for tabu is [-1]
        # I also know that in both cases all I got each time an optimal solution so I do not need to look at the time taken.
        valtabu = float(data_tabu[i][-1])
        valsr2 = float(data_sr2[i][-2])
        if abs(valsr2 - valtabu) > 0.01:
            plot_tabu.append(valtabu)
            plot_sr2.append(valsr2)
            diff.append(valtabu - valsr2)
    a, b, c = zip(*sorted(zip(plot_sr2, plot_tabu, diff), key=lambda pair : pair[2]))

    ind = list(range(len(a)))
    width = 0.4
    plt.bar(ind, a, width, label="2-SR", color="tab:blue")
    plt.bar([x+width for x in ind], b, width, label="TabuIGPWO", color="tab:brown")
    plt.legend()
    plt.xlim(-2, len(a)+1+width)
    plt.tight_layout()

    def on_resize(event):
        plt.tight_layout()
        plt.gcf().canvas.draw()

    cid = plt.gcf().canvas.mpl_connect('resize_event', on_resize)

    plt.show()