import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Stores the road network as an undirected weighted graph.
 * Algorithm classes read from this class, but the graph itself only looks after
 * vertices, edges, and neighbour lists.
 */
public class WeightedGraph<T extends Comparable<T>> {
    private final Map<T, List<WeightedEdge<T>>> neighbours = new TreeMap<>();
    private int edgeRows;

    public static WeightedGraph<String> fromCsv(Path csvPath) throws IOException {
        WeightedGraph<String> graph = new WeightedGraph<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String header = reader.readLine();
            if (header == null) {
                return graph;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length != 3) {
                    throw new IOException("Invalid path row: " + line);
                }
                String from = parts[0].trim();
                String to = parts[1].trim();
                int weight = Integer.parseInt(parts[2].trim());
                graph.addUndirectedEdge(from, to, weight);
            }
        }
        graph.sortNeighborLists();
        return graph;
    }

    public void addUndirectedEdge(T from, T to, int weight) {
        addDirectedEdge(from, to, weight);
        addDirectedEdge(to, from, weight);
        edgeRows++;
    }

    private void addDirectedEdge(T from, T to, int weight) {
        neighbours.computeIfAbsent(from, key -> new ArrayList<>()).add(new WeightedEdge<>(from, to, weight));
        neighbours.computeIfAbsent(to, key -> new ArrayList<>());
    }

    private void sortNeighborLists() {
        for (List<WeightedEdge<T>> list : neighbours.values()) {
            list.sort((left, right) -> {
                int byTo = left.getTo().compareTo(right.getTo());
                if (byTo != 0) {
                    return byTo;
                }
                return Integer.compare(left.getWeight(), right.getWeight());
            });
        }
    }

    public int vertexCount() {
        return neighbours.size();
    }

    public int edgeCount() {
        return edgeRows;
    }

    public boolean containsVertex(T vertex) {
        return neighbours.containsKey(vertex);
    }

    public Set<T> vertices() {
        return Collections.unmodifiableSet(neighbours.keySet());
    }

    public List<WeightedEdge<T>> edges() {
        List<WeightedEdge<T>> uniqueEdges = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (List<WeightedEdge<T>> list : neighbours.values()) {
            for (WeightedEdge<T> edge : list) {
                T first = edge.getFrom().compareTo(edge.getTo()) <= 0 ? edge.getFrom() : edge.getTo();
                T second = edge.getFrom().compareTo(edge.getTo()) <= 0 ? edge.getTo() : edge.getFrom();
                String key = first + "|" + second + "|" + edge.getWeight();

                if (seenKeys.add(key)) {
                    uniqueEdges.add(new WeightedEdge<>(first, second, edge.getWeight()));
                }
            }
        }

        Collections.sort(uniqueEdges);
        return Collections.unmodifiableList(uniqueEdges);
    }

    public List<WeightedEdge<T>> neighborsOf(T vertex) {
        List<WeightedEdge<T>> list = neighbours.get(vertex);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }
}
