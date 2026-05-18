import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static final int WARMUP_RUNS = 5;
    public static final int MEASURED_RUNS = 10;

    public static void main(String[] args) {
        String[] files = {"candidates_A.csv", "candidates_B.csv", "candidates_C.csv"};
        List<BenchmarkResult> results = new ArrayList<>();
        Path dataFolder;

        try {
            dataFolder = findDataFolder(args);
        } catch (IOException e) {
            System.out.println("Error finding data folder: " + e.getMessage());
            return;
        }

        System.out.println("Task A - Sorting Performance Summary");
        System.out.println("Ranking rule: priority_score descending, then location_id ascending");
        System.out.println("Iteration log: CSV loading, benchmark runs, and best-time improvements are printed below.");
        System.out.println();

        for (String file : files) {
            try {
                Path dataFile = dataFolder.resolve(file);
                ArrayList<Candidate> originalList = loadCandidates(dataFile);
                System.out.println("=== Iteration for " + file + " ===");
                System.out.printf("[CSV] OK: %s loaded, rows=%d%n", dataFile.toAbsolutePath(), originalList.size());

                long bubbleTime = averageBubbleTime(originalList, MEASURED_RUNS);
                long quickTime = averageQuickTime(originalList, MEASURED_RUNS);
                long quickMedianThreeTime = averageQuickMedianThreeTime(originalList, MEASURED_RUNS);
                long mergeTime = averageMergeTime(originalList, MEASURED_RUNS);
                results.add(new BenchmarkResult(file, bubbleTime, quickTime, quickMedianThreeTime, mergeTime));

                ArrayList<Candidate> sortedList = copyList(originalList);
                MergeSort.sort(sortedList);

                System.out.println("[SUMMARY] Average times recorded for " + file);
                System.out.println("Top 10 of " + file + ": " + topTenToString(sortedList));
                System.out.println("[CHECK] Sorted result valid: " + isSorted(sortedList));
                System.out.println();
            } catch (IOException e) {
                System.out.println("Error reading " + file + ": " + e.getMessage());
            }
        }

        if (!results.isEmpty()) {
            printSummaryTable(results);
            BenchmarkChartFrame.showChart(results);
        }
    }

    public static void printSummaryTable(List<BenchmarkResult> results) {
        System.out.println("=== Final Performance Summary ===");
        System.out.printf("%-18s %15s %15s %20s %15s%n",
                "Dataset", "Bubble (ns)", "Quick (ns)", "Quick M3 (ns)", "Merge (ns)");
        for (BenchmarkResult result : results) {
            System.out.printf("%-18s %15s %15s %20s %15s%n",
                    result.datasetName,
                    formatTime(result.bubbleTime),
                    formatTime(result.quickTime),
                    formatTime(result.quickMedianThreeTime),
                    formatTime(result.mergeTime));
        }
    }

    public static String formatTime(long time) {
        return String.format("%,d", time);
    }

    public static Path findDataFolder(String[] args) throws IOException {
        if (args.length > 0) {
            Path givenFolder = Path.of(args[0]);
            if (Files.isDirectory(givenFolder)) {
                return givenFolder;
            }
            throw new IOException("Dataset folder not found: " + givenFolder.toAbsolutePath());
        }

        Path currentFolder = Path.of("").toAbsolutePath();
        while (currentFolder != null) {
            Path dataFolder = currentFolder.resolve("data");
            if (hasCandidateFiles(dataFolder)) {
                return dataFolder;
            }

            Path nestedProjectDataFolder = currentFolder.resolve("cpt204").resolve("data");
            if (hasCandidateFiles(nestedProjectDataFolder)) {
                return nestedProjectDataFolder;
            }

            currentFolder = currentFolder.getParent();
        }

        throw new IOException("Could not find data folder. Run from the project root or pass the data folder path as an argument.");
    }

    public static boolean hasCandidateFiles(Path dataFolder) {
        return Files.isDirectory(dataFolder)
                && Files.isRegularFile(dataFolder.resolve("candidates_A.csv"))
                && Files.isRegularFile(dataFolder.resolve("candidates_B.csv"))
                && Files.isRegularFile(dataFolder.resolve("candidates_C.csv"));
    }

    public static ArrayList<Candidate> loadCandidates(Path fileName) throws IOException {
        ArrayList<Candidate> list = new ArrayList<>();
        BufferedReader reader = Files.newBufferedReader(fileName);
        String line = reader.readLine();

        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                String[] parts = line.split(",");
                String locationId = parts[0].trim();
                int priorityScore = Integer.parseInt(parts[1].trim());
                list.add(new Candidate(locationId, priorityScore));
            }
        }

        reader.close();
        return list;
    }

    public static ArrayList<Candidate> loadCandidates(String fileName) throws IOException {
        return loadCandidates(Path.of(fileName));
    }

    public static long averageBubbleTime(ArrayList<Candidate> originalList, int runs) {
        warmUpBubble(originalList, WARMUP_RUNS);
        return measureAverageTime("Bubble Sort", originalList, runs, BubbleSort::sort);
    }

    public static long averageQuickTime(ArrayList<Candidate> originalList, int runs) {
        warmUpQuick(originalList, WARMUP_RUNS);
        return measureAverageTime("Quick Sort", originalList, runs, QuickSort::sort);
    }

    public static long averageMergeTime(ArrayList<Candidate> originalList, int runs) {
        warmUpMerge(originalList, WARMUP_RUNS);
        return measureAverageTime("Merge Sort", originalList, runs, MergeSort::sort);
    }

    public static long averageQuickMedianThreeTime(ArrayList<Candidate> originalList, int runs) {
        warmUpQuickMedianThree(originalList, WARMUP_RUNS);
        return measureAverageTime("Quick Sort Median-of-Three", originalList, runs, QuickSortMedianThree::sort);
    }

    public static long measureAverageTime(String algorithmName, ArrayList<Candidate> originalList, int runs, SortAction sorter) {
        System.out.printf("[PERF] %s warm-up complete: %d runs%n", algorithmName, WARMUP_RUNS);
        long total = 0;
        long bestTime = Long.MAX_VALUE;
        for (int i = 0; i < runs; i++) {
            ArrayList<Candidate> list = copyList(originalList);
            long start = System.nanoTime();
            sorter.sort(list);
            long end = System.nanoTime();
            long elapsedTime = end - start;
            total += elapsedTime;

            if (elapsedTime < bestTime) {
                if (bestTime == Long.MAX_VALUE) {
                    System.out.printf("[PERF] %s run %02d/%02d: %,d ns (initial best)%n",
                            algorithmName, i + 1, runs, elapsedTime);
                } else {
                    double improvement = (bestTime - elapsedTime) * 100.0 / bestTime;
                    System.out.printf("[PERF] %s run %02d/%02d: %,d ns (best improved by %.2f%%)%n",
                            algorithmName, i + 1, runs, elapsedTime, improvement);
                }
                bestTime = elapsedTime;
            } else {
                System.out.printf("[PERF] %s run %02d/%02d: %,d ns%n",
                        algorithmName, i + 1, runs, elapsedTime);
            }
        }

        long averageTime = total / runs;
        System.out.printf("[PERF] %s average: %,d ns, best: %,d ns%n", algorithmName, averageTime, bestTime);
        return averageTime;
    }

    public static void warmUpBubble(ArrayList<Candidate> originalList, int runs) {
        for (int i = 0; i < runs; i++) {
            ArrayList<Candidate> list = copyList(originalList);
            BubbleSort.sort(list);
        }
    }

    public static void warmUpQuick(ArrayList<Candidate> originalList, int runs) {
        for (int i = 0; i < runs; i++) {
            ArrayList<Candidate> list = copyList(originalList);
            QuickSort.sort(list);
        }
    }

    public static void warmUpMerge(ArrayList<Candidate> originalList, int runs) {
        for (int i = 0; i < runs; i++) {
            ArrayList<Candidate> list = copyList(originalList);
            MergeSort.sort(list);
        }
    }

    public static void warmUpQuickMedianThree(ArrayList<Candidate> originalList, int runs) {
        for (int i = 0; i < runs; i++) {
            ArrayList<Candidate> list = copyList(originalList);
            QuickSortMedianThree.sort(list);
        }
    }

    public static int compareCandidates(Candidate a, Candidate b) {
        if (a.priorityScore != b.priorityScore) {
            return b.priorityScore - a.priorityScore;
        }
        return a.locationId.compareTo(b.locationId);
    }

    public static ArrayList<Candidate> copyList(ArrayList<Candidate> originalList) {
        ArrayList<Candidate> newList = new ArrayList<>();
        for (Candidate candidate : originalList) {
            newList.add(candidate);
        }
        return newList;
    }

    public static String topTenToString(ArrayList<Candidate> list) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10 && i < list.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(list.get(i).locationId)
                    .append("(")
                    .append(list.get(i).priorityScore)
                    .append(")");
        }
        return builder.toString();
    }

    public static boolean isSorted(ArrayList<Candidate> list) {
        for (int i = 1; i < list.size(); i++) {
            if (compareCandidates(list.get(i - 1), list.get(i)) > 0) {
                return false;
            }
        }
        return true;
    }

    public interface SortAction {
        void sort(ArrayList<Candidate> list);
    }

    public static class BenchmarkResult {
        public final String datasetName;
        public final long bubbleTime;
        public final long quickTime;
        public final long quickMedianThreeTime;
        public final long mergeTime;

        public BenchmarkResult(String datasetName, long bubbleTime, long quickTime, long quickMedianThreeTime, long mergeTime) {
            this.datasetName = datasetName;
            this.bubbleTime = bubbleTime;
            this.quickTime = quickTime;
            this.quickMedianThreeTime = quickMedianThreeTime;
            this.mergeTime = mergeTime;
        }
    }
}
