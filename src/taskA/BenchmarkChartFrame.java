import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

public class BenchmarkChartFrame extends JFrame {
    public BenchmarkChartFrame(List<Main.BenchmarkResult> results) {
        setTitle("Sorting Benchmark Visualization");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(new ChartPanel(results));
        pack();
        setLocationRelativeTo(null);
    }

    public static void showChart(List<Main.BenchmarkResult> results) {
        SwingUtilities.invokeLater(() -> {
            BenchmarkChartFrame frame = new BenchmarkChartFrame(results);
            frame.setVisible(true);
        });
    }

    public static class ChartPanel extends JPanel {
        private static final Color[] BAR_COLORS = {
                new Color(231, 111, 81),
                new Color(42, 157, 143),
                new Color(233, 196, 106),
                new Color(38, 70, 83)
        };

        private static final String[] LABELS = {"Bubble", "Quick", "Quick M3", "Merge"};
        private final List<Main.BenchmarkResult> results;

        public ChartPanel(List<Main.BenchmarkResult> results) {
            this.results = results;
            setPreferredSize(new Dimension(1000, 600));
            setBackground(new Color(248, 249, 250));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int left = 90;
            int right = 50;
            int top = 95;
            int bottom = 120;

            g2.setColor(new Color(33, 37, 41));
            g2.setFont(new Font("SansSerif", Font.BOLD, 22));
            g2.drawString("Average Sorting Time by Dataset", left, 35);

            int chartWidth = width - left - right;
            int chartHeight = height - top - bottom;

            long maxValue = 1;
            for (Main.BenchmarkResult result : results) {
                maxValue = Math.max(maxValue, result.bubbleTime);
                maxValue = Math.max(maxValue, result.quickTime);
                maxValue = Math.max(maxValue, result.quickMedianThreeTime);
                maxValue = Math.max(maxValue, result.mergeTime);
            }

            long paddedMaxValue = (long) Math.ceil(maxValue * 1.15);

            drawAxes(g2, left, top, chartWidth, chartHeight, paddedMaxValue);
            drawBars(g2, left, top, chartWidth, chartHeight, paddedMaxValue);
            drawLegend(g2, left, height - 45);
        }

        private void drawAxes(Graphics2D g2, int left, int top, int chartWidth, int chartHeight, long maxValue) {
            g2.setColor(new Color(108, 117, 125));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(left, top, left, top + chartHeight);
            g2.drawLine(left, top + chartHeight, left + chartWidth, top + chartHeight);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            FontMetrics metrics = g2.getFontMetrics();
            int lines = 5;
            for (int i = 0; i <= lines; i++) {
                int y = top + chartHeight - i * chartHeight / lines;
                g2.setColor(new Color(222, 226, 230));
                g2.drawLine(left, y, left + chartWidth, y);

                long value = maxValue * i / lines;
                String axisLabel = formatMilliseconds(value);
                g2.setColor(new Color(73, 80, 87));
                g2.drawString(axisLabel, left - metrics.stringWidth(axisLabel) - 18, y + 5);
            }
        }

        private void drawBars(Graphics2D g2, int left, int top, int chartWidth, int chartHeight, long maxValue) {
            int groupCount = results.size();
            int groupWidth = chartWidth / groupCount;
            int barWidth = Math.max(20, groupWidth / 6);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            FontMetrics metrics = g2.getFontMetrics();

            for (int i = 0; i < groupCount; i++) {
                Main.BenchmarkResult result = results.get(i);
                long[] values = {
                        result.bubbleTime,
                        result.quickTime,
                        result.quickMedianThreeTime,
                        result.mergeTime
                };

                int groupLeft = left + i * groupWidth;
                int barsLeft = groupLeft + (groupWidth - barWidth * values.length) / 2;

                for (int j = 0; j < values.length; j++) {
                    int barHeight = (int) Math.round((values[j] * 1.0 / maxValue) * chartHeight);
                    int x = barsLeft + j * barWidth;
                    int y = top + chartHeight - barHeight;

                    g2.setColor(BAR_COLORS[j]);
                    g2.fillRoundRect(x, y, barWidth - 6, barHeight, 10, 10);

                    String valueLabel = formatMilliseconds(values[j]);
                    int labelX = x + (barWidth - 6 - metrics.stringWidth(valueLabel)) / 2;
                    int labelY = y - 8;
                    Color labelColor = new Color(52, 58, 64);
                    if (labelY < top + metrics.getAscent() + 4) {
                        labelY = y + metrics.getAscent() + 6;
                        labelColor = Color.WHITE;
                    }
                    g2.setColor(labelColor);
                    g2.drawString(valueLabel, labelX, labelY);
                }

                g2.setColor(new Color(33, 37, 41));
                String datasetLabel = formatDatasetName(result.datasetName);
                int datasetLabelX = groupLeft + (groupWidth - metrics.stringWidth(datasetLabel)) / 2;
                g2.drawString(datasetLabel, datasetLabelX, top + chartHeight + 35);
            }
        }

        private void drawLegend(Graphics2D g2, int left, int y) {
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            int x = left;
            for (int i = 0; i < LABELS.length; i++) {
                g2.setColor(BAR_COLORS[i]);
                g2.fillRect(x, y - 12, 18, 18);
                g2.setColor(new Color(33, 37, 41));
                g2.drawString(LABELS[i], x + 26, y + 2);
                x += 130;
            }
        }

        private String formatMilliseconds(long value) {
            return String.format("%.3f ms", value / 1_000_000.0);
        }

        private String formatDatasetName(String datasetName) {
            if ("candidates_A.csv".equals(datasetName)) {
                return "Dataset A";
            }
            if ("candidates_B.csv".equals(datasetName)) {
                return "Dataset B";
            }
            if ("candidates_C.csv".equals(datasetName)) {
                return "Dataset C";
            }
            return datasetName;
        }
    }
}
