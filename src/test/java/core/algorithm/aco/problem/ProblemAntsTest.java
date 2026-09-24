package core.algorithm.aco.problem;

import core.SimpleOptimizationProblem;
import core.TestProblems;
import core.algorithm.aco.ACO;
import core.algorithm.aco.ACO.ExecutionMode;
import core.algorithm.aco.Ant;
import core.algorithm.aco.problem.cfp.MCFPPheromoneMatrix;
import core.algorithm.aco.problem.cfp.MultiCFPAnt;
import core.algorithm.aco.problem.wsn.WSNAnt;
import core.algorithm.aco.problem.wsn.WSNAnt.CandidateStrategy;
import core.algorithm.aco.problem.wsn.WSNAnt.SelectionBias;
import core.algorithm.aco.problem.wsn.WSNPheromoneMatrix;
import core.algorithm.aco.problem.wsn.WSNQPheromoneMatrix;
import core.algorithm.localsearch.IterationBasedTC;
import core.base.Solution;
import core.problems.coalitionFormation.MCFPModel;
import core.problems.wsn.WSN;
import core.problems.wsn.WSNOptimizationProblem;
import core.utils.random.SplittableRNG;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ProblemAntsTest {

    private static List<Ant> colony(int size, Supplier<Ant> factory) {
        List<Ant> c = new ArrayList<>();
        for (int i = 0; i < size; i++) c.add(factory.get());
        return c;
    }

    @Test
    void wsnAntBuildsFeasibleNetworks() {
        WSNOptimizationProblem problem = TestProblems.smallWsn(2, 2, 1);
        WSN wsn = (WSN) problem.model();

        WSNAnt ant = new WSNAnt(0.6);
        WSNPheromoneMatrix trails = new WSNPheromoneMatrix(1.0, 1, 0.1);
        trails.init(problem);
        ant.init(problem, trails, new SplittableRNG(3));

        for (int i = 0; i < 20; i++) {
            Solution s = ant.constructSolution();
            assertTrue(wsn.isFeasible(s.getRepresentation()), "infeasible network: " + s);
            assertEquals(problem.objectiveValue(s.getRepresentation()), s.objectiveValue(), 0.0);
        }
    }

    @Test
    void wsnAcoRunsInEveryModeAndStrategy() {
        WSNOptimizationProblem problem = TestProblems.smallWsn(1, 1, 2);
        for (ExecutionMode mode : ExecutionMode.values()) {
            for (CandidateStrategy strategy : CandidateStrategy.values()) {
                for (SelectionBias bias : SelectionBias.values()) {
                    List<Ant> ants = colony(4, () -> new WSNAnt(0.6, strategy, bias));
                    ACO aco = new ACO(new WSNQPheromoneMatrix(1.0, ants.size(), 0.1), ants,
                            new IterationBasedTC(15), mode).withThreads(2).withSeed(4);
                    Solution best = aco.perform(problem);
                    assertNotNull(best, mode + "/" + strategy + "/" + bias);
                    assertTrue(((WSN) problem.model()).isFeasible(best.getRepresentation()),
                            "best solution infeasible for " + mode + "/" + strategy + "/" + bias);
                }
            }
        }
    }

    /** Regression test: the pheromone matrix used to be sized agents x (agents + 1). */
    @Test
    void mcfpAcoWorksWithMoreTasksThanAgents() {
        SimpleOptimizationProblem problem = TestProblems.mcfp(12, 5, 1);
        MCFPModel model = (MCFPModel) problem.model();
        assertTrue(model.getTaskCount() > model.getAgentCount());

        List<Ant> ants = colony(4, MultiCFPAnt::new);
        ACO aco = new ACO(new MCFPPheromoneMatrix(10, ants.size(), 0.1), ants,
                new IterationBasedTC(20), ExecutionMode.SYNCHRONOUS).withSeed(2);
        assertNotNull(aco.perform(problem));
    }

    @Test
    void mcfpAcoRunsOnATypicalInstance() {
        SimpleOptimizationProblem problem = TestProblems.mcfp(5, 20, 3);
        List<Ant> ants = colony(4, MultiCFPAnt::new);
        ACO aco = new ACO(new MCFPPheromoneMatrix(10, ants.size(), 0.1), ants,
                new IterationBasedTC(30), ExecutionMode.ISLAND).withThreads(2).withSeed(3);
        assertNotNull(aco.perform(problem));
    }
}
