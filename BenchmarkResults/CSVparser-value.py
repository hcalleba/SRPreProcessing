import csv
import os

if __name__ == "__main__":
    header = ["Topology", "DemandFile", "InvCap", "TabuIGPWO", "MIP", "2-SR full", "3-SR full", "2-SRPP", "2-SRPP-adj", "3-SRPP", "3-SRPP-adj", "2-CG4SR", "3-CG4SR"]

    Topologies = []
    DemandFiles = []
    InvCap = []
    TabuIGPWO = []
    MIP = []
    _2SRTEP = []
    _3SRTEP = []
    _2SRPP = []
    _2SRPPadj = []
    _3SRPP = []
    _3SRPPadj = []
    _2CG4SR = []
    _3CG4SR = []

    # InvCap + MIP
    csvfile = open("MIP-NO-SPLIT.2-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    for row in csvreader:
        Topologies.append(row[0])
        DemandFiles.append(row[1])
        InvCap.append(row[4])
        MIP.append(row[5])
    # TabuIGPWO
    csvfile = open("OSPF.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    for row in csvreader:
        TabuIGPWO.append(row[5])
    # 2-SR full
    csvfile = open("full.2-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    for row in csvreader:
        _2SRTEP.append(row[6])
    # 3-SR full
    csvfile = open("full.3-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    for row in csvreader:
        _3SRTEP.append(row[6])
    # 2-SRPP
    csvfile = open("SRPP.2-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    for row in csvreader:
        _2SRPP.append(row[6])
    # 3-SRPP
    csvfile = open("SRPP.3-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    for row in csvreader:
        _3SRPP.append(row[6])
    # 2-CG4SR
    csvfile = open("CG4SR.2-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    for row in csvreader:
        _2CG4SR.append(row[5])
    # 3-CG4SR
    csvfile = open("CG4SR.3-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    for row in csvreader:
        _3CG4SR.append(row[5])
    # 2-SRPP adjacency
    csvfile = open("loadFromFile-adj.2-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    cnt = 0
    for row in csvreader:
        while (Topologies[cnt] != row[0]):
            _2SRPPadj.append(_2SRPP[cnt])
            cnt += 1
        _2SRPPadj.append(row[6])
        cnt += 1
    for i in range(cnt, len(_2SRPP)):
        _2SRPPadj.append(_2SRPP[i])
    # 3-SRPP adjacency
    csvfile = open("loadFromFile-adj.3-SR.result.csv", 'r')
    csvreader = csv.reader(csvfile)
    next(csvreader)
    cnt = 0
    for row in csvreader:
        while (Topologies[cnt] != row[0]):
            _3SRPPadj.append(_3SRPP[cnt])
            cnt += 1
        _3SRPPadj.append(row[6])
        cnt += 1
    for i in range(cnt, len(_3SRPP)):
        _3SRPPadj.append(_3SRPP[i])


    print(len(Topologies))
    print(len(DemandFiles))
    print(len(InvCap))
    print(len(TabuIGPWO))
    print(len(MIP))
    print(len(_2SRTEP))
    print(len(_3SRTEP))
    print(len(_2SRPP))
    print(len(_2SRPPadj))
    print(len(_3SRPP))
    print(len(_3SRPPadj))
    print(len(_2CG4SR))
    print(len(_3CG4SR))


    csvfile = open ("all/value.result.csv", 'w', newline='')
    csvwriter = csv.writer(csvfile)
    csvwriter.writerow(header)
    for i in range(len(Topologies)):
        row = [Topologies[i], DemandFiles[i], InvCap[i], TabuIGPWO[i], MIP[i], _2SRTEP[i],
               _3SRTEP[i], _2SRPP[i], _2SRPPadj[i], _3SRPP[i], _3SRPPadj[i], _2CG4SR[i], _3CG4SR[i]]
        csvwriter.writerow(row)