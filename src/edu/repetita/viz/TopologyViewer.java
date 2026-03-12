package edu.repetita.viz;

import edu.repetita.core.Topology;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Force-directed (Fruchterman-Reingold) graph viewer for a clustered topology.
 *
 * Usage:
 *   TopologyViewer.show(topology, clusterAssignment);
 *
 * clusterAssignment[i] is the cluster index for node i (0-based).
 * Pass null to draw all nodes in a single default color.
 *
 * Edge width is proportional to edge capacity.
 * Nodes are colored by cluster using a fixed high-contrast palette.
 */
public class TopologyViewer extends JFrame {

    // 20-color high-contrast palette (wraps for > 20 clusters)
    private static final Color[] PALETTE = {
        new Color(0xe6194b), new Color(0x3cb44b), new Color(0x4363d8),
        new Color(0xf58231), new Color(0x911eb4), new Color(0x42d4f4),
        new Color(0xf032e6), new Color(0xbfef45), new Color(0xfabed4),
        new Color(0x469990), new Color(0xdcbeff), new Color(0x9a6324),
        new Color(0x808000), new Color(0xffd8b1), new Color(0x000075),
        new Color(0xa9a9a9), new Color(0xaaffc3), new Color(0x800000),
        new Color(0xffe119), new Color(0xffffff)
    };

    /** Opens the viewer window on the Swing EDT. */
    public static void show(Topology topology, int[] clusterAssignment) {
        SwingUtilities.invokeLater(() -> {
            TopologyViewer frame = new TopologyViewer(topology, clusterAssignment);
            frame.setVisible(true);
        });
    }

