import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class App {
    private static final int TOP_COUNT = 10;
    private static final int PREVIEW_LIMIT = 20;

    public static void main(String[] args) throws IOException {
        Path dataFolder = findDataFolder(args);
        CandidateLoader loader = new CandidateLoader();

        Map<String, List<LocationCandidate>> topTargets = new LinkedHashMap<>();
        topTargets.put("A", loader.selectTopK(dataFolder.resolve("candidates_A.csv"), TOP_COUNT));
        topTargets.put("B", loader.selectTopK(dataFolder.resolve("candidates_B.csv"), TOP_COUNT));
        topTargets.put("C", loader.selectTopK(dataFolder.resolve("candidates_C.csv"), TOP_COUNT));

        WeightedGraph<String> cityGraph = WeightedGraph.fromCsv(dataFolder.resolve("paths.csv"));
        GraphAnalyzer graphAnalyzer = new GraphAnalyzer();
        PathFindingAlgorithm<String> pathFinder = new DijkstraAlgorithm<>();
        GraphTraversalAlgorithm<String> bfs = new BFSAlgorithm<>();
        GraphTraversalAlgorithm<String> dfs = new DFSAlgorithm<>();
        MinimumSpanningTreeAlgorithm<String> mstBuilder = new PrimAlgorithm<>();

        printSelectedTargets(topTargets);
        printGraphChecks(cityGraph, collectTargetIds(topTargets), graphAnalyzer);
        printTraversalChecks(cityGraph, topTargets.get("A").get(0).getLocationId(), bfs, dfs);
        Map<String, PathResult<String>> caseResults = printRequiredShortestPathCases(cityGraph, topTargets, pathFinder);
        printPrimExperiment(cityGraph, topTargets.get("A").get(0).getLocationId(), mstBuilder);
        printRepresentationBenchmark(cityGraph, topTargets);
        printGlobalRouteOptimization(cityGraph);

        if (!hasFlag(args, "--no-ui")) {
            openCaseVisualizers(cityGraph, topTargets, caseResults);
        }
    }

    private static Path findDataFolder(String[] args) throws IOException {
        String folderArgument = firstNonFlagArgument(args);
        if (folderArgument != null) {
            Path givenFolder = Paths.get(folderArgument);
            if (Files.isDirectory(givenFolder)) {
                return givenFolder;
            }
            throw new IOException("Dataset folder not found: " + givenFolder.toAbsolutePath());
        }

        Path[] placesToTry = {
                Paths.get("data"),
                Paths.get("..", "Group Project Datasets"),
                Paths.get("Group Project Datasets"),
                Paths.get("..", "..", "Group Project Datasets"),
                Paths.get("..", "data"),
                Paths.get("..", "..", "data")
        };

        for (Path folder : placesToTry) {
            if (Files.isDirectory(folder)) {
                return folder;
            }
        }

        throw new IOException("Could not find Group Project Datasets folder. Pass it as a command-line argument.");
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

    private static void printSelectedTargets(Map<String, List<LocationCandidate>> topTargets) {
        System.out.println("=== Task A Top 10 Targets Used By Task B ===");
        for (Map.Entry<String, List<LocationCandidate>> entry : topTargets.entrySet()) {
            System.out.println("Dataset " + entry.getKey() + ": " + joinCandidates(entry.getValue()));
        }
        System.out.println();
    }

    private static String joinCandidates(List<LocationCandidate> candidates) {
        List<String> parts = new ArrayList<>();
        for (LocationCandidate candidate : candidates) {
            parts.add(candidate.toString());
        }
        return String.join(", ", parts);
    }

    private static <T extends Comparable<T>> void printGraphChecks(
            WeightedGraph<T> graph,
            Iterable<T> targetIds,
            GraphAnalyzer analyzer
    ) {
        System.out.println("=== Graph Construction Sanity Checks ===");
        System.out.println("Graph type: undirected weighted graph");
        System.out.println("Vertices: " + graph.vertexCount());
        System.out.println("Undirected edges: " + graph.edgeCount());
        System.out.println("Connected components: " + analyzer.countConnectedComponents(graph));

        List<T> missingTargets = new ArrayList<>(analyzer.collectMissingVertices(graph, targetIds));
        System.out.println("Selected targets missing from graph: " + missingTargets.size());
        if (!missingTargets.isEmpty()) {
            System.out.println("Missing target ids: " + joinValues(missingTargets, ", "));
        }
        System.out.println();
    }

    private static <T extends Comparable<T>> void printTraversalChecks(
            WeightedGraph<T> graph,
            T start,
            GraphTraversalAlgorithm<T> bfsAlgorithm,
            GraphTraversalAlgorithm<T> dfsAlgorithm
    ) {
        System.out.println("=== BFS/DFS Traversal Checks From " + start + " ===");

        TraversalResult<T> bfs = bfsAlgorithm.traverse(graph, start);
        System.out.println("BFS visited vertices: " + bfs.getSearchOrder().size());
        System.out.println("BFS order preview: " + preview(bfs.getSearchOrder(), PREVIEW_LIMIT));
        System.out.println("BFS parent preview: " + previewParents(bfs, PREVIEW_LIMIT));

        TraversalResult<T> dfs = dfsAlgorithm.traverse(graph, start);
        System.out.println("DFS visited vertices: " + dfs.getSearchOrder().size());
        System.out.println("DFS order preview: " + preview(dfs.getSearchOrder(), PREVIEW_LIMIT));
        System.out.println("DFS parent preview: " + previewParents(dfs, PREVIEW_LIMIT));
        System.out.println();
    }

    private static <T> String preview(List<T> values, int limit) {
        int end = Math.min(limit, values.size());
        List<T> shown = values.subList(0, end);
        if (values.size() <= limit) {
            return joinValues(shown, " -> ");
        }
        return joinValues(shown, " -> ") + " -> ...";
    }

    private static <T> String previewParents(TraversalResult<T> result, int limit) {
        List<String> parts = new ArrayList<>();
        int count = 0;
        for (Map.Entry<T, T> entry : result.getParent().entrySet()) {
            if (count >= limit) {
                parts.add("...");
                break;
            }
            parts.add(entry.getKey() + "<-" + (entry.getValue() == null ? "ROOT" : entry.getValue()));
            count++;
        }
        return String.join(", ", parts);
    }

    private static Map<String, PathResult<String>> printRequiredShortestPathCases(
            WeightedGraph<String> graph,
            Map<String, List<LocationCandidate>> topTargets,
            PathFindingAlgorithm<String> pathFinder
    ) {
        String a1 = topTargets.get("A").get(0).getLocationId();
        String a10 = topTargets.get("A").get(9).getLocationId();
        String b1 = topTargets.get("B").get(0).getLocationId();
        String b5 = topTargets.get("B").get(4).getLocationId();
        String c1 = topTargets.get("C").get(0).getLocationId();
        String c5 = topTargets.get("C").get(4).getLocationId();

        System.out.println("=== Required Task B Shortest Path Cases ===");
        Map<String, PathResult<String>> results = new LinkedHashMap<>();
        results.put("Case 1", runCase(graph, pathFinder, "Case 1", Arrays.asList(a1, a1)));
        results.put("Case 2", runCase(graph, pathFinder, "Case 2", Arrays.asList(a1, a10)));
        results.put("Case 3", runCase(graph, pathFinder, "Case 3", Arrays.asList(a1, b5, b1)));
        results.put("Case 4", runCase(graph, pathFinder, "Case 4", Arrays.asList(a1, b5, c5, c1)));
        System.out.println();

        return results;
    }

    private static <T extends Comparable<T>> PathResult<T> runCase(
            WeightedGraph<T> graph,
            PathFindingAlgorithm<T> pathFinder,
            String caseName,
            List<T> mustPass
    ) {
        long startTime = System.nanoTime();
        PathResult<T> result = shortestPathThrough(graph, pathFinder, mustPass);
        long timeUsed = System.nanoTime() - startTime;

        System.out.println(caseName);
        System.out.println("  Required route points: " + joinValues(mustPass, " -> "));
        System.out.println("  Start: " + result.getSource());
        System.out.println("  Destination: " + result.getDestination());
        System.out.println("  Path: " + result.formatPath());
        System.out.println("  Total cost: " + (result.isReachable() ? formatCost(result.getTotalCost()) : "UNREACHABLE"));
        System.out.println("  Runtime (ns): " + timeUsed);

        return result;
    }

    private static <T extends Comparable<T>> PathResult<T> shortestPathThrough(
            WeightedGraph<T> graph,
            PathFindingAlgorithm<T> pathFinder,
            List<T> mustPass
    ) {
        if (mustPass.size() < 2) {
            throw new IllegalArgumentException("At least source and destination are required.");
        }

        T source = mustPass.get(0);
        T target = mustPass.get(mustPass.size() - 1);
        double costSoFar = 0.0;
        List<T> wholePath = new ArrayList<>();

        // For waypoint cases, stitch together the shortest path between each pair.
        for (int i = 0; i < mustPass.size() - 1; i++) {
            PathResult<T> part = pathFinder.findPath(graph, mustPass.get(i), mustPass.get(i + 1));
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

    private static <T extends Comparable<T>> void printPrimExperiment(
            WeightedGraph<T> graph,
            T start,
            MinimumSpanningTreeAlgorithm<T> mstBuilder
    ) {
        System.out.println("=== Optional Prim MST Experiment ===");
        MinimumSpanningTreeResult<T> mst = mstBuilder.buildTree(graph, start);
        System.out.println("Start vertex: " + mst.getStart());
        System.out.println("MST/search component edge count: " + mst.getTreeEdges().size());
        System.out.println("MST total weight: " + mst.getTotalWeight());
        System.out.println("Note: Prim connects a graph component with minimum total edge weight.");
        System.out.println("It is separate from the required shortest-path cases.");
    }

    private static void printRepresentationBenchmark(
            WeightedGraph<String> graph,
            Map<String, List<LocationCandidate>> topTargets
    ) {
        String source = topTargets.get("A").get(0).getLocationId();
        String destination = topTargets.get("A").get(9).getLocationId();
        new GraphRepresentationBenchmark().printComparison(graph, source, destination);
    }

    private static void printGlobalRouteOptimization(WeightedGraph<String> graph) {
        System.out.println("=== Advanced Global Route Optimization Experiment ===");

        GlobalRouteOptimizer optimizer = new GlobalRouteOptimizer(15);
        List<String> targets = Arrays.asList(
                "L0001", "L0002", "L0003", "L0004", "L0005",
                "L0101", "L0102", "L0103"
        );

        long startTime = System.nanoTime();
        GlobalRouteOptimizer.TspResult result = optimizer.solveTsp(graph, targets);
        long runtimeNs = System.nanoTime() - startTime;

        System.out.println("Targets: " + joinValues(targets, " -> "));
        System.out.println("Global route cost: " + formatCost(result.getTotalCost()));
        System.out.println("Visit order: " + result.formatVisitOrder());
        System.out.println("Runtime (ns): " + runtimeNs);
        System.out.println("Note: This is an extension using Floyd-Warshall + state-compression DP.");
        System.out.println();
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

    private static String formatCost(double cost) {
        if (Math.rint(cost) == cost) {
            return String.valueOf((long) cost);
        }
        return String.format("%.2f", cost);
    }

    private static <T> String joinValues(List<T> values, String delimiter) {
        List<String> labels = new ArrayList<>();
        for (T value : values) {
            labels.add(String.valueOf(value));
        }
        return String.join(delimiter, labels);
    }

    private static void openCaseVisualizers(
            WeightedGraph<String> graph,
            Map<String, List<LocationCandidate>> topTargets,
            Map<String, PathResult<String>> caseResults
    ) {
        List<String> important = new ArrayList<>();
        for (List<LocationCandidate> group : topTargets.values()) {
            for (LocationCandidate place : group) {
                important.add(place.getLocationId());
            }
        }

        SwingUtilities.invokeLater(() -> {
            int index = 0;
            for (Map.Entry<String, PathResult<String>> entry : caseResults.entrySet()) {
                PathResult<String> result = entry.getValue();
                JFrame frame = GraphVisualizer.showInFrame(graph, result.getPath(), important);
                frame.setTitle(entry.getKey() + " - " + result.getSource() + " to " + result.getDestination());
                frame.setLocation(40 + index * 45, 40 + index * 35);
                index++;
            }
        });
    }
}
