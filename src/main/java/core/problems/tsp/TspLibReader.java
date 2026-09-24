package core.problems.tsp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reader for TSPLIB95 files (symmetric/asymmetric TSP instances and tours), following the
 * format description in G. Reinelt, "TSPLIB 95" (Universität Heidelberg).
 * <p>
 * Supported:
 * <ul>
 *   <li>{@code EDGE_WEIGHT_TYPE}: EXPLICIT, EUC_2D, EUC_3D, CEIL_2D, MAN_2D, MAN_3D, MAX_2D,
 *       MAX_3D, GEO and ATT, with the integer rounding the specification prescribes (so the
 *       tour lengths can be compared with published optima);</li>
 *   <li>{@code EDGE_WEIGHT_FORMAT} (for EXPLICIT): FULL_MATRIX and all eight triangular
 *       row/column formats;</li>
 *   <li>{@code TOUR_SECTION} of {@code .tour} files (the first tour is returned).</li>
 * </ul>
 * Sections that are not needed to build a distance matrix (display data, fixed edges, VRP
 * sections) are skipped.
 */
public final class TspLibReader {

    /** Parsed instance. {@code distances} is a full {@code dimension x dimension} matrix. */
    public record Instance(String name, String type, String comment, int dimension,
                           String edgeWeightType, double[][] distances) {

        public TSP toTSP() {
            return new TSP(dimension, distances);
        }
    }

    private TspLibReader() {
    }

    /** Reads a TSPLIB instance file and returns it as a {@link TSP}. */
    public static TSP readTsp(Path file) {
        return readInstance(file).toTSP();
    }

    /** Reads a TSPLIB instance file, keeping its header information. */
    public static Instance readInstance(Path file) {
        Parsed p = parse(file);
        if (p.dimension <= 0)
            throw error(file, "missing or invalid DIMENSION");
        if (p.edgeWeightType == null)
            throw error(file, "missing EDGE_WEIGHT_TYPE");

        double[][] d = p.edgeWeightType.equals("EXPLICIT")
                ? explicitMatrix(file, p)
                : coordinateMatrix(file, p);
        return new Instance(p.name, p.type, p.comment, p.dimension, p.edgeWeightType, d);
    }

    /**
     * Reads the first tour of a TSPLIB {@code .tour} file.
     *
     * @return the tour as 0-based city indices (TSPLIB numbers cities from 1)
     */
    public static int[] readTour(Path file) {
        Parsed p = parse(file);
        if (p.tour.isEmpty())
            throw error(file, "no TOUR_SECTION found");
        int[] tour = new int[p.tour.size()];
        for (int i = 0; i < tour.length; i++) {
            tour[i] = p.tour.get(i) - 1;
        }
        return tour;
    }

    // ------------------------------------------------------------------ parsing

    /** Raw content of a file before distances are computed. */
    private static final class Parsed {
        String name = "";
        String type = "";
        String comment = "";
        int dimension = -1;
        String edgeWeightType;
        String edgeWeightFormat;
        double[][] coords;          // [node][x, y, (z)] indexed by node id - 1
        final List<Double> weights = new ArrayList<>();
        final List<Integer> tour = new ArrayList<>();
        boolean tourComplete;
    }

    private static Parsed parse(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read TSPLIB file " + file, e);
        }

