package core.examples;

import core.SimpleOptimizationProblem;
import core.algorithm.aco.ACO;
import core.algorithm.aco.ACO.ExecutionMode;
import core.algorithm.aco.Ant;
import core.algorithm.aco.problem.cfp.MCFPPheromoneMatrix;
import core.algorithm.aco.problem.cfp.MultiCFPAnt;
import core.algorithm.localsearch.IterationBasedTC;
import core.base.Solution;
import core.problems.coalitionFormation.MCFPCostObjective;
import core.problems.coalitionFormation.MCFPModel;
import core.problems.coalitionFormation.MultiCFPGenerator;
import core.utils.random.SplittableRNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures how much the parallel execution modes of {@link ACO} speed up a large coalition
 * formation instance.
 * <p>
 * Every mode gets the same budget (iterations x ants constructed solutions) on the same
 * seeded instance, so the wall-clock times are directly comparable. A short warm-up run
 * first lets the JIT compile the hot code, so the first measured mode is not penalised.
 * <pre>
 *   CFPSpeedupBenchmark [tasks] [agents] [ants] [iterations] [threads]
 *   defaults:            100     1000     32     40           available processors
 * </pre>
 * Building one solution costs roughly O(agents * (agents + tasks * abilities)), so larger
 * instances make the per-solution work dominate the thread coordination overhead.
 */
public final class CFPSpeedupBenchmark {

    private static final long INSTANCE_SEED = 2026;
    private static final long RUN_SEED = 7;
    private static final int ABILITIES = 5;

    private CFPSpeedupBenchmark() {
    }

    public static void main(String[] args) {
        int tasks = intArg(args, 0, 100);
        int agents = intArg(args, 1, 1000);
        int ants = intArg(args, 2, 32);
        int iterations = intArg(args, 3, 40);
        int threads = intArg(args, 4, Runtime.getRuntime().availableProcessors());

        MCFPModel model = MultiCFPGenerator.generateMultiCFProblem(tasks, agents, ABILITIES,
                new SplittableRNG(INSTANCE_SEED));
        SimpleOptimizationProblem problem = new SimpleOptimizationProblem(model);
        problem.addObjective(new MCFPCostObjective());

        System.out.printf("CFP instance: %d tasks, %d agents, %d abilities (seed %d)%n",
                tasks - 1, agents, ABILITIES, INSTANCE_SEED); // task 0 is the dummy "unassigned" task
        System.out.printf("Budget: %d ants x %d iterations = %d solutions per mode, %d threads%n%n",
                ants, iterations, ants * iterations, threads);

        // Warm-up so that JIT compilation does not distort the first measurement.
        run(problem, ExecutionMode.SYNCHRONOUS, ants, Math.max(2, iterations / 10), threads);

        System.out.printf("%-13s %10s %12s %9s %14s %9s%n",
                "mode", "time [s]", "solutions/s", "speed-up", "best cost", "feasible");
        double sequentialSeconds = 0;
        for (ExecutionMode mode : ExecutionMode.values()) {
            Result r = run(problem, mode, ants, iterations, threads);
            if (mode == ExecutionMode.SEQUENTIAL)
                sequentialSeconds = r.seconds;
            System.out.printf("%-13s %10.2f %12.0f %8.2fx %14.0f %9s%n",
                    mode, r.seconds, r.solutions / r.seconds, sequentialSeconds / r.seconds,
                    r.best.objectiveValue(), model.isFeasible(r.best.getRepresentation()) ? "yes" : "no");
        }
    }

    private record Result(double seconds, long solutions, Solution best) {
    }

    private static Result run(SimpleOptimizationProblem problem, ExecutionMode mode, int ants, int iterations, int threads) {
        List<Ant> colony = new ArrayList<>(ants);
        for (int i = 0; i < ants; i++) {
            colony.add(new MultiCFPAnt());
        }
        ACO aco = new ACO(MCFPPheromoneMatrix.withAutoInit(ants, 0.1), colony, new IterationBasedTC(iterations), mode)
                .withThreads(threads)
                .withSeed(RUN_SEED);

        long start = System.nanoTime();
        Solution best = aco.perform(problem);
        double seconds = (System.nanoTime() - start) / 1e9;
        return new Result(seconds, aco.getSolutionCount(), best);
    }

    private static int intArg(String[] args, int index, int fallback) {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }
}
