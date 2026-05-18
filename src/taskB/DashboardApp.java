import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class DashboardApp {
    private static final int TOP_COUNT = 10;
    private static final int SORT_WARMUP_RUNS = 5;
    private static final int SORT_MEASURED_RUNS = 10;
    private static final int REPRESENTATION_WARMUP_RUNS = 1;
    private static final int REPRESENTATION_MEASURED_RUNS = 5;

    public static void main(String[] args) {
        try {
            Path dataFolder = resolveDataFolder(args);
            DashboardData data = buildDashboardData(dataFolder);
            if (hasFlag(args, "--check")) {
                printCheckSummary(data);
                return;
            }
            SwingUtilities.invokeLater(() -> showDashboard(data));
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }

    private static Path resolveDataFolder(String[] args) throws IOException {
        String folderArgument = firstNonFlagArgument(args);
        if (folderArgument == null) {
            return Main.findDataFolder(new String[0]);
        }
        return Main.findDataFolder(new String[]{folderArgument});
    }

    private static String firstNonFlagArgument(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                return arg;
            }
        }
        return null;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printCheckSummary(DashboardData data) {
        System.out.println("Dashboard data check OK");
        System.out.println("Data folder: " + data.dataFolder.toAbsolutePath());
        System.out.println("Task A datasets: " + data.taskAResults.size());
        System.out.println("Task B graph vertices: " + data.taskBData.graph.vertexCount());
        System.out.println("Task B graph edges: " + data.taskBData.graph.edgeCount());
        System.out.println("Route cases: " + data.taskBData.pathCases.size());
        System.out.println("Representation rows: " + data.taskBData.representationRows.size());
    }

    private static DashboardData buildDashboardData(Path dataFolder) throws IOException {
        List<TaskASetResult> taskAResults = buildTaskAResults(dataFolder);
        TaskBData taskBData = buildTaskBData(dataFolder);
        return new DashboardData(dataFolder, taskAResults, taskBData);
    }

    private static List<TaskASetResult> buildTaskAResults(Path dataFolder) throws IOException {
        String[] files = {"candidates_A.csv", "candidates_B.csv", "candidates_C.csv"};
        List<TaskASetResult> results = new ArrayList<>();

        for (String file : files) {
            Path csv = dataFolder.resolve(file);
            ArrayList<Candidate> originalList = Main.loadCandidates(csv);
            SortMetric bubble = measureSort(originalList, BubbleSort::sort);
            SortMetric quick = measureSort(originalList, QuickSort::sort);
            SortMetric quickM3 = measureSort(originalList, QuickSortMedianThree::sort);
            SortMetric merge = measureSort(originalList, MergeSort::sort);

            ArrayList<Candidate> sortedList = Main.copyList(originalList);
            MergeSort.sort(sortedList);
            results.add(new TaskASetResult(file, originalList.size(), bubble, quick, quickM3, merge, topTen(sortedList)));
        }

        return results;
    }

    private static SortMetric measureSort(ArrayList<Candidate> originalList, Main.SortAction sorter) {
        for (int i = 0; i < SORT_WARMUP_RUNS; i++) {
            ArrayList<Candidate> list = Main.copyList(originalList);
            sorter.sort(list);
        }

        long total = 0L;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < SORT_MEASURED_RUNS; i++) {
            ArrayList<Candidate> list = Main.copyList(originalList);
            long start = System.nanoTime();
            sorter.sort(list);
            long elapsed = System.nanoTime() - start;
            total += elapsed;
            best = Math.min(best, elapsed);
        }

        return new SortMetric(total / SORT_MEASURED_RUNS, best);
    }

    private static List<String> topTen(ArrayList<Candidate> sortedList) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 10 && i < sortedList.size(); i++) {
            Candidate candidate = sortedList.get(i);
            values.add(candidate.locationId + " (" + candidate.priorityScore + ")");
        }
        return values;
    }

    private static TaskBData buildTaskBData(Path dataFolder) throws IOException {
        CandidateLoader loader = new CandidateLoader();
        Map<String, List<LocationCandidate>> topTargets = new LinkedHashMap<>();
        topTargets.put("A", loader.selectTopK(dataFolder.resolve("candidates_A.csv"), TOP_COUNT));
        topTargets.put("B", loader.selectTopK(dataFolder.resolve("candidates_B.csv"), TOP_COUNT));
        topTargets.put("C", loader.selectTopK(dataFolder.resolve("candidates_C.csv"), TOP_COUNT));

        WeightedGraph<String> graph = WeightedGraph.fromCsv(dataFolder.resolve("paths.csv"));
        GraphAnalyzer analyzer = new GraphAnalyzer();
        List<String> targetIds = collectTargetIds(topTargets);
        int components = analyzer.countConnectedComponents(graph);
        List<String> missingTargets = new ArrayList<>(analyzer.collectMissingVertices(graph, targetIds));

        String start = topTargets.get("A").get(0).getLocationId();
        TraversalResult<String> bfs = new BFSAlgorithm<String>().traverse(graph, start);
        TraversalResult<String> dfs = new DFSAlgorithm<String>().traverse(graph, start);

        List<PathCaseResult> pathCases = buildPathCases(graph, topTargets);
        MinimumSpanningTreeResult<String> mst = new PrimAlgorithm<String>().buildTree(graph, start);
        GlobalRouteResult globalRoute = buildGlobalRoute(graph);
        List<RepresentationBenchmarkRow> representationRows = buildRepresentationRows(
                graph,
                start,
                topTargets.get("A").get(9).getLocationId()
        );

        return new TaskBData(
                topTargets,
                graph,
                targetIds,
                components,
                missingTargets,
                bfs,
                dfs,
                pathCases,
                mst,
                globalRoute,
                representationRows
        );
    }

    private static List<PathCaseResult> buildPathCases(
            WeightedGraph<String> graph,
            Map<String, List<LocationCandidate>> topTargets
    ) {
        String a1 = topTargets.get("A").get(0).getLocationId();
        String a10 = topTargets.get("A").get(9).getLocationId();
        String b1 = topTargets.get("B").get(0).getLocationId();
        String b5 = topTargets.get("B").get(4).getLocationId();
        String c1 = topTargets.get("C").get(0).getLocationId();
        String c5 = topTargets.get("C").get(4).getLocationId();

        List<PathCaseResult> cases = new ArrayList<>();
        cases.add(runPathCase(graph, "Case 1", Arrays.asList(a1, a1)));
        cases.add(runPathCase(graph, "Case 2", Arrays.asList(a1, a10)));
        cases.add(runPathCase(graph, "Case 3", Arrays.asList(a1, b5, b1)));
        cases.add(runPathCase(graph, "Case 4", Arrays.asList(a1, b5, c5, c1)));
        return cases;
    }

    private static PathCaseResult runPathCase(WeightedGraph<String> graph, String name, List<String> requiredPoints) {
        PathFindingAlgorithm<String> pathFinder = new DijkstraAlgorithm<>();
        long start = System.nanoTime();
        PathResult<String> result = shortestPathThrough(graph, pathFinder, requiredPoints);
        long runtimeNs = System.nanoTime() - start;
        return new PathCaseResult(name, requiredPoints, result, runtimeNs);
    }

    private static PathResult<String> shortestPathThrough(
            WeightedGraph<String> graph,
            PathFindingAlgorithm<String> pathFinder,
            List<String> mustPass
    ) {
        String source = mustPass.get(0);
        String target = mustPass.get(mustPass.size() - 1);
        double costSoFar = 0.0;
        List<String> wholePath = new ArrayList<>();

        for (int i = 0; i < mustPass.size() - 1; i++) {
            PathResult<String> part = pathFinder.findPath(graph, mustPass.get(i), mustPass.get(i + 1));
            if (!part.isReachable()) {
                return PathResult.unreachable(source, target);
            }

            costSoFar += part.getTotalCost();
            if (wholePath.isEmpty()) {
                wholePath.addAll(part.getPath());
            } else {
                wholePath.addAll(part.getPath().subList(1, part.getPath().size()));
            }
        }

        return new PathResult<>(source, target, costSoFar, wholePath);
    }

    private static GlobalRouteResult buildGlobalRoute(WeightedGraph<String> graph) {
        List<String> targets = Arrays.asList(
                "L0001", "L0002", "L0003", "L0004", "L0005",
                "L0101", "L0102", "L0103"
        );
        GlobalRouteOptimizer optimizer = new GlobalRouteOptimizer(15);
        long start = System.nanoTime();
        GlobalRouteOptimizer.TspResult result = optimizer.solveTsp(graph, targets);
        long runtimeNs = System.nanoTime() - start;
        return new GlobalRouteResult(targets, result, runtimeNs);
    }

    private static List<RepresentationBenchmarkRow> buildRepresentationRows(
            WeightedGraph<String> listGraph,
            String start,
            String destination
    ) {
        AdjacencyMatrixGraph<String> matrixGraph = AdjacencyMatrixGraph.fromWeightedGraph(listGraph);
        List<RepresentationBenchmarkRow> rows = new ArrayList<>();

        rows.add(compare("BFS", "Adjacency List", () -> new BFSAlgorithm<String>().traverse(listGraph, start),
                "visited=" + new BFSAlgorithm<String>().traverse(listGraph, start).getSearchOrder().size()));
        rows.add(compare("BFS", "Adjacency Matrix", () -> new MatrixBFSAlgorithm<String>().traverse(matrixGraph, start),
                "visited=" + new MatrixBFSAlgorithm<String>().traverse(matrixGraph, start).getSearchOrder().size()));
        rows.add(compare("DFS", "Adjacency List", () -> new DFSAlgorithm<String>().traverse(listGraph, start),
                "visited=" + new DFSAlgorithm<String>().traverse(listGraph, start).getSearchOrder().size()));
        rows.add(compare("DFS", "Adjacency Matrix", () -> new MatrixDFSAlgorithm<String>().traverse(matrixGraph, start),
                "visited=" + new MatrixDFSAlgorithm<String>().traverse(matrixGraph, start).getSearchOrder().size()));

        PathResult<String> listPath = new DijkstraAlgorithm<String>().findPath(listGraph, start, destination);
        PathResult<String> matrixPath = new MatrixDijkstraAlgorithm<String>().findPath(matrixGraph, start, destination);
        rows.add(compare("Dijkstra", "Adjacency List", () -> new DijkstraAlgorithm<String>().findPath(listGraph, start, destination),
                "cost=" + formatCost(listPath.getTotalCost()) + ", pathNodes=" + listPath.getPath().size()));
        rows.add(compare("Dijkstra", "Adjacency Matrix", () -> new MatrixDijkstraAlgorithm<String>().findPath(matrixGraph, start, destination),
                "cost=" + formatCost(matrixPath.getTotalCost()) + ", pathNodes=" + matrixPath.getPath().size()));

        MinimumSpanningTreeResult<String> listMst = new PrimAlgorithm<String>().buildTree(listGraph, start);
        MinimumSpanningTreeResult<String> matrixMst = new MatrixPrimAlgorithm<String>().buildTree(matrixGraph, start);
        rows.add(compare("Prim MST", "Adjacency List", () -> new PrimAlgorithm<String>().buildTree(listGraph, start),
                "weight=" + listMst.getTotalWeight() + ", edges=" + listMst.getTreeEdges().size()));
        rows.add(compare("Prim MST", "Adjacency Matrix", () -> new MatrixPrimAlgorithm<String>().buildTree(matrixGraph, start),
                "weight=" + matrixMst.getTotalWeight() + ", edges=" + matrixMst.getTreeEdges().size()));

        return rows;
    }

    private static RepresentationBenchmarkRow compare(
            String algorithm,
            String representation,
            Runnable runOnce,
            String resultSummary
    ) {
        long average = measureRunnable(runOnce);
        return new RepresentationBenchmarkRow(algorithm, representation, average, resultSummary);
    }

    private static long measureRunnable(Runnable runOnce) {
        for (int i = 0; i < REPRESENTATION_WARMUP_RUNS; i++) {
            runOnce.run();
        }

        long total = 0L;
        for (int i = 0; i < REPRESENTATION_MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            runOnce.run();
            total += System.nanoTime() - start;
        }
        return total / REPRESENTATION_MEASURED_RUNS;
    }

    private static void showDashboard(DashboardData data) {
        JFrame frame = new JFrame("CPT204 Task A + Task B Results Dashboard");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JLabel title = new JLabel("CPT204 Integrated Results Dashboard");
        title.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        frame.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", buildOverviewPanel(data));
        tabs.addTab("Task A Sorting", buildTaskAPanel(data.taskAResults));
        tabs.addTab("Task B Routes", buildRoutesPanel(data.taskBData));
        tabs.addTab("Algorithms", buildAlgorithmsPanel(data.taskBData));
        frame.add(tabs, BorderLayout.CENTER);

        frame.setSize(1280, 860);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JPanel buildOverviewPanel(DashboardData data) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setFont(new Font("Monospaced", Font.PLAIN, 13));
        text.setText(buildOverviewText(data));
        panel.add(new JScrollPane(text), BorderLayout.CENTER);

        return panel;
    }

    private static String buildOverviewText(DashboardData data) {
        StringBuilder builder = new StringBuilder();
        builder.append("Data folder: ").append(data.dataFolder.toAbsolutePath()).append("\n\n");
        builder.append("Task A CSV loading\n");
        for (TaskASetResult result : data.taskAResults) {
            builder.append("  ").append(result.datasetName)
                    .append(": rows=").append(result.rowCount)
                    .append(", top=").append(String.join(", ", result.topTen.subList(0, Math.min(3, result.topTen.size()))))
                    .append("\n");
        }

        TaskBData taskB = data.taskBData;
        builder.append("\nTask B graph\n");
        builder.append("  Vertices: ").append(taskB.graph.vertexCount()).append("\n");
        builder.append("  Undirected edges: ").append(taskB.graph.edgeCount()).append("\n");
        builder.append("  Connected components: ").append(taskB.connectedComponents).append("\n");
        builder.append("  Selected targets missing from graph: ").append(taskB.missingTargets.size()).append("\n");
        builder.append("  BFS visited: ").append(taskB.bfs.getSearchOrder().size()).append("\n");
        builder.append("  DFS visited: ").append(taskB.dfs.getSearchOrder().size()).append("\n");
        builder.append("  MST weight: ").append(taskB.mst.getTotalWeight()).append("\n");
        builder.append("  Global route cost: ").append(formatCost(taskB.globalRoute.result.getTotalCost())).append("\n");
        return builder.toString();
    }

    private static JPanel buildTaskAPanel(List<TaskASetResult> taskAResults) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        List<Main.BenchmarkResult> chartResults = new ArrayList<>();
        for (TaskASetResult result : taskAResults) {
            chartResults.add(new Main.BenchmarkResult(
                    result.datasetName,
                    result.bubble.averageNs,
                    result.quick.averageNs,
                    result.quickM3.averageNs,
                    result.merge.averageNs
            ));
        }

        BenchmarkChartFrame.ChartPanel chart = new BenchmarkChartFrame.ChartPanel(chartResults);
        JTable summaryTable = buildTable(taskASummaryRows(taskAResults), new String[]{
                "Dataset", "Rows", "Bubble avg", "Quick avg", "Quick M3 avg", "Merge avg", "Best algorithm"
        });
        JTextArea topTen = new JTextArea(buildTopTenText(taskAResults));
        topTen.setEditable(false);
        topTen.setFont(new Font("Monospaced", Font.PLAIN, 13));

        JPanel lower = new JPanel(new GridLayout(1, 2, 10, 10));
        lower.add(new JScrollPane(summaryTable));
        lower.add(new JScrollPane(topTen));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chart, lower);
        split.setResizeWeight(0.62);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private static Object[][] taskASummaryRows(List<TaskASetResult> results) {
        Object[][] rows = new Object[results.size()][7];
        for (int i = 0; i < results.size(); i++) {
            TaskASetResult result = results.get(i);
            rows[i][0] = result.datasetName;
            rows[i][1] = result.rowCount;
            rows[i][2] = formatNs(result.bubble.averageNs);
            rows[i][3] = formatNs(result.quick.averageNs);
            rows[i][4] = formatNs(result.quickM3.averageNs);
            rows[i][5] = formatNs(result.merge.averageNs);
            rows[i][6] = result.bestAlgorithmName();
        }
        return rows;
    }

    private static String buildTopTenText(List<TaskASetResult> results) {
        StringBuilder builder = new StringBuilder();
        for (TaskASetResult result : results) {
            builder.append(result.datasetName).append("\n");
            for (int i = 0; i < result.topTen.size(); i++) {
                builder.append(String.format("  %2d. %s%n", i + 1, result.topTen.get(i)));
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private static JPanel buildRoutesPanel(TaskBData data) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JComboBox<String> caseSelector = new JComboBox<>();
        for (PathCaseResult pathCase : data.pathCases) {
            caseSelector.addItem(pathCase.name);
        }

        GraphVisualizer graphPanel = new GraphVisualizer();
        graphPanel.setPreferredSize(new Dimension(900, 640));
        graphPanel.setImportantVertices(data.targetIds);

        JTextArea details = new JTextArea();
        details.setEditable(false);
        details.setFont(new Font("Monospaced", Font.PLAIN, 13));

        caseSelector.addActionListener(event -> {
            PathCaseResult selected = data.pathCases.get(caseSelector.getSelectedIndex());
            graphPanel.setGraphAndPath(data.graph, selected.result.getPath());
            details.setText(buildPathCaseText(selected));
        });
        caseSelector.setSelectedIndex(0);

        JTable casesTable = buildTable(pathCaseRows(data.pathCases), new String[]{
                "Case", "Required points", "Cost", "Runtime", "Path nodes"
        });

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(new JLabel("Route case:"), BorderLayout.WEST);
        top.add(caseSelector, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.add(new JScrollPane(casesTable), BorderLayout.NORTH);
        right.add(new JScrollPane(details), BorderLayout.CENTER);
        right.setPreferredSize(new Dimension(430, 640));

        panel.add(top, BorderLayout.NORTH);
        panel.add(graphPanel, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private static Object[][] pathCaseRows(List<PathCaseResult> pathCases) {
        Object[][] rows = new Object[pathCases.size()][5];
        for (int i = 0; i < pathCases.size(); i++) {
            PathCaseResult pathCase = pathCases.get(i);
            rows[i][0] = pathCase.name;
            rows[i][1] = String.join(" -> ", pathCase.requiredPoints);
            rows[i][2] = formatCost(pathCase.result.getTotalCost());
            rows[i][3] = formatNs(pathCase.runtimeNs);
            rows[i][4] = pathCase.result.getPath().size();
        }
        return rows;
    }

    private static String buildPathCaseText(PathCaseResult pathCase) {
        return pathCase.name + "\n"
                + "Required: " + String.join(" -> ", pathCase.requiredPoints) + "\n"
                + "Source: " + pathCase.result.getSource() + "\n"
                + "Destination: " + pathCase.result.getDestination() + "\n"
                + "Cost: " + formatCost(pathCase.result.getTotalCost()) + "\n"
                + "Runtime: " + formatNs(pathCase.runtimeNs) + "\n\n"
                + "Path:\n" + pathCase.result.formatPath();
    }

    private static JPanel buildAlgorithmsPanel(TaskBData data) {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        panel.add(wrapTextArea("Selected Task A Targets Used by Task B", buildTargetsText(data.topTargets)));
        panel.add(wrapTextArea("Traversal and MST", buildTraversalText(data)));
        panel.add(new JScrollPane(buildTable(representationRows(data.representationRows), new String[]{
                "Algorithm", "Representation", "Avg runtime", "Result"
        })));
        panel.add(wrapTextArea("Global Route Optimization", buildGlobalRouteText(data.globalRoute)));
        return panel;
    }

    private static JPanel wrapTextArea(String title, String content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        JTextArea textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
        return panel;
    }

    private static String buildTargetsText(Map<String, List<LocationCandidate>> targets) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<LocationCandidate>> entry : targets.entrySet()) {
            builder.append("Dataset ").append(entry.getKey()).append("\n");
            for (LocationCandidate candidate : entry.getValue()) {
                builder.append("  ").append(candidate).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private static String buildTraversalText(TaskBData data) {
        return "Graph type: undirected weighted graph\n"
                + "Vertices: " + data.graph.vertexCount() + "\n"
                + "Undirected edges: " + data.graph.edgeCount() + "\n"
                + "Connected components: " + data.connectedComponents + "\n"
                + "Missing selected targets: " + data.missingTargets.size() + "\n\n"
                + "BFS visited: " + data.bfs.getSearchOrder().size() + "\n"
                + "BFS preview: " + preview(data.bfs.getSearchOrder(), 14) + "\n\n"
                + "DFS visited: " + data.dfs.getSearchOrder().size() + "\n"
                + "DFS preview: " + preview(data.dfs.getSearchOrder(), 14) + "\n\n"
                + "Prim MST start: " + data.mst.getStart() + "\n"
                + "Prim MST edges: " + data.mst.getTreeEdges().size() + "\n"
                + "Prim MST total weight: " + data.mst.getTotalWeight() + "\n";
    }

    private static Object[][] representationRows(List<RepresentationBenchmarkRow> rows) {
        Object[][] values = new Object[rows.size()][4];
        for (int i = 0; i < rows.size(); i++) {
            RepresentationBenchmarkRow row = rows.get(i);
            values[i][0] = row.getAlgorithmName();
            values[i][1] = row.getRepresentationName();
            values[i][2] = formatNs(row.getAverageRuntimeNs());
            values[i][3] = row.getResultSummary();
        }
        return values;
    }

    private static String buildGlobalRouteText(GlobalRouteResult globalRoute) {
        return "Targets:\n"
                + "  " + String.join(" -> ", globalRoute.targets) + "\n\n"
                + "Cost: " + formatCost(globalRoute.result.getTotalCost()) + "\n"
                + "Runtime: " + formatNs(globalRoute.runtimeNs) + "\n\n"
                + "Visit order:\n"
                + globalRoute.result.formatVisitOrder() + "\n\n"
                + "Method: Floyd-Warshall + state-compression DP";
    }

    private static JTable buildTable(Object[][] rows, String[] columns) {
        DefaultTableModel model = new DefaultTableModel(rows, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    private static List<String> collectTargetIds(Map<String, List<LocationCandidate>> topTargets) {
        List<String> ids = new ArrayList<>();
        for (List<LocationCandidate> group : topTargets.values()) {
            for (LocationCandidate place : group) {
                ids.add(place.getLocationId());
            }
        }
        return ids;
    }

    private static String preview(List<String> values, int limit) {
        int end = Math.min(limit, values.size());
        String prefix = String.join(" -> ", values.subList(0, end));
        if (values.size() <= limit) {
            return prefix;
        }
        return prefix + " -> ...";
    }

    private static String formatNs(long value) {
        return String.format("%,d ns", value);
    }

    private static String formatCost(double cost) {
        if (Double.isInfinite(cost)) {
            return "UNREACHABLE";
        }
        if (Math.rint(cost) == cost) {
            return String.valueOf((long) cost);
        }
        return String.format("%.2f", cost);
    }

    private static class DashboardData {
        private final Path dataFolder;
        private final List<TaskASetResult> taskAResults;
        private final TaskBData taskBData;

        private DashboardData(Path dataFolder, List<TaskASetResult> taskAResults, TaskBData taskBData) {
            this.dataFolder = dataFolder;
            this.taskAResults = taskAResults;
            this.taskBData = taskBData;
        }
    }

    private static class TaskASetResult {
        private final String datasetName;
        private final int rowCount;
        private final SortMetric bubble;
        private final SortMetric quick;
        private final SortMetric quickM3;
        private final SortMetric merge;
        private final List<String> topTen;

        private TaskASetResult(
                String datasetName,
                int rowCount,
                SortMetric bubble,
                SortMetric quick,
                SortMetric quickM3,
                SortMetric merge,
                List<String> topTen
        ) {
            this.datasetName = datasetName;
            this.rowCount = rowCount;
            this.bubble = bubble;
            this.quick = quick;
            this.quickM3 = quickM3;
            this.merge = merge;
            this.topTen = topTen;
        }

        private String bestAlgorithmName() {
            long best = Math.min(Math.min(bubble.averageNs, quick.averageNs), Math.min(quickM3.averageNs, merge.averageNs));
            if (best == bubble.averageNs) {
                return "Bubble Sort";
            }
            if (best == quick.averageNs) {
                return "Quick Sort";
            }
            if (best == quickM3.averageNs) {
                return "Quick M3";
            }
            return "Merge Sort";
        }
    }

    private static class SortMetric {
        private final long averageNs;
        private final long bestNs;

        private SortMetric(long averageNs, long bestNs) {
            this.averageNs = averageNs;
            this.bestNs = bestNs;
        }
    }

    private static class TaskBData {
        private final Map<String, List<LocationCandidate>> topTargets;
        private final WeightedGraph<String> graph;
        private final List<String> targetIds;
        private final int connectedComponents;
        private final List<String> missingTargets;
        private final TraversalResult<String> bfs;
        private final TraversalResult<String> dfs;
        private final List<PathCaseResult> pathCases;
        private final MinimumSpanningTreeResult<String> mst;
        private final GlobalRouteResult globalRoute;
        private final List<RepresentationBenchmarkRow> representationRows;

        private TaskBData(
                Map<String, List<LocationCandidate>> topTargets,
                WeightedGraph<String> graph,
                List<String> targetIds,
                int connectedComponents,
                List<String> missingTargets,
                TraversalResult<String> bfs,
                TraversalResult<String> dfs,
                List<PathCaseResult> pathCases,
                MinimumSpanningTreeResult<String> mst,
                GlobalRouteResult globalRoute,
                List<RepresentationBenchmarkRow> representationRows
        ) {
            this.topTargets = topTargets;
            this.graph = graph;
            this.targetIds = targetIds;
            this.connectedComponents = connectedComponents;
            this.missingTargets = missingTargets;
            this.bfs = bfs;
            this.dfs = dfs;
            this.pathCases = pathCases;
            this.mst = mst;
            this.globalRoute = globalRoute;
            this.representationRows = representationRows;
        }
    }

    private static class PathCaseResult {
        private final String name;
        private final List<String> requiredPoints;
        private final PathResult<String> result;
        private final long runtimeNs;

        private PathCaseResult(String name, List<String> requiredPoints, PathResult<String> result, long runtimeNs) {
            this.name = name;
            this.requiredPoints = requiredPoints;
            this.result = result;
            this.runtimeNs = runtimeNs;
        }
    }

    private static class GlobalRouteResult {
        private final List<String> targets;
        private final GlobalRouteOptimizer.TspResult result;
        private final long runtimeNs;

        private GlobalRouteResult(List<String> targets, GlobalRouteOptimizer.TspResult result, long runtimeNs) {
            this.targets = targets;
            this.result = result;
            this.runtimeNs = runtimeNs;
        }
    }
}
