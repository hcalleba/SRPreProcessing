import csv
import matplotlib.pyplot as plt
import matplotlib.rcsetup

if __name__ == "__main__":
    font = {'family' : 'DejaVu Sans',
        'weight' : 'normal',
        'size'   : 42}
    matplotlib.rc('font', **font)
    
    with open("InverseCapacity/SRPP_solve.csv", "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_a = list(reader)

    with open("InverseCapacity/SRPP_solve_noadj.csv", "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_b = list(reader)

    # I know both files have the same topology/demand in the same order so I can just compare the results in the order they arrive.
    plot_a = []
    plot_b = []
    diff = []
    for i in range(len(data_a)):
        # uMax index for 2-sr is [-2]
        # uMax index for 2-sr-noadj is [-2]
        # I also know that in both cases all I got each time an optimal solution so I do not need to look at the time taken.
        val_a = float(data_a[i][-2])
        val_b = float(data_b[i][-2])
        if abs(val_b - val_a) > 0.01:
            plot_a.append(val_a)
            plot_b.append(val_b)
            diff.append(val_b - val_a)
            
    a, b, c = zip(*sorted(zip(plot_a, plot_b, diff), key=lambda pair : pair[2]))

    ind = list(range(len(a)))
    width = 0.4
    plt.bar(ind, a, width, label="2-SR", color="tab:blue")
    plt.bar([x+width for x in ind], b, width, label="2-SR no adjacency", color="tab:red")
    plt.legend()
    plt.xlim(-0.6, len(a))
    plt.show()