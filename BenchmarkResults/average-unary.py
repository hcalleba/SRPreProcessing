import csv
import matplotlib.pyplot as plt


# TO CHANGE DEPENDING ON THE COMPARISON NEEDED
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
    sum_2unary = 0
    sum_2heuristic2x = 0
    sum_2heuristic11x = 0

    sum_3unary = 0
    sum_3heuristic2x = 0
    sum_3heuristic11x = 0

    number_of_instances = 0
    for i in range(len(data_unary)):
        try:
            val_2unary = float(data_unary[i][index_2unary])
            val_2heuristic2x = float(data_heuristic2x[i][index_2heuristic2x])
            val_2heuristic11x = float(data_heuristic11x[i][index_2heuristic11x])

            val_3unary = float(data_unary[i][index_3unary])
            val_3heuristic2x = float(data_heuristic2x[i][index_3heuristic2x])
            val_3heuristic11x = float(data_heuristic11x[i][index_3heuristic11x])


            time_2unary = float(data_unary[i][tindex_2unary])
            time_2heuristic2x = float(data_heuristic2x[i][tindex_2heuristic2x])
            time_2heuristic11x = float(data_heuristic11x[i][tindex_2heuristic11x])

            time_3unary = float(data_unary[i][tindex_3unary])
            time_3heuristic2x = float(data_heuristic2x[i][tindex_3heuristic2x])
            time_3heuristic11x = float(data_heuristic11x[i][tindex_3heuristic11x])
        except ValueError:
            time_2unary = -1
        if (in_range(time_2unary) and in_range(time_3unary) and in_range(time_2heuristic2x) and in_range(time_3heuristic2x) and in_range(time_2heuristic11x) and in_range(time_3heuristic11x)):
            sum_2unary += val_2unary
            sum_2heuristic2x += val_2heuristic2x
            sum_2heuristic11x += val_2heuristic11x

            sum_3unary += val_3unary
            sum_3heuristic2x += val_3heuristic2x
            sum_3heuristic11x += val_3heuristic11x

            number_of_instances += 1
        
    if number_of_instances > 0:
        print("Number of instances: " + str(number_of_instances))
        print("2-SRPP-unary: " + str(sum_2unary/number_of_instances))
        print("2-heuristic2x: " + str(sum_2heuristic2x/number_of_instances))
        print("2-heuristic11x: " + str(sum_2heuristic11x/number_of_instances))


        print("3-SRPP-unary: " + str(sum_3unary/number_of_instances))
        print("3-heuristic2x: " + str(sum_3heuristic2x/number_of_instances))
        print("3-heuristic11x: " + str(sum_3heuristic11x/number_of_instances))