        Parsed p = new Parsed();
        String section = null;   // current data section, or null while reading the header

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty())
                continue;
            if (line.equals("EOF"))
                break;

            if (Character.isLetter(line.charAt(0))) {
                int colon = line.indexOf(':');
                if (colon >= 0) {
                    // Header entry "KEYWORD : value" (spacing around ':' varies between files).
                    readHeader(file, p, line.substring(0, colon).trim().toUpperCase(Locale.ROOT),
                            line.substring(colon + 1).trim());
                    section = null;
                    continue;
                }
                // Start of a data section; a few files put data on the same line.
                String[] parts = line.split("\\s+", 2);
                section = parts[0].toUpperCase(Locale.ROOT);
                if (parts.length == 1)
                    continue;
                line = parts[1];
            }

            if (section == null)
                throw error(file, "data outside of a section: " + line);
            readData(file, p, section, line);
        }
        return p;
    }

    private static void readHeader(Path file, Parsed p, String key, String value) {
        switch (key) {
            case "NAME" -> p.name = value;
            case "TYPE" -> p.type = value.toUpperCase(Locale.ROOT);
            case "COMMENT" -> p.comment = p.comment.isEmpty() ? value : p.comment + "\n" + value;
            case "DIMENSION" -> {
                try {
                    p.dimension = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw error(file, "invalid DIMENSION '" + value + "'");
                }
            }
            case "EDGE_WEIGHT_TYPE" -> p.edgeWeightType = value.toUpperCase(Locale.ROOT);
            case "EDGE_WEIGHT_FORMAT" -> p.edgeWeightFormat = value.toUpperCase(Locale.ROOT);
            default -> {
                // Other keywords (CAPACITY, NODE_COORD_TYPE, DISPLAY_DATA_TYPE, ...) are not needed.
            }
        }
    }

    private static void readData(Path file, Parsed p, String section, String line) {
        String[] tokens = line.split("\\s+");
        switch (section) {
            case "NODE_COORD_SECTION" -> readCoordinate(file, p, tokens);
            case "EDGE_WEIGHT_SECTION" -> {
                for (String t : tokens) p.weights.add(parseNumber(file, t));
            }
            case "TOUR_SECTION" -> {
                // Ids until -1; a file may contain several tours, only the first is kept.
                for (String t : tokens) {
                    if (p.tourComplete) return;
                    int id = (int) parseNumber(file, t);
                    if (id == -1) p.tourComplete = true;
                    else p.tour.add(id);
                }
            }
            default -> {
                // DISPLAY_DATA_SECTION, FIXED_EDGES_SECTION, DEMAND_SECTION, ... are skipped.
            }
        }
    }

    private static void readCoordinate(Path file, Parsed p, String[] tokens) {
        if (p.dimension <= 0)
            throw error(file, "NODE_COORD_SECTION before DIMENSION");
        if (tokens.length < 3)
            throw error(file, "coordinate line needs an id and at least two values: " + String.join(" ", tokens));
        if (p.coords == null)
            p.coords = new double[p.dimension][];
        int id = (int) parseNumber(file, tokens[0]);
        if (id < 1 || id > p.dimension)
            throw error(file, "node id " + id + " outside 1.." + p.dimension);
        double[] c = new double[tokens.length - 1];
        for (int i = 1; i < tokens.length; i++) {
            c[i - 1] = parseNumber(file, tokens[i]);
        }
        p.coords[id - 1] = c;
    }

    private static double parseNumber(Path file, String token) {
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            throw error(file, "not a number: '" + token + "'");
        }
    }

    // ------------------------------------------------------------------ distances

    private static double[][] coordinateMatrix(Path file, Parsed p) {
        if (p.coords == null)
            throw error(file, "EDGE_WEIGHT_TYPE " + p.edgeWeightType + " requires a NODE_COORD_SECTION");
        int requiredDims = p.edgeWeightType.endsWith("_3D") ? 3 : 2;
        for (int i = 0; i < p.dimension; i++) {
            if (p.coords[i] == null)
                throw error(file, "missing coordinates for node " + (i + 1));
            if (p.coords[i].length < requiredDims)
                throw error(file, p.edgeWeightType + " needs " + requiredDims + " coordinates for node " + (i + 1));
        }

        Metric metric = metric(file, p.edgeWeightType);
        int n = p.dimension;
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                d[i][j] = d[j][i] = metric.distance(p.coords[i], p.coords[j]);
            }
        }
        return d;
    }

    @FunctionalInterface
    private interface Metric {
        double distance(double[] a, double[] b);
    }

    private static Metric metric(Path file, String type) {
        return switch (type) {
            case "EUC_2D" -> (a, b) -> nint(Math.hypot(a[0] - b[0], a[1] - b[1]));
            case "EUC_3D" -> (a, b) -> nint(Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2])));
            case "CEIL_2D" -> (a, b) -> Math.ceil(Math.hypot(a[0] - b[0], a[1] - b[1]));
            case "MAN_2D" -> (a, b) -> nint(Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]));
            case "MAN_3D" -> (a, b) -> nint(Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]));
            case "MAX_2D" -> (a, b) -> Math.max(nint(Math.abs(a[0] - b[0])), nint(Math.abs(a[1] - b[1])));
            case "MAX_3D" -> (a, b) -> Math.max(Math.max(nint(Math.abs(a[0] - b[0])), nint(Math.abs(a[1] - b[1]))),
                    nint(Math.abs(a[2] - b[2])));
            case "GEO" -> TspLibReader::geo;
            case "ATT" -> TspLibReader::att;
            default -> throw error(file, "unsupported EDGE_WEIGHT_TYPE " + type);
        };
    }

    /** Nearest integer, as defined by TSPLIB: {@code (int) (x + 0.5)}. */
    private static int nint(double x) {
        return (int) (x + 0.5);
    }

    private static double sq(double x) {
        return x * x;
    }

    /** Pseudo-Euclidean distance used by att48 and att532. */
    private static double att(double[] a, double[] b) {
        double r = Math.sqrt((sq(a[0] - b[0]) + sq(a[1] - b[1])) / 10.0);
        int t = nint(r);
        return t < r ? t + 1 : t;
    }

    private static final double GEO_PI = 3.141592;       // value fixed by the TSPLIB specification
    private static final double EARTH_RADIUS = 6378.388;

    /** Geographical distance in km; coordinates are latitude/longitude in DDD.MM format. */
    private static double geo(double[] a, double[] b) {
        double latA = geoRadians(a[0]), lonA = geoRadians(a[1]);
        double latB = geoRadians(b[0]), lonB = geoRadians(b[1]);
        double q1 = Math.cos(lonA - lonB);
        double q2 = Math.cos(latA - latB);
        double q3 = Math.cos(latA + latB);
        return (int) (EARTH_RADIUS * Math.acos(0.5 * ((1.0 + q1) * q2 - (1.0 - q1) * q3)) + 1.0);
    }

    /**
     * Converts DDD.MM (degrees and minutes) to radians. The degrees are the integer part of the
     * value, which is how the published optima were computed (Concorde does the same). The
     * specification's text says {@code nint}, but that differs for minutes of 30 or more.
     */
    private static double geoRadians(double x) {
        int deg = (int) x;
        double min = x - deg;
        return GEO_PI * (deg + 5.0 * min / 3.0) / 180.0;
    }

    private static double[][] explicitMatrix(Path file, Parsed p) {
        int n = p.dimension;
        String format = p.edgeWeightFormat == null ? "FULL_MATRIX" : p.edgeWeightFormat;
        List<Double> w = p.weights;
        double[][] d = new double[n][n];

        // Column-wise triangular formats list the same values as the opposite row-wise format
        // (the matrix is symmetric), so they reuse its filling order.
        String rowFormat = switch (format) {
            case "UPPER_COL" -> "LOWER_ROW";
            case "LOWER_COL" -> "UPPER_ROW";
            case "UPPER_DIAG_COL" -> "LOWER_DIAG_ROW";
            case "LOWER_DIAG_COL" -> "UPPER_DIAG_ROW";
            default -> format;
        };

        int expected = switch (rowFormat) {
            case "FULL_MATRIX" -> n * n;
            case "UPPER_ROW", "LOWER_ROW" -> n * (n - 1) / 2;
            case "UPPER_DIAG_ROW", "LOWER_DIAG_ROW" -> n * (n + 1) / 2;
            default -> throw error(file, "unsupported EDGE_WEIGHT_FORMAT " + format);
        };
        if (w.size() != expected)
            throw error(file, format + " with DIMENSION " + n + " needs " + expected
                    + " weights but EDGE_WEIGHT_SECTION has " + w.size());

        int k = 0;
        for (int i = 0; i < n; i++) {
            switch (rowFormat) {
                case "FULL_MATRIX" -> {
                    for (int j = 0; j < n; j++) d[i][j] = w.get(k++);
                }
                case "UPPER_ROW" -> {
                    for (int j = i + 1; j < n; j++) d[i][j] = d[j][i] = w.get(k++);
                }
                case "LOWER_ROW" -> {
                    for (int j = 0; j < i; j++) d[i][j] = d[j][i] = w.get(k++);
                }
                case "UPPER_DIAG_ROW" -> {
                    for (int j = i; j < n; j++) d[i][j] = d[j][i] = w.get(k++);
                }
                case "LOWER_DIAG_ROW" -> {
                    for (int j = 0; j <= i; j++) d[i][j] = d[j][i] = w.get(k++);
                }
                default -> throw new IllegalStateException(rowFormat);
            }
        }
        return d;
    }

    private static IllegalArgumentException error(Path file, String message) {
        return new IllegalArgumentException("Invalid TSPLIB file " + file + ": " + message);
    }
}
