import csv
import matplotlib.pyplot as plt
import matplotlib.rcsetup

# TO CHANGE DEPENDING ON THE COMPARISON NEEDED
file_a = "Unary/SRPP_solve.csv"
index_a_v = -2 # uMax index for template 1 is [-2]
index_a_t = -4 # TotalTime index for template 1 is [-4]
name_a = "Unary"

file_b = "Unary_heuristic/SRPP_solve_2x+1.csv"
index_b_v = -2 # uMax index for template 2 is [-2]
index_b_t = -4 # TotalTime index for template 2 is [-4]
name_b = "Unary heuristic 2x+1"

if __name__ == "__main__":

    font = {'family' : 'DejaVu Sans',
        'weight' : 'normal',
        'size'   : 42}
    matplotlib.rc('font', **font)

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
    
    a2, b2, c2 = zip(*sorted(zip(plot_a, plot_b, diff), key=lambda pair : pair[2], reverse=True))

    ind = list(range(len(a2)))
    width = 0.4

    plt.bar(ind, a2, width, label=name_a, color="tab:red")
    plt.bar([x+width for x in ind], b2, width, label=name_b , color="tab:green")
    plt.legend()

    def on_resize(event):
        plt.tight_layout()
        plt.gcf().canvas.draw()
    cid = plt.gcf().canvas.mpl_connect('resize_event', on_resize)

    plt.show()