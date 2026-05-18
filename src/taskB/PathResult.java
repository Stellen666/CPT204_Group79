import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Returned by shortest-path methods: path, cost, and optional timing.
 */
public class PathResult<T> {
    private final T source;
    private final T destination;
    private final double totalCost;
    private final long runtimeNs;
    private final List<T> path;

    public PathResult(T source, T destination, double totalCost, List<T> path) {
        this(source, destination, totalCost, path, 0L);
    }

    public PathResult(T source, T destination, double totalCost, List<T> path, long runtimeNs) {
        this.source = source;
        this.destination = destination;
        this.totalCost = totalCost;
        this.runtimeNs = runtimeNs;
        this.path = new ArrayList<>(path);
    }

    public static <T> PathResult<T> unreachable(T source, T destination) {
        return new PathResult<>(source, destination, Double.POSITIVE_INFINITY, Collections.emptyList());
    }

    public static <T> PathResult<T> unreachable(T source, T destination, long runtimeNs) {
        return new PathResult<>(source, destination, Double.POSITIVE_INFINITY, Collections.emptyList(), runtimeNs);
    }

    public T getSource() {
        return source;
    }

    public T getDestination() {
        return destination;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public long getRuntimeNs() {
        return runtimeNs;
    }

    public List<T> getPath() {
        return Collections.unmodifiableList(path);
    }

    public boolean isReachable() {
        return !Double.isInfinite(totalCost);
    }

    public String formatPath() {
        if (!isReachable()) {
            return "UNREACHABLE";
        }
        List<String> labels = new ArrayList<>();
        for (T vertex : path) {
            labels.add(String.valueOf(vertex));
        }
        return String.join(" -> ", labels);
    }
}
