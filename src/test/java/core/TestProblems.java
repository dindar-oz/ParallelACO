package core;

import core.problems.coalitionFormation.MCFPCostObjective;
import core.problems.coalitionFormation.MCFPModel;
import core.problems.coalitionFormation.MultiCFPGenerator;
import core.problems.tsp.TSP;
import core.problems.tsp.TSPMinimumDistanceObjective;
import core.problems.wsn.Point2D;
import core.problems.wsn.WSNOptimizationProblem;
import core.problems.wsn.WSNProblemGenerator;
import core.utils.random.SplittableRNG;

import java.util.SplittableRandom;

/** Small, deterministic problem instances shared by the tests. */
public final class TestProblems {

    private TestProblems() {
    }

    /** Random Euclidean TSP with {@code n} cities in a 100x100 square. */
    public static SimpleOptimizationProblem randomTsp(int n, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        double[][] xy = new double[n][2];
        for (double[] p : xy) {
            p[0] = r.nextDouble(100);
            p[1] = r.nextDouble(100);
        }
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                d[i][j] = Math.hypot(xy[i][0] - xy[j][0], xy[i][1] - xy[j][1]);

        SimpleOptimizationProblem problem = new SimpleOptimizationProblem(new TSP(n, d));
        problem.addObjective(new TSPMinimumDistanceObjective());
        return problem;
    }

    /**
     * WSN instance: a grid of potential sensor positions and random targets inside it. The
     * ranges are generous enough for a feasible deployment to exist.
     */
    public static WSNOptimizationProblem smallWsn(int m, int k, long seed) {
        Point2D[] positions = WSNProblemGenerator.generateGrid(new Point2D(200, 200), new Point2D(10, 10), 30);
        SplittableRandom r = new SplittableRandom(seed);
        Point2D[] targets = new Point2D[25];
        for (int i = 0; i < targets.length; i++) {
            targets[i] = new Point2D(20 + r.nextInt(160), 20 + r.nextInt(160));
        }
        return WSNProblemGenerator.builder()
                .m(m).k(k)
                .communicatingRange(70)
                .sensingRange(50)
                .build()
                .generateProblemInstance(targets, positions);
    }

    public static SimpleOptimizationProblem mcfp(int tasks, int agents, long seed) {
        MCFPModel model = MultiCFPGenerator.generateMultiCFProblem(tasks, agents, 3, new SplittableRNG(seed));
        SimpleOptimizationProblem problem = new SimpleOptimizationProblem(model);
        problem.addObjective(new MCFPCostObjective());
        return problem;
    }
}
