package picocli.excepts;

/**
 * Exception indicating a problem during {@code CommandLine} initialization.
 * 
 * @since 2.0
 */
public class InitializationException extends PicocliException {
    private static final long serialVersionUID = 8423014001666638895L;

    public InitializationException(String msg) {
        super(msg);
    }

    public InitializationException(String msg, Exception ex) {
        super(msg, ex);
    }

    public InitializationException(Exception ex) {
        super(ex);
    }
}