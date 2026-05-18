import java.util.ArrayList;
import java.util.List;

public class MatrixPrimAlgorithm<T extends Comparable<T>> {
    public MinimumSpanningTreeResult<T> buildTree(AdjacencyMatrixGraph<T> graph, T start) {
        if (!graph.containsVertex(start)) {
            return new MinimumSpanningTreeResult<>(start, 0, new ArrayList<>());
        }

        int n = graph.vertexCount();
        int startIndex = graph.indexOf(start);
        boolean[] picked = new boolean[n];
        double[] bestWeight = new double[n];
        int[] parent = new int[n];

        for (int i = 0; i < n; i++) {
            bestWeight[i] = Double.POSITIVE_INFINITY;
            parent[i] = -1;
        }
        bestWeight[startIndex] = 0.0;

        for (int step = 0; step < n; step++) {
            int current = findCheapestUnused(bestWeight, picked);
            if (current == -1) {
                break;
            }

            picked[current] = true;
            for (int next = 0; next < n; next++) {
                double weight = graph.weightAt(current, next);
                if (!picked[next] && !Double.isInfinite(weight) && weight < bestWeight[next]) {
                    bestWeight[next] = weight;
                    parent[next] = current;
                }
            }
        }

        List<WeightedEdge<T>> treeEdges = new ArrayList<>();
        int totalWeight = 0;
        for (int child = 0; child < n; child++) {
            if (child == startIndex || parent[child] == -1) {
                continue;
            }

            int weight = (int) graph.weightAt(parent[child], child);
            treeEdges.add(new WeightedEdge<>(graph.vertexAt(parent[child]), graph.vertexAt(child), weight));
            totalWeight += weight;
        }

        return new MinimumSpanningTreeResult<>(start, totalWeight, treeEdges);
    }

    private int findCheapestUnused(double[] bestWeight, boolean[] picked) {
        int best = -1;
        for (int i = 0; i < bestWeight.length; i++) {
            if (!picked[i] && (best == -1 || bestWeight[i] < bestWeight[best])) {
                best = i;
            }
        }
        return best;
    }
}
