package core.algorithm.localsearch;


import core.algorithm.Iterating;
import core.algorithm.base.OptimizationAlgorithm;
import core.base.OptimizationProblem;

public class IterationCountTC implements TerminalCondition {
    private long maxIterationCount;

    public IterationCountTC(long maxIterationCount) {
        this.maxIterationCount = maxIterationCount;
    }



    @Override
    public boolean isSatisfied(OptimizationProblem problem, OptimizationAlgorithm alg) {
        if (! (alg instanceof Iterating))
            throw new RuntimeException("This TC works only for iterating algorithms");
        Iterating iterating = (Iterating) alg;
        return iterating.iterationcount()>maxIterationCount;
    }

    @Override
    public void init() {

    }

    @Override
    public TerminalCondition clone() {
        return new IterationCountTC(maxIterationCount);
    }
}
