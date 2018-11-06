package picocli.model;

import java.util.Arrays;

import picocli.util.Assert;

final class Model {
    public static boolean initializable(Object current, Object candidate, Object defaultValue) {
        return current == null && isNonDefault(candidate, defaultValue);
    }

    public static boolean initializable(Object current, Object[] candidate, Object[] defaultValue) {
        return current == null && isNonDefault(candidate, defaultValue);
    }

    public static boolean isNonDefault(Object candidate, Object defaultValue) {
        return !Assert.notNull(defaultValue, "defaultValue").equals(candidate);
    }

    public static boolean isNonDefault(Object[] candidate, Object[] defaultValue) {
        return !Arrays.equals(Assert.notNull(defaultValue, "defaultValue"), candidate);
    }

    Model() {
    }
}
