package picocli.util;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;

public class StringUtilsExt {
    /**
     * Retrieves the longest string. Null-safe.
     * 
     * @param items
     * @return Empty string if null or empty collection.
     */
    public static String longest(Collection<String> items) {
        return ObjectUtils.isNotEmpty(items)
                ? Comparators.Length.sortDesc(new ArrayList<>(items)).get(0)
                : StringUtils.EMPTY;
    }

    /**
     * Retrieves the shortest string. Null-safe.
     * 
     * @param items
     * @return Empty string if null or empty collection.
     */
    public static String shortest(Collection<String> items) {
        return ObjectUtils.isNotEmpty(items)
                ? Comparators.Length.sortAsc(new ArrayList<>(items)).get(0)
                : StringUtils.EMPTY;
    }
}
