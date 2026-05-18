import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Matrix version of the graph, used only for the representation comparison.
 */
public class AdjacencyMatrixGraph<T extends Comparable<T>> {
    public static final double INF = Double.POSITIVE_INFINITY;

    private final List<T> vertices;
    private final Map<T, Integer> indexByVertex;
    private final double[][] matrix;
    private final int edgeCount;

    private AdjacencyMatrixGraph(List<T> vertices, double[][] matrix, int edgeCount) {
        this.vertices = new ArrayList<>(vertices);
        this.matrix = matrix;
        this.edgeCount = edgeCount;
        this.indexByVertex = new HashMap<>();

        for (int i = 0; i < vertices.size(); i++) {
            indexByVertex.put(vertices.get(i), i);
        }
    }

    public static <T extends Comparable<T>> AdjacencyMatrixGraph<T> fromWeightedGraph(WeightedGraph<T> graph) {
        List<T> vertices = new ArrayList<>(graph.vertices());
        Collections.sort(vertices);

        Map<T, Integer> index = new HashMap<>();
        for (int i = 0; i < vertices.size(); i++) {
            index.put(vertices.get(i), i);
        }

        int n = vertices.size();
        double[][] matrix = new double[n][n];
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                matrix[row][col] = row == col ? 0.0 : INF;
            }
        }

        for (WeightedEdge<T> edge : graph.edges()) {
            int from = index.get(edge.getFrom());
            int to = index.get(edge.getTo());
            double weight = edge.getWeight();

            if (weight < matrix[from][to]) {
                matrix[from][to] = weight;
                matrix[to][from] = weight;
            }
        }

        return new AdjacencyMatrixGraph<>(vertices, matrix, graph.edgeCount());
    }

    public int vertexCount() {
        return vertices.size();
    }

    public int edgeCount() {
        return edgeCount;
    }

    public int matrixCellCount() {
        return vertices.size() * vertices.size();
    }

    public boolean containsVertex(T vertex) {
        return indexByVertex.containsKey(vertex);
    }

    public List<T> vertices() {
        return Collections.unmodifiableList(vertices);
    }

    public int indexOf(T vertex) {
        Integer index = indexByVertex.get(vertex);
        if (index == null) {
            throw new IllegalArgumentException("Unknown vertex: " + vertex);
        }
        return index;
    }

    public T vertexAt(int index) {
        return vertices.get(index);
    }

    public double weightAt(int fromIndex, int toIndex) {
        return matrix[fromIndex][toIndex];
    }
}
