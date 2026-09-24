package core.examples;

import core.SimpleOptimizationProblem;
import core.algorithm.aco.ACO;
import core.algorithm.aco.ACO.ExecutionMode;
import core.algorithm.aco.Ant;
import core.algorithm.aco.problem.tsp.TSPAnt;
import core.algorithm.aco.problem.tsp.TSPPheromoneMatrix;
import core.algorithm.localsearch.TimeBasedTC;
import core.problems.tsp.TSP;
import core.problems.tsp.TSPMinimumDistanceObjective;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;

/**
 * Races sequential ACO against parallel ACO on the same TSP instance and plots, live, the best
 * tour length each has found so far.
 * <pre>
 *   ConvergenceDemo [cities | file.tsp] [seconds] [parallel modes...]
 *   defaults:        1000                 60        SYNCHRONOUS
 * </pre>
 * All algorithms start at the same moment with the same seed, colony and parameters; only the
 * execution mode differs. The parallel runs share {@code cores - 1} threads so that the
 * sequential run keeps a core of its own and the race is fair. When the time is up, the chart
 * is saved to {@code convergence.png} and the raw data to {@code convergence.csv}; the window
 * stays open until it is closed.
 */
public final class ConvergenceDemo {

    private static final long SEED = 42;
    private static final int ANTS = 10;
    private static final double EVAPORATION = 0.1;

    private ConvergenceDemo() {
    }

    public static void main(String[] args) throws Exception {
        String instanceArg = args.length > 0 ? args[0] : "1000";
        double seconds = args.length > 1 ? Double.parseDouble(args[1]) : 60;
        List<ExecutionMode> parallelModes = new ArrayList<>();
        for (int i = 2; i < args.length; i++)
            parallelModes.add(ExecutionMode.valueOf(args[i].toUpperCase(Locale.ROOT)));
        if (parallelModes.isEmpty())
            parallelModes.add(ExecutionMode.SYNCHRONOUS);
        if (parallelModes.size() > ConvergencePlot.SERIES_COLORS.length - 1)
            throw new IllegalArgumentException("At most " + (ConvergencePlot.SERIES_COLORS.length - 1) + " parallel modes");

        String instanceName;
        TSP tsp;
        if (instanceArg.matches("\\d+")) {
            int n = Integer.parseInt(instanceArg);
            tsp = randomTsp(n);
            instanceName = "random TSP, " + n + " cities";
        } else {
            tsp = TSP.fromTspLib(Path.of(instanceArg));
            instanceName = Path.of(instanceArg).getFileName() + " (" + tsp.getN() + " cities)";
        }
        SimpleOptimizationProblem problem = new SimpleOptimizationProblem(tsp);
        problem.addObjective(new TSPMinimumDistanceObjective());

        // Algorithms and their series: sequential first, so it always gets colour slot 1.
        int cores = Runtime.getRuntime().availableProcessors();
        int threadsEach = Math.max(1, (cores - 1) / parallelModes.size());
        long durationMillis = (long) (seconds * 1000);

        List<ACO> algorithms = new ArrayList<>();
        List<ConvergencePlot.Series> series = new ArrayList<>();
        List<ExecutionMode> modes = new ArrayList<>();
        modes.add(ExecutionMode.SEQUENTIAL);
        modes.addAll(parallelModes);
        for (int i = 0; i < modes.size(); i++) {
            ExecutionMode mode = modes.get(i);
            ACO aco = buildAco(mode, durationMillis, threadsEach);
            String label = mode == ExecutionMode.SEQUENTIAL
                    ? "Sequential (1 thread)"
                    : capitalise(mode) + " (" + threadsEach + " threads)";
            algorithms.add(aco);
            series.add(new ConvergencePlot.Series(label, ConvergencePlot.SERIES_COLORS[i], aco::getSolutionCount));
        }

        double nnTour = tsp.nearestNeighbourTourLength(0);
        ConvergencePlot plot = new ConvergencePlot("ACO convergence: sequential vs parallel · " + instanceName,
                series, seconds, nnTour, "nearest-neighbour tour");

        long[] t0 = new long[1];
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(algorithms.size());
        for (int i = 0; i < algorithms.size(); i++) {
            ACO aco = algorithms.get(i);
            ConvergencePlot.Series s = series.get(i);
            // Timestamps come from one shared clock so the curves are directly comparable.
            aco.addImprovementListener((elapsed, best) -> s.add((System.nanoTime() - t0[0]) / 1e9, best.objectiveValue()));
            Thread runner = new Thread(() -> {
                try {
                    start.await();
                    aco.perform(problem);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    s.finishedAt = (System.nanoTime() - t0[0]) / 1e9;
                    done.countDown();
                }
            }, "convergence-" + modes.get(i));
            runner.setDaemon(true);
            runner.start();
        }

        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("ACO convergence");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setContentPane(plot);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        System.out.printf("Racing %s on %s for %.0f s (%d cores)%n", modes, instanceName, seconds, cores);
        t0[0] = System.nanoTime();
        start.countDown();

        Timer refresh = new Timer(100, e -> {
            plot.setNow((System.nanoTime() - t0[0]) / 1e9);
            plot.repaint();
        });
        refresh.start();

        done.await();
        refresh.stop();
        SwingUtilities.invokeAndWait(() -> {
            plot.setNow(series.stream().mapToDouble(s -> s.finishedAt).max().orElse(seconds));
            plot.repaint();
        });

        Path png = Path.of("convergence.png").toAbsolutePath();
        Path csv = Path.of("convergence.csv").toAbsolutePath();
        SwingUtilities.invokeAndWait(() -> {
            try {
                ImageIO.write(plot.snapshot(), "png", png.toFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        writeCsv(csv, series);

        for (int i = 0; i < algorithms.size(); i++) {
            System.out.printf("%-26s best %,.0f after %,d tours%n", series.get(i).label,
                    algorithms.get(i).getBestSolution().objectiveValue(), algorithms.get(i).getSolutionCount());
        }
        System.out.println("Saved " + png + " and " + csv + " (close the window to exit)");
    }

    private static ACO buildAco(ExecutionMode mode, long durationMillis, int threads) {
        List<Ant> colony = new ArrayList<>(ANTS);
        for (int i = 0; i < ANTS; i++)
            colony.add(new TSPAnt());
        return new ACO(TSPPheromoneMatrix.withNearestNeighbourInit(ANTS, EVAPORATION), colony,
                new TimeBasedTC(durationMillis), mode)
                .withSeed(SEED)
                .withThreads(threads);
    }

    /** Random Euclidean instance with cities in a 10,000 x 10,000 square (fixed seed). */
    private static TSP randomTsp(int n) {
        SplittableRandom r = new SplittableRandom(SEED);
        double[][] xy = new double[n][2];
        for (double[] p : xy) {
            p[0] = r.nextDouble(10_000);
            p[1] = r.nextDouble(10_000);
        }
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                d[i][j] = Math.rint(Math.hypot(xy[i][0] - xy[j][0], xy[i][1] - xy[j][1]));
        return new TSP(n, d);
    }

    private static void writeCsv(Path file, List<ConvergencePlot.Series> series) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file))) {
            out.println("algorithm,seconds,best");
            for (ConvergencePlot.Series s : series)
                for (double[] p : s.points)
                    out.printf(Locale.ROOT, "%s,%.3f,%.1f%n", s.label, p[0], p[1]);
        }
    }

    private static String capitalise(ExecutionMode mode) {
        String name = mode.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
