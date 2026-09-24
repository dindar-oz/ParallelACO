package core.problems.tsp;

import core.base.ProblemModel;
import core.base.Representation;
import core.representation.Permutation;

import java.nio.file.Path;

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
     * Reads a TSPLIB instance file, using the distance metric the file declares.
     *
     * @see TspLibReader
     */
    public static TSP fromTspLib(Path file) {
        return TspLibReader.readTsp(file);
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
