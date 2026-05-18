import java.util.ArrayList;
import java.util.List;

/**
 * Compares list and matrix graph representations using the same operations.
 */
public class GraphRepresentationBenchmark {
    private static final int WARMUP_RUNS = 1;
    private static final int MEASURED_RUNS = 5;

    private final GraphTraversalAlgorithm<String> listBfs = new BFSAlgorithm<>();
    private final GraphTraversalAlgorithm<String> listDfs = new DFSAlgorithm<>();
    private final PathFindingAlgorithm<String> listDijkstra = new DijkstraAlgorithm<>();
    private final MinimumSpanningTreeAlgorithm<String> listPrim = new PrimAlgorithm<>();

    private final MatrixBFSAlgorithm<String> matrixBfs = new MatrixBFSAlgorithm<>();
    private final MatrixDFSAlgorithm<String> matrixDfs = new MatrixDFSAlgorithm<>();
    private final MatrixDijkstraAlgorithm<String> matrixDijkstra = new MatrixDijkstraAlgorithm<>();
    private final MatrixPrimAlgorithm<String> matrixPrim = new MatrixPrimAlgorithm<>();

    public void printComparison(WeightedGraph<String> listGraph, String start, String destination) {
        System.out.println("=== Adjacency List vs Adjacency Matrix Experiment ===");

        long buildStart = System.nanoTime();
        AdjacencyMatrixGraph<String> matrixGraph = AdjacencyMatrixGraph.fromWeightedGraph(listGraph);
        long matrixBuildNs = System.nanoTime() - buildStart;

        System.out.println("Vertices: " + listGraph.vertexCount());
        System.out.println("Undirected edges: " + listGraph.edgeCount());
        System.out.println("Adjacency list edge records (approx.): " + (listGraph.edgeCount() * 2));
        System.out.println("Adjacency matrix cells: " + matrixGraph.matrixCellCount());
        System.out.println("Matrix build time (ns): " + matrixBuildNs);
        System.out.println();

        List<RepresentationBenchmarkRow> rows = new ArrayList<>();
        rows.add(compareBfs(listGraph, matrixGraph, start, true));
        rows.add(compareBfs(listGraph, matrixGraph, start, false));
        rows.add(compareDfs(listGraph, matrixGraph, start, true));
        rows.add(compareDfs(listGraph, matrixGraph, start, false));
        rows.add(compareDijkstra(listGraph, matrixGraph, start, destination, true));
        rows.add(compareDijkstra(listGraph, matrixGraph, start, destination, false));
        rows.add(comparePrim(listGraph, matrixGraph, start, true));
        rows.add(comparePrim(listGraph, matrixGraph, start, false));

        printRows(rows);
        System.out.println();
    }

    private RepresentationBenchmarkRow compareBfs(
            WeightedGraph<String> listGraph,
            AdjacencyMatrixGraph<String> matrixGraph,
            String start,
            boolean useList
    ) {
        ResultBox<TraversalResult<String>> result = new ResultBox<>();
        long average = measure(() -> result.value = useList
                ? listBfs.traverse(listGraph, start)
                : matrixBfs.traverse(matrixGraph, start));

        return new RepresentationBenchmarkRow(
                "BFS",
                useList ? "Adjacency List" : "Adjacency Matrix",
                average,
                "visited=" + result.value.getSearchOrder().size()
        );
    }

    private RepresentationBenchmarkRow compareDfs(
            WeightedGraph<String> listGraph,
            AdjacencyMatrixGraph<String> matrixGraph,
            String start,
            boolean useList
    ) {
        ResultBox<TraversalResult<String>> result = new ResultBox<>();
        long average = measure(() -> result.value = useList
                ? listDfs.traverse(listGraph, start)
                : matrixDfs.traverse(matrixGraph, start));

        return new RepresentationBenchmarkRow(
                "DFS",
                useList ? "Adjacency List" : "Adjacency Matrix",
                average,
                "visited=" + result.value.getSearchOrder().size()
        );
    }

    private RepresentationBenchmarkRow compareDijkstra(
            WeightedGraph<String> listGraph,
            AdjacencyMatrixGraph<String> matrixGraph,
            String source,
            String destination,
            boolean useList
    ) {
        ResultBox<PathResult<String>> result = new ResultBox<>();
        long average = measure(() -> result.value = useList
                ? listDijkstra.findPath(listGraph, source, destination)
                : matrixDijkstra.findPath(matrixGraph, source, destination));

        return new RepresentationBenchmarkRow(
                "Dijkstra",
                useList ? "Adjacency List" : "Adjacency Matrix",
                average,
                "cost=" + formatCost(result.value.getTotalCost()) + ", pathNodes=" + result.value.getPath().size()
        );
    }

    private RepresentationBenchmarkRow comparePrim(
            WeightedGraph<String> listGraph,
            AdjacencyMatrixGraph<String> matrixGraph,
            String start,
            boolean useList
    ) {
        ResultBox<MinimumSpanningTreeResult<String>> result = new ResultBox<>();
        long average = measure(() -> result.value = useList
                ? listPrim.buildTree(listGraph, start)
                : matrixPrim.buildTree(matrixGraph, start));

        return new RepresentationBenchmarkRow(
                "Prim MST",
                useList ? "Adjacency List" : "Adjacency Matrix",
                average,
                "weight=" + result.value.getTotalWeight() + ", edges=" + result.value.getTreeEdges().size()
        );
    }

    private long measure(Runnable runOnce) {
        for (int i = 0; i < WARMUP_RUNS; i++) {
            runOnce.run();
        }

        long total = 0L;
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            runOnce.run();
            total += System.nanoTime() - start;
        }
        return total / MEASURED_RUNS;
    }

    private void printRows(List<RepresentationBenchmarkRow> rows) {
        System.out.printf("%-12s %-18s %16s   %s%n", "Algorithm", "Representation", "Avg runtime(ns)", "Result");
        for (RepresentationBenchmarkRow row : rows) {
            System.out.printf(
                    "%-12s %-18s %16d   %s%n",
                    row.getAlgorithmName(),
                    row.getRepresentationName(),
                    row.getAverageRuntimeNs(),
                    row.getResultSummary()
            );
        }
    }

    private String formatCost(double cost) {
        if (Double.isInfinite(cost)) {
            return "UNREACHABLE";
        }
        if (Math.rint(cost) == cost) {
            return String.valueOf((long) cost);
        }
        return String.format("%.2f", cost);
    }

    private static class ResultBox<T> {
        private T value;
    }
}
