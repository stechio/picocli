package picocli.excepts;

import picocli.CommandLine;
import picocli.model.ITypeConverter;

/**
 * Exception indicating that an annotated field had a type for which no {@link ITypeConverter}
 * was {@linkplain #registerConverter(Class, ITypeConverter) registered}.
 */
public class MissingTypeConverterException extends ParameterException {
    private static final long serialVersionUID = -6050931703233083760L;

    public MissingTypeConverterException(CommandLine commandLine, String msg) {
        super(commandLine, msg);
    }
}