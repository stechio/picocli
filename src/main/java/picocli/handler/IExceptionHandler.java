package picocli.handler;

import java.io.PrintStream;
import java.util.List;

import picocli.except.ParameterException;
import picocli.help.Ansi;

/**
 * Represents a function that can handle a {@code ParameterException} that occurred while
 * {@linkplain #parse(String...) parsing} the command line arguments. This is a <a href=
 * "https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">functional
 * interface</a> whose functional method is
 * {@link #handleException(CommandLine.ParameterException, PrintStream, CommandLine.Help.Ansi, String...)}.
 * <p>
 * Implementations of this function can be passed to the
 * {@link #parseWithHandlers(IParseResultHandler, PrintStream, Help.Ansi, IExceptionHandler, String...)
 * CommandLine::parseWithHandlers} methods to handle situations when the command line could not
 * be parsed.
 * </p>
 * 
 * @deprecated Use {@link IExceptionHandler2} instead.
 * @see DefaultExceptionHandler
 * @since 2.0
 */
@Deprecated
public interface IExceptionHandler {
    /**
     * Handles a {@code ParameterException} that occurred while {@linkplain #parse(String...)
     * parsing} the command line arguments and optionally returns a list of results.
     * 
     * @param ex
     *            the ParameterException describing the problem that occurred while parsing the
     *            command line arguments, and the CommandLine representing the command or
     *            subcommand whose input was invalid
     * @param out
     *            the {@code PrintStream} to print help to if requested
     * @param ansi
     *            for printing help messages using ANSI styles and colors
     * @param args
     *            the command line arguments that could not be parsed
     * @return a list of results, or an empty list if there are no results
     */
    List<Object> handleException(ParameterException ex, PrintStream out, Ansi ansi,
            String... args);
}