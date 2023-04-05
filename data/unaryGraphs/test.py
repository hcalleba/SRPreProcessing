import matplotlib.pyplot as plt
import matplotlib.colors


with open("2-SRmatrix.mat", "r") as f:
    mat2 = eval(f.readline())

newmat = []

for i in mat2:
    somme = sum(i)
    i.append(somme)
    newmat.append([])
    for j in i:
        newmat[-1].append(j/somme)

plt.imshow(newmat, cmap="magma_r", interpolation='None', norm=matplotlib.colors.LogNorm())

for i in range(len(mat2)):
    for j in range(len(mat2[0])):
        text = plt.text(j, i, mat2[i][j], ha="center", va="center", color="w", fontsize=6)
xlabels = list(range(1, len(newmat[0])))
xlabels.append("Total")
plt.xticks(range(len(newmat[0])), xlabels, rotation=45)
plt.yticks(range(len(newmat)), range(1, len(newmat)+1))
plt.tick_params(top=True, labeltop=True, bottom=False, labelbottom=False)

plt.title("2-SR")

plt.colorbar()
plt.show()