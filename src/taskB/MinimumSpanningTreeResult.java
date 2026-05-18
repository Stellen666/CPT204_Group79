import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MinimumSpanningTreeResult<T extends Comparable<T>> {
    private final T start;
    private final int totalWeight;
    private final List<WeightedEdge<T>> treeEdges;

    public MinimumSpanningTreeResult(T start, int totalWeight, List<WeightedEdge<T>> treeEdges) {
        this.start = start;
        this.totalWeight = totalWeight;
        this.treeEdges = new ArrayList<>(treeEdges);
    }

    public T getStart() {
        return start;
    }

    public int getTotalWeight() {
        return totalWeight;
    }

    public List<WeightedEdge<T>> getTreeEdges() {
        return Collections.unmodifiableList(treeEdges);
    }
}
