import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.Set;

/**
 * A few graph checks that do not really belong inside the storage class.
 */
public class GraphAnalyzer {
    public <T extends Comparable<T>> int countConnectedComponents(WeightedGraph<T> graph) {
        Set<T> alreadySeen = new HashSet<>();
        int components = 0;

        for (T vertex : graph.vertices()) {
            if (alreadySeen.contains(vertex)) {
                continue;
            }

            components++;
            markComponent(graph, vertex, alreadySeen);
        }

        return components;
    }

    private <T extends Comparable<T>> void markComponent(WeightedGraph<T> graph, T start, Set<T> visited) {
        Queue<T> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            T here = queue.remove();
            for (WeightedEdge<T> edge : graph.neighborsOf(here)) {
                T next = edge.getTo();
                if (!visited.contains(next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
    }

    public <T extends Comparable<T>> Set<T> collectMissingVertices(WeightedGraph<T> graph, Iterable<T> vertexIds) {
        Set<T> missing = new LinkedHashSet<>();
        for (T vertexId : vertexIds) {
            if (!graph.containsVertex(vertexId)) {
                missing.add(vertexId);
            }
        }
        return missing;
    }
}
