public class RepresentationBenchmarkRow {
    private final String algorithmName;
    private final String representationName;
    private final long averageRuntimeNs;
    private final String resultSummary;

    public RepresentationBenchmarkRow(
            String algorithmName,
            String representationName,
            long averageRuntimeNs,
            String resultSummary
    ) {
        this.algorithmName = algorithmName;
        this.representationName = representationName;
        this.averageRuntimeNs = averageRuntimeNs;
        this.resultSummary = resultSummary;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public String getRepresentationName() {
        return representationName;
    }

    public long getAverageRuntimeNs() {
        return averageRuntimeNs;
    }

    public String getResultSummary() {
        return resultSummary;
    }
}
