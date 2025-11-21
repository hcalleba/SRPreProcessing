package edu.repetita.solvers.grp;

import com.gurobi.gurobi.*;
import edu.repetita.core.Demands;
import edu.repetita.core.Setting;
import edu.repetita.core.Solver;
import edu.repetita.core.Topology;
import java.util.*;

import static edu.repetita.io.IOConstants.SOLVER_OBJVALUES_MINMAXLINKUSAGE;

public class GRP extends Solver {

    private long solveTime = 0;
    private long maxTime;

    // Paramètres pour la génération adversariale
    private static final int NUM_ADVERSARIAL_MATRICES = 10;
    private static final int MAX_PERTURBED_DEMANDS = 5; // X
    private static final double PERTURBATION_PERCENT = 0.20; // Y (10%)

    @Override
    protected void setObjective() {
        objective = SOLVER_OBJVALUES_MINMAXLINKUSAGE;
    }

    @Override
    public String name() {
        return "GRP";
    }

    @Override
    public String getDescription() {
        return "Solves the topology with the requested demand file as seen by the general routing problem." +
                "This is not practically implementable in a network but serves as a baseline for comparisons.";
    }

    @Override
    public void solve(Setting setting, long milliseconds) {
        long startTime = System.currentTimeMillis();
        maxTime = milliseconds;

        Topology topology = setting.getTopology();
        ArrayList<Demands> demands = setting.getDemands();

        if (demands.isEmpty()) {
            System.err.println("No demand matrix provided");
            return;
        }

        // Scaler la matrice de base à MLU = 1.0
        // Demands baseMatrix = scaleMatrixToMLU(topology, demands.get(0), 1.0); // TODO ajouter ?
        Demands baseMatrix = demands.getFirst();

        // Générer les matrices adversariales
        List<AdversarialMatrix> adversarialMatrices = generateAdversarialMatrices(
                topology, baseMatrix, NUM_ADVERSARIAL_MATRICES,
                MAX_PERTURBED_DEMANDS, PERTURBATION_PERCENT
        );

        // Afficher les résultats
        printAdversarialResults(adversarialMatrices);

        solveTime = System.currentTimeMillis() - startTime;
    }

    /**
     * Classe pour stocker une matrice adversariale et ses métadonnées
     */
    private static class AdversarialMatrix {
        Demands matrix;
        Set<Integer> perturbedDemandIndices;
        double mlu;

        AdversarialMatrix(Demands matrix, Set<Integer> perturbedIndices, double mlu) {
            this.matrix = matrix;
            this.perturbedDemandIndices = perturbedIndices;
            this.mlu = mlu;
        }
    }

    /**
     * Classe pour stocker le routage (flots sur les arcs)
     */
    private static class Routing {
        // flow[startNode][endNode][edge] = proportion du flot de startNode vers endNode sur l'arc edge
        double[][][] flow;

        Routing(int nNodes, int nEdges) {
            this.flow = new double[nNodes][nNodes][nEdges];
        }
    }

    /**
     * Scale une matrice de demandes pour atteindre un MLU cible
     */
    private Demands scaleMatrixToMLU(Topology topology, Demands originalMatrix, double targetMLU) {
        // Résoudre pour obtenir le MLU actuel
        Routing routing = new Routing(topology.nNodes, topology.nEdges);
        double currentMLU = solveRouting(topology, Collections.singletonList(originalMatrix), routing);

        if (currentMLU <= 0) {
            System.err.println("Error: current MLU is " + currentMLU);
            return originalMatrix;
        }

        // Créer une nouvelle matrice scalée
        Demands scaledMatrix = new Demands(originalMatrix.nDemands);

        double scaleFactor = targetMLU / currentMLU;
        for (int i = 0; i < originalMatrix.nDemands; i++) {
            scaledMatrix.source[i] = originalMatrix.source[i];
            scaledMatrix.dest[i] = originalMatrix.dest[i];
            scaledMatrix.amount[i] = Math.floor(originalMatrix.amount[i] * scaleFactor);
        }

        System.out.println("Scaled matrix from MLU " + currentMLU + " to " + targetMLU +
                " (scale factor: " + scaleFactor + ")");

        return scaledMatrix;
    }

