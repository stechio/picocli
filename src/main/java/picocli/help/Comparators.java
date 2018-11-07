package picocli.help;

import java.util.Comparator;

import picocli.model.OptionSpec;
import picocli.model.Range;
import picocli.util.Comparators.Length;

public final class Comparators {
    /**
     * Sorts {@code OptionSpec} instances by their max arity first, then their min arity, then
     * delegates to super class.
     */
    public static class OptionArityAndNameAlphabeticalLength extends OptionNameAlphabeticalLength {
        public static final Comparator<OptionSpec> Order = new OptionArityAndNameAlphabeticalLength();

        @Override
        public int compare(OptionSpec o1, OptionSpec o2) {
            Range arity1 = o1.arity();
            Range arity2 = o2.arity();
            int result = arity1.max - arity2.max;
            if (result == 0) {
                result = arity1.min - arity2.min;
            }
            if (result == 0) { // arity is same
                if (o1.isMultiValue() && !o2.isMultiValue()) {
                    result = 1;
                } // f1 > f2
                if (!o1.isMultiValue() && o2.isMultiValue()) {
                    result = -1;
                } // f1 < f2
            }
            return result == 0 ? super.compare(o1, o2) : result;
        }
    }

    /**
     * Sorts {@code OptionSpec} instances by their name in case-insensitive alphabetic order. If an
     * option has multiple names, the shortest name is used for the sorting. Help options follow
     * non-help options.
     */
    public static class OptionNameAlphabeticalLength implements Comparator<OptionSpec> {
        public static final Comparator<OptionSpec> Order = new OptionNameAlphabeticalLength();

        @Override
        public int compare(OptionSpec o1, OptionSpec o2) {
            if (o1 == null)
                return 1;
            else if (o2 == null)
                return -1;
            String[] names1 = Length.sortAsc(o1.names());
            String[] names2 = Length.sortAsc(o2.names());
            int result = names1[0].toUpperCase().compareTo(names2[0].toUpperCase()); // case insensitive sort
            result = result == 0 ? -names1[0].compareTo(names2[0]) : result; // lower case before upper case
            return o1.help() == o2.help() ? result : o2.help() ? -1 : 1; // help options come last
        }
    }

    private Comparators() {
    }
}
