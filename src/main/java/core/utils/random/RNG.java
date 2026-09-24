package core.utils.random;

/**
 * Minimal random number source used by the algorithms.
 * <p>
 * Implementations are not required to be thread-safe. Code that hands work to other threads
 * should give each thread its own generator obtained via {@link #split()}, which also keeps
 * seeded runs reproducible.
 * <p>
 * Created by dindar.oz on 7.01.2016.
 */
public interface RNG {

    /** @return a uniformly distributed int in {@code [0, max)} */
    int randInt(int max);

    /** @return a uniformly distributed double in {@code [0, 1)} */
    double randDouble();

    boolean randBoolean();

    /** @return a uniformly distributed long, used to derive seeds for child generators */
    long randLong();

    /**
     * Creates a new generator that is statistically independent of this one.
     * The child's state is derived from this generator, so splitting a seeded
     * generator in a fixed order always yields the same children.
     */
    RNG split();
}
