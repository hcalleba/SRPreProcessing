import csv
import matplotlib.pyplot as plt

if __name__ == "__main__":
    directories = ["1_core", "8_cores", "16_cores"]

    data = []

    for nbcores in directories:
        csvfile = open (nbcores + ".res.csv", 'r')

        csvreader = csv.reader(csvfile)
        csvlength = 1300//5
        next(csvreader)  # Skip header

        temp = []
        temp2 = []
        for row in csvreader:
            temp.append(float(row[4]))
        for i in range(csvlength):
            temp2.append(sum(temp[i*5:(i+1)*5]))
        data.append(temp2)


    indices_sorted_16c = [i[0] for i in sorted(enumerate(data[1]), key=lambda x:x[1])]
    data2 = [[], [], []]
    for i in indices_sorted_16c:
        data2[0].append(data[0][i])
        data2[1].append(data[1][i])
        data2[2].append(data[2][i])

    plt.scatter(range(csvlength), data2[0], s=10, c='red')
    plt.scatter(range(csvlength), data2[1], s=10, c='blue')
    plt.scatter(range(csvlength), data2[2], s=10, c='green')
    plt.yscale('log')
    plt.show()
