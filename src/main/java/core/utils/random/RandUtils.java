package core.utils.random;

import java.util.List;

/**
 * Static random helpers and roulette-wheel selection.
 * <p>
 * The methods without an {@link RNG} argument use a process-wide default generator. That
 * generator is a thread-safe {@link SecureRandomRNG} unless {@link #setSeed(long)} replaces it
 * with a seeded (non-thread-safe) one. Parallel code should not rely on the default: pass a
 * per-thread generator (see {@link RNG#split()}) to the overloads that accept one.
 */
public final class RandUtils {

    private static volatile RNG r = new SecureRandomRNG();

    private RandUtils() {
    }

    /**
     * Replaces the default generator with a seeded one so that single-threaded code using the
     * static helpers becomes reproducible.
     */
    public static void setSeed(long seed) {
        r = new SplittableRNG(seed);
    }

    public static RNG getDefaultRNG() {
        return r;
    }

    public static int randInt(int n) {
        return r.randInt(n);
    }

    public static double randDouble() {
        return r.randDouble();
    }

    /** @return a uniformly distributed double in {@code [lb, ub)} */
    public static double randDouble(double lb, double ub) {
        return lb + (ub - lb) * r.randDouble();
    }

    /** @return {@code true} with probability {@code p} */
    public static boolean randBoolean(double p) {
        return r.randDouble() < p;
    }

    /** In-place Fisher-Yates shuffle driven by the given generator. */
    public static <T> void shuffle(RNG rng, List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.randInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    // ------------------------------------------------------------------ roulette selection

    /** Returns an index chosen with probability proportional to its weight. */
    public static int rouletteSelect(double[] weight) {
        return rouletteSelect(r, weight);
    }

    public static int rouletteSelect(double[] weight, int len) {
        return rouletteSelect(r, weight, len);
    }

    public static int rouletteSelect(RNG rng, double[] weight) {
        return rouletteSelect(rng, weight, weight.length);
    }

    /**
     * Returns an index in {@code [0, len)} chosen with probability {@code weight[i] / sum(weight)}.
     * Weights must be non-negative. If they are all zero the choice is uniform.
     */
    public static int rouletteSelect(RNG rng, double[] weight, int len) {
        double weightSum = 0;
        for (int i = 0; i < len; i++) {
            weightSum += weight[i];
        }
        if (!(weightSum > 0))
            return rng.randInt(len);

        double value = rng.randDouble() * weightSum;
        for (int i = 0; i < len; i++) {
            value -= weight[i];
            if (value < 0) return i;
        }
        // Only reached through rounding errors: return the last index that could be selected.
        return lastPositive(weight, len);
    }

    /** Spins the same wheel {@code count} times; cheaper than repeated {@link #rouletteSelect}. */
    public static int[] rouletteSelectMulti(RNG rng, double[] weights, int count) {
        Roulette roulette = new Roulette(rng, weights);
        int[] indexes = new int[count];
        for (int i = 0; i < count; i++) {
            indexes[i] = roulette.spin();
        }
        return indexes;
    }

    /** Inverse roulette selection: the smaller the weight, the more likely the index. */
    public static int rouletteSelectInverse(double[] weight) {
        return rouletteSelectInverse(r, weight);
    }

    public static int rouletteSelectInverse(double[] weight, int end) {
        return rouletteSelectInverse(r, weight, end);
    }

    public static int rouletteSelectInverse(RNG rng, double[] weight) {
        return rouletteSelectInverse(rng, weight, weight.length);
    }

    /**
     * Inverse roulette selection over {@code [0, end)}. Each weight is mirrored around the
     * middle of its range, {@code w'[i] = (min + max) - w[i]}, so the smallest weight gets the
     * largest share and equal weights stay uniform. The wheel is then spun on the mirrored
     * weights.
     */
    public static int rouletteSelectInverse(RNG rng, double[] weight, int end) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < end; i++) {
            min = Math.min(min, weight[i]);
            max = Math.max(max, weight[i]);
        }
        double mirror = min + max;

        // The mirrored weights sum to end * mirror - sum(weight).
        double invertedSum = 0;
        for (int i = 0; i < end; i++) {
            invertedSum += mirror - weight[i];
        }
        if (!(invertedSum > 0))
            return rng.randInt(end);

        double value = rng.randDouble() * invertedSum;
        int lastPositive = 0;
        for (int i = 0; i < end; i++) {
            double w = mirror - weight[i];
            if (w > 0) lastPositive = i;
            value -= w;
            if (value < 0) return i;
        }
        return lastPositive; // rounding errors only
    }

    private static int lastPositive(double[] weight, int len) {
        for (int i = len - 1; i >= 0; i--) {
            if (weight[i] > 0) return i;
        }
        return len - 1;
    }
}
