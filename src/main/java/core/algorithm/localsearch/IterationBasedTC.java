package core.algorithm.localsearch;

import core.algorithm.base.OptimizationAlgorithm;
import core.base.OptimizationProblem;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stops after {@code iterationLimit} iterations, where every call to {@link #isSatisfied}
 * counts as one iteration (so call it once per iteration). The counter is atomic, so
 * concurrent callers never lose counts.
 * <p>
 * For algorithms that expose their own counter, {@link core.algorithm.nsga.IterationCountTC}
 * is an alternative whose check has no side effects.
 */
public class IterationBasedTC implements TerminalCondition {

    private final int iterationLimit;
    private final AtomicInteger currentIteration = new AtomicInteger();

    public IterationBasedTC(int iterationLimit) {
        this.iterationLimit = iterationLimit;
    }

    @Override
    public boolean isSatisfied(OptimizationProblem problem, OptimizationAlgorithm alg) {
        // Consume one iteration if any is left; otherwise the limit is reached.
        return currentIteration.getAndUpdate(i -> i < iterationLimit ? i + 1 : i) >= iterationLimit;
    }

    @Override
    public void init() {
        currentIteration.set(0);
    }

    @Override
    public TerminalCondition clone() {
        return new IterationBasedTC(iterationLimit);
    }
}
