package core.utils.random;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandUtilsTest {

    private static final int SPINS = 200_000;
    private static final double TOLERANCE = 0.01;

    private static double[] frequencies(int size, java.util.function.IntSupplier spin) {
        double[] f = new double[size];
        for (int i = 0; i < SPINS; i++) f[spin.getAsInt()]++;
        for (int i = 0; i < size; i++) f[i] /= SPINS;
        return f;
    }

    @Test
    void rouletteSelectIsProportionalToWeights() {
        RNG rng = new SplittableRNG(1);
        double[] w = {1, 3, 6};
        double[] f = frequencies(3, () -> RandUtils.rouletteSelect(rng, w));
        assertArrayEquals(new double[]{0.1, 0.3, 0.6}, f, TOLERANCE);
    }

    @Test
    void rouletteSelectNeverPicksZeroWeights() {
        RNG rng = new SplittableRNG(2);
        double[] w = {0, 5, 0, 5, 0};
        for (int i = 0; i < 10_000; i++) {
            int idx = RandUtils.rouletteSelect(rng, w);
            assertTrue(idx == 1 || idx == 3, "picked zero-weight index " + idx);
        }
    }

    @Test
    void rouletteSelectFallsBackToUniformForAllZeroWeights() {
        RNG rng = new SplittableRNG(3);
        double[] f = frequencies(4, () -> RandUtils.rouletteSelect(rng, new double[4]));
        assertArrayEquals(new double[]{0.25, 0.25, 0.25, 0.25}, f, TOLERANCE);
    }

    @Test
    void rouletteSelectRespectsLength() {
        RNG rng = new SplittableRNG(4);
        double[] w = {1, 1, 100};  // index 2 is outside len = 2
        for (int i = 0; i < 10_000; i++) {
            assertTrue(RandUtils.rouletteSelect(rng, w, 2) < 2);
        }
    }

    /** Regression test: the old implementation returned [0.83, 0.17, 0.0] for these weights. */
    @Test
    void inverseRouletteMirrorsWeights() {
        RNG rng = new SplittableRNG(5);
        double[] w = {1, 1, 10};
        // mirrored = (1+10) - w = {10, 10, 1} -> {10/21, 10/21, 1/21}
        double[] f = frequencies(3, () -> RandUtils.rouletteSelectInverse(rng, w));
        assertArrayEquals(new double[]{10 / 21.0, 10 / 21.0, 1 / 21.0}, f, TOLERANCE);
    }

    @Test
    void inverseRouletteIsUniformForEqualWeights() {
        RNG rng = new SplittableRNG(6);
        double[] f = frequencies(3, () -> RandUtils.rouletteSelectInverse(rng, new double[]{2, 2, 2}));
        assertArrayEquals(new double[]{1 / 3.0, 1 / 3.0, 1 / 3.0}, f, TOLERANCE);
    }

    @Test
    void rouletteSelectMultiMatchesWeights() {
        RNG rng = new SplittableRNG(7);
        int[] picks = RandUtils.rouletteSelectMulti(rng, new double[]{1, 0, 3}, SPINS);
        double[] f = new double[3];
        for (int p : picks) f[p]++;
        for (int i = 0; i < 3; i++) f[i] /= SPINS;
        assertArrayEquals(new double[]{0.25, 0.0, 0.75}, f, TOLERANCE);
    }

    @Test
    void seededGeneratorsAreReproducibleAndSplitsAreIndependent() {
        RNG a = new SplittableRNG(99);
        RNG b = new SplittableRNG(99);
        for (int i = 0; i < 100; i++) assertEquals(a.randLong(), b.randLong());

        RNG parent = new SplittableRNG(99);
        RNG child1 = parent.split();
        RNG child2 = parent.split();
        assertNotEquals(child1.randLong(), child2.randLong());
    }

    @Test
    void shuffleIsAPermutationAndSeeded() {
        List<Integer> l1 = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        List<Integer> l2 = new ArrayList<>(l1);
        RandUtils.shuffle(new SplittableRNG(11), l1);
        RandUtils.shuffle(new SplittableRNG(11), l2);
        assertEquals(l1, l2);
        assertEquals(45, l1.stream().mapToInt(Integer::intValue).sum());
        assertEquals(10, l1.stream().distinct().count());
    }
}