    /**
     * Génère une liste de matrices adversariales
     */
    private List<AdversarialMatrix> generateAdversarialMatrices(
            Topology topology, Demands baseMatrix, int numMatrices,
            int maxPerturbedDemands, double perturbationPercent) {

        List<AdversarialMatrix> adversarialMatrices = new ArrayList<>();
        List<Demands> allMatrices = new ArrayList<>();
        allMatrices.add(baseMatrix);

        System.out.println("\n=== Generating Adversarial Matrices ===");
        System.out.println("Base matrix MLU (should be ~1.0): " + solveRouting(topology, Collections.singletonList(baseMatrix), null)); // TODO remove

        for (int i = 0; i < numMatrices; i++) {
            System.out.println("\n--- Generating adversarial matrix " + (i + 1) + " ---");

            // Résoudre le routage sur toutes les matrices actuelles
            Routing currentRouting = new Routing(topology.nNodes, topology.nEdges);
            double currentMLU = solveRouting(topology, allMatrices, currentRouting);

            System.out.println("Current combined MLU: " + currentMLU);

            // Générer la pire matrice étant donné ce routage
            AdversarialMatrix worstMatrix = generateWorstMatrix(
                    topology, baseMatrix, currentRouting, maxPerturbedDemands, perturbationPercent
            );

            adversarialMatrices.add(worstMatrix);
            allMatrices.add(worstMatrix.matrix);

            System.out.println("Generated matrix with MLU: " + worstMatrix.mlu);
            System.out.println("Perturbed " + worstMatrix.perturbedDemandIndices.size() + " demands");
        }

        return adversarialMatrices;
    }

    /**
     * Génère la pire matrice de demandes étant donné un routage fixé
     */
    private AdversarialMatrix generateWorstMatrix(
            Topology topology, Demands baseMatrix, Routing routing,
            int maxPerturbedDemands, double perturbationPercent) {

        int budgetRestant = maxPerturbedDemands;
        Set<Integer> perturbedDemands = new HashSet<>();

        // Créer une copie de la matrice de base
        Demands worstMatrix = copyDemands(baseMatrix);

        while (budgetRestant > 0) {
            // Trouver l'arc qui donnera le pire MLU
            EdgeWorstCase worstCase = findWorstEdge(
                    topology, baseMatrix, routing, perturbedDemands, perturbationPercent, budgetRestant
            );

            if (worstCase == null || worstCase.demands.isEmpty()) {
                System.out.println("No more demands to perturb (budget remaining: " + budgetRestant + ")");
                break;
            }

            // Perturber les demandes de cet arc
            int nbToPerturb = Math.min(worstCase.demands.size(), budgetRestant);
            for (int i = 0; i < nbToPerturb; i++) {
                int demandIdx = worstCase.demands.get(i);
                perturbedDemands.add(demandIdx);
                worstMatrix.amount[demandIdx] = baseMatrix.amount[demandIdx] * (1.0 + perturbationPercent);
            }

            budgetRestant -= nbToPerturb;

            System.out.println("  Perturbed " + nbToPerturb + " demands on edge " +
                    worstCase.edgeIdx + " (MLU would be " + worstCase.mlu + ")");
        }

        // Calculer le MLU réel de cette matrice avec le routage donné
        double actualMLU = calculateMLU(topology, worstMatrix, routing);

        return new AdversarialMatrix(worstMatrix, perturbedDemands, actualMLU);
    }

    /**
     * Classe pour stocker le pire cas pour un arc
     */
    private static class EdgeWorstCase {
        int edgeIdx;
        double mlu;
        List<Integer> demands; // triés par contribution décroissante

        EdgeWorstCase(int edgeIdx, double mlu, List<Integer> demands) {
            this.edgeIdx = edgeIdx;
            this.mlu = mlu;
            this.demands = demands;
        }
    }

