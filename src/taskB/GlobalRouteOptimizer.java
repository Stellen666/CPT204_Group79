import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalRouteOptimizer {
    private static final double INF = Double.POSITIVE_INFINITY;
    private static final int DEFAULT_MAX_TARGETS = 20;

    private final int maxTargets;
    private List<String> vertexIds = new ArrayList<>();
    private Map<String, Integer> vertexIndex = new HashMap<>();

    public GlobalRouteOptimizer() {
        this(DEFAULT_MAX_TARGETS);
    }

    public GlobalRouteOptimizer(int maxTargets) {
        if (maxTargets < 2 || maxTargets > 20) {
            throw new IllegalArgumentException("Exact DP is intended for 2 to 20 target vertices.");
        }
        this.maxTargets = maxTargets;
    }

    public List<String> getVertexIds() {
        return Collections.unmodifiableList(vertexIds);
    }

    public int getIndexOf(String vertexId) {
        Integer index = vertexIndex.get(vertexId);
        if (index == null) {
            throw new IllegalArgumentException("Unknown vertex: " + vertexId);
        }
        return index;
    }

    public double[][] computeAllPairsShortestPath(WeightedGraph<String> graph) {
        rebuildVertexIndex(graph);

        int n = vertexIds.size();
        double[][] dist = new double[n][n];

        for (int i = 0; i < n; i++) {
            Arrays.fill(dist[i], INF);
            dist[i][i] = 0.0;
        }

        for (WeightedEdge<String> edge : graph.edges()) {
            int from = vertexIndex.get(edge.getFrom());
            int to = vertexIndex.get(edge.getTo());
            double weight = edge.getWeight();

            if (weight < dist[from][to]) {
                dist[from][to] = weight;
                dist[to][from] = weight;
            }
        }

        // Try every middle vertex once; this is the usual Floyd-Warshall update.
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                if (Double.isInfinite(dist[i][k])) {
                    continue;
                }

                for (int j = 0; j < n; j++) {
                    double throughK = dist[i][k] + dist[k][j];
                    if (throughK < dist[i][j]) {
                        dist[i][j] = throughK;
                    }
                }
            }
        }

        return dist;
    }

    public TspResult solveTsp(WeightedGraph<String> graph, List<String> targetIds) {
        double[][] allPairs = computeAllPairsShortestPath(graph);
        return solveTsp(targetIds, allPairs);
    }

    public TspResult solveTsp(List<String> targetIds, double[][] allPairsDistances) {
        validateTargets(targetIds, allPairsDistances);

        int n = targetIds.size();
        double[][] targetCost = buildTargetDistanceMatrix(targetIds, allPairsDistances);

        int stateCount = 1 << n;
        double[][] bestCost = new double[stateCount][n];
        int[][] parent = new int[stateCount][n];

        for (int mask = 0; mask < stateCount; mask++) {
            Arrays.fill(bestCost[mask], INF);
            Arrays.fill(parent[mask], -1);
        }

        int start = 0;
        int startMask = 1 << start;
        bestCost[startMask][start] = 0.0;

        for (int mask = 0; mask < stateCount; mask++) {
            if ((mask & startMask) == 0) {
                continue;
            }

            for (int u = 0; u < n; u++) {
                if ((mask & (1 << u)) == 0 || Double.isInfinite(bestCost[mask][u])) {
                    continue;
                }

                for (int v = 0; v < n; v++) {
                    if ((mask & (1 << v)) != 0) {
                        continue;
                    }

                    int nextMask = mask | (1 << v);
                    double candidateCost = bestCost[mask][u] + targetCost[u][v];

                    if (candidateCost < bestCost[nextMask][v]) {
                        bestCost[nextMask][v] = candidateCost;
                        parent[nextMask][v] = u;
                    }
                }
            }
        }

        int fullMask = stateCount - 1;
        double routeCost = INF;
        int last = -1;

        for (int u = 1; u < n; u++) {
            double loopCost = bestCost[fullMask][u] + targetCost[u][start];
            if (loopCost < routeCost) {
                routeCost = loopCost;
                last = u;
            }
        }

        if (last == -1 || Double.isInfinite(routeCost)) {
            return TspResult.unreachable(targetIds);
        }

        List<String> order = rebuildTspOrder(targetIds, parent, fullMask, last);
        order.add(targetIds.get(start));

        return new TspResult(routeCost, order, true);
    }

    private void rebuildVertexIndex(WeightedGraph<String> graph) {
        vertexIds = new ArrayList<>(graph.vertices());
        Collections.sort(vertexIds);

        vertexIndex = new HashMap<>();
        for (int i = 0; i < vertexIds.size(); i++) {
            vertexIndex.put(vertexIds.get(i), i);
        }
    }

    private void validateTargets(List<String> targetIds, double[][] allPairsDistances) {
        if (targetIds == null || targetIds.size() < 2) {
            throw new IllegalArgumentException("At least two target vertices are required for TSP.");
        }
        if (targetIds.size() > maxTargets) {
            throw new IllegalArgumentException(
                    "Too many targets for exact DP: " + targetIds.size() + ". Limit is " + maxTargets + "."
            );
        }
        if (allPairsDistances == null || allPairsDistances.length != vertexIds.size()) {
            throw new IllegalArgumentException("All-pairs matrix does not match the current graph index.");
        }

        for (String target : targetIds) {
            if (!vertexIndex.containsKey(target)) {
                throw new IllegalArgumentException("Target vertex is not in the graph: " + target);
            }
        }
    }

    private double[][] buildTargetDistanceMatrix(List<String> targetIds, double[][] allPairsDistances) {
        int n = targetIds.size();
        double[][] targetDist = new double[n][n];

        for (int i = 0; i < n; i++) {
            int graphI = vertexIndex.get(targetIds.get(i));
            for (int j = 0; j < n; j++) {
                int graphJ = vertexIndex.get(targetIds.get(j));
                targetDist[i][j] = allPairsDistances[graphI][graphJ];
            }
        }

        return targetDist;
    }

    private List<String> rebuildTspOrder(List<String> targetIds, int[][] parent, int mask, int last) {
        List<String> reverseOrder = new ArrayList<>();
        int current = last;
        int currentMask = mask;

        while (current != -1) {
            reverseOrder.add(targetIds.get(current));
            int previous = parent[currentMask][current];

            currentMask = currentMask & ~(1 << current);
            current = previous;
        }

        Collections.reverse(reverseOrder);
        return reverseOrder;
    }

    public static class TspResult {
        private final double totalCost;
        private final List<String> visitOrder;
        private final boolean reachable;

        public TspResult(double totalCost, List<String> visitOrder, boolean reachable) {
            this.totalCost = totalCost;
            this.visitOrder = new ArrayList<>(visitOrder);
            this.reachable = reachable;
        }

        public static TspResult unreachable(List<String> targetIds) {
            return new TspResult(INF, targetIds == null ? Collections.emptyList() : targetIds, false);
        }

        public double getTotalCost() {
            return totalCost;
        }

        public List<String> getVisitOrder() {
            return Collections.unmodifiableList(visitOrder);
        }

        public boolean isReachable() {
            return reachable;
        }

        public String formatVisitOrder() {
            if (!reachable) {
                return "UNREACHABLE";
            }
            return String.join(" -> ", visitOrder);
        }
    }
}
