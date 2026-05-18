import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CandidateLoader {
    public List<LocationCandidate> readCandidates(Path csvPath) throws IOException {
        List<LocationCandidate> list = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String header = reader.readLine();
            if (header == null) {
                return list;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length != 2) {
                    throw new IOException("Invalid candidate row: " + line);
                }
                String locationId = parts[0].trim();
                int priorityScore = Integer.parseInt(parts[1].trim());
                list.add(new LocationCandidate(locationId, priorityScore));
            }
        }
        return list;
    }

    public List<LocationCandidate> selectTopK(Path csvPath, int k) throws IOException {
        ArrayList<Candidate> candidates = Main.loadCandidates(csvPath.toString());
        ArrayList<Candidate> sortedCandidates = Main.copyList(candidates);
        MergeSort.sort(sortedCandidates);

        int end = Math.min(k, sortedCandidates.size());
        List<LocationCandidate> topCandidates = new ArrayList<>();
        for (int i = 0; i < end; i++) {
            Candidate candidate = sortedCandidates.get(i);
            topCandidates.add(new LocationCandidate(candidate.locationId, candidate.priorityScore));
        }
        return topCandidates;
    }
}
