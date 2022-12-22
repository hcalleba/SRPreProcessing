from cProfile import label
import csv
import matplotlib.pyplot as plt
import numpy as np

unknown = 0.0

# METTRE UNKNOWN QD LE TEMPS D'EXEC EST > 3600

if __name__ == "__main__":
    topology = []
    demand = []
    v_SR2 = []
    v_tabu = []

    t_topology = []
    t_demand = []
    t_SR2 = []
    t_tabu = []

    # Values file
    csvfile = open("value.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)    # skip header
    for row in csvreader:
        topology.append(row[0])
        demand.append(row[1])
        v_tabu.append(row[3])
        v_SR2.append(row[7])
    
    # Time file
    csvfile = open("time.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)    # skip header
    for row in csvreader:
        t_tabu.append(row[2])
        t_SR2.append(row[6])
    
    for i in range(len(v_tabu)):
        if(v_tabu[i] == "-"):
            v_tabu[i] = unknown
        else:
            v_tabu[i] = float(v_tabu[i])
        if(v_SR2[i] == "-"):
            v_SR2[i] = unknown
        else:
            v_SR2[i] = float(v_SR2[i])
    
    for i in range(len(t_SR2)):
        if(t_SR2[i] == "-" or float(t_SR2[i]) > 3600):
            v_SR2[i] = unknown
    
    for i in range(len(v_tabu))[::-1]:
        minimum = min(v_tabu[i], v_SR2[i])
        maximum = max(v_tabu[i], v_SR2[i])
        if (maximum-minimum < 0.01) or minimum == unknown:
            del v_tabu[i]
            del v_SR2[i]
    
    a, b = zip(*sorted(zip(v_SR2, v_tabu), key=lambda pair: pair[1]))
    
    ind = np.arange(len(v_SR2))
    width = 0.4
    plt.bar(ind, a, width, label="2-SRPP-adj")
    plt.bar(ind+width, b, width, label="TabuIGPWO")
    #plt.ylim(0.87, 2)
    plt.legend()
    plt.show()