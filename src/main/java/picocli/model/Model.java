package picocli.model;

import java.util.Arrays;

import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.IVersionProvider;
import picocli.excepts.InitializationException;
import picocli.util.Assert;

final class Model {
    public static ITypeConverter<?>[] createConverter(IFactory factory,
            Class<? extends ITypeConverter<?>>[] classes) {
        ITypeConverter<?>[] result = new ITypeConverter<?>[classes.length];
        for (int i = 0; i < classes.length; i++) {
            result[i] = create(factory, classes[i]);
        }
        return result;
    }

    public static IVersionProvider createVersionProvider(IFactory factory,
            Class<? extends IVersionProvider> cls) {
        return create(factory, cls);
    }

    public static IDefaultValueProvider createDefaultValueProvider(IFactory factory,
            Class<? extends IDefaultValueProvider> cls) {
        return create(factory, cls);
    }

    public static Iterable<String> createCompletionCandidates(IFactory factory,
            Class<? extends Iterable<String>> cls) {
        return create(factory, cls);
    }

    public static <T> T create(IFactory factory, Class<T> cls) {
        try {
            return factory.create(cls);
        } catch (Exception ex) {
            throw new InitializationException("Could not instantiate " + cls + ": " + ex, ex);
        }
    }
    
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
