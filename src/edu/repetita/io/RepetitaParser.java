package edu.repetita.io;

import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Topology;
import edu.repetita.solvers.sr.srpp.segmenttree.SegmentTreeRoot;
import edu.repetita.utils.datastructures.Conversions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static edu.repetita.core.Demands.toTrafficMatrix;

/**
 * Utility class that contains static methods to parse topologies and demands.
 */
final public class RepetitaParser {

    private RepetitaParser() {}

    /**
     * Parses a topology file.
     *
     * @param filename the name of the file to parse
     * @return the topology contained in filename
     * @throws IOException if there is some problem when reading the file
     */
    public static Topology parseTopology(String filename) throws IOException {
        try (Stream<String> lineStream = Files.lines(Paths.get(filename))) {  // autoclose stream
            Iterator<String> lines = lineStream.iterator();

            // Nodes
            ArrayList<String> nodeLabels = new ArrayList<String>();

            // Edges
            ArrayList<String> edgeLabels = new ArrayList<String>();
            ArrayList<Integer> srcs = new ArrayList<Integer>();
            ArrayList<Integer> dests = new ArrayList<Integer>();
            ArrayList<Integer> weights = new ArrayList<Integer>();
            ArrayList<Double> capacities = new ArrayList<Double>();
            ArrayList<Integer> latencies = new ArrayList<Integer>();

            // First two lines of nodes are useless to parse
            lines.next();
            lines.next();

            // Node info: label x_coordinate y_coordinate
            while (lines.hasNext()) {
                String line = lines.next();
                if (line.isEmpty()) break; // stop at empty line

                String[] data = line.split(" ");
                String label = data[0];
                // double x = Double.parseDouble(data[1]);  // not using coordinates
                // double y = Double.parseDouble(data[2]);
                nodeLabels.add(label);
            }

            // First two lines of edges are useless to parse
            lines.next();
            lines.next();

            // Edge info: src dest weight bw delay
            while (lines.hasNext()) {
                String line = lines.next();
                if (line.isEmpty()) break;  // stop at empty line

                String[] data = line.split(" ");
                String label = data[0];
                int src = Integer.parseInt(data[1]);
                int dest = Integer.parseInt(data[2]);
                int weight = Integer.parseInt(data[3]);
                double bw = Double.parseDouble(data[4]);
                int delay = Integer.parseInt(data[5]);

                edgeLabels.add(label);
                srcs.add(src);
                dests.add(dest);
                weights.add(weight);
                capacities.add(bw);
                latencies.add(delay);
            }

            // make topology from parsed info
            String[] nodeLabel = nodeLabels.toArray(new String[0]);  // I hate doing this, Java.
            String[] edgeLabel = edgeLabels.toArray(new String[0]);
            int[] edgeSrc = Conversions.arrayListInteger2arrayint(srcs);
            int[] edgeDest = Conversions.arrayListInteger2arrayint(dests);
            int[] edgeWeight = Conversions.arrayListInteger2arrayint(weights);
            double[] edgeCapacity = Conversions.arrayListDouble2arraydouble(capacities);
            int[] edgeLatency = Conversions.arrayListInteger2arrayint(latencies);

            return new Topology(nodeLabel, edgeLabel, edgeSrc, edgeDest, edgeWeight, edgeCapacity, edgeLatency);
        } catch (Exception e) { // let it crash!
            throw e;
        }
    }

    /**
     * Parses a demands file.
     *
     * @param filename the file containing the topology
     * @return the demands contained in filename
     * @throws IOException if there is some problem when reading the file
     */
    public static Demands parseDemands(String filename) throws IOException {
        try (Stream<String> lineStream = Files.lines(Paths.get(filename))) {  // autoclose stream
            Iterator<String> lines = lineStream.iterator();

            // Demands
            ArrayList<String> labels = new ArrayList<String>();
            ArrayList<Integer> srcs = new ArrayList<Integer>();
            ArrayList<Integer> dests = new ArrayList<Integer>();
            ArrayList<Double> bws = new ArrayList<Double>();

            // First two lines are useless to parse
            lines.next();
            lines.next();

            // Demands info: label src dest bw
            while (lines.hasNext()) {
                String line = lines.next();
                if (line.isEmpty()) break;

                String[] data = line.split(" ");
                String label = data[0];
                int src = Integer.parseInt(data[1]);
                int dest = Integer.parseInt(data[2]);
                double bw = Double.parseDouble(data[3]);

                labels.add(label);
                srcs.add(src);
                dests.add(dest);
                bws.add(bw);
            }

            // make Demands from parsed info

            String label[] = labels.toArray(new String[0]);
            int[] source = Conversions.arrayListInteger2arrayint(srcs);
            int[] dest = Conversions.arrayListInteger2arrayint(dests);
            double[] amount = Conversions.arrayListDouble2arraydouble(bws);

            return new Demands(label, source, dest, amount);
        } catch (Exception e) { // let it crash
            throw e;
        }
    }

