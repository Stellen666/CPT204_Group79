import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recursive depth-first traversal with parent links for the discovered tree.
 */
public class DFSAlgorithm<T extends Comparable<T>> implements GraphTraversalAlgorithm<T> {
    @Override
    public TraversalResult<T> traverse(WeightedGraph<T> graph, T root) {
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
            WeightedGraph<T> graph,
            T here,
            Set<T> visited,
            Map<T, T> parent,
            List<T> order
    ) {
        visited.add(here);
        order.add(here);

        for (WeightedEdge<T> edge : graph.neighborsOf(here)) {
            T next = edge.getTo();
            if (!visited.contains(next)) {
                parent.put(next, here);
                dfs(graph, next, visited, parent, order);
            }
        }
    }
}
