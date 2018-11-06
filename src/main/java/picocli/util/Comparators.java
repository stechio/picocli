package picocli.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class Comparators {
    public static class Length implements Comparator<String> {
        public static final Comparator<String> Order = new Length();
        public static final Comparator<String> ReverseOrder = Collections.reverseOrder(Order);

        /**
         * Sorts the specified list of strings shortest-first and returns it.
         */
        public static List<String> sortAsc(List<String> items) {
            Collections.sort(items, Order);
            return items;
        }

        /**
         * Sorts the specified array of strings shortest-first and returns it.
         */
        public static String[] sortAsc(String[] items) {
            Arrays.sort(items, Order);
            return items;
        }

        /**
         * Sorts the specified list of strings longest-first and returns it.
         */
        public static List<String> sortDesc(List<String> items) {
            Collections.sort(items, ReverseOrder);
            return items;
        }

        /**
         * Sorts the specified array of strings longest-first and returns it.
         */
        public static String[] sortDesc(String[] items) {
            Arrays.sort(items, ReverseOrder);
            return items;
        }

        @Override
        public int compare(String o1, String o2) {
            return o1.length() - o2.length();
        }
    }

    private Comparators() {
    }
}
