import csv
import matplotlib.pyplot as plt
import matplotlib.rcsetup

if __name__ == "__main__":
    font = {'family' : 'DejaVu Sans',
        'weight' : 'normal',
        'size'   : 22}
    matplotlib.rc('font', **font)

    with open("InverseCapacity/SRPP_solve.csv", "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_sr = list(reader)

    # I know both files have the same topology/demand in the same order so I can just compare the results in the order they arrive.
    plot_sr2 = []
    plot_sr3 = []
    diff = []
    for i in range(len(data_sr)):
        # uMax index for 2-sr is [-2]
        # uMax index for 3-sr is [-1]
        # solvetime index for 2-sr is [2]
        # solvetime index for 3-sr is [3]
        # Some values might not be correct, I need to check that the time used for the solve part is < 1800 seconds
        
        try:
            valsr2 = float(data_sr[i][-2])
            valsr3 = float(data_sr[i][-1])
            if abs(valsr2 - valsr3) > 0.01 and float(data_sr[i][2]) < 1800 and float(data_sr[i][3]) < 1800:
                plot_sr2.append(valsr2)
                plot_sr3.append(valsr3)
                diff.append(valsr2 - valsr3)
                print("{} {}".format(data_sr[i][0], data_sr[i][1]))
        except ValueError:
            pass
    
    a, b, c = zip(*sorted(zip(plot_sr2, plot_sr3, diff), key=lambda pair : pair[2]))

    ind = list(range(len(a)))
    width = 0.4
    plt.bar([x+width for x in ind], b, width, label="3-SR" , color="tab:orange")
    plt.bar(ind, a, width, label="2-SR", color="tab:blue")
    plt.legend()
    plt.xlim(-0.6, len(a))
    plt.show()