import csv
import matplotlib.pyplot as plt


# TO CHANGE DEPENDING ON THE COMPARISON NEEDED
file_a = "InverseCapacity/SRPP_solve.csv"
index_a_v = -2 # uMax index for 2-sr is [-2]
index_a_t = -4 # TotalTime index for 2-sr is [-4]
file_b = "InverseCapacity/SRPP_solve_noadj.csv"
index_b_v = -2 # uMax index for 2-sr-noadj is [-2]
index_b_t = -4 # TotalTime index for 2-sr-noadj is [-4]

if __name__ == "__main__":

    with open(file_a, "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_a = list(reader)
    
    with open(file_b, "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_b = list(reader)

    # I know both files have the same topology/demand in the same order so I can just compare the results in the order they arrive.
    plot_a = []
    plot_b = []
    diff = []
    nb_passed = 0
    for i in range(len(data_a)):
        try:
            val_a = float(data_a[i][index_a_v])
            val_b = float(data_b[i][index_b_v])
            if abs(val_a - val_b) > 0.01 and float(data_a[i][index_a_t]) < 1800 and float(data_b[i][index_b_t]) < 1800:
                plot_a.append(val_a)
                plot_b.append(val_b)
                diff.append(val_a - val_b)
        except ValueError:
            pass
    
    a1, b1, c1 = zip(*sorted(zip(plot_a, plot_b, diff), key=lambda pair : pair[0]))
    a2, b2, c2 = zip(*sorted(zip(plot_a, plot_b, diff), key=lambda pair : pair[1]))
    a3, b3, c3 = zip(*sorted(zip(plot_a, plot_b, diff), key=lambda pair : pair[2], reverse=True))

    ind = list(range(len(a1)))
    fig, axs = plt.subplots(2,2)
    width = 0.4
    axs[0,0].bar(ind, a1, width, label=file_a[:-4], color="tab:red")
    axs[0,0].bar([x+width for x in ind], b1, width, label=file_b[:-4] , color="tab:green")
    axs[0,0].legend()

    axs[0,1].bar(ind, a2, width, label=file_a[:-4], color="tab:red")
    axs[0,1].bar([x+width for x in ind], b2, width, label=file_b[:-4] , color="tab:green")

    axs[1,0].bar(ind, a3, width, label=file_a[:-4], color="tab:red")
    axs[1,0].bar([x+width for x in ind], b3, width, label=file_b[:-4] , color="tab:green")

    plt.show()