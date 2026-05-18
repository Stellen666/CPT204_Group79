/**
 * Small helper for timing different algorithm objects in the same way.
 */
public class AlgorithmEvaluator<T> {
    public PerformanceLog<T> evaluate(T algorithm, Runnable runOnce) {
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm cannot be null.");
        }
        if (runOnce == null) {
            throw new IllegalArgumentException("Task cannot be null.");
        }

        long start = System.nanoTime();
        runOnce.run();
        long elapsedNs = System.nanoTime() - start;

        return new PerformanceLog<>(algorithm, algorithm.getClass().getSimpleName(), elapsedNs);
    }
}
