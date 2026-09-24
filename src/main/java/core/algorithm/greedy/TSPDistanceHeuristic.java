package core.algorithm.greedy;

import core.problems.tsp.TSP;
import core.utils.Pair;

public class TSPDistanceHeuristic implements Heuristic<TSP, Pair<Integer,Integer>> {

    @Override
    public double value(TSP tsp, Pair<Integer, Integer> edge) {
        return -tsp.getDistance(edge.getFirst(),edge.getSecond());
    }
}
