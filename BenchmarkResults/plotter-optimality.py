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
    v_SR3 = []
    v_tabu = []
    v_CG4SR3 = []

    t_topology = []
    t_demand = []
    t_SR2 = []
    t_SR3 = []
    t_tabu = []
    t_CG4SR3 = []

    # Values file
    csvfile = open("value.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)    # skip header
    for row in csvreader:
        topology.append(row[0])
        demand.append(row[1])
        v_tabu.append(row[3])
        v_SR2.append(row[7])
        v_SR3.append(row[9])
        v_CG4SR3.append(row[12])
    
    # Time file
    csvfile = open("time.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)    # skip header
    for row in csvreader:
        t_tabu.append(row[2])
        t_SR2.append(row[6])
        t_SR3.append(row[8])
        t_CG4SR3.append(row[11])
    
    for i in range(len(v_tabu)):
        if(v_tabu[i] == "-"):
            v_tabu[i] = unknown
        else:
            v_tabu[i] = float(v_tabu[i])
        if(v_SR2[i] == "-"):
            v_SR2[i] = unknown
        else:
            v_SR2[i] = float(v_SR2[i])
        if(v_SR3[i] == "-"):
            v_SR3[i] = unknown
        else:
            v_SR3[i] = float(v_SR3[i])
        if(v_CG4SR3[i] == "-"):
            v_CG4SR3[i] = unknown
        else:
            v_CG4SR3[i] = float(v_CG4SR3[i])
    
    for i in range(len(t_SR2)):
        if(t_SR2[i] == "-" or float(t_SR2[i]) > 3600):
            v_SR2[i] = unknown
        if(t_SR3[i] == "-" or float(t_SR3[i]) > 3600):
            v_SR3[i] = unknown
        if(t_CG4SR3[i] == "-" or float(t_CG4SR3[i]) > 3600):
            v_CG4SR3[i] = unknown
    
    for i in range(len(v_tabu))[::-1]:
        minimum = min(v_tabu[i], v_SR2[i], v_SR3[i], v_CG4SR3[i])
        maximum = max(v_tabu[i], v_SR2[i], v_SR3[i], v_CG4SR3[i])
        if (maximum-minimum < 0.05) or minimum == unknown:
            del v_tabu[i]
            del v_SR2[i]
            del v_SR3[i]
            del v_CG4SR3[i]
    
    ind = np.arange(len(v_SR2))
    width = 0.3
    plt.bar(ind, v_SR2, width, label="2-SRPP")
    plt.bar(ind+width, v_SR3, width, label="3-SRPP")
    plt.bar(ind+2*width, v_tabu, width, label="tabu")
    #plt.ylim(0.87, 2)
    plt.legend()
    plt.show()