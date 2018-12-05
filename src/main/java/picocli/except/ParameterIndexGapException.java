package picocli.except;

import picocli.annot.Parameters;

/**
 * Exception indicating that there was a gap in the indices of the fields annotated with
 * {@link Parameters}.
 */
public class ParameterIndexGapException extends InitializationException {
    private static final long serialVersionUID = -1520981133257618319L;

    public ParameterIndexGapException(String msg) {
        super(msg);
    }
}