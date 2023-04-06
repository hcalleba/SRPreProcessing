import matplotlib.pyplot as plt

def on_resize(event):
        plt.tight_layout()
        plt.gcf().canvas.draw()

with open("2-SRmatrix.mat", "r") as f:
    mat2 = eval(f.readline())
with open("3-SRmatrix.mat", "r") as f:
    mat3 = eval(f.readline())

bpmat2 = [[j+1 for j in range(len(i)) for _ in range(i[j])] for i in mat2]
bpmat3 = [[j+1 for j in range(len(i)) for _ in range(i[j])] for i in mat3]

bpmatall = [mat[i] for i in range(len(bpmat2)) for mat in [bpmat2, bpmat3]]


box = plt.boxplot(bpmatall, patch_artist=True)
for element in ['boxes', 'fliers', 'medians']:
    for i in range(len(box[element])):
        if i % 2 == 0:
            plt.setp(box[element][i], color='b')
        else:
            plt.setp(box[element][i], color='r')
for element in ['whiskers', 'caps']:
     for i in range(len(box[element])):
        if i/2 % 2 < 1:
            plt.setp(box[element][i], color='b')
        else:
            plt.setp(box[element][i], color='r')
     
for patch in range(len(box['boxes'])):
    if patch % 2 == 0:
        box['boxes'][patch].set(facecolor='cyan')
        box['fliers'][patch].set(markeredgecolor='cyan')
    else:
        box['boxes'][patch].set(facecolor='orange')
        box['fliers'][patch].set(markeredgecolor='orange')


plt.title("Boxplot of the length of the optimal SR-path compared to shortest path")
plt.xticks(range(1, len(bpmatall)+1, 2), range(1, len(mat2)+1))
plt.yticks(range(1, len(mat3[0])+1), range(1, len(mat3[0])+1))
plt.legend([box["boxes"][0], box["boxes"][1]], ["2-SR", "3-SR"])
plt.xlabel("Length of the shortest path")
plt.ylabel("Length of the optimal sr-path")

cid = plt.gcf().canvas.mpl_connect('resize_event', on_resize)

plt.show()