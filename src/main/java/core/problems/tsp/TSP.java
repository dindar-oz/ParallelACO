package core.problems.tsp;

import core.base.ProblemModel;
import core.base.Representation;
import core.problems.tsp.tsplib.datamodel.tsp.Tsp;
import core.problems.tsp.tsplib.datamodel.types.EdgeWeightType;
import core.problems.tsp.tsplib.util.EdgeWeightCalculationMethodFactory;
import core.representation.Permutation;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Travelling Salesman Problem instance defined by a full distance matrix.
 */
public class TSP implements ProblemModel {

    private final int n;
    private final double[][] distances;
    private final boolean symmetric;

    public TSP(int n, double[][] distances) {
        this.n = n;
        this.distances = distances;
        this.symmetric = isSymmetric(distances);
    }

    /**
     * Builds an instance from a parsed TSPLIB file using the metric the file declares
     * ({@code EDGE_WEIGHT_TYPE}). This matters for comparing against published optima: e.g. the
     * {@code att*} instances use the ATT pseudo-Euclidean distance, and {@code EUC_2D} distances
     * are rounded to the nearest integer.
     */
    public static TSP fromTsp(Tsp tsp) {
        int n = tsp.getDimension();
        double[][] distances = new double[n][n];
        EdgeWeightType type = tsp.getEdgeWeightType();

        if (type == EdgeWeightType.EXPLICIT) {
            int[][] data = tsp.getEdgeWeightData()
                    .orElseThrow(() -> new IllegalArgumentException("EXPLICIT instance without EDGE_WEIGHT_SECTION"));
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++)
                    distances[r][c] = data[r][c];
            return new TSP(n, distances);
        }

        List<Tsp.Node> nodes = tsp.getNodes()
                .orElseThrow(() -> new IllegalArgumentException("Instance has no NODE_COORD_SECTION"));
        // Plain (unrounded) Euclidean distance is only a fallback for files without a known type.
        BiFunction<Tsp.Node, Tsp.Node, ? extends Number> metric = type == null
                ? (a, b) -> Math.hypot(a.getX() - b.getX(), a.getY() - b.getY())
                : EdgeWeightCalculationMethodFactory.getEdgeWeightCalculationMethod(type);

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                distances[r][c] = r == c ? 0 : metric.apply(nodes.get(r), nodes.get(c)).doubleValue();
            }
        }
        return new TSP(n, distances);
    }

    private static boolean isSymmetric(double[][] d) {
        for (int r = 0; r < d.length; r++)
            for (int c = r + 1; c < d.length; c++)
                if (d[r][c] != d[c][r]) return false;
        return true;
    }

    @Override
    public boolean isFeasible(Representation r) {
        assert r instanceof Permutation : "TSP only accepts Permutation";

        Permutation p = (Permutation) r;

        return p.size()==n;

    }

    public int getN() {
        return n;
    }

    public double getDistance(int first, int second) {
        return distances[first][second];
    }

    /** @return {@code true} when {@code d(i,j) == d(j,i)} for every pair of cities */
    public boolean isSymmetric() {
        return symmetric;
    }

    /**
     * Length of the tour obtained by starting at {@code start} and always moving to the
     * nearest unvisited city. Commonly used to scale pheromone values (e.g. {@code tau0 = m / C_nn}).
     */
    public double nearestNeighbourTourLength(int start) {
        boolean[] visited = new boolean[n];
        visited[start] = true;
        int current = start;
        double length = 0;
        for (int step = 1; step < n; step++) {
            int next = -1;
            for (int c = 0; c < n; c++) {
                if (!visited[c] && (next < 0 || distances[current][c] < distances[current][next]))
                    next = c;
            }
            length += distances[current][next];
            visited[next] = true;
            current = next;
        }
        return length + distances[current][start];
    }
}
