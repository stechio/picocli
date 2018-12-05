package picocli.except;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import picocli.CommandLine;
import picocli.annot.Option;
import picocli.annot.Parameters;
import picocli.model.CommandSpec;
import picocli.util.CollectionUtilsExt;

/**
 * Exception indicating that a command line argument could not be mapped to any of the fields
 * annotated with {@link Option} or {@link Parameters}.
 */
public class UnmatchedArgumentException extends ParameterException {
    private static final long serialVersionUID = -8700426380701452440L;
    private List<String> unmatched;

    public UnmatchedArgumentException(CommandLine commandLine, String msg) {
        super(commandLine, msg);
    }

    public UnmatchedArgumentException(CommandLine commandLine, Stack<String> args) {
        this(commandLine, new ArrayList<String>(CollectionUtilsExt.reverse(args)));
    }

    public UnmatchedArgumentException(CommandLine commandLine, List<String> args) {
        this(commandLine,
                describe(args, commandLine) + (args.size() == 1 ? ": " : "s: ") + str(args));
        unmatched = args;
    }

    /**
     * Returns {@code true} and prints suggested solutions to the specified stream if such
     * solutions exist, otherwise returns {@code false}.
     * 
     * @since 3.3.0
     */
    public static boolean printSuggestions(ParameterException ex, PrintStream out) {
        return ex instanceof UnmatchedArgumentException
                && ((UnmatchedArgumentException) ex).printSuggestions(out);
    }

    /**
     * Returns the unmatched command line arguments.
     * 
     * @since 3.3.0
     */
    public List<String> getUnmatched() {
        return Collections.unmodifiableList(unmatched);
    }

    /**
     * Returns {@code true} if the first unmatched command line arguments resembles an option,
     * {@code false} otherwise.
     * 
     * @since 3.3.0
     */
    public boolean isUnknownOption() {
        return isUnknownOption(unmatched, getCommandLine());
    }

    /**
     * Returns {@code true} and prints suggested solutions to the specified stream if such
     * solutions exist, otherwise returns {@code false}.
     * 
     * @since 3.3.0
     */
    public boolean printSuggestions(PrintStream out) {
        List<String> suggestions = getSuggestions();
        if (!suggestions.isEmpty()) {
            out.println(isUnknownOption() ? "Possible solutions: " + str(suggestions)
                    : "Did you mean: " + str(suggestions).replace(", ", " or ") + "?");
        }
        return !suggestions.isEmpty();
    }

    /**
     * Returns suggested solutions if such solutions exist, otherwise returns an empty list.
     * 
     * @since 3.3.0
     */
    public List<String> getSuggestions() {
        if (unmatched == null || unmatched.isEmpty()) {
            return Collections.emptyList();
        }
        String arg = unmatched.get(0);
        String stripped = CommandSpec.stripPrefix(arg);
        CommandSpec spec = getCommandLine().getCommandSpec();
        if (spec.resemblesOption(arg, null)) {
            return spec.findOptionNamesWithPrefix(
                    stripped.substring(0, Math.min(2, stripped.length())));
        } else if (!spec.subcommands().isEmpty()) {
            List<String> mostSimilar = CosineSimilarity.mostSimilar(arg,
                    spec.subcommands().keySet());
            return mostSimilar.subList(0, Math.min(3, mostSimilar.size()));
        }
        return Collections.emptyList();
    }

    private static boolean isUnknownOption(List<String> unmatch, CommandLine cmd) {
        return unmatch != null && !unmatch.isEmpty()
                && cmd.getCommandSpec().resemblesOption(unmatch.get(0), null);
    }

    private static String describe(List<String> unmatch, CommandLine cmd) {
        return isUnknownOption(unmatch, cmd) ? "Unknown option" : "Unmatched argument";
    }

    static String str(List<String> list) {
        String s = list.toString();
        return s.substring(0, s.length() - 1).substring(1);
    }
}