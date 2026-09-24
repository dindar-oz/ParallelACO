package core.algorithm.greedy;

import core.SimpleOptimizationProblem;
import core.algorithm.SimpleSolution;
import core.problems.tsp.TSP;
import core.problems.tsp.TSPMinimumDistanceObjective;
import core.algorithm.base.SingleObjectiveOA;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.representation.Permutation;
import core.utils.Pair;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TSPGreedyAlgorithm implements SingleObjectiveOA {

    Heuristic<TSP, Pair<Integer,Integer>> heuristic;
    Random rng = new SecureRandom();

    public TSPGreedyAlgorithm(Heuristic<TSP, Pair<Integer, Integer>> heuristic) {
        this.heuristic = heuristic;
    }

    @Override
    public String getName() {
        return "TSP Greedy";
    }

    @Override
    public Solution perform(OptimizationProblem problem) {

        TSP tsp = (TSP) problem.model();

        List<Integer> visited = new ArrayList<>();
        List<Integer> nonVisited = IntStream.range(0,tsp.getN()).boxed().collect(Collectors.toList());

        visitRandomCity(tsp,visited,nonVisited);

        while(!nonVisited.isEmpty())
        {
            visitANewCity(tsp,visited,nonVisited);

            System.out.println(Arrays.toString(visited.toArray()));
        }

        Permutation p = new Permutation(visited);

        Solution solution = new SimpleSolution(p,problem.objectiveValues(p));

        return solution;
    }

    private void visitANewCity(TSP tsp, List<Integer> visited, List<Integer> nonVisited) {
        Integer currentCity= visited.get(visited.size()-1);

        int best = nonVisited.stream().max((x,y)-> Double.compare(heuristic.value(tsp,Pair.makePair(currentCity,x)),
                                                        heuristic.value(tsp,Pair.makePair(currentCity,y)))).get();

        int index = nonVisited.indexOf(best);
        visitCity(visited,nonVisited,index);
    }

    private void visitRandomCity(TSP tsp, List<Integer> visited, List<Integer> nonVisited) {
        int city = rng.nextInt(tsp.getN());

        visitCity(visited,nonVisited,city);
    }

    private void visitCity(List<Integer> visited, List<Integer> nonVisited, int cityIndex) {
        visited.add(nonVisited.get(cityIndex));
        nonVisited.remove(cityIndex);
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

        SingleObjectiveOA alg = new TSPGreedyAlgorithm( new TSPDistanceHeuristic());

        Solution s = alg.perform(problem);

        System.out.println(s);

    }
}
