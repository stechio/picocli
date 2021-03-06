package picocli.except;

import picocli.model.ArgSpec;

/**
 * Exception indicating that multiple fields have been annotated with the same Option name.
 */
public class DuplicateOptionAnnotationsException extends InitializationException {
    private static final long serialVersionUID = -3355128012575075641L;

    public DuplicateOptionAnnotationsException(String msg) {
        super(msg);
    }

    public static DuplicateOptionAnnotationsException create(String name, ArgSpec argSpec1,
            ArgSpec argSpec2) {
        return new DuplicateOptionAnnotationsException("Option name '" + name + "' is used by both "
                + argSpec1.toString() + " and " + argSpec2.toString());
    }
}