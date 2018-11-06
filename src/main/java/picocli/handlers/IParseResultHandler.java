package picocli.handlers;

import java.io.PrintStream;
import java.util.List;

import picocli.CommandLine;
import picocli.excepts.ExecutionException;
import picocli.excepts.ParameterException;
import picocli.help.Ansi;

/**
 * Represents a function that can process a List of {@code CommandLine} objects resulting from
 * successfully {@linkplain #parse(String...) parsing} the command line arguments. This is a
 * <a href=
 * "https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">functional
 * interface</a> whose functional method is
 * {@link #handleParseResult(List, PrintStream, CommandLine.Help.Ansi)}.
 * <p>
 * Implementations of this functions can be passed to the
 * {@link #parseWithHandlers(IParseResultHandler, PrintStream, Help.Ansi, IExceptionHandler, String...)
 * CommandLine::parseWithHandler} methods to take some next step after the command line was
 * successfully parsed.
 * </p>
 * 
 * @see RunFirst
 * @see RunLast
 * @see RunAll
 * @deprecated Use {@link IParseResultHandler2} instead.
 * @since 2.0
 */
@Deprecated
public interface IParseResultHandler {
    /**
     * Processes a List of {@code CommandLine} objects resulting from successfully
     * {@linkplain #parse(String...) parsing} the command line arguments and optionally returns
     * a list of results.
     * 
     * @param parsedCommands
     *            the {@code CommandLine} objects that resulted from successfully parsing the
     *            command line arguments
     * @param out
     *            the {@code PrintStream} to print help to if requested
     * @param ansi
     *            for printing help messages using ANSI styles and colors
     * @return a list of results, or an empty list if there are no results
     * @throws ParameterException
     *             if a help command was invoked for an unknown subcommand. Any
     *             {@code ParameterExceptions} thrown from this method are treated as if this
     *             exception was thrown during parsing and passed to the
     *             {@link IExceptionHandler}
     * @throws ExecutionException
     *             if a problem occurred while processing the parse results; use
     *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
     *             where processing failed
     */
    List<Object> handleParseResult(List<CommandLine> parsedCommands, PrintStream out, Ansi ansi)
            throws ExecutionException;
}