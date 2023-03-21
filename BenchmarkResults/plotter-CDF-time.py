import csv
import matplotlib.pyplot as plt

def add_to_list(lst, line, idx, treshold=1800):
    try:
        num = float(line[idx])
        if (num <= treshold):
            lst.append(num)
    except ValueError:
        pass


if __name__ == "__main__":
    directory = "InverseCapacity"
    filenames = ["SRPP_solve.csv", "CG4SR.csv", "full.csv", "MIP-NO-SPLIT.csv"]

    SRPP2 = []
    SRPP3 = []
    CG4SR2 = []
    CG4SR3 = []
    full2 = []
    full3 = []
    MIP2 = []

    with open(directory + "/" + filenames[0]) as f:
        reader = csv.reader(f)
        next(reader)
        for line in reader:
            add_to_list(SRPP2, line, 4)
            add_to_list(SRPP3, line, 5)

    with open(directory + "/" + filenames[1]) as f:
        reader = csv.reader(f)
        next(reader)
        for line in reader:
            add_to_list(CG4SR2, line, 2)
            add_to_list(CG4SR3, line, 3)
    
    with open(directory + "/" + filenames[2]) as f:
        reader = csv.reader(f)
        next(reader)
        for line in reader:
            add_to_list(full2, line, 2)
            add_to_list(full3, line, 3)
    
    with open(directory + "/" + filenames[3]) as f:
        reader = csv.reader(f)
        next(reader)
        for line in reader:
            add_to_list(MIP2, line, 2)
    
    SRPP2.sort()
    SRPP3.sort()
    CG4SR2.sort()
    CG4SR3.sort()
    full2.sort()
    full3.sort()
    MIP2.sort()
    
    fig, axs = plt.subplots(1, 2)
    axs[0].plot(SRPP2, range(len(SRPP2)), label="SRPP")
    axs[0].plot(CG4SR2, range(len(CG4SR2)), label="CG4SR")
    axs[0].plot(full2, range(len(full2)), label="full")
    axs[0].plot(MIP2, range(len(MIP2)), label="MIP")

    axs[1].plot(SRPP3, range(len(SRPP3)), label="SRPP")
    axs[1].plot(CG4SR3, range(len(CG4SR3)), label="CG4SR")
    axs[1].plot(full3, range(len(full3)), label="full")

    axs[0].set_title("2-SR")
    axs[1].set_title("3-SR")
    axs[0].legend()
    axs[1].legend()
    axs[0].set(xscale="log", xlabel="Solve time (seconds)", ylabel="Number of instances solved")
    axs[1].set(xscale="log", xlabel="Solve time (seconds)")
    #axs[0].xscale('log')
    #axs[1].xscale('log')
    #fig.ylabel("Number of instances solved")
    #fig.xlabel("Solve time (seconds)")
    
    plt.show()