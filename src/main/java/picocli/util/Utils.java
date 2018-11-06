package picocli.util;

import org.apache.commons.lang3.StringUtils;

public class Utils {
    /**
     * Same as {@link ObjectUtils#isEmpty(Object)}, except that String arrays are considered empty
     * also when all their items are empty.
     * 
     * @param object
     * @return
     */
    public static boolean isEmpty(final Object object) {
        if (ObjectUtils.isEmpty(object))
            return true;
        else if (object.getClass().isArray()
                && object.getClass().getComponentType().equals(String.class)) {
            String[] array = (String[]) object;
            for (int index = 0; index < array.length; index++) {
                if (StringUtils.isNotEmpty(array[index]))
                    return false;
            }
            return true;
        }
        return false;
    }

    public static boolean isNotEmpty(final Object object) {
        return !isEmpty(object);
    }

    /**
     * Same as {@link StringUtils#join(Object[],String,int,int)}, except that null arrays return
     * empty.
     * 
     * @param object
     */
    public static <T> String join(T[] array, String separator, int startIndex, int endIndex) {
        return isEmpty(array) ? StringUtils.EMPTY
                : StringUtils.join(array, separator, startIndex, endIndex);
    }

    public static int countTrailingSpaces(String str) {
        if (str == null)
            return 0;

        int trailingSpaces = 0;
        for (int i = str.length(); --i >= 0 && str.charAt(i) == ' ';) {
            trailingSpaces++;
        }
        return trailingSpaces;
    }

    /**
     * Safely retrieves the array item at the given position.
     *
     * @param array
     * @param index
     * @return Empty string if null {@code array} or out-of-bounds {@code index}.
     */
    public static String safeGet(String[] array, int index) {
        return ObjectUtilsExt.safeEmptyGet(array, index);
    }

    public static String safeFormat(String formatString, Object... params) {
        return formatString == null ? "" : String.format(formatString, params);
    }
}
