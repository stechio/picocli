package picocli.handler;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import picocli.CommandLine;
import picocli.except.ExecutionException;
import picocli.except.ParameterException;
import picocli.help.Ansi;
import picocli.help.HelpCommand;
import picocli.model.ParseResult;

/**
 * Command line parse result handler that prints help if requested, and otherwise executes the
 * most specific {@code Runnable} or {@code Callable} subcommand. For use in the
 * {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
 * parseWithHandler} methods.
 * <p>
 * Something like this:
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     // RunLast implementation: print help if requested, otherwise execute the most specific subcommand
 *     List<CommandLine> parsedCommands = parseResult.asCommandLineList();
 *     if (CommandLine.printHelpIfRequested(parsedCommands, out(), err(), ansi())) {
 *         return emptyList();
 *     }
 *     CommandLine last = parsedCommands.get(parsedCommands.size() - 1);
 *     Object command = last.getCommand();
 *     Object result = null;
 *     if (command instanceof Runnable) {
 *         try {
 *             ((Runnable) command).run();
 *         } catch (Exception ex) {
 *             throw new ExecutionException(last, "Error in runnable " + command, ex);
 *         }
 *     } else if (command instanceof Callable) {
 *         try {
 *             result = ((Callable) command).call();
 *         } catch (Exception ex) {
 *             throw new ExecutionException(last, "Error in callable " + command, ex);
 *         }
 *     } else {
 *         throw new ExecutionException(last,
 *                 "Parsed command (" + command + ") is not Runnable or Callable");
 *     }
 *     if (hasExitCode()) {
 *         System.exit(exitCode());
 *     }
 *     return Arrays.asList(result);
 * }
 * </pre>
 * <p>
 * From picocli v2.0, {@code RunLast} is used to implement the
 * {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...) run} and
 * {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...) call} convenience
 * methods.
 * </p>
 * 
 * @since 2.0
 */
public class RunLast extends AbstractParseResultHandler<List<Object>>
        implements IParseResultHandler {
    /**
     * Prints help if requested, and otherwise executes the most specific {@code Runnable} or
     * {@code Callable} subcommand. Finally, either a list of result objects is returned, or the
     * JVM is terminated if an exit code {@linkplain #andExit(int) was set}. If the last
     * (sub)command does not implement either {@code Runnable} or {@code Callable}, an
     * {@code ExecutionException} is thrown detailing the problem and capturing the offending
     * {@code CommandLine} object.
     *
     * @param parsedCommands
     *            the {@code CommandLine} objects that resulted from successfully parsing the
     *            command line arguments
     * @param out
     *            the {@code PrintStream} to print help to if requested
     * @param ansi
     *            for printing help messages using ANSI styles and colors
     * @return an empty list if help was requested, or a list containing a single element: the
     *         result of calling the {@code Callable}, or a {@code null} element if the last
     *         (sub)command was a {@code Runnable}
     * @throws ParameterException
     *             if the {@link HelpCommand HelpCommand} was invoked for an unknown subcommand.
     *             Any {@code ParameterExceptions} thrown from this method are treated as if
     *             this exception was thrown during parsing and passed to the
     *             {@link IExceptionHandler}
     * @throws ExecutionException
     *             if a problem occurred while processing the parse results; use
     *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
     *             where processing failed
     */
    public List<Object> handleParseResult(List<CommandLine> parsedCommands, PrintStream out,
            Ansi ansi) {
        if (CommandLine.printHelpIfRequested(parsedCommands, out, err(), ansi)) {
            return returnResultOrExit(Collections.emptyList());
        }
        return returnResultOrExit(CommandLine.execute(parsedCommands.get(parsedCommands.size() - 1),
                new ArrayList<Object>()));
    }

    /**
     * Executes the most specific {@code Runnable} or {@code Callable} subcommand. If the last
     * (sub)command does not implement either {@code Runnable} or {@code Callable}, an
     * {@code ExecutionException} is thrown detailing the problem and capturing the offending
     * {@code CommandLine} object.
     *
     * @param parseResult
     *            the {@code ParseResult} that resulted from successfully parsing the command
     *            line arguments
     * @return an empty list if help was requested, or a list containing a single element: the
     *         result of calling the {@code Callable}, or a {@code null} element if the last
     *         (sub)command was a {@code Runnable}
     * @throws ExecutionException
     *             if a problem occurred while processing the parse results; use
     *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
     *             where processing failed
     * @since 3.0
     */
    protected List<Object> handle(ParseResult parseResult) throws ExecutionException {
        List<CommandLine> parsedCommands = parseResult.asCommandLineList();
        return CommandLine.execute(parsedCommands.get(parsedCommands.size() - 1), new ArrayList<Object>());
    }

    @Override
    protected RunLast self() {
        return this;
    }
}