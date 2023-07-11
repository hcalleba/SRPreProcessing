package edu.repetita.main;

import edu.repetita.core.Setting;
import edu.repetita.io.RepetitaParser;
import edu.repetita.paths.ShortestPaths;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * This Main function was used to compare the size of the shortest path with the size of the path i the optimal solution.
 */
public class UnaryMainTest {

    final static String pathsDir = "data/unaryPaths/";
    final static String topoDir = "data/2016TopologyZooUCL_unary/";
    final static String demandsDir = "data/2016TopologyZooUCL_inverseCapacity/";
    final static String outputDir = "data/unaryOutput/";

    String filename;
    String demandNumber;
    String srNumber;

    Setting setting;
    int[][] shortestDistances;  // shortestPaths
    ArrayList<int[]> paths;  // list of optimal sr-paths

    public static void main(String[] args) {
        // 1. lire une instance et compute shortest paths
        // 2. lire toutes les solutions correspondantes pour 2- et 3-SR et compute path length
        // 3. mettre dans un fichier.

        UnaryMainTest main = new UnaryMainTest();
        main.main();
    }

    public void main() {
        Iterator<File> it = getFilesInDirectory(pathsDir);
        while(it.hasNext()) {
            File f = it.next();
            String[] file = f.getName().split("[.]");
            filename = file[file.length - 4];
            demandNumber = file[file.length - 3];
            srNumber = file[file.length - 2];

            readFiles();

            try {
                paths = new ArrayList<>();
                RepetitaParser.parseOptPaths(f.toString(), paths, setting);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

            try {
                String outputFile = outputDir + filename + "." + demandNumber + "." + srNumber + ".out";
                FileWriter writer = new FileWriter(outputFile);
                for (int[] srpath : paths) {
                    int[] distances = computeDistances(srpath);
                    writer.write(String.valueOf(distances[0]) + ", " + String.valueOf(distances[1]) + "\n");
                }
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static Iterator<File> getFilesInDirectory(String path) {
        File fObj = new File(path);
        if (fObj.exists() && fObj.isDirectory()) {
            return Arrays.stream(fObj.listFiles()).iterator();
        }
        return null;
    }

    public void readFiles() {
        setting = new Setting();
        setting.setTopologyFilename(topoDir + filename + ".graph");
        setting.setDemandsFilename(demandsDir + filename + "." + demandNumber + ".demands");
        ShortestPaths sp = new ShortestPaths(setting.getTopology());
        sp.computeShortestPaths();
        shortestDistances = sp.distance;
    }

    public int[] computeDistances(int[] srpath) {
        int totalDistance = 0;
        int shortestDistance = 0;
        for (int  i = 0; i < srpath.length-1; ++i) {
            int start = srpath[i], end = srpath[i+1];
            if (end >= setting.getTopology().nNodes) {
                totalDistance += setting.getTopology().edgeWeight[end - setting.getTopology().nNodes];
                end = setting.getTopology().edgeDest[end - setting.getTopology().nNodes];
            }
            totalDistance += this.shortestDistances[start][end];
        }
        int start = srpath[0], end = srpath[srpath.length-1];
        if (end >= setting.getTopology().nNodes) {
            totalDistance += setting.getTopology().edgeWeight[end - setting.getTopology().nNodes];;
            end = setting.getTopology().edgeDest[end - setting.getTopology().nNodes];
        }
        shortestDistance += shortestDistances[start][end];
        return new int[] {shortestDistance, totalDistance};
    }
}
