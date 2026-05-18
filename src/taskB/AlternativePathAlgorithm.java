/**
 * A placeholder for trying another path finder later.
 * Since the dataset has no coordinates, it currently falls back to Dijkstra.
 */
public class AlternativePathAlgorithm<T extends Comparable<T>> implements PathFindingAlgorithm<T> {
    private final PathFindingAlgorithm<T> dijkstra = new DijkstraAlgorithm<>();

    @Override
    public PathResult<T> findPath(WeightedGraph<T> graph, T source, T destination) {
        return dijkstra.findPath(graph, source, destination);
    }
}
