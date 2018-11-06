package picocli.handlers;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import picocli.CommandLine;
import picocli.excepts.ExecutionException;
import picocli.excepts.ParameterException;
import picocli.help.Ansi;
import picocli.help.HelpCommand;
import picocli.model.ParseResult;

/**
 * Command line parse result handler that prints help if requested, and otherwise executes the
 * top-level command and all subcommands as {@code Runnable} or {@code Callable}. For use in the
 * {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
 * parseWithHandler} methods.
 * 
 * @since 2.0
 */
public class RunAll extends AbstractParseResultHandler<List<Object>>
        implements IParseResultHandler {
    /**
     * Prints help if requested, and otherwise executes the top-level command and all
     * subcommands as {@code Runnable} or {@code Callable}. Finally, either a list of result
     * objects is returned, or the JVM is terminated if an exit code {@linkplain #andExit(int)
     * was set}. If any of the {@code CommandLine} commands does not implement either
     * {@code Runnable} or {@code Callable}, an {@code ExecutionException} is thrown detailing
     * the problem and capturing the offending {@code CommandLine} object.
     *
     * @param parsedCommands
     *            the {@code CommandLine} objects that resulted from successfully parsing the
     *            command line arguments
     * @param out
     *            the {@code PrintStream} to print help to if requested
     * @param ansi
     *            for printing help messages using ANSI styles and colors
     * @return an empty list if help was requested, or a list containing the result of executing
     *         all commands: the return values from calling the {@code Callable} commands,
     *         {@code null} elements for commands that implement {@code Runnable}
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
        List<Object> result = new ArrayList<Object>();
        for (CommandLine parsed : parsedCommands) {
            CommandLine.execute(parsed, result);
        }
        return returnResultOrExit(result);
    }

    /**
     * Executes the top-level command and all subcommands as {@code Runnable} or
     * {@code Callable}. If any of the {@code CommandLine} commands does not implement either
     * {@code Runnable} or {@code Callable}, an {@code ExecutionException} is thrown detailing
     * the problem and capturing the offending {@code CommandLine} object.
     *
     * @param parseResult
     *            the {@code ParseResult} that resulted from successfully parsing the command
     *            line arguments
     * @return an empty list if help was requested, or a list containing the result of executing
     *         all commands: the return values from calling the {@code Callable} commands,
     *         {@code null} elements for commands that implement {@code Runnable}
     * @throws ExecutionException
     *             if a problem occurred while processing the parse results; use
     *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
     *             where processing failed
     * @since 3.0
     */
    protected List<Object> handle(ParseResult parseResult) throws ExecutionException {
        List<Object> result = new ArrayList<Object>();
        CommandLine.execute(parseResult.commandSpec().commandLine(), result);
        while (parseResult.hasSubcommand()) {
            parseResult = parseResult.subcommand();
            CommandLine.execute(parseResult.commandSpec().commandLine(), result);
        }
        return returnResultOrExit(result);
    }

    @Override
    protected RunAll self() {
        return this;
    }
}