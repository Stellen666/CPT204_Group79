public class WeightedEdge<T extends Comparable<T>> implements Comparable<WeightedEdge<T>> {
    private final T from;
    private final T to;
    private final int weight;

    public WeightedEdge(T from, T to, int weight) {
        this.from = from;
        this.to = to;
        this.weight = weight;
    }

    public T getFrom() {
        return from;
    }

    public T getTo() {
        return to;
    }

    public int getWeight() {
        return weight;
    }

    @Override
    public int compareTo(WeightedEdge<T> other) {
        int byWeight = Integer.compare(this.weight, other.weight);
        if (byWeight != 0) {
            return byWeight;
        }
        int byFrom = this.from.compareTo(other.from);
        if (byFrom != 0) {
            return byFrom;
        }
        return this.to.compareTo(other.to);
    }

    @Override
    public String toString() {
        return from + " -> " + to + " (" + weight + ")";
    }
}
