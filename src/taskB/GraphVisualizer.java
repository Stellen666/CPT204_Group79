import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class GraphVisualizer extends JPanel {
    private static final int PANEL_WIDTH = 1200;
    private static final int PANEL_HEIGHT = 850;

    private static final double NODE_GAP = 70.0;
    private static final double LAYOUT_MARGIN = 90.0;
    private static final double NORMAL_NODE_RADIUS = 3.8;
    private static final double TARGET_NODE_RADIUS = 6.0;
    private static final double PATH_NODE_RADIUS = 7.0;

    private static final Color BACKGROUND = new Color(250, 251, 253);
    private static final Color EDGE_COLOR = new Color(185, 190, 198, 120);
    private static final Color NODE_COLOR = new Color(70, 76, 86);
    private static final Color TARGET_COLOR = new Color(215, 52, 52);
    private static final Color PATH_COLOR = new Color(0, 105, 220);
    private static final Color START_COLOR = new Color(24, 145, 79);
    private static final Color END_COLOR = new Color(123, 65, 190);
    private static final Color LABEL_COLOR = new Color(40, 43, 48);

    private WeightedGraph<String> graph;
    private List<WeightedEdge<String>> graphEdges = new ArrayList<>();
    private List<String> activePath = new ArrayList<>();

    private final Set<String> importantVertices = new LinkedHashSet<>();
    private final Set<String> pathVertices = new HashSet<>();
    private final Map<String, Point2D.Double> positions = new HashMap<>();

    private double zoom = 0.45;
    private double panX = 45.0;
    private double panY = 45.0;

    private Point dragStart;
    private double dragStartPanX;
    private double dragStartPanY;

    public GraphVisualizer() {
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        installMouseControls();
    }

    // Called by App whenever it wants to show one of the route cases.
    public void setGraphAndPath(WeightedGraph<String> graph, List<String> highlightedPath) {
        this.graph = graph;
        this.graphEdges = graph == null ? new ArrayList<>() : new ArrayList<>(graph.edges());
        this.activePath = highlightedPath == null ? new ArrayList<>() : new ArrayList<>(highlightedPath);

        rebuildPathLookups();
        buildGridLayout();
        repaint();
    }

    // These are drawn in red so the selected Task A targets are easy to spot.
    public void setImportantVertices(Collection<String> vertices) {
        importantVertices.clear();
        if (vertices != null) {
            importantVertices.addAll(vertices);
        }
        repaint();
    }

    public static JFrame showInFrame(
            WeightedGraph<String> graph,
            List<String> highlightedPath,
            Collection<String> importantVertices
    ) {
        GraphVisualizer panel = new GraphVisualizer();
        panel.setImportantVertices(importantVertices);
        panel.setGraphAndPath(graph, highlightedPath);

        JFrame frame = new JFrame("Urban Infrastructure Graph Visualizer");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;
    }

    private void installMouseControls() {
        // Basic map controls: drag to pan, wheel to zoom.
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                dragStartPanX = panX;
                dragStartPanY = panY;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) {
                    return;
                }

                panX = dragStartPanX + e.getX() - dragStart.x;
                panY = dragStartPanY + e.getY() - dragStart.y;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoomAroundMouse(e);
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private void zoomAroundMouse(MouseWheelEvent e) {
        double oldZoom = zoom;
        double factor = e.getWheelRotation() < 0 ? 1.12 : 1.0 / 1.12;
        zoom = clamp(zoom * factor, 0.12, 3.5);

        // Keep the point under the mouse from drifting during zoom.
        double worldX = (e.getX() - panX) / oldZoom;
        double worldY = (e.getY() - panY) / oldZoom;

        panX = e.getX() - worldX * zoom;
        panY = e.getY() - worldY * zoom;
        repaint();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void rebuildPathLookups() {
        pathVertices.clear();

        pathVertices.addAll(activePath);
    }

    private void buildGridLayout() {
        positions.clear();
        if (graph == null) {
            return;
        }

        // The data has no real coordinates, so a stable grid is enough here.
        List<String> vertices = new ArrayList<>(graph.vertices());
        Collections.sort(vertices);

        int columnCount = (int) Math.ceil(Math.sqrt(vertices.size()));
        for (int i = 0; i < vertices.size(); i++) {
            String vertex = vertices.get(i);
            int row = i / columnCount;
            int column = i % columnCount;

            double x = LAYOUT_MARGIN + column * NODE_GAP + smallJitter(vertex, 0);
            double y = LAYOUT_MARGIN + row * NODE_GAP + smallJitter(vertex, 1);
            positions.put(vertex, new Point2D.Double(x, y));
        }
    }

    private double smallJitter(String id, int salt) {
        int hash = Math.abs((id + "#" + salt).hashCode());
        return (hash % 13) - 6;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        AffineTransform oldTransform = g2.getTransform();
        g2.translate(panX, panY);
        g2.scale(zoom, zoom);

        // Draw the pale network first, then the active route on top.
        drawEdges(g2);
        drawHighlightedPath(g2);
        drawVertices(g2);

        g2.setTransform(oldTransform);
        drawLegend(g2);
        g2.dispose();
    }

    private void drawEdges(Graphics2D g2) {
        g2.setColor(EDGE_COLOR);
        g2.setStroke(new BasicStroke((float) (0.8 / zoom)));

        for (WeightedEdge<String> edge : graphEdges) {
            Point2D.Double from = positions.get(edge.getFrom());
            Point2D.Double to = positions.get(edge.getTo());
            if (from == null || to == null) {
                continue;
            }

            g2.draw(new Line2D.Double(from, to));
            drawWeightIfUseful(g2, edge, from, to);
        }
    }

    private void drawWeightIfUseful(Graphics2D g2, WeightedEdge<String> edge, Point2D.Double from, Point2D.Double to) {
        if (zoom < 1.35) {
            return;
        }

        // At normal zoom there are too many labels, so show weights only up close.
        double midX = (from.x + to.x) / 2.0;
        double midY = (from.y + to.y) / 2.0;

        g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g2.setColor(new Color(95, 101, 112, 135));
        g2.drawString(String.valueOf(edge.getWeight()), (float) midX, (float) midY);
        g2.setColor(EDGE_COLOR);
    }

    private void drawHighlightedPath(Graphics2D g2) {
        if (activePath.size() < 2) {
            return;
        }

        g2.setColor(PATH_COLOR);
        g2.setStroke(new BasicStroke((float) (4.0 / zoom), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (int i = 0; i < activePath.size() - 1; i++) {
            Point2D.Double from = positions.get(activePath.get(i));
            Point2D.Double to = positions.get(activePath.get(i + 1));
            if (from == null || to == null) {
                continue;
            }

            g2.draw(new Line2D.Double(from, to));
            drawArrowHead(g2, from, to);
        }
    }

    private void drawArrowHead(Graphics2D g2, Point2D.Double from, Point2D.Double to) {
        double angle = Math.atan2(to.y - from.y, to.x - from.x);
        double arrowLength = 13.0 / zoom;
        double arrowWidth = 6.0 / zoom;

        double tipX = to.x;
        double tipY = to.y;
        double backX = tipX - Math.cos(angle) * arrowLength;
        double backY = tipY - Math.sin(angle) * arrowLength;

        Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(tipX, tipY);
        arrow.lineTo(
                backX + Math.cos(angle + Math.PI / 2.0) * arrowWidth,
                backY + Math.sin(angle + Math.PI / 2.0) * arrowWidth
        );
        arrow.lineTo(
                backX + Math.cos(angle - Math.PI / 2.0) * arrowWidth,
                backY + Math.sin(angle - Math.PI / 2.0) * arrowWidth
        );
        arrow.closePath();

        g2.fill(arrow);
    }

    private void drawVertices(Graphics2D g2) {
        if (graph == null) {
            return;
        }

        for (String vertex : graph.vertices()) {
            Point2D.Double point = positions.get(vertex);
            if (point == null) {
                continue;
            }

            double radius = nodeRadius(vertex);
            Shape circle = new Ellipse2D.Double(point.x - radius, point.y - radius, radius * 2.0, radius * 2.0);

            g2.setColor(nodeColor(vertex));
            g2.fill(circle);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke((float) (0.9 / zoom)));
            g2.draw(circle);

            if (shouldDrawLabel(vertex)) {
                drawNodeLabel(g2, vertex, point, radius);
            }
        }
    }

    private double nodeRadius(String vertex) {
        if (pathVertices.contains(vertex)) {
            return PATH_NODE_RADIUS;
        }
        if (importantVertices.contains(vertex)) {
            return TARGET_NODE_RADIUS;
        }
        return NORMAL_NODE_RADIUS;
    }

    private Color nodeColor(String vertex) {
        if (!activePath.isEmpty() && vertex.equals(activePath.get(0))) {
            return START_COLOR;
        }
        if (!activePath.isEmpty() && vertex.equals(activePath.get(activePath.size() - 1))) {
            return END_COLOR;
        }
        if (pathVertices.contains(vertex)) {
            return PATH_COLOR;
        }
        if (importantVertices.contains(vertex)) {
            return TARGET_COLOR;
        }
        return NODE_COLOR;
    }

    private boolean shouldDrawLabel(String vertex) {
        return zoom > 1.55 || pathVertices.contains(vertex) || importantVertices.contains(vertex);
    }

    private void drawNodeLabel(Graphics2D g2, String vertex, Point2D.Double point, double radius) {
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(LABEL_COLOR);
        g2.drawString(vertex, (float) (point.x + radius + 3), (float) (point.y - radius - 2));
    }

    private void drawLegend(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = g2.getFontMetrics();

        String[] lines = {
                "Mouse wheel: zoom",
                "Drag: pan",
                "Red: selected inspection target",
                "Blue line: active route",
                String.format("Zoom: %.2fx", zoom)
        };

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, fm.stringWidth(line));
        }

        int x = 14;
        int y = 16;
        int boxWidth = width + 26;
        int boxHeight = lines.length * 18 + 16;

        g2.setColor(new Color(255, 255, 255, 225));
        g2.fillRoundRect(x, y, boxWidth, boxHeight, 10, 10);
        g2.setColor(new Color(190, 194, 202));
        g2.drawRoundRect(x, y, boxWidth, boxHeight, 10, 10);

        g2.setColor(LABEL_COLOR);
        int textY = y + 24;
        for (String line : lines) {
            g2.drawString(line, x + 13, textY);
            textY += 18;
        }
    }

}
