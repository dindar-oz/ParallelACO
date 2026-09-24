package core.examples;

import core.SimpleOptimizationProblem;
import core.algorithm.aco.ACO;
import core.algorithm.aco.ACO.ExecutionMode;
import core.algorithm.aco.Ant;
import core.algorithm.aco.problem.cfp.MCFPPheromoneMatrix;
import core.algorithm.aco.problem.cfp.MultiCFPAnt;
import core.algorithm.aco.problem.tsp.TSPAnt;
import core.algorithm.aco.problem.tsp.TSPPheromoneMatrix;
import core.algorithm.aco.problem.wsn.WSNAnt;
import core.algorithm.aco.problem.wsn.WSNQPheromoneMatrix;
import core.algorithm.localsearch.IterationBasedTC;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.coalitionFormation.MCFPCostObjective;
import core.problems.coalitionFormation.MCFPModel;
import core.problems.coalitionFormation.MultiCFPGenerator;
import core.problems.tsp.TSP;
import core.problems.tsp.TSPMinimumDistanceObjective;
import core.problems.tsp.TspLibReader;
import core.problems.wsn.WSNProblemGenerator;
import core.representation.Permutation;
import core.utils.random.SplittableRNG;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Command-line demos of {@link ACO} on the three supported problems.
 * <pre>
 *   ACODemo tsp [file.tsp] [mode] [iterations]      default ./data/tsplib/att48.tsp
 *   ACODemo wsn [instance.json] [mode] [iterations] default ./data/wsn/reference/m_1_k_1_tc_100_dim_600_600_1.json
 *   ACODemo cfp [mode] [iterations]                 random 20-task / 40-agent instance
 * </pre>
 * {@code mode} is one of SEQUENTIAL, SYNCHRONOUS, ASYNCHRONOUS, ISLAND (default SYNCHRONOUS).
 */
public final class ACODemo {

    private static final long SEED = 42;

    private ACODemo() {
    }

    public static void main(String[] args) {
        String problem = args.length > 0 ? args[0].toLowerCase() : "tsp";
        switch (problem) {
            case "tsp" -> demoTSP(arg(args, 1, "./data/tsplib/att48.tsp"), mode(args, 2), iterations(args, 3, 500));
            case "wsn" -> demoWSN(arg(args, 1, "./data/wsn/reference/m_1_k_1_tc_100_dim_600_600_1.json"),
                    mode(args, 2), iterations(args, 3, 200));
            case "cfp" -> demoCFP(mode(args, 1), iterations(args, 2, 1000));
            default -> System.err.println("Unknown problem '" + problem + "'. Use tsp, wsn or cfp.");
        }
    }

    private static void demoTSP(String file, ExecutionMode mode, int iterations) {
        requireFile(file);
        TSP tsp = TSP.fromTspLib(Path.of(file));
        SimpleOptimizationProblem problem = new SimpleOptimizationProblem(tsp);
        problem.addObjective(new TSPMinimumDistanceObjective());

        // If an optimal tour is shipped next to the instance, print its cost for reference.
        String optTourFile = file.replaceFirst("\\.tsp$", ".opt.tour");
        if (Files.exists(Path.of(optTourFile))) {
            int[] nodes = TspLibReader.readTour(Path.of(optTourFile)); // already 0-based
            System.out.println("Optimal tour cost: " + problem.objectiveValue(new Permutation(nodes)));
        }

        List<Ant> colony = colony(10, TSPAnt::new);
        ACO aco = new ACO(TSPPheromoneMatrix.withNearestNeighbourInit(colony.size(), 0.1), colony,
                new IterationBasedTC(iterations), mode).withSeed(SEED);
        run(aco, problem);
    }

    private static void demoWSN(String file, ExecutionMode mode, int iterations) {
        requireFile(file);
        OptimizationProblem problem = WSNProblemGenerator.generateProblemInstanceFromJson(file);

        List<Ant> colony = colony(8, () -> new WSNAnt(0.6));
        ACO aco = new ACO(new WSNQPheromoneMatrix(1.0, colony.size(), 0.1), colony,
                new IterationBasedTC(iterations), mode).withSeed(SEED);
        run(aco, problem);
    }

    private static void demoCFP(ExecutionMode mode, int iterations) {
        MCFPModel mcfp = MultiCFPGenerator.generateMultiCFProblem(20, 40, 5, new SplittableRNG(SEED));
        SimpleOptimizationProblem problem = new SimpleOptimizationProblem(mcfp);
        problem.addObjective(new MCFPCostObjective());

        List<Ant> colony = colony(8, MultiCFPAnt::new);
        ACO aco = new ACO(MCFPPheromoneMatrix.withAutoInit(colony.size(), 0.1), colony,
                new IterationBasedTC(iterations), mode).withSeed(SEED);
        run(aco, problem);
    }

    // ------------------------------------------------------------------ helpers

    private static void run(ACO aco, OptimizationProblem problem) {
        long start = System.nanoTime();
        Solution best = aco.perform(problem);
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf("%s: best = %.4f after %d solutions in %.2f s%n",
                aco.getName(), best.objectiveValue(), aco.getSolutionCount(), seconds);
    }

    private static List<Ant> colony(int size, Supplier<Ant> factory) {
        List<Ant> colony = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            colony.add(factory.get());
        }
        return colony;
    }

    private static void requireFile(String file) {
        if (!Files.isRegularFile(Path.of(file)))
            throw new IllegalArgumentException("Instance file not found: " + Path.of(file).toAbsolutePath()
                    + " (the data/ directory is not part of the repository)");
    }

    private static String arg(String[] args, int index, String fallback) {
        return args.length > index ? args[index] : fallback;
    }

    private static ExecutionMode mode(String[] args, int index) {
        return ExecutionMode.valueOf(arg(args, index, "SYNCHRONOUS").toUpperCase());
    }

    private static int iterations(String[] args, int index, int fallback) {
        return Integer.parseInt(arg(args, index, String.valueOf(fallback)));
    }
}
