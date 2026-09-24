package core.problems.tsp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TspLibReaderTest {

    @TempDir
    Path dir;

    private Path write(String content) throws IOException {
        Path file = Files.createTempFile(dir, "instance", ".tsp");
        Files.writeString(file, content);
        return file;
    }

    /** Header plus a coordinate section with the given metric. */
    private TSP coordinates(String metric, String... nodes) throws IOException {
        StringBuilder sb = new StringBuilder()
                .append("NAME: test\nTYPE: TSP\nDIMENSION: ").append(nodes.length)
                .append("\nEDGE_WEIGHT_TYPE: ").append(metric).append("\nNODE_COORD_SECTION\n");
        for (int i = 0; i < nodes.length; i++) sb.append(i + 1).append(' ').append(nodes[i]).append('\n');
        return TspLibReader.readTsp(write(sb.append("EOF\n").toString()));
    }

    @Test
    void attMetricFromResourceFile() throws URISyntaxException {
        TSP tsp = TspLibReader.readTsp(Path.of(getClass().getResource("/tsplib/tiny_att.tsp").toURI()));
        // r = sqrt((dx^2 + dy^2) / 10), rounded up when fractional.
        assertEquals(4.0, tsp.getDistance(0, 1), 0.0);   // sqrt(10) = 3.16 (plain Euclidean: 10)
        assertEquals(7.0, tsp.getDistance(0, 2), 0.0);   // sqrt(40) = 6.32
        assertEquals(8.0, tsp.getDistance(1, 2), 0.0);   // sqrt(50) = 7.07
        assertTrue(tsp.isSymmetric());
    }

    @Test
    void euclideanMetricsRoundAsSpecified() throws IOException {
        // Distance between the two points is sqrt(1.4^2 + 2^2) = 2.441
        assertEquals(2.0, coordinates("EUC_2D", "0 0", "1.4 2").getDistance(0, 1), 0.0);
        assertEquals(3.0, coordinates("CEIL_2D", "0 0", "1.4 2").getDistance(0, 1), 0.0);
        // |dx| + |dy| = 3.4 -> 3; max(nint(1.4), nint(2)) = 2
        assertEquals(3.0, coordinates("MAN_2D", "0 0", "1.4 2").getDistance(0, 1), 0.0);
        assertEquals(2.0, coordinates("MAX_2D", "0 0", "1.4 2").getDistance(0, 1), 0.0);
        // sqrt(1 + 4 + 4) = 3
        assertEquals(3.0, coordinates("EUC_3D", "0 0 0", "1 2 2").getDistance(0, 1), 0.0);
    }

    @Test
    void geoMetricUsesDegreesAndMinutes() throws IOException {
        // One degree of latitude: 6378.388 * (pi/180) = 111.3 km -> (int)(111.3 + 1) = 112
        assertEquals(112.0, coordinates("GEO", "0.00 0.00", "1.00 0.00").getDistance(0, 1), 0.0);
        // 0.55 means 0 degrees 55 minutes = 0.9167 degrees -> (int)(102.0 + 1) = 103
        assertEquals(103.0, coordinates("GEO", "0.00 0.00", "0.55 0.00").getDistance(0, 1), 0.0);
    }

    @Test
    void nodesMayBeListedOutOfOrder() throws IOException {
        Path file = write("""
                NAME : shuffled
                DIMENSION : 3
                EDGE_WEIGHT_TYPE : EUC_2D
                NODE_COORD_SECTION
                3 0 4
                1 0 0
                2 3 0
                EOF
                """);
        TSP tsp = TspLibReader.readTsp(file);
        assertEquals(3.0, tsp.getDistance(0, 1), 0.0);
        assertEquals(4.0, tsp.getDistance(0, 2), 0.0);
        assertEquals(5.0, tsp.getDistance(1, 2), 0.0);
    }

    // Symmetric 4x4 matrix used for all triangular formats:
    //      0  1  2  3
    //   0 [0  1  2  3]
    //   1 [1  0  4  5]
    //   2 [2  4  0  6]
    //   3 [3  5  6  0]
    private static final double[][] SYMMETRIC = {{0, 1, 2, 3}, {1, 0, 4, 5}, {2, 4, 0, 6}, {3, 5, 6, 0}};

    @ParameterizedTest
    @ValueSource(strings = {
            "UPPER_ROW|1 2 3 4 5 6",
            "LOWER_COL|1 2 3 4 5 6",
            "LOWER_ROW|1 2 4 3 5 6",
            "UPPER_COL|1 2 4 3 5 6",
            "UPPER_DIAG_ROW|0 1 2 3 0 4 5 0 6 0",
            "LOWER_DIAG_COL|0 1 2 3 0 4 5 0 6 0",
            "LOWER_DIAG_ROW|0 1 0 2 4 0 3 5 6 0",
            "UPPER_DIAG_COL|0 1 0 2 4 0 3 5 6 0"
    })
    void explicitTriangularFormats(String spec) throws IOException {
        String[] parts = spec.split("\\|");
        Path file = write("NAME: tri\nTYPE: TSP\nDIMENSION: 4\nEDGE_WEIGHT_TYPE: EXPLICIT\n"
                + "EDGE_WEIGHT_FORMAT: " + parts[0] + "\nEDGE_WEIGHT_SECTION\n" + parts[1] + "\nEOF\n");
        TSP tsp = TspLibReader.readTsp(file);
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                assertEquals(SYMMETRIC[i][j], tsp.getDistance(i, j), 0.0, parts[0] + " (" + i + "," + j + ")");
    }

    @Test
    void explicitFullMatrixKeepsAsymmetry() throws IOException {
        Path file = write("""
                NAME: asym
                TYPE: ATSP
                DIMENSION: 3
                EDGE_WEIGHT_TYPE: EXPLICIT
                EDGE_WEIGHT_FORMAT: FULL_MATRIX
                EDGE_WEIGHT_SECTION
                 0 1 9
                 9 0 1
                 1 9 0
                EOF
                """);
        TspLibReader.Instance instance = TspLibReader.readInstance(file);
        assertEquals("ATSP", instance.type());
        assertEquals("asym", instance.name());
        TSP tsp = instance.toTSP();
        assertFalse(tsp.isSymmetric());
        assertEquals(1.0, tsp.getDistance(0, 1), 0.0);
        assertEquals(9.0, tsp.getDistance(1, 0), 0.0);
    }

    @Test
    void readsFirstTourAsZeroBasedIndices() throws IOException {
        Path file = write("""
                NAME : tiny.opt.tour
                TYPE : TOUR
                DIMENSION : 4
                TOUR_SECTION
                1
                3 2
                4
                -1
                2 1 3 4 -1
                EOF
                """);
        assertArrayEquals(new int[]{0, 2, 1, 3}, TspLibReader.readTour(file));
    }

    @Test
    void wrongWeightCountIsReported() throws IOException {
        Path file = write("NAME: bad\nDIMENSION: 4\nEDGE_WEIGHT_TYPE: EXPLICIT\n"
                + "EDGE_WEIGHT_FORMAT: UPPER_ROW\nEDGE_WEIGHT_SECTION\n1 2 3\nEOF\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TspLibReader.readTsp(file));
        assertTrue(e.getMessage().contains("needs 6 weights"), e.getMessage());
    }

    @Test
    void missingCoordinatesAreReported() throws IOException {
        Path file = write("NAME: bad\nDIMENSION: 3\nEDGE_WEIGHT_TYPE: EUC_2D\nNODE_COORD_SECTION\n1 0 0\n2 1 1\nEOF\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TspLibReader.readTsp(file));
        assertTrue(e.getMessage().contains("node 3"), e.getMessage());
    }

    @Test
    void unsupportedMetricIsReported() throws IOException {
        Path file = write("NAME: bad\nDIMENSION: 2\nEDGE_WEIGHT_TYPE: XRAY1\nNODE_COORD_SECTION\n1 0 0\n2 1 1\nEOF\n");
        assertThrows(IllegalArgumentException.class, () -> TspLibReader.readTsp(file));
    }
}
