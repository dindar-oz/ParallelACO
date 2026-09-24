package core.examples;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/**
 * Live step chart of "best objective value found so far" against wall-clock time, one line per
 * algorithm. Plain Swing/Java2D, so the project needs no charting dependency.
 * <p>
 * Design (following the project's data-viz conventions): 2px lines in fixed categorical
 * order, a legend plus direct labels at the line ends, a recessive grid, one y-axis, text in
 * neutral ink rather than series colours, and a hover crosshair with a tooltip reading every
 * series at the hovered time.
 */
final class ConvergencePlot extends JPanel {

    // Reference palette: surface, text and categorical slots 1-3 (validated all-pairs).
    private static final Color SURFACE = new Color(0xfcfcfb);
    private static final Color TEXT_PRIMARY = new Color(0x0b0b0b);
    private static final Color TEXT_SECONDARY = new Color(0x52514e);
    private static final Color GRID = new Color(0xe6e5e0);
    private static final Color AXIS = new Color(0xb8b7b0);
    static final Color[] SERIES_COLORS = {new Color(0x2a78d6), new Color(0xeb6834), new Color(0x1baf7a)};

    private static final int LEFT = 84, RIGHT = 150, TOP = 104, BOTTOM = 56;

    /** One algorithm's best-so-far history. Written by worker threads, read by the EDT. */
    static final class Series {
        final String label;
        final Color color;
        final LongSupplier solutionCount;
        final List<double[]> points = new CopyOnWriteArrayList<>();   // {seconds, value}
        volatile double finishedAt = Double.NaN;

        Series(String label, Color color, LongSupplier solutionCount) {
            this.label = label;
            this.color = color;
            this.solutionCount = solutionCount;
        }

        void add(double seconds, double value) {
            points.add(new double[]{seconds, value});
        }

        double valueAt(double seconds) {
            double v = Double.NaN;
            for (double[] p : points) {
                if (p[0] > seconds) break;
                v = p[1];
            }
            return v;
        }

        double last() {
            return points.isEmpty() ? Double.NaN : points.get(points.size() - 1)[1];
        }
    }

    private final String title;
    private final List<Series> series;
    private final double durationSeconds;
    private final double referenceValue;
    private final String referenceLabel;
    private volatile double nowSeconds;
    private int hoverX = -1;

