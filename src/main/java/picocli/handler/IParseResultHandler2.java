package picocli.handler;

import picocli.CommandLine;
import picocli.except.ExecutionException;
import picocli.except.ParameterException;
import picocli.help.Ansi;
import picocli.model.ParseResult;

/**
 * Represents a function that can process the {@code ParseResult} object resulting from
 * successfully {@linkplain #parseArgs(String...) parsing} the command line arguments. This is a
 * <a href=
 * "https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">functional
 * interface</a> whose functional method is
 * {@link IParseResultHandler2#handleParseResult(CommandLine.ParseResult)}.
 * <p>
 * Implementations of this function can be passed to the
 * {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
 * CommandLine::parseWithHandlers} methods to take some next step after the command line was
 * successfully parsed.
 * </p>
 * <p>
 * This interface replaces the {@link IParseResultHandler} interface; it takes the parse result
 * as a {@code ParseResult} object instead of a List of {@code CommandLine} objects, and it has
 * the freedom to select the {@link Ansi} style to use and what {@code PrintStreams} to print
 * to.
 * </p>
 * 
 * @param <R>
 *            the return type of this handler
 * @see RunFirst
 * @see RunLast
 * @see RunAll
 * @since 3.0
 */
public interface IParseResultHandler2<R> {
    /**
     * Processes the {@code ParseResult} object resulting from successfully
     * {@linkplain CommandLine#parseArgs(String...) parsing} the command line arguments and
     * returns a return value.
     * 
     * @param parseResult
     *            the {@code ParseResult} that resulted from successfully parsing the command
     *            line arguments
     * @throws ParameterException
     *             if a help command was invoked for an unknown subcommand. Any
     *             {@code ParameterExceptions} thrown from this method are treated as if this
     *             exception was thrown during parsing and passed to the
     *             {@link IExceptionHandler2}
     * @throws ExecutionException
     *             if a problem occurred while processing the parse results; use
     *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
     *             where processing failed
     */
    R handleParseResult(ParseResult parseResult) throws ExecutionException;
}