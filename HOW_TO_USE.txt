First the Gurobi.jar file should be added to the lib folder.
A valid gurobi license should also be installed on the computer.
In the case you use an academic license on a computer with no access to internet, this article can be of good use:
https://sproul.xyz/blog/posts/gurobi-academic-validation.html
Note that &version=9 should be added to the end too and that wget does not seem to work, but pasting the link into my chrome browser worked.
Also trailing spaces need to be deleted at each line in the gurobi.lic file that you will create.

Second, the commons-lang-2.6.jar file should also be added to the lib file.
It is downloadable from from here https://archive.apache.org/dist/commons/lang/binaries/commons-lang-2.6-bin.tar.gz

Now we need to compile all the java files.
First head to the starting directory (should end with /SRPreProc
For linux distributions, the following command can be used
javac -classpath ./lib/gurobi.jar:./lib/commons-lang-2.6.jar $(find ./src -name "*.java")
This will create a bunch of *.class files
For Windows users there probably exist lookalike commands. Note that you will need to change
./lib/gurobi.jar:./lib/commons-lang-2.6.jar by "./lib/gurobi.jar;./lib/commons-lang-2.6.jar"
(the : is replaced by ; to separate files and the "" are needed unless run from a batch file)



The program should then be able to run with e.g.:

java -classpath "./src:./lib/gurobi.jar:./lib/commons-lang-2.6.jar" edu.repetita.main.Main -scenario SRPP -graph ./data/2016TopologyZooUCL_inverseCapacity/Iris.graph -demands ./data/2016TopologyZooUCL_inverseCapacity/Iris.0000.demands -maxSR 2 -outpaths outpathfile.txt

Once again on windows the : in the classpath should be replaced by ;

For more information about the possible arguments and their meaning we refer to the README.txt file
or you can execute:
java -classpath "./src:./lib/gurobi.jar:./lib/commons-lang-2.6.jar" edu.repetita.main.Main -h
Which will print a help message