    ConvergencePlot(String title, List<Series> series, double durationSeconds,
                    double referenceValue, String referenceLabel) {
        this.title = title;
        this.series = series;
        this.durationSeconds = durationSeconds;
        this.referenceValue = referenceValue;
        this.referenceLabel = referenceLabel;
        setPreferredSize(new Dimension(1100, 680));
        setBackground(SURFACE);

        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hoverX = e.getX() >= LEFT && e.getX() <= getWidth() - RIGHT ? e.getX() : -1;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverX = -1;
                repaint();
            }
        };
        addMouseListener(hover);
        addMouseMotionListener(hover);
    }

    void setNow(double seconds) {
        nowSeconds = Math.min(seconds, durationSeconds);
    }

    /** Renders the chart (without hover) into an image, e.g. for saving a PNG. */
    BufferedImage snapshot() {
        BufferedImage img = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        int savedHover = hoverX;
        hoverX = -1;
        paintAll(g);
        hoverX = savedHover;
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int x0 = LEFT, x1 = getWidth() - RIGHT, y0 = TOP, y1 = getHeight() - BOTTOM;

        drawHeader(g);

        double[] yRange = yRange();
        if (yRange == null) {
            g.setColor(TEXT_SECONDARY);
            g.setFont(font(13, Font.PLAIN));
            g.drawString("Waiting for the first solutions…", x0, (y0 + y1) / 2);
            g.dispose();
            return;
        }
        Axis x = new Axis(0, durationSeconds, x0, x1);
        Axis y = new Axis(yRange[0], yRange[1], y1, y0);    // inverted: larger values higher up

        drawGridAndAxes(g, x, y, x0, x1, y0, y1);
        drawReference(g, x, y, x0, x1);

        // Series lines, clipped to the plot area.
        Graphics2D plot = (Graphics2D) g.create();
        plot.clipRect(x0, y0 - 2, x1 - x0 + 2, y1 - y0 + 4);
        plot.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Series s : series) {
            Path2D path = stepPath(s, x, y);
            if (path != null) {
                plot.setColor(s.color);
                plot.draw(path);
            }
        }
        plot.dispose();

        drawEndLabels(g, x, y, x1);
        if (hoverX >= 0)
            drawHover(g, x, y, y0, y1);
        g.dispose();
    }

    private void drawHeader(Graphics2D g) {
        g.setColor(TEXT_PRIMARY);
        g.setFont(font(17, Font.BOLD));
        g.drawString(title, LEFT, 30);

        g.setFont(font(12, Font.PLAIN));
        g.setColor(TEXT_SECONDARY);
        boolean done = series.stream().allMatch(s -> !Double.isNaN(s.finishedAt));
        g.drawString(String.format("Best tour length found so far (lower is better; axis capped at 1.5x the %s) · %s %.0f s / %.0f s",
                referenceLabel, done ? "finished at" : "running", nowSeconds, durationSeconds), LEFT, 50);

        // Legend: one row per series with its current numbers.
        int y = 74;
        for (Series s : series) {
            g.setColor(s.color);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(LEFT, y - 4, LEFT + 18, y - 4);
            g.setColor(TEXT_PRIMARY);
            g.setFont(font(12, Font.BOLD));
            g.drawString(s.label, LEFT + 26, y);
            int w = g.getFontMetrics().stringWidth(s.label);
            g.setFont(font(12, Font.PLAIN));
            g.setColor(TEXT_SECONDARY);
            double best = s.last();
            g.drawString(String.format("  best %s · %,d tours · %d improvements",
                    Double.isNaN(best) ? "–" : String.format("%,.0f", best),
                    s.solutionCount.getAsLong(), s.points.size()), LEFT + 26 + w, y);
            y += 16;
        }
    }

    private void drawGridAndAxes(Graphics2D g, Axis x, Axis y, int x0, int x1, int y0, int y1) {
        g.setFont(font(11, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        g.setStroke(new BasicStroke(1f));

        for (double v : y.ticks()) {
            int py = y.map(v);
            g.setColor(GRID);
            g.drawLine(x0, py, x1, py);
            g.setColor(TEXT_SECONDARY);
            String label = String.format("%,.0f", v);
            g.drawString(label, x0 - 8 - fm.stringWidth(label), py + fm.getAscent() / 2 - 1);
        }
        for (double v : x.ticks()) {
            int px = x.map(v);
            g.setColor(TEXT_SECONDARY);
            String label = String.format("%.0f s", v);
            g.drawString(label, px - fm.stringWidth(label) / 2, y1 + 18);
        }
        g.setColor(AXIS);
        g.drawLine(x0, y1, x1, y1);

        g.setColor(TEXT_SECONDARY);
        g.drawString("wall-clock time", (x0 + x1) / 2 - fm.stringWidth("wall-clock time") / 2, y1 + 38);
    }

    private void drawReference(Graphics2D g, Axis x, Axis y, int x0, int x1) {
        if (Double.isNaN(referenceValue) || referenceValue < y.lo || referenceValue > y.hi)
            return;
        int py = y.map(referenceValue);
        g.setColor(AXIS);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
        g.drawLine(x0, py, x1, py);
        g.setFont(font(11, Font.PLAIN));
        g.setColor(TEXT_SECONDARY);
        g.drawString(String.format("%s %,.0f", referenceLabel, referenceValue), x0 + 6, py - 5);
    }

    /** Best-so-far is a step function: flat until the next improvement, then a drop. */
    private Path2D stepPath(Series s, Axis x, Axis y) {
        List<double[]> pts = new ArrayList<>(s.points);
        if (pts.isEmpty())
            return null;
        Path2D path = new Path2D.Double();
        double[] first = pts.get(0);
        path.moveTo(x.mapD(first[0]), y.mapD(first[1]));
        double prev = first[1];
        for (int i = 1; i < pts.size(); i++) {
            double[] p = pts.get(i);
            path.lineTo(x.mapD(p[0]), y.mapD(prev));
            path.lineTo(x.mapD(p[0]), y.mapD(p[1]));
            prev = p[1];
        }
        double end = Double.isNaN(s.finishedAt) ? nowSeconds : Math.min(s.finishedAt, durationSeconds);
        path.lineTo(x.mapD(Math.max(end, pts.get(pts.size() - 1)[0])), y.mapD(prev));
        return path;
    }

    /** Series name at the right end of each line; nudged apart when they would overlap. */
    private void drawEndLabels(Graphics2D g, Axis x, Axis y, int x1) {
        g.setFont(font(12, Font.BOLD));
        List<int[]> placed = new ArrayList<>();   // {seriesIndex, y}
        for (int i = 0; i < series.size(); i++) {
            double last = series.get(i).last();
            if (!Double.isNaN(last))
                placed.add(new int[]{i, y.map(last)});
        }
        placed.sort((a, b) -> Integer.compare(a[1], b[1]));
        for (int i = 1; i < placed.size(); i++) {
            if (placed.get(i)[1] - placed.get(i - 1)[1] < 16)
                placed.get(i)[1] = placed.get(i - 1)[1] + 16;
        }
        for (int[] p : placed) {
            Series s = series.get(p[0]);
            g.setColor(s.color);
            g.fillOval(x1 + 8, p[1] - 4, 8, 8);
            g.setColor(TEXT_PRIMARY);
            g.drawString(shortName(s.label), x1 + 22, p[1] + 4);
        }
    }

    private void drawHover(Graphics2D g, Axis x, Axis y, int y0, int y1) {
        double t = x.invert(hoverX);
        g.setColor(AXIS);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(hoverX, y0, hoverX, y1);

        List<String> lines = new ArrayList<>();
        lines.add(String.format("t = %.1f s", t));
        for (Series s : series) {
            double v = s.valueAt(t);
            lines.add(String.format("%s   %s", shortName(s.label), Double.isNaN(v) ? "–" : String.format("%,.0f", v)));
            if (!Double.isNaN(v)) {
                g.setColor(SURFACE);
                g.fillOval(hoverX - 6, y.map(v) - 6, 12, 12);
                g.setColor(s.color);
                g.fillOval(hoverX - 4, y.map(v) - 4, 8, 8);
            }
        }

        g.setFont(font(12, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        int w = lines.stream().mapToInt(fm::stringWidth).max().orElse(0) + 34;
        int h = lines.size() * 18 + 10;
        int bx = hoverX + 14 + w > getWidth() - 8 ? hoverX - 14 - w : hoverX + 14;
        int by = y0 + 8;
        g.setColor(Color.WHITE);
        g.fillRoundRect(bx, by, w, h, 8, 8);
        g.setColor(GRID);
        g.drawRoundRect(bx, by, w, h, 8, 8);
        for (int i = 0; i < lines.size(); i++) {
            int ty = by + 20 + i * 18;
            if (i > 0) {
                g.setColor(series.get(i - 1).color);
                g.fillOval(bx + 10, ty - 9, 8, 8);
            }
            g.setColor(i == 0 ? TEXT_SECONDARY : TEXT_PRIMARY);
            g.drawString(lines.get(i), bx + (i == 0 ? 10 : 24), ty);
        }
    }

    /** y-range over everything visible, padded, or null if nothing has been found yet. */
    private double[] yRange() {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (Series s : series) {
            for (double[] p : s.points) {
                lo = Math.min(lo, p[1]);
                hi = Math.max(hi, p[1]);
            }
        }
        if (lo == Double.POSITIVE_INFINITY)
            return null;
        if (!Double.isNaN(referenceValue)) {
            lo = Math.min(lo, referenceValue);
            // The first random tours can be several times worse than the reference and would
            // squash the interesting part of the race into a thin band, so cap the axis at 1.5x
            // the reference; those early values enter from the top edge (the tooltip shows them).
            hi = Math.min(Math.max(hi, referenceValue), 1.5 * referenceValue);
        }
        double pad = Math.max((hi - lo) * 0.06, Math.abs(hi) * 0.005);
        return new double[]{lo - pad, hi + pad};
    }

    private static String shortName(String label) {
        int paren = label.indexOf(" (");
        return paren > 0 ? label.substring(0, paren) : label;
    }

    private static Font font(int size, int style) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    /** Linear mapping between a data range and a pixel range, with "nice" tick values. */
    private static final class Axis {
        final double lo, hi;
        final int p0, p1;

        Axis(double lo, double hi, int p0, int p1) {
            this.lo = lo;
            this.hi = hi == lo ? lo + 1 : hi;
            this.p0 = p0;
            this.p1 = p1;
        }

        double mapD(double v) {
            return p0 + (v - lo) / (hi - lo) * (p1 - p0);
        }

        int map(double v) {
            return (int) Math.round(mapD(v));
        }

        double invert(int px) {
            return lo + (px - p0) / (double) (p1 - p0) * (hi - lo);
        }

        List<Double> ticks() {
            double step = niceStep((hi - lo) / 6);
            List<Double> ticks = new ArrayList<>();
            for (double v = Math.ceil(lo / step) * step; v <= hi + step * 1e-9; v += step)
                ticks.add(v);
            return ticks;
        }

        private static double niceStep(double raw) {
            double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
            double f = raw / magnitude;
            return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * magnitude;
        }
    }
}
