/**
 * Common shape for shortest-path implementations.
 */
public interface PathFindingAlgorithm<T extends Comparable<T>> {
    PathResult<T> findPath(WeightedGraph<T> graph, T source, T destination);
}
