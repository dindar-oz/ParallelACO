package core.problems.tsp;

import core.ObjectiveFunction;
import core.ObjectiveType;
import core.base.ProblemModel;
import core.base.Representation;
import core.representation.Permutation;

public class TSPMinimumDistanceObjective  implements ObjectiveFunction {
    @Override
    public double value(ProblemModel problemModel, Representation r) {

        Permutation p = (Permutation) r;
        TSP tsp = (TSP) problemModel;

        double total =0;
        for (int i = 0; i < p.size()-1; i++) {
            total += tsp.getDistance(p.get(i), p.get(i+1));
        }
        // Closing edge back to the start (last -> first, which matters for asymmetric instances).
        total += tsp.getDistance(p.last(), p.get(0));
        return total;
    }

    @Override
    public ObjectiveType type() {
        return ObjectiveType.Minimization;
    }
}
