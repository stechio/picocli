package picocli.handlers;

import picocli.CommandLine;
import picocli.excepts.ExecutionException;
import picocli.excepts.ParameterException;
import picocli.help.HelpCommand;
import picocli.model.ParseResult;

/**
 * Command line parse result handler that returns a value. This handler prints help if
 * requested, and otherwise calls {@link #handle(CommandLine.ParseResult)} with the parse
 * result. Facilitates implementation of the {@link IParseResultHandler2} interface.
 * <p>
 * Note that {@code AbstractParseResultHandler} is a generic type. This, along with the abstract
 * {@code self} method, allows method chaining to work properly in subclasses, without the need
 * for casts. An example subclass can look like this:
 * </p>
 * 
 * <pre>
 * {@code
 * class MyResultHandler extends AbstractParseResultHandler<MyReturnType> {
 *
 *     protected MyReturnType handle(ParseResult parseResult) throws ExecutionException { ... }
 *
 *     protected MyResultHandler self() { return this; }
 * }
 * }
 * </pre>
 * 
 * @since 3.0
 */
public abstract class AbstractParseResultHandler<R> extends
        AbstractHandler<R, AbstractParseResultHandler<R>> implements IParseResultHandler2<R> {
    /**
     * Prints help if requested, and otherwise calls {@link #handle(CommandLine.ParseResult)}.
     * Finally, either a list of result objects is returned, or the JVM is terminated if an exit
     * code {@linkplain #andExit(int) was set}.
     *
     * @param parseResult
     *            the {@code ParseResult} that resulted from successfully parsing the command
     *            line arguments
     * @return the result of {@link #handle(ParseResult) processing parse results}
     * @throws ParameterException
     *             if the {@link HelpCommand HelpCommand} was invoked for an unknown subcommand.
     *             Any {@code ParameterExceptions} thrown from this method are treated as if
     *             this exception was thrown during parsing and passed to the
     *             {@link IExceptionHandler2}
     * @throws ExecutionException
     *             if a problem occurred while processing the parse results; client code can use
     *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
     *             where processing failed
     */
    public R handleParseResult(ParseResult parseResult) throws ExecutionException {
        if (CommandLine.printHelpIfRequested(parseResult.asCommandLineList(), out(), err(), ansi())) {
            return returnResultOrExit(null);
        }
        return returnResultOrExit(handle(parseResult));
    }

    /**
     * Processes the specified {@code ParseResult} and returns the result as a list of objects.
     * Implementations are responsible for catching any exceptions thrown in the {@code handle}
     * method, and rethrowing an {@code ExecutionException} that details the problem and
     * captures the offending {@code CommandLine} object.
     *
     * @param parseResult
     *            the {@code ParseResult} that resulted from successfully parsing the command
     *            line arguments
     * @return the result of processing parse results
     * @throws ExecutionException
     *             if a problem occurred while processing the parse results; client code can use
     *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
     *             where processing failed
     */
    protected abstract R handle(ParseResult parseResult) throws ExecutionException;
}