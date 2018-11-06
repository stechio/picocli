package picocli.excepts;

import picocli.CommandLine;
import picocli.util.Assert;

/**
 * Exception indicating a problem while invoking a command or subcommand.
 * 
 * @since 2.0
 */
public class ExecutionException extends PicocliException {
    private static final long serialVersionUID = 7764539594267007998L;
    private final CommandLine commandLine;

    public ExecutionException(CommandLine commandLine, String msg) {
        super(msg);
        this.commandLine = Assert.notNull(commandLine, "commandLine");
    }

    public ExecutionException(CommandLine commandLine, String msg, Exception ex) {
        super(msg, ex);
        this.commandLine = Assert.notNull(commandLine, "commandLine");
    }

    /**
     * Returns the {@code CommandLine} object for the (sub)command that could not be invoked.
     * 
     * @return the {@code CommandLine} object for the (sub)command where invocation failed.
     */
    public CommandLine getCommandLine() {
        return commandLine;
    }
}