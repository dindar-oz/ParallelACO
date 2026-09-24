package core.algorithm.aco;

import core.algorithm.SimpleSolution;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.representation.Permutation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PheromoneMatrixTest {

    /**
     * Minimal matrix whose deposit adds the solution's objective value to cell
     * {@code (0, representation[0])}.
     */
    static final class TestMatrix extends PheromoneMatrix {
        private final int rows;
        private final int cols;

        TestMatrix(double initial, int colonySize, double rho, int rows, int cols) {
            super(initial, colonySize, rho);
            this.rows = rows;
            this.cols = cols;
        }

        @Override
        public void init(OptimizationProblem problem) {
            allocate(rows, cols, initialValue);
        }

        @Override
        protected void deposit(OptimizationProblem problem, Solution s) {
            add(index(0, ((Permutation) s.getRepresentation()).get(0)), s.objectiveValue());
        }

        @Override
        protected PheromoneMatrix create(int colonySize) {
            return new TestMatrix(initialValue, colonySize, evaporationRatio, rows, cols);
        }
    }

    private static Solution depositOn(int col, double amount) {
        return new SimpleSolution(new Permutation(new int[]{col}), amount);
    }

    @Test
    void lazyEvaporationMatchesEagerEvaporation() {
        TestMatrix m = new TestMatrix(1.0, 1, 0.1, 2, 3);
        m.init(null);
        double[] eager = {1, 1, 1};

        // Each step shrinks the internal scale by 0.9 * 0.5 = 0.45, so after ~290 steps it drops
        // below 1e-100 and the matrix must renormalise at least once during the loop.
        for (int step = 0; step < 400; step++) {
            m.update(null, depositOn(step % 3, 0.5));           // evaporate 0.1, then deposit
            for (int c = 0; c < 3; c++) eager[c] *= 0.9;
            eager[step % 3] += 0.5;

            m.evaporate(0.5);
            for (int c = 0; c < 3; c++) eager[c] *= 0.5;
        }
        for (int c = 0; c < 3; c++) {
            assertEquals(eager[c], m.get(0, c), Math.abs(eager[c]) * 1e-9, "column " + c);
        }
    }

    @Test
    void updateEvaporatesByRhoOverColonySize() {
        TestMatrix m = new TestMatrix(2.0, 4, 0.4, 1, 2);
        m.init(null);
        m.update(null, depositOn(0, 1.0));
        assertEquals(2.0 * (1 - 0.1) + 1.0, m.get(0), 1e-12);
        assertEquals(2.0 * (1 - 0.1), m.get(1), 1e-12);
    }

    @Test
    void bulkReadMatchesSingleReads() {
        TestMatrix m = new TestMatrix(1.0, 1, 0.2, 2, 3);
        m.init(null);
        m.update(null, depositOn(2, 3.0));
        int[] idx = {m.index(0, 2), m.index(1, 1), m.index(0, 0)};
        double[] out = new double[3];
        m.read(idx, 3, out);
        for (int i = 0; i < 3; i++) assertEquals(m.get(idx[i]), out[i], 0.0);
    }

    @Test
    void maxMinBoundsAreEnforced() {
        TestMatrix m = new TestMatrix(1.0, 1, 0.5, 1, 2);
        m.withBounds(0.25, 2.0);
        m.init(null);

        m.update(null, depositOn(0, 100.0));      // would be 100.5 without the upper bound
        assertEquals(2.0, m.get(0), 1e-12);

        for (int i = 0; i < 20; i++) m.evaporate(0.5);
        assertEquals(0.25, m.get(1), 1e-12);        // decayed far below tauMin
    }

    @Test
    void forColonyKeepsParametersAndBounds() {
        TestMatrix m = new TestMatrix(1.0, 8, 0.3, 1, 1);
        m.withBounds(0.5, 0.75);
        PheromoneTrails copy = m.forColony(2);
        copy.init(null);
        assertEquals(0.3, copy.getEvaporationRatio());
        assertEquals(0.75, ((PheromoneMatrix) copy).get(0), 0.0); // initial 1.0 capped at tauMax
        assertSame(TestMatrix.class, copy.getClass());
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new TestMatrix(1, 0, 0.1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TestMatrix(1, 1, 0.0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TestMatrix(1, 1, 1.5, 1, 1));
    }
}
