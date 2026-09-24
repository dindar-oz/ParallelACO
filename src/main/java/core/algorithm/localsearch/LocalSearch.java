package core.algorithm.localsearch;

import core.SimpleOptimizationProblem;
import core.algorithm.AbstractSMetaheuristic;
import core.problems.tsp.TSP;
import core.problems.tsp.TSPMinimumDistanceObjective;
import core.problems.tsp.TSPRandomSG;
import core.algorithm.base.SingleObjectiveOA;
import core.base.OptimizationProblem;
import core.base.Solution;

public class LocalSearch extends AbstractSMetaheuristic {

    NeighboringPolicy neighboringFunction;
    private TerminalCondition terminalCondition;

    public LocalSearch(NeighboringPolicy neighboringFunction, SolutionGenerator solutionGenerator, TerminalCondition terminalCondition) {
        super(solutionGenerator);
        this.neighboringFunction = neighboringFunction;
        this.terminalCondition = terminalCondition;
    }

    @Override
    protected void _perform(OptimizationProblem problem) {
        while(!terminalCondition.isSatisfied(problem,this))
        {
            Solution solution = neighboringFunction.getNeighbor(problem,getCurrentSolution());
            if (problem.objectiveType().betterThan(solution.objectiveValue(),getCurrentSolution().objectiveValue()))
                setCurrentSolution(problem,solution);
            else break;


            printBest();
        }
    }

    private void printBest() {

    }

    @Override
    protected void _init(OptimizationProblem problem) {

    }

    @Override
    public String getName() {
        return null;
    }

    public static void main(String[] args) {
        double distances[][] = {
                {0,2,3,4},
                {2,0,5,7},
                {3,5,0,1},
                {4,7,1,0}
        };

        TSP tsp = new TSP(4, distances);

        SimpleOptimizationProblem problem = new SimpleOptimizationProblem(tsp);
        problem.addObjective(new TSPMinimumDistanceObjective());

        SingleObjectiveOA alg = new LocalSearch(new BestImproveNP(new PermutationSwapIterator()),new TSPRandomSG(),new IterationBasedTC(100));

        Solution s = alg.perform(problem);
        System.out.println(s);
    }
}
