import csv
import matplotlib.pyplot as plt


# TO CHANGE DEPENDING ON THE COMPARISON NEEDED
file = "Unary_heuristic/SRPP_solve_1.1x+3.csv"
tindex = 5 # time index for 2-sr unary

def in_range(val, min=0, max=1800):
    return val >= min and val <= max

if __name__ == "__main__":
    
    with open(file, "r") as f:
        reader = csv.reader(f)
        next(reader)        # Skip header
        data_unary = list(reader)
    
    number_of_instances = 0
    for i in range(len(data_unary)):
        try:
            val = float(data_unary[i][tindex])
        except ValueError:
            val = -1
        if (in_range(val)):
            number_of_instances += 1
        
    if number_of_instances > 0:
        print("Number of instances: " + str(number_of_instances))