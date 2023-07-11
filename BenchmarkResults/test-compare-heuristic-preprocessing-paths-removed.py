import csv
import matplotlib.pyplot as plt
import matplotlib.rcsetup

font = {'family' : 'DejaVu Sans',
        'weight' : 'normal',
        'size'   : 20}
matplotlib.rc('font', **font)

with open("Unary/SRPP_preprocess.csv", "r") as f:
    reader = csv.reader(f)
    next(reader)        # Skip header
    data_unary = list(reader)

with open("Unary_heuristic/SRPP_preprocess_1.1x+3.csv", "r") as f:
    reader = csv.reader(f)
    next(reader)        # Skip header
    data_11x = list(reader)

with open("Unary_heuristic/SRPP_preprocess_2x+1.csv", "r") as f:
    reader = csv.reader(f)
    next(reader)        # Skip header
    data_2x = list(reader)

new_data2 = []
new_data3 = []

for i in range(len(data_unary)):
    if data_unary[i][3] != "-":
        new_data2.append([data_unary[i][3], data_11x[i][3], data_2x[i][3]])
    if data_unary[i][4] != "-":
        new_data3.append([data_unary[i][4], data_11x[i][4], data_2x[i][4]])

sum_unary2 = 0
sum_11x2 = 0
sum_2x2 = 0
for i in range(len(new_data2)):
    sum_unary2 += float(new_data2[i][0])
    sum_11x2 += float(new_data2[i][1])
    sum_2x2 += float(new_data2[i][2])

sum_unary3 = 0
sum_11x3 = 0
sum_2x3 = 0
for i in range(len(new_data3)):
    sum_unary3 += float(new_data3[i][0])
    sum_11x3 += float(new_data3[i][1])
    sum_2x3 += float(new_data3[i][2])

print("unary: " + str(sum_unary3))
print("11x: " + str(sum_11x3))
print("2x: " + str(sum_2x3))
print("11x/unary: " + str(sum_11x3/sum_unary3))
print("2x/unary: " + str(sum_2x3/sum_unary3))

a = [(float(i[0]), float(i[1])/float(i[0])) for i in new_data2]
b = [(float(i[0]), float(i[1])/float(i[0])) for i in new_data3]
x2, y2 = zip(*a)
x3, y3 = zip(*b)

fig, ax = plt.subplots(2, 1)
ax[0].plot(x2, y2, 'ro')
ax[0].set_title("2-SR")
ax[1].plot(x3, y3, 'bo')
ax[1].set_title("3-SR")


plt.xlabel("Number of paths without heuristic")
fig.supylabel("Proportion of paths left with heuristic")
ax[0].set_xscale('log')
ax[1].set_xscale('log')

def on_resize(event):
    plt.tight_layout()
    plt.gcf().canvas.draw()

cid = plt.gcf().canvas.mpl_connect('resize_event', on_resize)

plt.show()