    private TopologyViewer(Topology topology, int[] clusterAssignment) {
        super("Topology — " + topology.nNodes + " nodes, " + topology.nEdges + " edges");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1280, 960);
        setLocationRelativeTo(null);
        add(new GraphPanel(topology, clusterAssignment));
    }

    // -------------------------------------------------------------------------

    private static class GraphPanel extends JPanel {

        private static final int NODE_RADIUS = 7;
        private static final int PADDING     = 70;
        private static final int ITERATIONS  = 500;

        private final Topology topo;
        private final int[]    cluster;        // may be null
        private final int      numClusters;
        private final double   minCap, maxCap;

        // Normalised node positions in [0, 1]
        private final double[] px, py;

        // Pan / zoom state
        private double scale     = 1.0;
        private double offsetX   = 0.0;
        private double offsetY   = 0.0;
        private int    dragStartX, dragStartY;

        GraphPanel(Topology topo, int[] cluster) {
            this.topo    = topo;
            this.cluster = cluster;
            this.px      = new double[topo.nNodes];
            this.py      = new double[topo.nNodes];

            // Capacity range for edge-width normalisation
            double minC = Double.MAX_VALUE, maxC = -Double.MAX_VALUE;
            for (int e = 0; e < topo.nEdges; e++) {
                minC = Math.min(minC, topo.edgeCapacity[e]);
                maxC = Math.max(maxC, topo.edgeCapacity[e]);
            }
            this.minCap = minC;
            this.maxCap = maxC;

            int maxCluster = 0;
            if (cluster != null)
                for (int c : cluster) maxCluster = Math.max(maxCluster, c);
            this.numClusters = maxCluster + 1;

            runForceDirected();
            attachInteraction();
        }

        // --- Layout -----------------------------------------------------------

        /**
         * Fruchterman-Reingold force-directed layout.
         *
         * Key design choices that match networkx spring_layout behaviour:
         *  - Random initialisation near the centre (not on a circle).
         *  - Simulation runs in unconstrained space — NO per-iteration clamping.
         *    Clamping during simulation acts like invisible walls and traps nodes
         *    at the boundary, which is exactly what caused the "nodes on the edge"
         *    problem.
         *  - Positions are normalised to [margin, 1-margin] once, after the
         *    simulation has converged.
         *  - Undirected edge list is precomputed so the HashSet dedup is not
         *    repeated every iteration.
         */
        private void runForceDirected() {
            int n = topo.nNodes;

            // 1. Initialise with random positions near the centre
            Random rng = new Random(42);
            double[] x = new double[n];
            double[] y = new double[n];
            for (int i = 0; i < n; i++) {
                x[i] = 0.4 + rng.nextDouble() * 0.2;
                y[i] = 0.4 + rng.nextDouble() * 0.2;
            }

            // 2. Precompute undirected edge list (deduplicate symmetric pairs)
            Set<Long> seen = new HashSet<>();
            int edgeCount = 0;
            int[] eu = new int[topo.nEdges];
            int[] ev = new int[topo.nEdges];
            for (int e = 0; e < topo.nEdges; e++) {
                int  u   = topo.edgeSrc[e];
                int  v   = topo.edgeDest[e];
                long key = (long) Math.min(u, v) * 100_000 + Math.max(u, v);
                if (seen.add(key)) { eu[edgeCount] = u; ev[edgeCount] = v; edgeCount++; }
            }

            // 3. Fruchterman-Reingold iterations in unconstrained space
            double k       = Math.sqrt(1.0 / n);   // ideal inter-node distance
            double temp    = 0.1;
            double cooling = temp / ITERATIONS;
            double[] dx = new double[n];
            double[] dy = new double[n];

            for (int iter = 0; iter < ITERATIONS; iter++) {
                Arrays.fill(dx, 0);
                Arrays.fill(dy, 0);

                // Repulsive forces between every pair of nodes
                for (int u = 0; u < n; u++) {
                    for (int v = u + 1; v < n; v++) {
                        double dX   = x[u] - x[v];
                        double dY   = y[u] - y[v];
                        double dist = Math.max(Math.hypot(dX, dY), 1e-4);
                        double f    = k * k / dist / dist;   // force magnitude / dist
                        dx[u] += f * dX;  dy[u] += f * dY;
                        dx[v] -= f * dX;  dy[v] -= f * dY;
                    }
                }

                // Attractive forces along edges
                for (int e = 0; e < edgeCount; e++) {
                    int    u    = eu[e], v = ev[e];
                    double dX   = x[v] - x[u];
                    double dY   = y[v] - y[u];
                    double dist = Math.max(Math.hypot(dX, dY), 1e-4);
                    double f    = dist / k;   // force magnitude / dist
                    dx[u] += f * dX;  dy[u] += f * dY;
                    dx[v] -= f * dX;  dy[v] -= f * dY;
                }

                // Apply displacement capped by temperature — no clamping
                for (int u = 0; u < n; u++) {
                    double disp = Math.hypot(dx[u], dy[u]);
                    if (disp > 0) {
                        double capped = Math.min(disp, temp) / disp;
                        x[u] += dx[u] * capped;
                        y[u] += dy[u] * capped;
                    }
                }

                temp = Math.max(temp - cooling, 1e-4);
            }

            // 4. Normalise into [margin, 1-margin] independently per axis,
            //    exactly as networkx does after its simulation.
            double margin = 0.05;
            double minX = x[0], maxX = x[0], minY = y[0], maxY = y[0];
            for (int u = 1; u < n; u++) {
                if (x[u] < minX) minX = x[u]; if (x[u] > maxX) maxX = x[u];
                if (y[u] < minY) minY = y[u]; if (y[u] > maxY) maxY = y[u];
            }
            double rangeX = maxX - minX, rangeY = maxY - minY;
            double span   = 1.0 - 2 * margin;
            for (int u = 0; u < n; u++) {
                px[u] = margin + (rangeX > 1e-10 ? (x[u] - minX) / rangeX : 0.5) * span;
                py[u] = margin + (rangeY > 1e-10 ? (y[u] - minY) / rangeY : 0.5) * span;
            }
        }

        // --- Interaction (scroll to zoom, drag to pan) ------------------------

        private void attachInteraction() {
            addMouseWheelListener(e -> {
                double factor = e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1;
                // Zoom toward cursor
                double mx = e.getX(), my = e.getY();
                offsetX = mx - (mx - offsetX) * factor;
                offsetY = my - (my - offsetY) * factor;
                scale  *= factor;
                repaint();
            });

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    dragStartX = e.getX();
                    dragStartY = e.getY();
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) {
                    offsetX += e.getX() - dragStartX;
                    offsetY += e.getY() - dragStartY;
                    dragStartX = e.getX();
                    dragStartY = e.getY();
                    repaint();
                }
            });
        }

        // --- Painting ---------------------------------------------------------

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Apply pan / zoom transform
            g2.translate(offsetX, offsetY);
            g2.scale(scale, scale);

            int drawW = getWidth()  - 2 * PADDING;
            int drawH = getHeight() - 2 * PADDING;

            drawEdges(g2, drawW, drawH);
            drawNodes(g2, drawW, drawH);

            // Reset transform before drawing the fixed legend
            g2.scale(1 / scale, 1 / scale);
            g2.translate(-offsetX, -offsetY);
            drawLegend(g2);
        }

        private void drawEdges(Graphics2D g2, int W, int H) {
            Set<Long> drawn = new HashSet<>();
            for (int e = 0; e < topo.nEdges; e++) {
                int  u   = topo.edgeSrc[e];
                int  v   = topo.edgeDest[e];
                long key = (long) Math.min(u, v) * 100_000 + Math.max(u, v);
                if (!drawn.add(key)) continue;

                int x1 = PADDING + (int) (px[u] * W);
                int y1 = PADDING + (int) (py[u] * H);
                int x2 = PADDING + (int) (px[v] * W);
                int y2 = PADDING + (int) (py[v] * H);

                float width = normaliseCapacity(topo.edgeCapacity[e], 1f, 6f);
                g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(90, 90, 90, 150));
                g2.drawLine(x1, y1, x2, y2);
            }
        }

        private void drawNodes(Graphics2D g2, int W, int H) {
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            for (int u = 0; u < topo.nNodes; u++) {
                int   x     = PADDING + (int) (px[u] * W);
                int   y     = PADDING + (int) (py[u] * H);
                Color color = clusterColor(cluster != null ? cluster[u] : 0);

                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(color);
                g2.fillOval(x - NODE_RADIUS, y - NODE_RADIUS, 2 * NODE_RADIUS, 2 * NODE_RADIUS);
                g2.setColor(color.darker());
                g2.drawOval(x - NODE_RADIUS, y - NODE_RADIUS, 2 * NODE_RADIUS, 2 * NODE_RADIUS);

                g2.setColor(Color.DARK_GRAY);
                g2.drawString(topo.nodeLabel[u], x + NODE_RADIUS + 3, y + 4);
            }
        }

        private void drawLegend(Graphics2D g2) {
            if (cluster == null || numClusters <= 1) return;

            int x = 12, y = 10;
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g2.setColor(Color.DARK_GRAY);
            g2.drawString("Clusters", x, y + 12);

            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            for (int c = 0; c < numClusters; c++) {
                int rowY = y + 22 + c * 17;
                g2.setColor(clusterColor(c));
                g2.fillRect(x, rowY, 11, 11);
                g2.setColor(clusterColor(c).darker());
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(x, rowY, 11, 11);
                g2.setColor(Color.DARK_GRAY);
                g2.drawString("Cluster " + c, x + 15, rowY + 10);
            }
        }

        // --- Helpers ----------------------------------------------------------

        private float normaliseCapacity(double cap, float minW, float maxW) {
            if (maxCap <= minCap) return (minW + maxW) / 2f;
            return (float) (minW + (cap - minCap) / (maxCap - minCap) * (maxW - minW));
        }

        private static Color clusterColor(int idx) {
            if (idx < 0) return Color.GRAY;
            return PALETTE[idx % PALETTE.length];
        }
    }
}
