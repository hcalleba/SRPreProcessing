from cProfile import label
import csv
import matplotlib.pyplot as plt
import numpy as np

unknown = 0.0

# METTRE UNKNOWN QD LE TEMPS D'EXEC EST > 3600

if __name__ == "__main__":
    topology = []
    demand = []
    v_a = []
    v_b = []

    t_topology = []
    t_demand = []
    t_a = []
    t_b = []

    # Values file
    csvfile = open("value.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)    # skip header
    for row in csvreader:
        topology.append(row[0])
        demand.append(row[1])
        v_a.append(row[7])
        v_b.append(row[9])
    
    # Time file
    csvfile = open("time.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)    # skip header
    for row in csvreader:
        t_a.append(row[6])
        t_b.append(row[8])
    
    for i in range(len(v_b)):
        if(v_a[i] == "-"):
            v_a[i] = unknown
        else:
            v_a[i] = float(v_a[i])
        if(v_b[i] == "-"):
            v_b[i] = unknown
        else:
            v_b[i] = float(v_b[i])
    
    for i in range(len(t_a)):
        if(t_a[i] == "-" or float(t_a[i]) > 1800):
            v_a[i] = unknown
        if(t_b[i] == "-" or float(t_b[i]) > 1800):
            v_b[i] = unknown
    
    for i in range(len(v_a))[::-1]:
        minimum = min(v_a[i], v_b[i])
        maximum = max(v_a[i], v_b[i])
        if (maximum-minimum < 0.01) or minimum == unknown:
            del topology[i]
            del demand[i]
            del v_a[i]
            del v_b[i]
    
    a, b, c = zip(*sorted(zip(v_a, v_b, topology), key=lambda pair: pair[1]))
    print(c)
    
    ind = np.arange(len(v_a))
    width = 0.4
    plt.bar(ind, a, width, label="2-SR", color="tab:red")
    plt.bar(ind+width, b, width, label="3-SR" , color="tab:green")
    #plt.ylim(0.87, 2)
    plt.legend()
    plt.show()