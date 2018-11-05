package picocli.util;

import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Defaults;

public class ObjectUtilsX {
    /**
     * Safely retrieves the array item at the given position.
     *
     * @param array
     * @param index
     * @return Empty string if null {@code array} or out-of-bounds {@code index}.
     */
    public static String safeEmptyGet(String[] array, int index) {
        return safeGet(array, index, StringUtils.EMPTY);
    }

    /**
     * Safely retrieves the array item at the given position.
     *
     * @param array
     * @param index
     * @return Default value if null {@code array} or out-of-bounds {@code index}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T safeGet(T[] array, int index) {
        return safeGet(array, index,
                () -> Defaults.defaultValue((Class<T>) array.getClass().getComponentType()));
    }

    /**
     * Safely retrieves the array item at the given position.
     *
     * @param array
     * @param index
     * @param defaultValue
     * @return {@code defaultValue} if null {@code array} or out-of-bounds {@code index}.
     */
    public static <T> T safeGet(T[] array, int index, Supplier<T> defaultValue) {
        return array != null && array.length > index ? array[index] : defaultValue.get();
    }

    /**
     * Safely retrieves the array item at the given position.
     *
     * @param array
     * @param index
     * @param defaultValue
     * @return {@code defaultValue} if null {@code array} or out-of-bounds {@code index}.
     */
    public static <T> T safeGet(T[] array, int index, T defaultValue) {
        return safeGet(array, index, () -> defaultValue);
    }

    /**
     * Safely retrieves the array item at the given position.
     *
     * @param array
     * @param index
     * @return {@code null} if null {@code array} or out-of-bounds {@code index}.
     */
    public static String safeNullGet(String[] array, int index) {
        return safeGet(array, index, null);
    }
}
