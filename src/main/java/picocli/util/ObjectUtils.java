package picocli.util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * Functionalities yet not available as of current Apache Commons Lang3 3.8.1 release.
 * 
 * TODO: Remove when ACL3 3.9 released.
 */
class ObjectUtils {
    public static boolean isEmpty(final Object object) {
        if (object == null) {
            return true;
        }
        if (object instanceof CharSequence) {
            return ((CharSequence) object).length() == 0;
        }
        if (object.getClass().isArray()) {
            return Array.getLength(object) == 0;
        }
        if (object instanceof Collection<?>) {
            return ((Collection<?>) object).isEmpty();
        }
        if (object instanceof Map<?, ?>) {
            return ((Map<?, ?>) object).isEmpty();
        }
        return false;
    }

    public static boolean isNotEmpty(final Object object) {
        return !isEmpty(object);
    }
}
