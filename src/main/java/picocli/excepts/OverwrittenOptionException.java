package picocli.excepts;

import picocli.CommandLine;
import picocli.model.ArgSpec;

/**
 * Exception indicating that an option for a single-value option field has been specified
 * multiple times on the command line.
 */
public class OverwrittenOptionException extends ParameterException {
    private static final long serialVersionUID = 1338029208271055776L;
    private final ArgSpec overwrittenArg;

    public OverwrittenOptionException(CommandLine commandLine, ArgSpec overwritten,
            String msg) {
        super(commandLine, msg);
        overwrittenArg = overwritten;
    }

    /**
     * Returns the {@link ArgSpec} for the option which was being overwritten.
     * 
     * @since 3.8
     */
    public ArgSpec getOverwritten() {
        return overwrittenArg;
    }
}