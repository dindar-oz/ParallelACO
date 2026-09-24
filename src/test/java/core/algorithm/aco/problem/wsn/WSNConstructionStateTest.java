package core.algorithm.aco.problem.wsn;

import core.TestProblems;
import core.problems.wsn.WSN;
import core.problems.wsn.WSNOptimizationProblem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WSNConstructionStateTest {

    /**
     * The incremental counters must agree with the full feasibility check of the model after
     * every single step of random construction sequences.
     */
    @ParameterizedTest
    @CsvSource({"1,1", "2,2", "3,1", "1,3"})
    void incrementalFeasibilityMatchesModel(int m, int k) {
        WSNOptimizationProblem problem = TestProblems.smallWsn(m, k, 42);
        WSN wsn = (WSN) problem.model();
        WSNData data = WSNData.of(wsn);
        WSNConstructionState state = new WSNConstructionState(data);
        SplittableRandom r = new SplittableRandom(m * 31L + k);

        for (int run = 0; run < 20; run++) {
            state.reset();
            for (int step = 0; step < data.positionsSize(); step++) {
                state.turnOn(r.nextInt(data.positionsSize()));
                assertEquals(wsn.isFeasible(state.toBitString()), state.isFeasible(),
                        "mismatch at run " + run + ", step " + step);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"1,1", "2,2"})
    void wsnDataAgreesWithModelFeasibility(int m, int k) {
        WSN wsn = (WSN) TestProblems.smallWsn(m, k, 7).model();
        WSNData data = WSNData.of(wsn);
        WSNConstructionState state = new WSNConstructionState(data);
        SplittableRandom r = new SplittableRandom(3);
        for (int step = 0; step < data.positionsSize(); step++) {
            state.turnOn(r.nextInt(data.positionsSize()));
            assertEquals(wsn.isFeasible(state.toBitString()), data.isFeasible(state.toBitString()));
        }
    }
}
