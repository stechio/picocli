package picocli.excepts;

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
}