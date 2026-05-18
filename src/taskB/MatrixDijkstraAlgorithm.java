import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MatrixDijkstraAlgorithm<T extends Comparable<T>> {
    public PathResult<T> findPath(AdjacencyMatrixGraph<T> graph, T source, T destination) {
        long startTime = System.nanoTime();

        if (!graph.containsVertex(source) || !graph.containsVertex(destination)) {
            return PathResult.unreachable(source, destination, System.nanoTime() - startTime);
        }
        if (source.equals(destination)) {
            List<T> path = new ArrayList<>();
            path.add(source);
            return new PathResult<>(source, destination, 0.0, path, System.nanoTime() - startTime);
        }

        int n = graph.vertexCount();
        int sourceIndex = graph.indexOf(source);
        int destinationIndex = graph.indexOf(destination);
        double[] dist = new double[n];
        int[] prev = new int[n];
        boolean[] used = new boolean[n];

        for (int i = 0; i < n; i++) {
            dist[i] = Double.POSITIVE_INFINITY;
            prev[i] = -1;
        }
        dist[sourceIndex] = 0.0;

        for (int step = 0; step < n; step++) {
            int current = findNearestUnused(dist, used);
            if (current == -1 || current == destinationIndex) {
                break;
            }

            used[current] = true;
            for (int next = 0; next < n; next++) {
                double weight = graph.weightAt(current, next);
                if (used[next] || Double.isInfinite(weight)) {
                    continue;
                }

                double newDist = dist[current] + weight;
                if (newDist < dist[next]) {
                    dist[next] = newDist;
                    prev[next] = current;
                }
            }
        }

        long runtime = System.nanoTime() - startTime;
        if (Double.isInfinite(dist[destinationIndex])) {
            return PathResult.unreachable(source, destination, runtime);
        }

        return new PathResult<>(
                source,
                destination,
                dist[destinationIndex],
                rebuildPath(graph, sourceIndex, destinationIndex, prev),
                runtime
        );
    }

    private int findNearestUnused(double[] dist, boolean[] used) {
        int best = -1;
        for (int i = 0; i < dist.length; i++) {
            if (!used[i] && (best == -1 || dist[i] < dist[best])) {
                best = i;
            }
        }
        return best;
    }

    private List<T> rebuildPath(AdjacencyMatrixGraph<T> graph, int source, int destination, int[] prev) {
        List<T> path = new ArrayList<>();
        int current = destination;

        while (current != -1) {
            path.add(graph.vertexAt(current));
            if (current == source) {
                break;
            }
            current = prev[current];
        }

        Collections.reverse(path);
        return path;
    }
}
