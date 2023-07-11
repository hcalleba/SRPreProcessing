import matplotlib.pyplot as plt
import matplotlib.colors
from mpl_toolkits.axes_grid1 import make_axes_locatable, axes_size

NBSR = "2"

def get_last_non_zero_index(l):
    for i in range(len(l)-1, -1, -1):
        if l[i] != 0:
            return i+1
    return 0

def on_resize(event):
        plt.tight_layout()
        plt.gcf().canvas.draw()

with open(NBSR + "-SRmatrix.mat", "r") as f:
    mat2 = eval(f.readline())

newmat = []

for i in mat2:
    somme = sum(i)
    i.append(somme)
    newmat.append([])
    for j in i:
        newmat[-1].append(j/somme)

li = get_last_non_zero_index(newmat[0][:-1])

bar = plt.bar(range(len(newmat[0][:li])), newmat[0][:li], color="r", label="1")
i = 0
for rect in bar:
    if (mat2[0][i] != 0):
        height = rect.get_height()
        plt.text(rect.get_x() + rect.get_width() / 2.0, height, mat2[0][i], ha='center', va='bottom', rotation = 90)#, rotation_mode = 'anchor')
    i+=1

plt.show()