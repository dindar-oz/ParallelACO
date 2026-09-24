package core.algorithm;

import core.base.Representation;
import core.base.Solution;

import java.util.Arrays;

public class SimpleSolution implements Solution {

    Representation representation;
    double[] objectives;

    public SimpleSolution(Representation representation, double[] objectives) {
        this.representation = representation;
        this.objectives = objectives;
    }

    public SimpleSolution(Representation representation, double objective) {
        this.representation = representation;
        this.objectives = new double[]{objective};
    }

    @Override
    public Solution clone() {
        return new SimpleSolution(representation.clone(),Arrays.copyOf(objectives,objectives.length));
    }

    @Override
    public Representation getRepresentation() {
        return representation;
    }

    @Override
    public double[] objectiveValues() {
        return objectives;
    }

    @Override
    public double objectiveValue(int index) {
        return objectives[index];
    }

    @Override
    public double objectiveValue() {
        return objectives[0];
    }

    @Override
    public String toString() {
        return Arrays.toString(objectives)+ " REP:"+representation ;
    }
}
