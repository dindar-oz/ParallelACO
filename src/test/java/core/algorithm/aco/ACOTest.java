package core.algorithm.aco;

import core.SimpleOptimizationProblem;
import core.TestProblems;
import core.algorithm.aco.ACO.ExecutionMode;
import core.algorithm.aco.problem.tsp.TSPAnt;
import core.algorithm.aco.problem.tsp.TSPPheromoneMatrix;
import core.algorithm.aco.problem.tsp.TSPQPheromoneMatrix;
import core.algorithm.localsearch.IterationBasedTC;
import core.algorithm.nsga.IterationCountTC;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.tsp.TSP;
import core.representation.Permutation;
import core.utils.random.RNG;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ACOTest {

    private static final int CITIES = 30;
    private static final int ANTS = 6;

    private static List<Ant> tspColony(int size) {
        List<Ant> colony = new ArrayList<>();
        for (int i = 0; i < size; i++) colony.add(new TSPAnt());
        return colony;
    }

    private static ACO tspAco(ExecutionMode mode, int iterations) {
        return new ACO(TSPPheromoneMatrix.withNearestNeighbourInit(ANTS, 0.1), tspColony(ANTS),
                new IterationBasedTC(iterations), mode).withThreads(3);
    }

    private static void assertValidTour(Solution s, int n) {
        int[] nodes = ((Permutation) s.getRepresentation()).getNodes().clone();
        Arrays.sort(nodes);
        int[] expected = new int[n];
        Arrays.setAll(expected, i -> i);
        assertArrayEquals(expected, nodes, "tour is not a permutation of all cities");
    }

    @ParameterizedTest
    @EnumSource(ExecutionMode.class)
    void everyModeProducesValidToursWithinBudget(ExecutionMode mode) {
        SimpleOptimizationProblem problem = TestProblems.randomTsp(CITIES, 1);
        ACO aco = tspAco(mode, 40).withSeed(7);

        Solution best = aco.perform(problem);

        assertNotNull(best);
        assertValidTour(best, CITIES);
        // 40 iterations x 6 ants; the parallel modes may overshoot by less than one round per thread.
        assertTrue(aco.getSolutionCount() >= 40 * ANTS, "too few solutions: " + aco.getSolutionCount());
        assertTrue(aco.getSolutionCount() < 40 * ANTS + 3 * ANTS, "too many solutions: " + aco.getSolutionCount());
    }

    @Test
    void acoBeatsTheNearestNeighbourTourOnASmallInstance() {
        SimpleOptimizationProblem problem = TestProblems.randomTsp(CITIES, 2);
        double nn = ((TSP) problem.model()).nearestNeighbourTourLength(0);
        Solution best = tspAco(ExecutionMode.SYNCHRONOUS, 150).withSeed(3).perform(problem);
        assertTrue(best.objectiveValue() <= nn, "ACO " + best.objectiveValue() + " vs NN " + nn);
    }

    @Test
    void sameSeedGivesSameResult() {
        SimpleOptimizationProblem problem = TestProblems.randomTsp(CITIES, 3);
        Solution a = tspAco(ExecutionMode.SEQUENTIAL, 30).withSeed(11).perform(problem);
        Solution b = tspAco(ExecutionMode.SEQUENTIAL, 30).withSeed(11).perform(problem);
        assertEquals(a.getRepresentation(), b.getRepresentation());
        assertEquals(a.objectiveValue(), b.objectiveValue(), 0.0);
    }

    @Test
    void synchronousModeReproducesSequentialRun() {
        SimpleOptimizationProblem problem = TestProblems.randomTsp(CITIES, 4);
        Solution sequential = tspAco(ExecutionMode.SEQUENTIAL, 30).withSeed(5).perform(problem);
        Solution synchronous = tspAco(ExecutionMode.SYNCHRONOUS, 30).withSeed(5).perform(problem);
        assertEquals(sequential.getRepresentation(), synchronous.getRepresentation());
        assertEquals(sequential.objectiveValue(), synchronous.objectiveValue(), 0.0);
    }

    @Test
    void algorithmInstanceCanBeReusedAcrossRuns() {
        SimpleOptimizationProblem problem = TestProblems.randomTsp(CITIES, 5);
        ACO aco = tspAco(ExecutionMode.ASYNCHRONOUS, 20);
        aco.perform(problem);
        long first = aco.getSolutionCount();
        aco.perform(problem);
        // The counter, stop flag and terminal condition are reset for every run.
        assertTrue(Math.abs(aco.getSolutionCount() - first) < 3 * ANTS);
    }

    @Test
    void iterationCountTCWorksBecauseACOIsIterating() {
        SimpleOptimizationProblem problem = TestProblems.randomTsp(CITIES, 6);
        ACO aco = new ACO(TSPPheromoneMatrix.withNearestNeighbourInit(ANTS, 0.1), tspColony(ANTS),
                new IterationCountTC(25), ExecutionMode.SEQUENTIAL);
        aco.perform(problem);
        assertEquals(26, aco.iterationcount()); // IterationCountTC stops once count > max
    }

    @Test
    void tspAntWorksWithTheQMatrixToo() {
        SimpleOptimizationProblem problem = TestProblems.randomTsp(CITIES, 7);
        ACO aco = new ACO(new TSPQPheromoneMatrix(1.0, ANTS, 0.1), tspColony(ANTS),
                new IterationBasedTC(10), ExecutionMode.SYNCHRONOUS).withSeed(1);
        assertValidTour(aco.perform(problem), CITIES);
    }

    @Test
    void antFailureIsPropagatedInsteadOfSwallowed() {
        Ant failing = new Ant() {
            @Override
            public void init(OptimizationProblem problem, PheromoneTrails trails, RNG rng) {
            }

            @Override
            public Solution constructSolution() {
                throw new IllegalStateException("boom");
            }

            @Override
            public Solution getSolution() {
                return null;
            }
        };
        List<Ant> colony = new ArrayList<>(tspColony(3));
        colony.add(failing);
        SimpleOptimizationProblem problem = TestProblems.randomTsp(10, 8);

        for (ExecutionMode mode : List.of(ExecutionMode.SYNCHRONOUS, ExecutionMode.ASYNCHRONOUS)) {
            ACO aco = new ACO(TSPPheromoneMatrix.withNearestNeighbourInit(4, 0.1), colony,
                    new IterationBasedTC(1000), mode).withThreads(2);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> aco.perform(problem));
            assertInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals("boom", e.getCause().getMessage());
        }
    }

    @Test
    void workerThreadsDoNotOutliveTheRun() {
        SimpleOptimizationProblem problem = TestProblems.randomTsp(CITIES, 9);
        tspAco(ExecutionMode.ASYNCHRONOUS, 10).perform(problem);
        tspAco(ExecutionMode.ISLAND, 10).perform(problem);
        boolean workersAlive = Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.getName().startsWith("aco-worker-") && t.isAlive());
        assertFalse(workersAlive, "the thread pool was not shut down");
    }

    @Test
    void emptyColonyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ACO(new TSPPheromoneMatrix(1, 1, 0.1), List.of(), new IterationBasedTC(1)));
    }
}
