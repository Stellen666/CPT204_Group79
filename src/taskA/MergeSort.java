import java.util.ArrayList;

public class MergeSort {
    public static void sort(ArrayList<Candidate> list) {
        if (list.size() > 1) {
            int mid = list.size() / 2;

            ArrayList<Candidate> firstHalf = new ArrayList<>();
            for (int i = 0; i < mid; i++) {
                firstHalf.add(list.get(i));
            }

            ArrayList<Candidate> secondHalf = new ArrayList<>();
            for (int i = mid; i < list.size(); i++) {
                secondHalf.add(list.get(i));
            }

            sort(firstHalf);
            sort(secondHalf);
            merge(firstHalf, secondHalf, list);
        }
    }

    public static void merge(ArrayList<Candidate> firstHalf, ArrayList<Candidate> secondHalf, ArrayList<Candidate> temp) {
        int currentFirstHalf = 0;
        int currentSecondHalf = 0;
        int current = 0;

        while (currentFirstHalf < firstHalf.size() && currentSecondHalf < secondHalf.size()) {
            if (Main.compareCandidates(firstHalf.get(currentFirstHalf), secondHalf.get(currentSecondHalf)) <= 0) {
                temp.set(current++, firstHalf.get(currentFirstHalf++));
            } else {
                temp.set(current++, secondHalf.get(currentSecondHalf++));
            }
        }

        while (currentFirstHalf < firstHalf.size()) {
            temp.set(current++, firstHalf.get(currentFirstHalf++));
        }

        while (currentSecondHalf < secondHalf.size()) {
            temp.set(current++, secondHalf.get(currentSecondHalf++));
        }
    }
}
