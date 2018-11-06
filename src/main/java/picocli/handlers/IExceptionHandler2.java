package picocli.handlers;

import picocli.excepts.ExecutionException;
import picocli.excepts.ParameterException;
import picocli.model.ParseResult;

/**
 * Classes implementing this interface know how to handle {@code ParameterExceptions} (usually
 * from invalid user input) and {@code ExecutionExceptions} that occurred while executing the
 * {@code Runnable} or {@code Callable} command.
 * <p>
 * Implementations of this interface can be passed to the
 * {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
 * CommandLine::parseWithHandlers} method.
 * </p>
 * <p>
 * This interface replaces the {@link IParseResultHandler} interface.
 * </p>
 * 
 * @param <R>
 *            the return type of this handler
 * @see DefaultExceptionHandler
 * @since 3.0
 */
public interface IExceptionHandler2<R> {
    /**
     * Handles a {@code ParameterException} that occurred while
     * {@linkplain #parseArgs(String...) parsing} the command line arguments and optionally
     * returns a list of results.
     * 
     * @param ex
     *            the ParameterException describing the problem that occurred while parsing the
     *            command line arguments, and the CommandLine representing the command or
     *            subcommand whose input was invalid
     * @param args
     *            the command line arguments that could not be parsed
     * @return an object resulting from handling the exception
     */
    R handleParseException(ParameterException ex, String[] args);

    /**
     * Handles a {@code ExecutionException} that occurred while executing the {@code Runnable}
     * or {@code Callable} command and optionally returns a list of results.
     * 
     * @param ex
     *            the ExecutionException describing the problem that occurred while executing
     *            the {@code Runnable} or {@code Callable} command, and the CommandLine
     *            representing the command or subcommand that was being executed
     * @param parseResult
     *            the result of parsing the command line arguments
     * @return an object resulting from handling the exception
     */
    R handleExecutionException(ExecutionException ex, ParseResult parseResult);
}