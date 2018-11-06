package picocli.util;

public class ClassUtilsExt {
    public static boolean isBoolean(Class<?> type) {
        return type == Boolean.class || type == Boolean.TYPE;
    }
}
