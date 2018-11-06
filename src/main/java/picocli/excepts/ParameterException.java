package picocli.excepts;

import java.util.Arrays;

import picocli.CommandLine;
import picocli.CommandLine.Model;
import picocli.util.Assert;

/** Exception indicating something went wrong while parsing command line options. */
public class ParameterException extends PicocliException {
    private static final long serialVersionUID = 1477112829129763139L;
    private final CommandLine commandLine;
    private Model.ArgSpec argSpec = null;
    private String value = null;

    /**
     * Constructs a new ParameterException with the specified CommandLine and error message.
     * 
     * @param commandLine
     *            the command or subcommand whose input was invalid
     * @param msg
     *            describes the problem
     * @since 2.0
     */
    public ParameterException(CommandLine commandLine, String msg) {
        super(msg);
        this.commandLine = Assert.notNull(commandLine, "commandLine");
    }

    /**
     * Constructs a new ParameterException with the specified CommandLine and error message.
     * 
     * @param commandLine
     *            the command or subcommand whose input was invalid
     * @param msg
     *            describes the problem
     * @param t
     *            the throwable that caused this ParameterException
     * @since 2.0
     */
    public ParameterException(CommandLine commandLine, String msg, Throwable t) {
        super(msg, t);
        this.commandLine = Assert.notNull(commandLine, "commandLine");
    }

    /**
     * Constructs a new ParameterException with the specified CommandLine and error message.
     * 
     * @param commandLine
     *            the command or subcommand whose input was invalid
     * @param msg
     *            describes the problem
     * @param t
     *            the throwable that caused this ParameterException
     * @param argSpec
     *            the argSpec that caused this ParameterException
     * @param value
     *            the value that caused this ParameterException
     * @since 3.2
     */
    public ParameterException(CommandLine commandLine, String msg, Throwable t, Model.ArgSpec argSpec,
            String value) {
        super(msg, t);
        this.commandLine = Assert.notNull(commandLine, "commandLine");
        if (argSpec == null && value == null) {
            throw new IllegalArgumentException("ArgSpec and value cannot both be null");
        }
        this.argSpec = argSpec;
        this.value = value;
    }

    /**
     * Constructs a new ParameterException with the specified CommandLine and error message.
     * 
     * @param commandLine
     *            the command or subcommand whose input was invalid
     * @param msg
     *            describes the problem
     * @param argSpec
     *            the argSpec that caused this ParameterException
     * @param value
     *            the value that caused this ParameterException
     * @since 3.2
     */
    public ParameterException(CommandLine commandLine, String msg, Model.ArgSpec argSpec,
            String value) {
        super(msg);
        this.commandLine = Assert.notNull(commandLine, "commandLine");
        if (argSpec == null && value == null) {
            throw new IllegalArgumentException("ArgSpec and value cannot both be null");
        }
        this.argSpec = argSpec;
        this.value = value;
    }

    /**
     * Returns the {@code CommandLine} object for the (sub)command whose input could not be
     * parsed.
     * 
     * @return the {@code CommandLine} object for the (sub)command where parsing failed.
     * @since 2.0
     */
    public CommandLine getCommandLine() {
        return commandLine;
    }

    /**
     * Returns the {@code ArgSpec} object for the (sub)command whose input could not be parsed.
     * 
     * @return the {@code ArgSpec} object for the (sub)command where parsing failed.
     * @since 3.2
     */
    public Model.ArgSpec getArgSpec() {
        return argSpec;
    }

    /**
     * Returns the {@code String} value for the (sub)command whose input could not be parsed.
     * 
     * @return the {@code String} value for the (sub)command where parsing failed.
     * @since 3.2
     */
    public String getValue() {
        return value;
    }

    public static ParameterException create(CommandLine cmd, Exception ex, String arg, int i,
            String[] args) {
        String msg = ex.getClass().getSimpleName() + ": " + ex.getLocalizedMessage()
                + " while processing argument at or before arg[" + i + "] '" + arg + "' in "
                + Arrays.toString(args) + ": " + ex.toString();
        return new ParameterException(cmd, msg, ex, null, arg);
    }
}