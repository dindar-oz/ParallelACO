package core.utils.random;

/**
 * Roulette wheel that is built once and spun many times; each spin is a binary search
 * over the cumulative weights.
 */
class Roulette
{
    private final double[] cumulative;
    private final double total;
    private final RNG rng;

    public Roulette(RNG rng, double[] weights) {
        this.rng = rng;
        cumulative = new double[weights.length + 1];
        for (int i = 0; i < weights.length; i++) {
            cumulative[i + 1] = cumulative[i] + weights[i];
        }
        total = cumulative[weights.length];
    }

    public int spin() {
        int n = cumulative.length - 1;
        if (!(total > 0))
            return rng.randInt(n); // degenerate wheel: every slot has zero weight

        double r = rng.randDouble() * total;

        // Binary search for the slot i with cumulative[i] <= r < cumulative[i+1].
        int a = 0;
        int b = n;
        while (b - a > 1) {
            int mid = (a + b) >>> 1;
            if (cumulative[mid] > r) b = mid;
            else a = mid;
        }
        return a;
    }
}
