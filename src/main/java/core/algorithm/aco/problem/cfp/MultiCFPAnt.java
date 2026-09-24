package core.algorithm.aco.problem.cfp;

import core.algorithm.SimpleSolution;
import core.algorithm.aco.BaseAnt;
import core.algorithm.aco.PheromoneMatrix;
import core.algorithm.aco.PheromoneTrails;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.coalitionFormation.MCFPModel;
import core.problems.coalitionFormation.MultiCoalition;
import core.representation.IntegerAssignment;
import core.utils.random.RNG;
import core.utils.random.RandUtils;

/**
 * Ant for the multi-task coalition formation problem. Each step:
 * <ol>
 *   <li>picks an idle agent by <em>inverse</em> roulette on its "unassigned" pheromone
 *       (column 0), so agents that good solutions tend to leave idle are picked less often;</li>
 *   <li>assigns it to a task chosen by roulette on the agent's task pheromone (columns 1..T).</li>
 * </ol>
 * Construction stops when the coalitions are feasible or no idle agent is left.
 */
public class MultiCFPAnt extends BaseAnt {

    private MCFPModel mcfp;
    private PheromoneMatrix pheromone;
    private MultiCoalition currentAssignment;

    // Reused buffers.
    private int[] idleAgents;
    private int[] cellIndices;
    private double[] agentWeights;
    private double[] taskWeights;

    @Override
    public void init(OptimizationProblem problem, PheromoneTrails pheromoneTrails, RNG rng) {
        super.init(problem, pheromoneTrails, rng);
        mcfp = (MCFPModel) problem.model();
        pheromone = requireMatrix(pheromoneTrails, MultiCFPAnt.class);

        int agents = mcfp.getAgentCount();
        idleAgents = new int[agents];
        cellIndices = new int[Math.max(agents, mcfp.getTaskCount())];
        agentWeights = new double[agents];
        taskWeights = new double[mcfp.getTaskCount()];
    }

    @Override
    protected void reset() {
        currentAssignment = new MultiCoalition(new IntegerAssignment(new int[mcfp.getAgentCount()]),
                mcfp.getAgents(), mcfp.getTaskCount());
    }

    @Override
    protected void next() {
        int idleCount = collectIdleAgents();

        for (int i = 0; i < idleCount; i++) {
            cellIndices[i] = pheromone.index(idleAgents[i], 0);
        }
        pheromone.read(cellIndices, idleCount, agentWeights);
        int agent = idleAgents[RandUtils.rouletteSelectInverse(rng, agentWeights, idleCount)];

        int taskCount = mcfp.getTaskCount();
        for (int t = 0; t < taskCount; t++) {
            cellIndices[t] = pheromone.index(agent, t + 1);
        }
        pheromone.read(cellIndices, taskCount, taskWeights);
        int task = RandUtils.rouletteSelect(rng, taskWeights, taskCount) + 1;

        currentAssignment.reassign(agent, task);
    }

    @Override
    protected boolean solutionConstructed() {
        return collectIdleAgents() == 0 || mcfp.isFeasible(currentAssignment);
    }

    /** Fills {@link #idleAgents} with agents assigned to no task; returns how many there are. */
    private int collectIdleAgents() {
        int[] assignment = currentAssignment.getCoalitionAssignment().getValues();
        int count = 0;
        for (int a = 0; a < assignment.length; a++) {
            if (assignment[a] == 0)
                idleAgents[count++] = a;
        }
        return count;
    }

    @Override
    protected Solution buildSolution() {
        return new SimpleSolution(currentAssignment, problem.objectiveValues(currentAssignment));
    }
}
