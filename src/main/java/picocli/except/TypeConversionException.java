package picocli.except;

import picocli.model.ITypeConverter;

/**
 * Exception thrown by {@link ITypeConverter} implementations to indicate a String could not be
 * converted.
 */
public class TypeConversionException extends PicocliException {
    private static final long serialVersionUID = 4251973913816346114L;

    public TypeConversionException(String msg) {
        super(msg);
    }

    public TypeConversionException(String msg, Throwable throwable) {
        super(msg, throwable);
    }

    public TypeConversionException(String value, Class<?> type, Throwable throwable) {
        super(String.format("Cannot convert '%s' to %s (%s)", value, type.getSimpleName(),
                throwable), throwable);
    }
}