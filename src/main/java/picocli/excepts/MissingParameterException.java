package picocli.excepts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import picocli.CommandLine;
import picocli.CommandLine.Model;

/**
 * Exception indicating that a required parameter was not specified.
 */
public class MissingParameterException extends ParameterException {
    private static final long serialVersionUID = 5075678535706338753L;
    private final List<Model.ArgSpec> missing;

    public MissingParameterException(CommandLine commandLine, Model.ArgSpec missing, String msg) {
        this(commandLine, Arrays.asList(missing), msg);
    }

    public MissingParameterException(CommandLine commandLine, Collection<Model.ArgSpec> missing,
            String msg) {
        super(commandLine, msg);
        this.missing = Collections.unmodifiableList(new ArrayList<Model.ArgSpec>(missing));
    }

    public List<Model.ArgSpec> getMissing() {
        return missing;
    }

    public static MissingParameterException create(CommandLine cmd,
            Collection<Model.ArgSpec> missing, String separator) {
        if (missing.size() == 1) {
            return new MissingParameterException(cmd, missing, "Missing required option '"
                    + describe(missing.iterator().next(), separator) + "'");
        }
        List<String> names = new ArrayList<String>(missing.size());
        for (Model.ArgSpec argSpec : missing) {
            names.add(describe(argSpec, separator));
        }
        return new MissingParameterException(cmd, missing,
                "Missing required options " + names.toString());
    }

    private static String describe(Model.ArgSpec argSpec, String separator) {
        String prefix = (argSpec.isOption()) ? ((Model.OptionSpec) argSpec).longestName() + separator
                : "params[" + ((Model.PositionalParamSpec) argSpec).index() + "]" + separator;
        return prefix + argSpec.paramLabel();
    }
}