    /**
     * Trouve l'arc qui donnerait le pire MLU si on perturbait ses demandes
     */
    private EdgeWorstCase findWorstEdge(
            Topology topology, Demands baseMatrix, Routing routing,
            Set<Integer> alreadyPerturbed, double perturbationPercent, int budgetRestant) {

        EdgeWorstCase worstCase = null;
        double worstMLU = 0.0;

        for (int edge = 0; edge < topology.nEdges; edge++) {
            // Trouver toutes les demandes non perturbées qui passent par cet arc
            List<DemandContribution> contributions = new ArrayList<>();

            for (int demandIdx = 0; demandIdx < baseMatrix.nDemands; demandIdx++) {
                if (alreadyPerturbed.contains(demandIdx)) continue;

                int src = baseMatrix.source[demandIdx];
                int dst = baseMatrix.dest[demandIdx];
                double flowOnEdge = routing.flow[src][dst][edge];

                if (flowOnEdge > 1e-6) { // Si la demande passe par cet arc
                    double contribution = baseMatrix.amount[demandIdx] * flowOnEdge;
                    contributions.add(new DemandContribution(demandIdx, contribution));
                }
            }

            if (contributions.isEmpty()) continue;

            // Trier par contribution décroissante
            contributions.sort((a, b) -> Double.compare(b.contribution, a.contribution));

            // Ne garder que les budgetRestant premières demandes
            int nbDemandsToPerturb = Math.min(contributions.size(), budgetRestant);
            Set<Integer> demandsToPerturb = new HashSet<>();
            for (int i = 0; i < nbDemandsToPerturb; i++) {
                demandsToPerturb.add(contributions.get(i).demandIdx);
            }

            // Calculer le MLU si on perturbait CES demandes spécifiques
            double edgeLoad = 0.0;
            for (int demandIdx = 0; demandIdx < baseMatrix.nDemands; demandIdx++) {
                int src = baseMatrix.source[demandIdx];
                int dst = baseMatrix.dest[demandIdx];
                double flowOnEdge = routing.flow[src][dst][edge];
                double amount = baseMatrix.amount[demandIdx];

                // Si cette demande serait perturbée (parmi les k meilleures pour cet arc)
                if (demandsToPerturb.contains(demandIdx)) {
                    amount *= (1.0 + perturbationPercent);
                }

                edgeLoad += amount * flowOnEdge;
            }

            double mlu = edgeLoad / topology.edgeCapacity[edge];

            if (mlu > worstMLU) {
                worstMLU = mlu;
                List<Integer> demandIndices = new ArrayList<>();
                // Ne garder que les demandes qu'on perturberait effectivement
                for (int i = 0; i < nbDemandsToPerturb; i++) {
                    demandIndices.add(contributions.get(i).demandIdx);
                }
                worstCase = new EdgeWorstCase(edge, mlu, demandIndices);
            }
        }

        return worstCase;
    }

    /**
     * Classe pour stocker la contribution d'une demande à un arc
     */
    private static class DemandContribution {
        int demandIdx;
        double contribution;

        DemandContribution(int demandIdx, double contribution) {
            this.demandIdx = demandIdx;
            this.contribution = contribution;
        }
    }

    /**
     * Calcule le MLU d'une matrice de demandes avec un routage donné
     */
    private double calculateMLU(Topology topology, Demands demands, Routing routing) {
        double maxUtilization = 0.0;

        for (int edge = 0; edge < topology.nEdges; edge++) {
            double edgeLoad = 0.0;

            for (int demandIdx = 0; demandIdx < demands.nDemands; demandIdx++) {
                int src = demands.source[demandIdx];
                int dst = demands.dest[demandIdx];
                double flowOnEdge = routing.flow[src][dst][edge];
                edgeLoad += demands.amount[demandIdx] * flowOnEdge;
            }

            double utilization = edgeLoad / topology.edgeCapacity[edge];
            maxUtilization = Math.max(maxUtilization, utilization);
        }

        return maxUtilization;
    }

