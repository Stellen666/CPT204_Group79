public class LocationCandidate implements Comparable<LocationCandidate> {
    private final String locationId;
    private final int priorityScore;

    public LocationCandidate(String locationId, int priorityScore) {
        this.locationId = locationId;
        this.priorityScore = priorityScore;
    }

    public String getLocationId() {
        return locationId;
    }

    public int getPriorityScore() {
        return priorityScore;
    }

    @Override
    public int compareTo(LocationCandidate other) {
        int scoreOrder = Integer.compare(other.priorityScore, this.priorityScore);
        if (scoreOrder != 0) {
            return scoreOrder;
        }
        return this.locationId.compareTo(other.locationId);
    }

    @Override
    public String toString() {
        return locationId + "(" + priorityScore + ")";
    }
}
