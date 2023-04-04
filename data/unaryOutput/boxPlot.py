import os
import matplotlib.pyplot as plt

initialweightsarray = []

def addToArray(shortestDistance, realDistance, array):
    if (len(array) < shortestDistance):
        for _ in range(len(array), shortestDistance):
            array.append([])
    array[shortestDistance - 1].append(realDistance)

def maxSubArray(array):
    maximum = array[0][0]
    for i in array:
        maximum = max(maximum, max(i))
    return maximum

def matrixPrint(matrix):
    for i in matrix:
        print(i)

for file in os.listdir('.'):
    split = file.split(".")
    if split[-1] == "out" and split[-2] == "2":
        with open(file, 'r') as f:
            for line in f:
                split = line.split(",")
                addToArray(int(split[0]), int(split[1]), initialweightsarray)

maximum = maxSubArray(initialweightsarray)
matrix = [[0 for _ in range(maximum)] for _ in range(len(initialweightsarray))]
for i in range(len(initialweightsarray)):
    for j in initialweightsarray[i]:
        matrix[i][j - 1] += 1

matrixPrint(matrix)

plt.boxplot(initialweightsarray)
plt.show()