package picocli.model;

import java.lang.reflect.Constructor;

import picocli.except.InitializationException;

public class Factory implements IFactory {
    public static <T> T create(IFactory factory, Class<T> cls) {
        try {
            return factory.create(cls);
        } catch (Exception ex) {
            throw new InitializationException("Could not instantiate " + cls + ": " + ex, ex);
        }
    }

    public static Iterable<String> createChoiceValues(IFactory factory,
            Class<? extends Iterable<String>> cls) {
        return create(factory, cls);
    }

    public static ITypeConverter<?>[] createConverter(IFactory factory,
            Class<? extends ITypeConverter<?>>[] classes) {
        ITypeConverter<?>[] result = new ITypeConverter<?>[classes.length];
        for (int i = 0; i < classes.length; i++) {
            result[i] = create(factory, classes[i]);
        }
        return result;
    }

    public static IDefaultValueProvider createDefaultValueProvider(IFactory factory,
            Class<? extends IDefaultValueProvider> cls) {
        return create(factory, cls);
    }

    public static IVersionProvider createVersionProvider(IFactory factory,
            Class<? extends IVersionProvider> cls) {
        return create(factory, cls);
    }

    @Override
    public <T> T create(Class<T> cls) throws Exception {
        try {
            return cls.newInstance();
        } catch (Exception ex) {
            Constructor<T> constructor = cls.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }
    }
}