import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Breadth-first traversal. It also keeps the parent links, which are useful
 * when checking the search tree in the report.
 */
public class BFSAlgorithm<T extends Comparable<T>> implements GraphTraversalAlgorithm<T> {
    @Override
    public TraversalResult<T> traverse(WeightedGraph<T> graph, T root) {
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

            for (WeightedEdge<T> edge : graph.neighborsOf(here)) {
                T next = edge.getTo();
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
