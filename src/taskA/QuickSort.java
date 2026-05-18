import java.util.ArrayList;

public class QuickSort {
    public static void sort(ArrayList<Candidate> list) {
        quickSort(list, 0, list.size() - 1);
    }

    public static void quickSort(ArrayList<Candidate> list, int first, int last) {
        if (last > first) {
            int pivotIndex = partition(list, first, last);
            quickSort(list, first, pivotIndex - 1);
            quickSort(list, pivotIndex + 1, last);
        }
    }

    public static int partition(ArrayList<Candidate> list, int first, int last) {
        Candidate pivot = list.get(first);
        int low = first + 1;
        int high = last;

        while (high > low) {
            while (low <= high && Main.compareCandidates(list.get(low), pivot) <= 0) {
                low++;
            }
            while (low <= high && Main.compareCandidates(list.get(high), pivot) > 0) {
                high--;
            }
            if (high > low) {
                Candidate temp = list.get(high);
                list.set(high, list.get(low));
                list.set(low, temp);
            }
        }

        while (high > first && Main.compareCandidates(list.get(high), pivot) >= 0) {
            high--;
        }

        if (Main.compareCandidates(pivot, list.get(high)) > 0) {
            list.set(first, list.get(high));
            list.set(high, pivot);
            return high;
        } else {
            return first;
        }
    }
}
