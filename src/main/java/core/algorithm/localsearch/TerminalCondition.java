package core.algorithm.localsearch;

import core.algorithm.base.OptimizationAlgorithm;
import core.base.OptimizationProblem;

/**
 * Decides when an algorithm stops.
 * <p>
 * Some implementations count their own invocations (e.g. {@link IterationBasedTC}), so
 * algorithms should call {@link #isSatisfied} exactly once per iteration.
 */
public interface TerminalCondition {
    boolean isSatisfied(OptimizationProblem problem, OptimizationAlgorithm alg);

    /** Resets the condition at the start of a run. */
    void init();

    /** @return an independent copy in its initial state */
    TerminalCondition clone();
}
