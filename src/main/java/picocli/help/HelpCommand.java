package picocli.help;

import java.io.PrintStream;

import picocli.CommandLine;
import picocli.annots.Command;
import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.excepts.ParameterException;
import picocli.model.Messages;
import picocli.util.Assert;

/**
 * Help command that can be installed as a subcommand on all application commands. When invoked
 * with a subcommand argument, it prints usage help for the specified subcommand. For example:
 * 
 * <pre>
 *
 * // print help for subcommand
 * command help subcommand
 * </pre>
 * <p>
 * When invoked without additional parameters, it prints usage help for the parent command. For
 * example:
 * </p>
 * 
 * <pre>
 *
 * // print help for command
 * command help
 * </pre>
 * 
 * For {@linkplain Messages internationalization}: this command has a {@code --help} option with
 * {@code descriptionKey = "helpCommand.help"}, and a {@code COMMAND} positional parameter with
 * {@code descriptionKey = "helpCommand.command"}.
 * 
 * @since 3.0
 */
@Command(name = "help", header = "Displays help information about the specified command", synopsisHeading = "%nUsage: ", helpCommand = true, description = {
        "%nWhen no COMMAND is given, the usage help for the main command is displayed.",
        "If a COMMAND is specified, the help for that command is shown.%n" })
public final class HelpCommand implements IHelpCommandInitializable, Runnable {

    @Option(names = { "-h",
            "--help" }, usageHelp = true, descriptionKey = "helpCommand.help", description = "Show usage help for the help command and exit.")
    private boolean helpRequested;

    @Parameters(paramLabel = "COMMAND", descriptionKey = "helpCommand.command", description = "The COMMAND to display the usage help message for.")
    private String[] commands = new String[0];

    private CommandLine self;
    private PrintStream out;
    private PrintStream err;
    private Ansi ansi;

    /**
     * Invokes {@link #usage(PrintStream, Help.Ansi) usage} for the specified command, or for
     * the parent command.
     */
    public void run() {
        CommandLine parent = self == null ? null : self.getParent();
        if (parent == null) {
            return;
        }
        if (commands.length > 0) {
            CommandLine subcommand = parent.getSubcommands().get(commands[0]);
            if (subcommand != null) {
                subcommand.usage(out, ansi);
            } else {
                throw new ParameterException(parent,
                        "Unknown subcommand '" + commands[0] + "'.", null, commands[0]);
            }
        } else {
            parent.usage(out, ansi);
        }
    }

    /** {@inheritDoc} */
    public void init(CommandLine helpCommandLine, Ansi ansi, PrintStream out, PrintStream err) {
        this.self = Assert.notNull(helpCommandLine, "helpCommandLine");
        this.ansi = Assert.notNull(ansi, "ansi");
        this.out = Assert.notNull(out, "out");
        this.err = Assert.notNull(err, "err");
    }
}