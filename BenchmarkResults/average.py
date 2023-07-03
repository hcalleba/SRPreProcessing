import csv
import matplotlib.pyplot as plt


# TO CHANGE DEPENDING ON THE COMPARISON NEEDED
file_srpp = "InverseCapacity/SRPP_solve.csv"
index_2srpp = -2 # uMax index for 2-sr
index_3srpp = -1 # uMax index for 3-sr
tindex_2srpp = -4 # time index for 2-sr
tindex_3srpp = -3 # time index for 3-sr

file_srpp_noadj = "InverseCapacity/SRPP_solve_noadj.csv"
index_2srpp_noadj = -2 # uMax index for 2-sr-noadj
index_3srpp_noadj = -1 # uMax index for 2-sr-noadj
tindex_2srpp_noadj = -4 # time index for 2-sr-noadj
tindex_3srpp_noadj = -3 # time index for 3-sr-noadj

file_cg4sr = "InverseCapacity/CG4SR.csv"
index_2cg4sr = -2 # uMax index for 2-cg4sr
index_3cg4sr = -1 # uMax index for 3-cg4sr
tindex_2cg4sr = -4 # time index for 2-cg4sr
tindex_3cg4sr = -3 # time index for 3-cg4sr

file_tabu = "OSPF/tabuIGPWO.csv"
index_post_tabu = -1 # uMax index for tabuIGPWO
index_pre_tabu = -2 # uMax index for pre optimisation

file_tabusr = "OSPF_SR/SRPP_solve.csv"
index_2tabusr = -2 # uMax index for tabu + 2-sr
index_3tabusr = -1 # uMax index for tabu + 3-sr
tindex_2tabusr = -4 # time index for tabu + 2-sr
tindex_3tabusr = -3 # time index for tabu + 3-sr

file_unary = "Unary/SRPP_solve.csv"
index_2unary = -2 # uMax index for 2-sr unary
index_3unary = -1 # uMax index for 3-sr unary
tindex_2unary = -4 # time index for 2-sr unary
tindex_3unary = -3 # time index for 3-sr unary

file_heuristic2x = "Unary_heuristic/SRPP_solve_2x+1.csv"
index_2heuristic2x = -2 # uMax index for 2-sr heuristic2x+1
index_3heuristic2x = -1 # uMax index for 3-sr heuristic2x+1
tindex_2heuristic2x = -4 # time index for 2-sr heuristic2x+1
tindex_3heuristic2x = -3 # time index for 3-sr heuristic2x+1

file_heuristic11x = "Unary_heuristic/SRPP_solve_1.1x+3.csv"
index_2heuristic11x = -2 # uMax index for 2-sr heuristic1.1x+3
index_3heuristic11x = -1 # uMax index for 3-sr heuristic1.1x+3
tindex_2heuristic11x = -4 # time index for 2-sr heuristic1.1x+3
tindex_3heuristic11x = -3 # time index for 3-sr heuristic1.1x+3

def in_range(val, min=0, max=1800):
    return val >= min and val <= max

