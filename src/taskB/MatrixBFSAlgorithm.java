import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class MatrixBFSAlgorithm<T extends Comparable<T>> {
    public TraversalResult<T> traverse(AdjacencyMatrixGraph<T> graph, T root) {
        long startTime = System.nanoTime();
        List<T> order = new ArrayList<>();
        Map<T, T> parent = new LinkedHashMap<>();

        if (!graph.containsVertex(root)) {
            return new TraversalResult<>(order, parent, System.nanoTime() - startTime);
        }

        Set<T> visited = new LinkedHashSet<>();
        Queue<T> queue = new ArrayDeque<>();
        visited.add(root);
        parent.put(root, null);
        queue.add(root);

        while (!queue.isEmpty()) {
            T here = queue.remove();
            order.add(here);
            int row = graph.indexOf(here);

            for (int col = 0; col < graph.vertexCount(); col++) {
                if (row == col || Double.isInfinite(graph.weightAt(row, col))) {
                    continue;
                }

                T next = graph.vertexAt(col);
                if (!visited.contains(next)) {
                    visited.add(next);
                    parent.put(next, here);
                    queue.add(next);
                }
            }
        }

        return new TraversalResult<>(order, parent, System.nanoTime() - startTime);
    }
}
