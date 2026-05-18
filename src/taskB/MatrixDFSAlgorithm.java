import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MatrixDFSAlgorithm<T extends Comparable<T>> {
    public TraversalResult<T> traverse(AdjacencyMatrixGraph<T> graph, T root) {
        long startTime = System.nanoTime();
        List<T> order = new ArrayList<>();
        Map<T, T> parent = new LinkedHashMap<>();

        if (!graph.containsVertex(root)) {
            return new TraversalResult<>(order, parent, System.nanoTime() - startTime);
        }

        Set<T> visited = new LinkedHashSet<>();
        parent.put(root, null);
        dfs(graph, root, visited, parent, order);

        return new TraversalResult<>(order, parent, System.nanoTime() - startTime);
    }

    private void dfs(
            AdjacencyMatrixGraph<T> graph,
            T here,
            Set<T> visited,
            Map<T, T> parent,
            List<T> order
    ) {
        visited.add(here);
        order.add(here);

        int row = graph.indexOf(here);
        for (int col = 0; col < graph.vertexCount(); col++) {
            if (row == col || Double.isInfinite(graph.weightAt(row, col))) {
                continue;
            }

            T next = graph.vertexAt(col);
            if (!visited.contains(next)) {
                parent.put(next, here);
                dfs(graph, next, visited, parent, order);
            }
        }
    }
}
