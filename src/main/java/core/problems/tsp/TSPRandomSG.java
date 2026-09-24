package core.problems.tsp;

import core.algorithm.SimpleSolution;
import core.algorithm.localsearch.SolutionGenerator;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.representation.Permutation;
import core.utils.random.RandUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TSPRandomSG implements SolutionGenerator {
    @Override
    public Solution generate(OptimizationProblem problem) {
        TSP tsp = (TSP) problem.model();
        int n = tsp.getN();
        int[] path = new int[n];
        List<Integer> availableCities = IntStream.range(0,n).boxed().collect(Collectors.toList());

        for (int i = 0; i < n; i++) {
            int index = RandUtils.randInt(availableCities.size());
            path[i] = availableCities.get(index);
            availableCities.remove(index);
        }
        Permutation permutation = new Permutation(path);
        Solution solution = new SimpleSolution(permutation,problem.objectiveValues(permutation));
        return solution;
    }
}
