package picocli.model;

import java.util.Comparator;

class PositionalParametersSorter implements Comparator<ArgSpec> {
    private static final Range OPTION_INDEX = new Range(0, 0, false, true, "0");

    public int compare(ArgSpec p1, ArgSpec p2) {
        int result = index(p1).compareTo(index(p2));
        return (result == 0) ? p1.arity().compareTo(p2.arity()) : result;
    }

    private Range index(ArgSpec arg) {
        return arg.isOption() ? OPTION_INDEX : ((PositionalParamSpec) arg).index();
    }
}