import csv
import matplotlib.pyplot as plt
import matplotlib.rcsetup

def add_to_list(lst, line, idx, treshold=1800):
    try:
        num = float(line[idx])
        if (num <= treshold):
            lst.append(num)
    except ValueError:
        pass


if __name__ == "__main__":

    invcap2 = []
    invcap3 = []
    unary2 = []
    unary3 = []
    heu2x2 = []
    heu2x3 = []
    heu11x2 = []
    heu11x3 = []

    all = [invcap2, invcap3, unary2, unary3, heu2x2, heu2x3, heu11x2, heu11x3]

    font = {'family' : 'DejaVu Sans',
        'weight' : 'normal',
        'size'   : 22}
    matplotlib.rc('font', **font)

    with open("InverseCapacity/SRPP_solve_noadj.csv") as f:
        reader = csv.reader(f)
        next(reader)
        for line in reader:
            add_to_list(invcap2, line, 4)
            add_to_list(invcap3, line, 5)

    with open("Unary/SRPP_solve.csv") as f:
        reader = csv.reader(f)
        next(reader)
        for line in reader:
            add_to_list(unary2, line, 4)
            add_to_list(unary3, line, 5)
    
    with open("Unary_heuristic/SRPP_solve_2x+1.csv") as f:
        reader = csv.reader(f)
        next(reader)
        for line in reader:
            add_to_list(heu2x2, line, 4)
            add_to_list(heu2x3, line, 5)
    
    with open("Unary_heuristic/SRPP_solve_1.1x+3.csv") as f:
        reader = csv.reader(f)
        next(reader)
        for line in reader:
            add_to_list(heu11x2, line, 4)
            add_to_list(heu11x3, line, 5)
    
    for i in all:
        i.sort()
    
    fig, axs = plt.subplots(1, 2)
    #axs[0].plot(invcap2, range(len(invcap2)), label="invcap")
    axs[0].plot(unary2, range(len(unary2)), label="2-SR")
    axs[0].plot(heu2x2, range(len(heu2x2)), label="2x+1")
    axs[0].plot(heu11x2, range(len(heu11x2)), label="1.1x+3")

    #axs[1].plot(invcap3, range(len(invcap3)), label="invcap")
    axs[1].plot(unary3, range(len(unary3)), label="3-SR")
    axs[1].plot(heu2x3, range(len(heu2x3)), label="2x+1")
    axs[1].plot(heu11x3, range(len(heu11x3)), label="1.1x+3")

    axs[0].set_title("2-SR")
    axs[1].set_title("3-SR")
    axs[0].legend()
    axs[1].legend()
    axs[0].set(xscale="log", xlabel="Solve time (seconds)", ylabel="Number of instances solved")
    axs[1].set(xscale="log", xlabel="Solve time (seconds)")
    #plt.tight_layout()    
    plt.show()