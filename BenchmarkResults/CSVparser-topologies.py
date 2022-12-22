import csv
import os

if __name__ == "__main__":
    header = ["Topology", "Nodes", "Edges", "2-SR preprocessing time", "3-SR preprocessing time", "3-SR preprocessing time",
        "2-SR useful paths", "3-SR useful paths", "4-SR useful paths", "2-SR percentage paths left", "3-SR percentage paths left",
        "4-SR percentage paths left", "2-SR useful paths adjacency", "3-SR useful paths adjacency"]
    
    topologies = []
    edges = []
    nodes = []
    _2SR = [[],[],[]] # time - paths - percentage
    _3SR = [[],[],[]]
    _4SR = [[],[],[]]
    adj2 = []
    adj3 = []

    # 2SR
    csvfile = open("topologies.2-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    for row in csvreader:
        topologies.append(row[0])
        nodes.append(row[4])
        edges.append(row[5])
        _2SR[0].append(row[2])
        _2SR[1].append(row[3])
        _2SR[2].append(row[7])
    
    # 3SR
    csvfile = open("topologies.3-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    for row in csvreader:
        _3SR[0].append(row[2])
        _3SR[1].append(row[3])
        _3SR[2].append(row[7])
    
    # 4SR
    csvfile = open("topologies.4-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    for row in csvreader:
        _4SR[0].append(row[2])
        _4SR[1].append(row[3])
        _4SR[2].append(row[7])

     # 2SR adjacency
    csvfile = open("preprocess-adj.2-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    cnt = 0
    for row in csvreader:
        while (topologies[cnt] != row[0]):
            adj2.append(_2SR[1][cnt])
            cnt += 1
        adj2.append(row[3])
        cnt += 1
    for i in range(cnt, len(_2SR[1])):
        adj2.append(_2SR[1][i])

    # 3SR adjacency
    csvfile = open("preprocess-adj.3-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    cnt = 0
    for row in csvreader:
        while (topologies[cnt] != row[0]):
            adj3.append(_3SR[1][cnt])
            cnt += 1
        adj3.append(row[3])
        cnt += 1
    for i in range(cnt, len(_3SR[1])):
        adj3.append(_3SR[1][i])

    csvfile = open ("all/topologies.result.csv", 'w', newline='')
    csvwriter = csv.writer(csvfile)
    csvwriter.writerow(header)
    for i in range(len(topologies)):
        row = [topologies[i], edges[i], nodes[i], _2SR[0][i], _3SR[0][i], _4SR[0][i] , _2SR[1][i],
        _3SR[1][i], _4SR[1][i], _2SR[2][i], _3SR[2][i], _4SR[2][i], adj2[i], adj3[i]]
        csvwriter.writerow(row)
