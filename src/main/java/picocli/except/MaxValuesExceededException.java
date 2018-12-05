package picocli.except;

import picocli.CommandLine;

/**
 * Exception indicating that more values were specified for an option or parameter than its
 * {@link Option#arity() arity} allows.
 */
public class MaxValuesExceededException extends ParameterException {
    private static final long serialVersionUID = 6536145439570100641L;

    public MaxValuesExceededException(CommandLine commandLine, String msg) {
        super(commandLine, msg);
    }
}