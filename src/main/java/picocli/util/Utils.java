package picocli.util;

import org.apache.commons.lang3.StringUtils;

public class Utils {
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
     * Same as {@link ObjectUtils#isEmpty(Object)}, except that CharSequence arrays and Iterable's
     * are considered empty also when all their items are empty.
     * 
     * @param object
     * @return
     */
    public static boolean isEmptyAtAll(final Object object) {
        if (ObjectUtils.isEmpty(object))
            return true;
        else if (object.getClass().isArray()
                && CharSequence.class.isAssignableFrom(object.getClass().getComponentType()))
            return StringUtils.isAllEmpty((CharSequence[]) object);
        else if (object instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) object) {
                if (!(item instanceof CharSequence) || StringUtils.isNotEmpty((CharSequence) item))
                    return false;
            }
            return true;
        } else
            return false;
    }

    public static boolean isNotEmptyAtAll(final Object object) {
        return !isEmptyAtAll(object);
    }

    public static String safeFormat(String formatString, Object... params) {
        return formatString == null ? "" : String.format(formatString, params);
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

    /**
     * Same as {@link StringUtils#join(Object[],String,int,int)}, except that null arrays return
     * empty.
     * 
     * @param object
     */
    public static <T> String safeJoin(T[] array, String separator, int startIndex, int endIndex) {
        return isEmptyAtAll(array) ? StringUtils.EMPTY
                : StringUtils.join(array, separator, startIndex, endIndex);
    }
}
