package core.utils.random;

import java.security.SecureRandom;

/**
 * {@link RNG} backed by {@link SecureRandom}. It is thread-safe but slow and cannot be seeded,
 * so prefer {@link SplittableRNG} for experiments that must be reproducible.
 * <p>
 * Created by dindar.oz on 7.01.2016.
 */
public class SecureRandomRNG implements RNG {
    private final SecureRandom r = new SecureRandom();

    @Override
    public int randInt(int max) {
        return r.nextInt(max);
    }

    @Override
    public double randDouble() {
        return r.nextDouble();
    }

    @Override
    public boolean randBoolean() {
        return r.nextBoolean();
    }

    @Override
    public long randLong() {
        return r.nextLong();
    }

    @Override
    public RNG split() {
        return new SecureRandomRNG();
    }
}
