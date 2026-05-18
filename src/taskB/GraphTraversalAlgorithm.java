/**
 * Common shape for BFS, DFS, and any other traversal tried later.
 */
public interface GraphTraversalAlgorithm<T extends Comparable<T>> {
    TraversalResult<T> traverse(WeightedGraph<T> graph, T root);
}
