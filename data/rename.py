import os

directory = "unaryPaths - Copy/"

for file in os.listdir(directory):
	old_name = directory + file
	new_name = directory + file[:-7] + ".0004." + file[-7] + ".paths"
	os.rename(old_name, new_name)