    /**
     * Résout le problème de routage pour une ou plusieurs matrices
     * et retourne le MLU optimal
     */
    private double solveRouting(Topology topology, List<Demands> demandsList, Routing outputRouting) {
        try {
            GRBEnv env = new GRBEnv(true);
            env.set(GRB.IntParam.OutputFlag, 0);
            env.set(GRB.IntParam.LogToConsole, 0);
            env.start();

            GRBModel model = new GRBModel(env);
            model.set(GRB.DoubleParam.TimeLimit, (double) (maxTime / 1000));
            model.set(GRB.IntParam.Threads, 4);

            // Variables de flot
            GRBVar[][][] flowVars = createFlowVariables(model, topology);
            GRBVar uMax = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "uMax");

            // Objectif
            GRBLinExpr objExpr = new GRBLinExpr();
            objExpr.addTerm(1.0, uMax);
            model.setObjective(objExpr, GRB.MINIMIZE);

            // Contraintes de conservation de flot
            addFlowConservationConstraints(model, topology, flowVars);

            // Contraintes de capacité pour chaque matrice
            addCapacityConstraints(model, topology, demandsList, flowVars, uMax);

            // Résoudre
            model.optimize();

            double result = model.get(GRB.DoubleAttr.ObjVal);

            // Extraire le routage si demandé
            if (outputRouting != null) {
                extractRouting(flowVars, topology, outputRouting);
            }

            model.dispose();
            env.dispose();

            return result;

        } catch (GRBException e) {
            System.err.println("Gurobi error: " + e.getErrorCode() + ". " + e.getMessage());
            return Double.MAX_VALUE;
        }
    }

    /**
     * Crée les variables de flot
     */
    private GRBVar[][][] createFlowVariables(GRBModel model, Topology topology) throws GRBException {
        GRBVar[][][] flowVars = new GRBVar[topology.nNodes][topology.nNodes][topology.nEdges];

        for (int src = 0; src < topology.nNodes; src++) {
            for (int dst = 0; dst < topology.nNodes; dst++) {
                if (src == dst) continue;
                for (int edge = 0; edge < topology.nEdges; edge++) {
                    flowVars[src][dst][edge] = model.addVar(
                            0.0, 1.0, 0.0, GRB.CONTINUOUS,
                            "flow_" + src + "_" + dst + "_" + edge
                    );
                }
            }
        }

        return flowVars;
    }

    /**
     * Ajoute les contraintes de conservation de flot
     */
    private void addFlowConservationConstraints(
            GRBModel model, Topology topology, GRBVar[][][] flowVars) throws GRBException {

        for (int src = 0; src < topology.nNodes; src++) {
            for (int dst = 0; dst < topology.nNodes; dst++) {
                if (src == dst) continue;

                for (int node = 0; node < topology.nNodes; node++) {
                    GRBLinExpr flowBalance = new GRBLinExpr();

                    // Flot sortant - flot entrant
                    for (int edge = 0; edge < topology.nEdges; edge++) {
                        if (topology.edgeSrc[edge] == node) {
                            flowBalance.addTerm(1.0, flowVars[src][dst][edge]);
                        }
                        if (topology.edgeDest[edge] == node) {
                            flowBalance.addTerm(-1.0, flowVars[src][dst][edge]);
                        }
                    }

                    // RHS
                    double rhs = 0.0;
                    if (node == src) rhs = 1.0;
                    else if (node == dst) rhs = -1.0;

                    model.addConstr(flowBalance, GRB.EQUAL, rhs,
                            "flow_conservation_" + src + "_" + dst + "_" + node);
                }
            }
        }
    }

    /**
     * Ajoute les contraintes de capacité
     */
    private void addCapacityConstraints(
            GRBModel model, Topology topology, List<Demands> demandsList,
            GRBVar[][][] flowVars, GRBVar uMax) throws GRBException {

        for (int matrixIdx = 0; matrixIdx < demandsList.size(); matrixIdx++) {
            Demands demands = demandsList.get(matrixIdx);

            for (int edge = 0; edge < topology.nEdges; edge++) {
                GRBLinExpr edgeLoad = new GRBLinExpr();

                for (int demandIdx = 0; demandIdx < demands.nDemands; demandIdx++) {
                    int src = demands.source[demandIdx];
                    int dst = demands.dest[demandIdx];
                    double amount = demands.amount[demandIdx];

                    edgeLoad.addTerm(amount, flowVars[src][dst][edge]);
                }

                edgeLoad.addTerm(-topology.edgeCapacity[edge], uMax);
                model.addConstr(edgeLoad, GRB.LESS_EQUAL, 0.0,
                        "capacity_" + edge + "_matrix_" + matrixIdx);
            }
        }
    }

    /**
     * Extrait le routage de la solution
     */
    private void extractRouting(GRBVar[][][] flowVars, Topology topology, Routing routing)
            throws GRBException {

        for (int src = 0; src < topology.nNodes; src++) {
            for (int dst = 0; dst < topology.nNodes; dst++) {
                if (src == dst) continue;
                for (int edge = 0; edge < topology.nEdges; edge++) {
                    routing.flow[src][dst][edge] = flowVars[src][dst][edge].get(GRB.DoubleAttr.X);
                }
            }
        }
    }

    /**
     * Copie une matrice de demandes
     */
    private Demands copyDemands(Demands original) {
        Demands copy = new Demands(original.nDemands);

        for (int i = 0; i < original.nDemands; i++) {
            copy.source[i] = original.source[i];
            copy.dest[i] = original.dest[i];
            copy.amount[i] = original.amount[i];
        }

        return copy;
    }

    /**
     * Affiche les résultats de la génération adversariale
     */
    private void printAdversarialResults(List<AdversarialMatrix> matrices) {
        System.out.println("\n=== Adversarial Matrix Generation Results ===");
        System.out.println(String.format("%-10s %-15s %-20s", "Matrix", "MLU", "Perturbed Demands"));
        System.out.println("-".repeat(50));

        for (int i = 0; i < matrices.size(); i++) {
            AdversarialMatrix am = matrices.get(i);
            System.out.println(String.format("%-10s %-15.4f %-20s",
                    "M_" + (i+1), am.mlu, am.perturbedDemandIndices.size()));
        }
    }

    @Override
    public long solveTime(Setting setting) {
        return solveTime;
    }
}