    public static Map<String,Map<String,String>> parseExternalSolverFeatures(String filename) {
        Map<String,Map<String,String>> solverFeatures = new HashMap<>();
        try {
            Stream<String> lineStream = Files.lines(Paths.get(filename));
            Iterator<String> lines = lineStream.iterator();

            Map<String,String> currFeatures = new HashMap<>();

            while (lines.hasNext()){
                String line = lines.next();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("[")){
                    if (! currFeatures.isEmpty()){
                        solverFeatures.put(currFeatures.get("name"),currFeatures);
                    }
                    currFeatures = new HashMap<>();
                    currFeatures.put(IOConstants.SOLVER_STARTDEF,line);
                    continue;
                }

                String[] featureArray = line.split(IOConstants.SOLVER_KEYVALUESEPARATOR);

                // The first element in the featureArray is the feature name.
                // The other elements constitute the feature value (they can be more than one if separated by '=')
                String featName = featureArray[0].trim();
                String value = featureArray[1];
                for (int i = 2; i < featureArray.length; i++) {
                    value = value.concat(IOConstants.SOLVER_KEYVALUESEPARATOR + featureArray[i]);
                }
                currFeatures.put(featName,value.trim());
            }

            // add last solver
            solverFeatures.put(currFeatures.get("name"),currFeatures);
        }
        catch (Exception e) { // let it crash
            System.err.println("Cannot parse external solver spec file " + filename);
            System.exit(-1);
        }

        return solverFeatures;
    }

    public static ArrayList<int[]> parseSRPaths(String filename, SegmentTreeRoot root, ArrayList<int[]> paths) throws IOException {
        try (Stream<String> lineStream = Files.lines(Paths.get(filename))) {  // autoclose stream
            Iterator<String> lines = lineStream.iterator();

            String line;
            // Demands info: label src dest bw
            while (lines.hasNext()) {
                line = lines.next();
                if (line.isEmpty()) break;

                line = line.replace ("[", "");
                line = line.replace ("]", "");
                String[] SRNodes = line.split (", ");

                /* Only add path if there is demand between nodes */
                try {
                    int lastSegment = Integer.parseInt(SRNodes[SRNodes.length - 1]);
                    int endNode = (lastSegment < root.nNodes) ? lastSegment : root.edgeDest[lastSegment-root.nNodes];
                    if (root.trafficMatrix[Integer.parseInt(SRNodes[0])][endNode] > 0) {
                        paths.add(new int[SRNodes.length]);
                        for (int i = 0; i < SRNodes.length; i++) {
                            paths.get(paths.size() - 1)[i] = Integer.parseInt(SRNodes[i]);
                        }
                    }
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            return paths;
        } catch (Exception e) { // let it crash
            throw e;
        }
    }

    public static ArrayList<int[]> parseOptPaths(String filename, ArrayList<int[]> paths, Setting setting) throws IOException {
        try (Stream<String> lineStream = Files.lines(Paths.get(filename))) {  // autoclose stream
            Iterator<String> lines = lineStream.iterator();

            lines.next();  // Ignore first two lines
            lines.next();

            double[][] demands = Demands.toTrafficMatrix(setting.getDemands(), setting.getTopology().nNodes);
            String line;
            while (lines.hasNext()) {
                line = lines.next();
                if (line.isEmpty()) break;

                line = line.replace ("[", "");
                line = line.replace ("]", "");
                String[] SRNodes = line.split (", ");

                /* Only add path if there is demand between nodes */
                int lastSegment = Integer.parseInt(SRNodes[SRNodes.length-1]);
                int endNode = (lastSegment < setting.getTopology().nNodes) ? lastSegment : setting.getTopology().edgeDest[lastSegment-setting.getTopology().nNodes];
                if (demands[Integer.parseInt(SRNodes[0])][endNode] > 0) {
                    paths.add(new int[SRNodes.length]);
                    for (int i = 0; i < SRNodes.length; i++) {
                        paths.get(paths.size() - 1)[i] = Integer.parseInt(SRNodes[i]);
                    }
                }
            }
            return paths;
        } catch (Exception e) { // let it crash
            throw e;
        }
    }
}
