/**
 * Common shape for minimum-spanning-tree experiments.
 */
public interface MinimumSpanningTreeAlgorithm<T extends Comparable<T>> {
    MinimumSpanningTreeResult<T> buildTree(WeightedGraph<T> graph, T start);
}
