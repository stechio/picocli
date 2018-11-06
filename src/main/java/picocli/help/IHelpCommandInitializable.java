package picocli.help;

import java.io.PrintStream;

import picocli.CommandLine;
import picocli.annots.Command;
import picocli.excepts.ParameterException;
import picocli.handlers.DefaultExceptionHandler;

/**
 * Help commands that provide usage help for other commands can implement this interface to be
 * initialized with the information they need.
 * <p>
 * The {@link #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi)
 * CommandLine::printHelpIfRequested} method calls the
 * {@link #init(CommandLine, picocli.CommandLine.Help.Ansi, PrintStream, PrintStream) init}
 * method on commands marked as {@link Command#helpCommand() helpCommand} before the help
 * command's {@code run} or {@code call} method is called.
 * </p>
 * <p>
 * <b>Implementation note:</b>
 * </p>
 * <p>
 * If an error occurs in the {@code run} or {@code call} method while processing the help
 * request, it is recommended custom Help commands throw a {@link ParameterException
 * ParameterException} with a reference to the parent command. The
 * {@link DefaultExceptionHandler DefaultExceptionHandler} will print the error message and the
 * usage for the parent command, and will terminate with the exit code of the exception handler
 * if one was set.
 * </p>
 * 
 * @since 3.0
 */
public interface IHelpCommandInitializable {
    /**
     * Initializes this object with the information needed to implement a help command that
     * provides usage help for other commands.
     * 
     * @param helpCommandLine
     *            the {@code CommandLine} object associated with this help command. Implementors
     *            can use this to walk the command hierarchy and get access to the help
     *            command's parent and sibling commands.
     * @param ansi
     *            whether to use Ansi colors or not
     * @param out
     *            the stream to print the usage help message to
     * @param err
     *            the error stream to print any diagnostic messages to, in addition to the
     *            output from the exception handler
     */
    void init(CommandLine helpCommandLine, Ansi ansi, PrintStream out, PrintStream err);
}