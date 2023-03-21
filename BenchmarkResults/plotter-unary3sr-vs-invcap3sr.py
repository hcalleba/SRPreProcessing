import csv
import matplotlib.pyplot as plt

if __name__ == "__main__":
    with open("Unary/SRPP_solve.csv", "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_a = list(reader)

    with open("InverseCapacity/SRPP_solve.csv", "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_b = list(reader)

    # I know both files have the same topology/demand in the same order so I can just compare the results in the order they arrive.
    plot_a = []
    plot_b = []
    diff = []
    for i in range(len(data_a)):
        # uMax index for unary-3-sr is [-1]
        # uMax index for invcap-3-sr is [-1]
        try:
            time_a = float(data_a[i][-3])
            time_b = float(data_b[i][-3])
        except ValueError:
            time_a = -1
        if time_a > 0 and time_a < 1800 and time_b < 1800:
            val_a = float(data_a[i][-1])
            val_b = float(data_b[i][-1])
            if abs(val_b - val_a) > 0.01:
                plot_a.append(val_a)
                plot_b.append(val_b)
                diff.append(val_b - val_a)
            
    a, b, c = zip(*sorted(zip(plot_b, plot_a, diff), key=lambda pair : pair[2]))

    ind = list(range(len(a)))
    width = 0.4
    plt.bar(ind, a, width, label="Unary 3-SRPP")
    plt.bar([x+width for x in ind], b, width, label="InvCap 3-SRPP")
    plt.legend()
    plt.show()

    a, b = zip(*sorted(zip(plot_b, plot_a), key=lambda pair : pair[0]))

    ind = list(range(len(a)))
    width = 0.4
    plt.bar(ind, a, width, label="Unary 3-SRPP")
    plt.bar([x+width for x in ind], b, width, label="InvCap 3-SRPP")
    plt.legend()
    plt.show()