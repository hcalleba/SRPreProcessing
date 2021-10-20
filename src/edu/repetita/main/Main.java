package edu.repetita.main;

import edu.repetita.core.Setting;
import edu.repetita.core.Solver;
import edu.repetita.io.IOConstants;
import edu.repetita.io.RepetitaParser;
import edu.repetita.io.RepetitaWriter;
import edu.repetita.solvers.sr.SRPP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class Main {

    /* Private print methods */
    private static String getUsage(){
        return "Typical usage: repetita " +
                "-graph topology_file -demands demands_filename -demandchanges list_demands_filename " +
                "-solver algorithm_id -scenario scenario_id -t max_execution_time -outpaths path_filename " +
                "-out output_filename -verbose debugging_level\n";
    }

    private static String getUsageOptions(){
        ArrayList<String> options = new ArrayList<>();
        ArrayList<String> descriptions = new ArrayList<>();

        options.addAll(Arrays.asList("h","doc","graph","demands","demandchanges",
                "t","outpaths","out","verbose"));

        descriptions.addAll(Arrays.asList(
                "only prints this help message",
                "only prints the README.txt file",
                "file.graph",
                "file.demands",
                "list of file.demands",
                "maximum time in seconds allowed to the solver",
                "name of the file collecting information of paths",
                "name of the file collecting all the information (standard output by default)",
                "level of debugging (default 0, only results reported)"
        ));

        return "All options:\n" + RepetitaWriter.formatAsListTwoColumns(options, descriptions, "  -");
    }


    private static void printHelp(String additional) {
        if (additional != null && !additional.equals("")) {
            System.out.println("\n" + additional + "\n");
        }

        System.out.println(getUsage());
        System.out.println(getUsageOptions());

        System.exit(1);
    }

    private static void printReadme(){
        // introduction
        String content = "Framework for repeatable experiments in Traffic Engineering.\n\n" +
                "Features:\n" +
                "- dataset with most instances from the Topology Zoo\n" +
                "- a collection of traffic engineering algorithms and analyses of their results\n" +
                "- libraries to simulate traffic distribution induced by ECMP, static (MPLS tunnels or OpenFlow rules)" +
                " and Segment Routing paths, compute Multicommodity Flow solutions, and much more!\n\n";

        content += getUsage() + "\n";

        System.out.println(content);
    }

    private static void printExternalSolverSpecs() {
        // prepare the new introduction to the file
        String doc = "For each external solver, specify how to use it within REPETITA\n\n" +
                "The definition of each solver must start with an identifier within square brackets.\n" +
                "It must also include the definition of the following features:\n";
        doc += IOConstants.getFormattedSolverSpecConstantsWithDescription() + "\n";
        StringBuilder content = new StringBuilder(RepetitaWriter.formatAsDocumentation(doc) + "\n");

        // read the information on the already configured external solvers, and append it to the content to write
        Map<String,Map<String,String>> features = RepetitaParser.parseExternalSolverFeatures(IOConstants.SOLVER_SPECSFILE);
        for (String configuredSolver: features.keySet()){
            content.append(features.get(configuredSolver).get(IOConstants.SOLVER_STARTDEF)).append("\n");
            for (String feat: IOConstants.getSolverSpecConstantsInOrder()){
                content.append(feat).append(IOConstants.SOLVER_KEYVALUESEPARATOR).append(features.get(configuredSolver).get(feat)).append("\n");
            }
            content.append("\n");
        }

        RepetitaWriter.writeToFile(content.toString(),IOConstants.SOLVER_SPECSFILE);
    }

    private static void print_doc() {
        printReadme();
    }


    /* Main method */
    public static void main(String[] args) throws Exception {
        String graphFilename = null;
        String demandsFilename = null;
        int verboseLevel = 0;
        boolean help = false;
        int maxSegments = 2;

        String solverChoice = "tabuLS";
        String scenarioChoice = "SingleSolverRun";

        // parse command line arguments
        int i = 0;
        while (i < args.length) {
            switch(args[i]) {
                case "-h":
                    help=true;
                    break;

                case "-doc":
                    print_doc();
                    return;

                case "-graph":
                    graphFilename = args[++i];
                    break;

                case "-demands":
                    demandsFilename = args[++i];
                    break;

                case "-outpaths":
                    RepetitaWriter.setOutpathsFilename(args[++i]);
                    break;

                case "-out":
                    RepetitaWriter.setOutputFilename(args[++i]);
                    break;

                case "-verbose":
                    verboseLevel = Integer.parseInt(args[++i]);
                    RepetitaWriter.setVerbose(verboseLevel);
                    break;

                case "-maxSR":
                    maxSegments = Integer.parseInt(args[++i]);
                    if (maxSegments < 2) {
                        printHelp("Number of segments should be at least two");
                    }

                default:
                    printHelp("Unknown option " + args[i]);
            }
            i++;
        }

        // check that the strictly necessary information has been provided in input (after having creating the storage)
        if (args.length < 1 || help) printHelp("");
        if (graphFilename == null) printHelp("Needs an input topology file");
        if (demandsFilename == null) printHelp("Needs an input demands file");

        System.out.println("This is the end.");

        // Set the settings according to command line parameters
        Setting setting = new Setting();
        setting.setTopologyFilename(graphFilename);
        setting.setDemandsFilename(demandsFilename);
        setting.setMaxSegments(maxSegments);

        // FROM HERE ON I WILL DO THE TESTS !
        Solver solver = new SRPP();
        solver.solve(setting,0);
    }
}
