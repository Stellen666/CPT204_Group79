import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Prim's algorithm for the optional minimum spanning tree experiment.
 */
public class PrimAlgorithm<T extends Comparable<T>> implements MinimumSpanningTreeAlgorithm<T> {
    @Override
    public MinimumSpanningTreeResult<T> buildTree(WeightedGraph<T> graph, T start) {
        List<WeightedEdge<T>> treeEdges = new ArrayList<>();
        if (!graph.containsVertex(start)) {
            return new MinimumSpanningTreeResult<>(start, 0, treeEdges);
        }

        Set<T> inTree = new HashSet<>();
        PriorityQueue<WeightedEdge<T>> edgeChoices = new PriorityQueue<>();
        int totalWeight = 0;

        inTree.add(start);
        edgeChoices.addAll(graph.neighborsOf(start));

        while (!edgeChoices.isEmpty()) {
            WeightedEdge<T> edge = edgeChoices.remove();
            if (inTree.contains(edge.getTo())) {
                continue;
            }

            inTree.add(edge.getTo());
            treeEdges.add(edge);
            totalWeight += edge.getWeight();

            for (WeightedEdge<T> next : graph.neighborsOf(edge.getTo())) {
                if (!inTree.contains(next.getTo())) {
                    edgeChoices.add(next);
                }
            }
        }

        return new MinimumSpanningTreeResult<>(start, totalWeight, treeEdges);
    }
}
