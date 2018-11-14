package picocli.excepts;

/**
 * Base class of all exceptions thrown by {@code picocli.CommandLine}.
 * <h2>Class Diagram of the Picocli Exceptions</h2>
 * <p>
 * <img src="doc-files/class-diagram-exceptions.png" alt="Class Diagram of the Picocli
 * Exceptions">
 * </p>
 * 
 * @since 2.0
 */
public class PicocliException extends RuntimeException {
    private static final long serialVersionUID = -2574128880125050818L;

    public PicocliException(String msg) {
        super(msg);
    }

    public PicocliException(String msg, Throwable t) {
        super(msg, t);
    }

    public PicocliException(Throwable t) {
        super(t);
    }
}