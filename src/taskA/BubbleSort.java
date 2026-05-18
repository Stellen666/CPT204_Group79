import java.util.ArrayList;

public class BubbleSort {
    public static void sort(ArrayList<Candidate> list) {
        boolean swapped;
        for (int k = 1; k < list.size(); k++) {
            swapped = false;
            for (int i = 0; i < list.size() - k; i++) {
                if (Main.compareCandidates(list.get(i), list.get(i + 1)) > 0) {
                    Candidate temp = list.get(i);
                    list.set(i, list.get(i + 1));
                    list.set(i + 1, temp);
                    swapped = true;
                }
            }
            if (!swapped) {
                break;
            }
        }
    }
}
