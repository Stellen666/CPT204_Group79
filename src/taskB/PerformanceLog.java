/**
 * Timing record used by AlgorithmEvaluator.
 */
public class PerformanceLog<T> {
    private final T algorithm;
    private final String algorithmName;
    private final long runtimeNs;

    public PerformanceLog(T algorithm, String algorithmName, long runtimeNs) {
        this.algorithm = algorithm;
        this.algorithmName = algorithmName;
        this.runtimeNs = runtimeNs;
    }

    public T getAlgorithm() {
        return algorithm;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public long getRuntimeNs() {
        return runtimeNs;
    }

    @Override
    public String toString() {
        return algorithmName + " runtime: " + runtimeNs + " ns";
    }
}
