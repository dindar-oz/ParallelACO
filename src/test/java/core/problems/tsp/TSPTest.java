package core.problems.tsp;

import core.SimpleOptimizationProblem;
import core.algorithm.aco.problem.tsp.TSPQPheromoneMatrix;
import core.representation.Permutation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TSPTest {

    // Symmetric 4-city instance:   0 -1- 1 -2- 2 -1- 3, plus the long edges given below.
    private static final double[][] D = {
            {0, 1, 5, 4},
            {1, 0, 2, 6},
            {5, 2, 0, 1},
            {4, 6, 1, 0}
    };

    @Test
    void nearestNeighbourTourFollowsClosestUnvisitedCity() {
        TSP tsp = new TSP(4, D);
        // From 0: 0->1 (1), 1->2 (2), 2->3 (1), back 3->0 (4) = 8
        assertEquals(8.0, tsp.nearestNeighbourTourLength(0), 0.0);
        // From 3: 3->2 (1), 2->1 (2), 1->0 (1), back 0->3 (4) = 8
        assertEquals(8.0, tsp.nearestNeighbourTourLength(3), 0.0);
    }

    /** Regression test: the old upper bound mixed up indices and cities and overwrote the length. */
    @Test
    void qMatrixUpperBoundIsNearestNeighbourTourLength() {
        SimpleOptimizationProblem problem = new SimpleOptimizationProblem(new TSP(4, D));
        problem.addObjective(new TSPMinimumDistanceObjective());
        TSPQPheromoneMatrix q = new TSPQPheromoneMatrix(1.0, 1, 0.1);
        q.init(problem);
        assertEquals(8.0, q.getUpperBound(), 0.0);
    }

    @Test
    void objectiveUsesClosingEdgeFromLastToFirst() {
        double[][] asymmetric = {
                {0, 1, 100},
                {100, 0, 1},
                {1, 100, 0}
        };
        TSP tsp = new TSP(3, asymmetric);
        assertFalse(tsp.isSymmetric());
        // 0 -> 1 -> 2 -> 0 = 1 + 1 + 1
        assertEquals(3.0, new TSPMinimumDistanceObjective().value(tsp, new Permutation(new int[]{0, 1, 2})), 0.0);
    }

    @Test
    void symmetricInstancesAreDetected() {
        assertTrue(new TSP(4, D).isSymmetric());
    }
}
