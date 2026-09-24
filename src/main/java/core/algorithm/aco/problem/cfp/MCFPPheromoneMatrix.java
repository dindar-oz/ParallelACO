package core.algorithm.aco.problem.cfp;

import core.algorithm.aco.PheromoneMatrix;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.coalitionFormation.MCFPModel;
import core.problems.coalitionFormation.MultiCoalition;
import core.representation.IntegerAssignment;

/**
 * Pheromone for the multi-task coalition formation problem: an
 * {@code agents x (tasks + 1)} matrix where cell {@code (a, t)} rewards assigning agent
 * {@code a} to task {@code t}. Column 0 means "not assigned to any task". Each solution
 * deposits {@code 1 / cost} on its assignments.
 */
public class MCFPPheromoneMatrix extends PheromoneMatrix {

    public MCFPPheromoneMatrix(double initialValue, int colonySize, double evaporationRatio) {
        super(initialValue, colonySize, evaporationRatio);
    }

    @Override
    public void init(OptimizationProblem problem) {
        MCFPModel mcfp = (MCFPModel) problem.model();
        // +1 column for "unassigned" (task index 0); real tasks are 1..taskCount.
        allocate(mcfp.getAgentCount(), mcfp.getTaskCount() + 1, initialValue);
    }

    @Override
    protected void deposit(OptimizationProblem problem, Solution s) {
        MultiCoalition mc = (MultiCoalition) s.getRepresentation();
        IntegerAssignment assignment = mc.getCoalitionAssignment();
        double delta = 1.0 / s.objectiveValue();
        for (int agent = 0; agent < assignment.getLength(); agent++) {
            add(index(agent, assignment.get(agent)), delta);
        }
    }

    @Override
    protected PheromoneMatrix create(int colonySize) {
        return new MCFPPheromoneMatrix(initialValue, colonySize, evaporationRatio);
    }
}
