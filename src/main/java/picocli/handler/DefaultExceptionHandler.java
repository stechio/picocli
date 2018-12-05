package picocli.handler;

import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

import picocli.except.ExecutionException;
import picocli.except.ParameterException;
import picocli.except.UnmatchedArgumentException;
import picocli.help.Ansi;
import picocli.model.ParseResult;

/**
 * Default exception handler that handles invalid user input by printing the exception message,
 * followed by the usage message for the command or subcommand whose input was invalid.
 * <p>
 * {@code ParameterExceptions} (invalid user input) is handled like this:
 * </p>
 * 
 * <pre>
 * err().println(paramException.getMessage());
 * paramException.getCommandLine().usage(err(), ansi());
 * if (hasExitCode())
 *     System.exit(exitCode());
 * else
 *     return returnValue;
 * </pre>
 * <p>
 * {@code ExecutionExceptions} that occurred while executing the {@code Runnable} or
 * {@code Callable} command are simply rethrown and not handled.
 * </p>
 * 
 * @since 2.0
 */
@SuppressWarnings("deprecation")
public class DefaultExceptionHandler<R>
        extends AbstractHandler<R, DefaultExceptionHandler<R>>
        implements IExceptionHandler, IExceptionHandler2<R> {
    public List<Object> handleException(ParameterException ex, PrintStream out, Ansi ansi,
            String... args) {
        internalHandleParseException(ex, out, ansi, args);
        return Collections.<Object>emptyList();
    }

    /**
     * Prints the message of the specified exception, followed by the usage message for the
     * command or subcommand whose input was invalid, to the stream returned by {@link #err()}.
     * 
     * @param ex
     *            the ParameterException describing the problem that occurred while parsing the
     *            command line arguments, and the CommandLine representing the command or
     *            subcommand whose input was invalid
     * @param args
     *            the command line arguments that could not be parsed
     * @return the empty list
     * @since 3.0
     */
    public R handleParseException(ParameterException ex, String[] args) {
        internalHandleParseException(ex, err(), ansi(), args);
        return returnResultOrExit(null);
    }

    private void internalHandleParseException(ParameterException ex, PrintStream out, Ansi ansi,
            String[] args) {
        out.println(ex.getMessage());
        if (!UnmatchedArgumentException.printSuggestions(ex, out)) {
            ex.getCommandLine().usage(out, ansi);
        }
    }

    /**
     * This implementation always simply rethrows the specified exception.
     * 
     * @param ex
     *            the ExecutionException describing the problem that occurred while executing
     *            the {@code Runnable} or {@code Callable} command
     * @param parseResult
     *            the result of parsing the command line arguments
     * @return nothing: this method always rethrows the specified exception
     * @throws ExecutionException
     *             always rethrows the specified exception
     * @since 3.0
     */
    public R handleExecutionException(ExecutionException ex, ParseResult parseResult) {
        throw ex;
    }

    @Override
    protected DefaultExceptionHandler<R> self() {
        return this;
    }
}