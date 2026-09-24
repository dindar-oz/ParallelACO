package core.utils.random;

import java.util.SplittableRandom;

/**
 * Fast, seedable {@link RNG} backed by {@link SplittableRandom}.
 * <p>
 * Not thread-safe: give every thread (e.g. every ant) its own instance via {@link #split()}.
 */
public final class SplittableRNG implements RNG {

    private final SplittableRandom random;

    /** Creates a generator with a fixed seed; runs using it are reproducible. */
    public SplittableRNG(long seed) {
        this(new SplittableRandom(seed));
    }

    /** Creates a generator with an unpredictable seed. */
    public SplittableRNG() {
        this(new SplittableRandom());
    }

    private SplittableRNG(SplittableRandom random) {
        this.random = random;
    }

    @Override
    public int randInt(int max) {
        return random.nextInt(max);
    }

    @Override
    public double randDouble() {
        return random.nextDouble();
    }

    @Override
    public boolean randBoolean() {
        return random.nextBoolean();
    }

    @Override
    public long randLong() {
        return random.nextLong();
    }

    @Override
    public RNG split() {
        return new SplittableRNG(random.split());
    }
}
