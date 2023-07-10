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

#plt.legend()


"""
plt.imshow(newmat, cmap="Greys", interpolation='None', norm=matplotlib.colors.LogNorm())

for i in range(len(mat2)):
    for j in range(len(mat2[0])):
        if mat2[i][j] != 0:
            if j % 2 == 1:
                text = plt.text(j, i, mat2[i][j], ha="center", va="top", color="c", fontsize=7)
            else:
                text = plt.text(j, i, mat2[i][j], ha="center", va="bottom", color="c", fontsize=7)
xlabels = list(range(1, len(newmat[0])))
xlabels.append("Total")
plt.xticks(range(len(newmat[0])), xlabels, rotation=45)
plt.yticks(range(len(newmat)), range(1, len(newmat)+1))
plt.tick_params(top=True, labeltop=True, bottom=False, labelbottom=False)

plt.title(NBSR + "-SR")
plt.gca().xaxis.set_label_position("top")
plt.xlabel("Length of optimal SR-path")
plt.ylabel("Length of shortest path")

aspect = 20
pad_fraction = 0.5

ax = plt.gca()
im = plt.gci()
divider = make_axes_locatable(ax)
width = axes_size.AxesY(ax, aspect=1./aspect)
pad = axes_size.Fraction(pad_fraction, width)
cax = divider.append_axes("right", size=width, pad=pad)
plt.colorbar(im, cax=cax)

#cid = plt.gcf().canvas.mpl_connect('resize_event', on_resize)"""

plt.show()