import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Dijkstra shortest path implementation used for the required route cases.
 */
public class DijkstraAlgorithm<T extends Comparable<T>> implements PathFindingAlgorithm<T> {
    @Override
    public PathResult<T> findPath(WeightedGraph<T> graph, T source, T destination) {
        long startTime = System.nanoTime();

        if (source.equals(destination)) {
            List<T> path = new ArrayList<>();
            path.add(source);
            return new PathResult<>(source, destination, 0.0, path, System.nanoTime() - startTime);
        }

        if (!graph.containsVertex(source) || !graph.containsVertex(destination)) {
            return PathResult.unreachable(source, destination, System.nanoTime() - startTime);
        }

        Map<T, Double> distanceFromStart = new HashMap<>();
        Map<T, T> previousVertex = new HashMap<>();
        Set<T> settled = new HashSet<>();
        PriorityQueue<NodeDistance<T>> waiting = new PriorityQueue<>();

        distanceFromStart.put(source, 0.0);
        waiting.add(new NodeDistance<>(source, 0.0));

        while (!waiting.isEmpty()) {
            NodeDistance<T> current = waiting.remove();
            if (settled.contains(current.vertex)) {
                continue;
            }

            settled.add(current.vertex);
            if (current.vertex.equals(destination)) {
                break;
            }

            for (WeightedEdge<T> edge : graph.neighborsOf(current.vertex)) {
                T next = edge.getTo();
                double candidateDistance = current.distance + edge.getWeight();

                if (candidateDistance < distanceFromStart.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    distanceFromStart.put(next, candidateDistance);
                    previousVertex.put(next, current.vertex);
                    waiting.add(new NodeDistance<>(next, candidateDistance));
                }
            }
        }

        long runtime = System.nanoTime() - startTime;
        if (!distanceFromStart.containsKey(destination)) {
            return PathResult.unreachable(source, destination, runtime);
        }

        return new PathResult<>(
                source,
                destination,
                distanceFromStart.get(destination),
                buildPath(source, destination, previousVertex),
                runtime
        );
    }

    private List<T> buildPath(T source, T destination, Map<T, T> prev) {
        List<T> path = new ArrayList<>();
        T current = destination;

        while (current != null) {
            path.add(current);
            if (current.equals(source)) {
                break;
            }
            current = prev.get(current);
        }

        Collections.reverse(path);
        return path;
    }

    private static class NodeDistance<T extends Comparable<T>> implements Comparable<NodeDistance<T>> {
        private final T vertex;
        private final double distance;

        private NodeDistance(T vertex, double distance) {
            this.vertex = vertex;
            this.distance = distance;
        }

        @Override
        public int compareTo(NodeDistance<T> other) {
            int byDistance = Double.compare(this.distance, other.distance);
            if (byDistance != 0) {
                return byDistance;
            }
            return this.vertex.compareTo(other.vertex);
        }
    }
}
