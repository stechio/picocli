package picocli.handler;

import java.io.PrintStream;

import picocli.help.Ansi;
import picocli.util.Assert;

/**
 * Abstract superclass for {@link IParseResultHandler2} and {@link IExceptionHandler2}
 * implementations.
 * <p>
 * Note that {@code AbstractHandler} is a generic type. This, along with the abstract
 * {@code self} method, allows method chaining to work properly in subclasses, without the need
 * for casts. An example subclass can look like this:
 * </p>
 * 
 * <pre>
 * {@code
 * class MyResultHandler extends AbstractHandler<MyReturnType, MyResultHandler> implements IParseResultHandler2<MyReturnType> {
 *
 *     public MyReturnType handleParseResult(ParseResult parseResult) { ... }
 *
 *     protected MyResultHandler self() { return this; }
 * }
 * }
 * </pre>
 * 
 * @param <R>
 *            the return type of this handler
 * @param <T>
 *            The type of the handler subclass; for fluent API method chaining
 * @since 3.0
 */
public abstract class AbstractHandler<R, T extends AbstractHandler<R, T>> {
    private Ansi ansi = Ansi.AUTO;
    private Integer exitCode;
    private PrintStream out = System.out;
    private PrintStream err = System.err;

    /**
     * Returns the stream to print command output to. Defaults to {@code System.out}, unless
     * {@link #useOut(PrintStream)} was called with a different stream.
     * <p>
     * {@code IParseResultHandler2} implementations should use this stream. By
     * <a href="http://www.gnu.org/prep/standards/html_node/_002d_002dhelp.html">convention</a>,
     * when the user requests help with a {@code --help} or similar option, the usage help
     * message is printed to the standard output stream so that it can be easily searched and
     * paged.
     * </p>
     */
    public PrintStream out() {
        return out;
    }

    /**
     * Returns the stream to print diagnostic messages to. Defaults to {@code System.err},
     * unless {@link #useErr(PrintStream)} was called with a different stream.
     * <p>
     * {@code IExceptionHandler2} implementations should use this stream to print error messages
     * (which may include a usage help message) when an unexpected error occurs.
     * </p>
     */
    public PrintStream err() {
        return err;
    }

    /**
     * Returns the ANSI style to use. Defaults to {@code Help.Ansi.AUTO}, unless
     * {@link #useAnsi(CommandLine.Help.Ansi)} was called with a different setting.
     */
    public Ansi ansi() {
        return ansi;
    }

    /**
     * Returns the exit code to use as the termination status, or {@code null} (the default) if
     * the handler should not call {@link System#exit(int)} after processing completes.
     * 
     * @see #andExit(int)
     */
    public Integer exitCode() {
        return exitCode;
    }

    /**
     * Returns {@code true} if an exit code was set with {@link #andExit(int)}, or {@code false}
     * (the default) if the handler should not call {@link System#exit(int)} after processing
     * completes.
     */
    public boolean hasExitCode() {
        return exitCode != null;
    }

    /**
     * Convenience method for subclasses that returns the specified result object if no exit
     * code was set, or otherwise, if an exit code {@linkplain #andExit(int) was set}, calls
     * {@code System.exit} with the configured exit code to terminate the currently running Java
     * virtual machine.
     */
    protected R returnResultOrExit(R result) {
        if (hasExitCode()) {
            System.exit(exitCode());
        }
        return result;
    }

    /**
     * Returns {@code this} to allow method chaining when calling the setters for a fluent API.
     */
    protected abstract T self();

    /**
     * Sets the stream to print command output to. For use by {@code IParseResultHandler2}
     * implementations.
     * 
     * @see #out()
     */
    public T useOut(PrintStream out) {
        this.out = Assert.notNull(out, "out");
        return self();
    }

    /**
     * Sets the stream to print diagnostic messages to. For use by {@code IExceptionHandler2}
     * implementations.
     * 
     * @see #err()
     */
    public T useErr(PrintStream err) {
        this.err = Assert.notNull(err, "err");
        return self();
    }

    /**
     * Sets the ANSI style to use.
     * 
     * @see #ansi()
     */
    public T useAnsi(Ansi ansi) {
        this.ansi = Assert.notNull(ansi, "ansi");
        return self();
    }

    /**
     * Indicates that the handler should call {@link System#exit(int)} after processing
     * completes and sets the exit code to use as the termination status.
     */
    public T andExit(int exitCode) {
        this.exitCode = exitCode;
        return self();
    }
}