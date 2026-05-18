import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores a traversal order together with its parent links.
 */
public class TraversalResult<T> {
    private final List<T> searchOrder;
    private final Map<T, T> parent;
    private final long runtimeNs;

    public TraversalResult(List<T> searchOrder, Map<T, T> parent) {
        this(searchOrder, parent, 0L);
    }

    public TraversalResult(List<T> searchOrder, Map<T, T> parent, long runtimeNs) {
        this.searchOrder = new ArrayList<>(searchOrder);
        this.parent = new LinkedHashMap<>(parent);
        this.runtimeNs = runtimeNs;
    }

    public List<T> getSearchOrder() {
        return Collections.unmodifiableList(searchOrder);
    }

    public Map<T, T> getParent() {
        return Collections.unmodifiableMap(parent);
    }

    public long getRuntimeNs() {
        return runtimeNs;
    }
}
