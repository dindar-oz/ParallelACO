package core.algorithm.ga;

import core.algorithm.base.OptimizationAlgorithm;
import core.algorithm.localsearch.TerminalCondition;
import core.base.OptimizationProblem;

/**
 * Stops once {@code timeLimitMillis} milliseconds have passed since {@link #init()}. It uses a
 * monotonic clock, so it is not affected by changes to the wall-clock time.
 */
public class ExecutionTimeTC implements TerminalCondition {

    private final long timeLimitNanos;
    private volatile long startNanos = System.nanoTime();

    public ExecutionTimeTC(long timeLimitMillis) {
        this.timeLimitNanos = timeLimitMillis * 1_000_000L;
    }

    public ExecutionTimeTC(ExecutionTimeTC other) {
        this.timeLimitNanos = other.timeLimitNanos;
    }

    @Override
    public boolean isSatisfied(OptimizationProblem problem, OptimizationAlgorithm alg) {
        return System.nanoTime() - startNanos >= timeLimitNanos;
    }

    @Override
    public void init() {
        startNanos = System.nanoTime();
    }

    @Override
    public TerminalCondition clone() {
        return new ExecutionTimeTC(this);
    }
}
