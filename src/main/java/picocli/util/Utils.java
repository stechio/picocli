package picocli.util;

import org.apache.commons.lang3.StringUtils;

public class Utils {
    /**
     * Almost the same behaviour as {@link ObjectUtils#isEmpty(Object)}, since String arrays are
     * considered empty also when all their items are empty.
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

    public static String safeGet(String[] array, int index) {
        return ObjectUtilsX.safeEmptyGet(array, index);
    }
}
