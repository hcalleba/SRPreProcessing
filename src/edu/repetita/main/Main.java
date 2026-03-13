package edu.repetita.main;

import edu.repetita.core.Setting;
import edu.repetita.io.RepetitaWriter;
import edu.repetita.solvers.sr.ClusterSolver;

import java.util.*;

public class Main {

    /* Private print methods */
    private static String getUsage(){
        return "TODO";
    }

    private static String getUsageOptions(){
        ArrayList<String> options = new ArrayList<>();
        ArrayList<String> descriptions = new ArrayList<>();

        options.addAll(Arrays.asList("h","graph","demand","srpaths","TODO"));

        descriptions.addAll(Arrays.asList(
                "only prints this help message",
                "file.graph",
                "file.demands",
                "name of the file to load SR-paths from",
                "TODO"
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


    /* Main method */
    public static void main(String[] args) throws Exception {
        String graphFilename = null;
        ArrayList<String> demandsFilename = new ArrayList<>();
        String srPathsFile = null;
        int timelimit = 10000;

        // GRP solver default parameters
        int numGeneratedMatrices = 10;
        int maxPerturbedDemands = 5;
        double perturbationPercent = 0.20;

        // parse command line arguments
        int i = 0;
        while (i < args.length) {
            switch(args[i]) {
                case "-h":
                    printHelp("");
                    System.exit(0);

                case "-t":
                    timelimit = Integer.parseInt(args[++i]);
                    break;

                case "-graph":
                    graphFilename = args[++i];
                    break;

                case "-demands":
                    i++;
                    while (i < args.length && !args[i].startsWith("-")) {
                        demandsFilename.add(args[i]);
                        i++;
                    }
                    i--; // to counter the increment in the while condition
                    break;

                case "-numGeneratedMatrices":
                    numGeneratedMatrices = Integer.parseInt(args[++i]);
                    break;

                case "-maxPerturbedDemands":
                    maxPerturbedDemands = Integer.parseInt(args[++i]);
                    break;

                case "-perturbationPercent":
                    perturbationPercent = Double.parseDouble(args[++i]);
                    break;

                case "-srpaths":
                    srPathsFile = args[++i];
                    break;

                default:
                    printHelp("Unknown option " + args[i]);
            }
            i++;
        }

        /* check that the strictly necessary information has been provided in input */
        if (args.length < 1) printHelp("");
        if (graphFilename == null) printHelp("Needs an input topology file");

        /* Set the settings according to command line parameters */
        Setting setting = new Setting();
        setting.setTopologyFilename(graphFilename);
        if (!demandsFilename.isEmpty()) {
            setting.setDemandsFilename(demandsFilename);
        }

//        AdversarialSolver solver = new AdversarialSolver();
//        solver.setNumAdversarialMatrices(numGeneratedMatrices);
//        solver.setMaxPerturbedDemands(maxPerturbedDemands);
//        solver.setPerturbationPercent(perturbationPercent);
//        solver.setSRPathsFile(srPathsFile);
//        solver.setExcludeAdjacencyPaths(true);
//        solver.solve(setting, (long) timelimit * 1000); // use parsed timeLimit

        ClusterSolver solver = new ClusterSolver();
        solver.setNumGeneratedMatrices(numGeneratedMatrices);
        solver.setSRPathsFile(srPathsFile);
        solver.setExcludeAdjacencyPaths(true);
        solver.solve(setting, (long) timelimit * 1000);
    }
}