if __name__ == "__main__":

    with open(file_srpp, "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_srpp = list(reader)
    
    with open(file_srpp_noadj, "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_srpp_noadj = list(reader)
    
    with open(file_cg4sr, "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_cg4sr = list(reader)
    
    with open (file_tabu, "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_tabu = list(reader)

    with open(file_tabusr, "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_tabusr = list(reader)
    
    with open(file_unary, "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_unary = list(reader)
    
    with open(file_heuristic2x, "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_heuristic2x = list(reader)
    
    with open(file_heuristic11x, "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_heuristic11x = list(reader)
    
    # I know all files have the same topology/demand in the same order so I can just compare the results in the order they arrive.
    sum_2srpp = 0
    sum_2srpp_noadj = 0
    sum_2cg4sr = 0
    sum_post_tabu = 0
    sum_2tabusr = 0
    sum_2unary = 0
    sum_2heuristic2x = 0
    sum_2heuristic11x = 0

    sum_3srpp = 0
    sum_3srpp_noadj = 0
    sum_3cg4sr = 0
    sum_pre_tabu = 0
    sum_3tabusr = 0
    sum_3unary = 0
    sum_3heuristic2x = 0
    sum_3heuristic11x = 0

    number_of_instances = 0
    for i in range(len(data_srpp)):
        try:
            val_2srpp = float(data_srpp[i][index_2srpp])
            val_2srpp_noadj = float(data_srpp_noadj[i][index_2srpp_noadj])
            val_2cg4sr = float(data_cg4sr[i][index_2cg4sr])
            val_post_tabu = float(data_tabu[i][index_post_tabu])
            val_2tabusr = float(data_tabusr[i][index_2tabusr])
            val_2unary = float(data_unary[i][index_2unary])
            val_2heuristic2x = float(data_heuristic2x[i][index_2heuristic2x])
            val_2heuristic11x = float(data_heuristic11x[i][index_2heuristic11x])

            val_3srpp = float(data_srpp[i][index_3srpp])
            val_3srpp_noadj = float(data_srpp_noadj[i][index_3srpp_noadj])
            val_3cg4sr = float(data_cg4sr[i][index_3cg4sr])
            val_pre_tabu = float(data_tabu[i][index_pre_tabu])
            val_3tabusr = float(data_tabusr[i][index_3tabusr])
            val_3unary = float(data_unary[i][index_3unary])
            val_3heuristic2x = float(data_heuristic2x[i][index_3heuristic2x])
            val_3heuristic11x = float(data_heuristic11x[i][index_3heuristic11x])


            time_2srpp = float(data_srpp[i][tindex_2srpp])
            time_2srpp_noadj = float(data_srpp_noadj[i][tindex_2srpp_noadj])
            time_2cg4sr = float(data_cg4sr[i][tindex_2cg4sr])
            time_2tabusr = float(data_tabusr[i][tindex_2tabusr])
            time_2unary = float(data_unary[i][tindex_2unary])
            time_2heuristic2x = float(data_heuristic2x[i][tindex_2heuristic2x])
            time_2heuristic11x = float(data_heuristic11x[i][tindex_2heuristic11x])

            time_3srpp = float(data_srpp[i][tindex_3srpp])
            time_3srpp_noadj = float(data_srpp_noadj[i][tindex_3srpp_noadj])
            time_3cg4sr = float(data_cg4sr[i][tindex_3cg4sr])
            time_3tabusr = float(data_tabusr[i][tindex_3tabusr])
            time_3unary = float(data_unary[i][tindex_3unary])
            time_3heuristic2x = float(data_heuristic2x[i][tindex_3heuristic2x])
            time_3heuristic11x = float(data_heuristic11x[i][tindex_3heuristic11x])
        except ValueError:
            time_2srpp = -1
        if (in_range(time_2srpp) and in_range(time_2srpp_noadj) and in_range(time_2cg4sr) and in_range(time_2tabusr) and in_range(time_2unary) and 
            in_range(time_3srpp) and in_range(time_3srpp_noadj) and in_range(time_3cg4sr) and in_range(time_3tabusr) and in_range(time_3unary)):
            sum_2srpp += val_2srpp
            sum_2srpp_noadj += val_2srpp_noadj
            sum_2cg4sr += val_2cg4sr
            sum_post_tabu += val_post_tabu
            sum_2tabusr += val_2tabusr
            sum_2unary += val_2unary
            sum_2heuristic2x += val_2heuristic2x
            sum_2heuristic11x += val_2heuristic11x

            sum_3srpp += val_3srpp
            sum_3srpp_noadj += val_3srpp_noadj
            sum_3cg4sr += val_3cg4sr
            sum_pre_tabu += val_pre_tabu
            sum_3tabusr += val_3tabusr
            sum_3unary += val_3unary
            sum_3heuristic2x += val_3heuristic2x
            sum_3heuristic11x += val_3heuristic11x

            number_of_instances += 1
        
    if number_of_instances > 0:
        print("Number of instances: " + str(number_of_instances))
        print("2-SRPP: " + str(sum_2srpp/number_of_instances))
        print("2-SRPP-noadj: " + str(sum_2srpp_noadj/number_of_instances))
        print("2-CG4SR: " + str(sum_2cg4sr/number_of_instances))
        print("post-TabuIGPWO: " + str(sum_post_tabu/number_of_instances))
        print("2-SRPP-tabu: " + str(sum_2tabusr/number_of_instances))
        print("2-SRPP-unary: " + str(sum_2unary/number_of_instances))
        print("2-heuristic2x: " + str(sum_2heuristic2x/number_of_instances))
        print("2-heuristic11x: " + str(sum_2heuristic11x/number_of_instances))

        print("3-SRPP: " + str(sum_3srpp/number_of_instances))
        print("3-SRPP-noadj: " + str(sum_3srpp_noadj/number_of_instances))
        print("3-CG4SR: " + str(sum_3cg4sr/number_of_instances))
        print("pre-TabuIGPWO: " + str(sum_pre_tabu/number_of_instances))
        print("3-SRPP-tabu: " + str(sum_3tabusr/number_of_instances))
        print("3-SRPP-unary: " + str(sum_3unary/number_of_instances))
        print("3-heuristic2x: " + str(sum_3heuristic2x/number_of_instances))
        print("3-heuristic11x: " + str(sum_3heuristic11x/number_of_instances))