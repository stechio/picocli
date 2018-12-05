package picocli.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import picocli.CommandLine;
import picocli.except.PicocliException;
import picocli.util.Assert;

/**
 * Encapsulates the result of parsing an array of command line arguments.
 * 
 * @since 3.0
 */
public class ParseResult {
    /** Creates and returns a new {@code ParseResult.Builder} for the specified command spec. */
    public static ParseResult.Builder builder(CommandSpec commandSpec) {
        return new Builder(commandSpec);
    }

    /** Builds immutable {@code ParseResult} instances. */
    public static class Builder {
        private final CommandSpec commandSpec;
        private final Set<OptionSpec> options = new LinkedHashSet<OptionSpec>();
        private final Set<PositionalParamSpec> positionals = new LinkedHashSet<PositionalParamSpec>();
        //TODO:private scope
        public final List<String> unmatched = new ArrayList<String>();
        private final List<String> originalArgList = new ArrayList<String>();
        private final List<List<PositionalParamSpec>> positionalParams = new ArrayList<List<PositionalParamSpec>>();
        private ParseResult subcommand;
        //TODO:private scope
        public boolean usageHelpRequested;
        public boolean versionHelpRequested;
        boolean isInitializingDefaultValues;
        private List<Exception> errors = new ArrayList<Exception>(1);
        List<Object> nowProcessing;

        private Builder(CommandSpec spec) {
            commandSpec = Assert.notNull(spec, "commandSpec");
        }

        /**
         * Creates and returns a new {@code ParseResult} instance for this builder's configuration.
         */
        public ParseResult build() {
            return new ParseResult(this);
        }

        void nowProcessing(ArgSpec spec, Object value) {
            if (nowProcessing != null && !isInitializingDefaultValues) {
                nowProcessing.add(spec.isPositional() ? spec : value);
            }
        }

        /**
         * Adds the specified {@code OptionSpec} or {@code PositionalParamSpec} to the list of
         * options and parameters that were matched on the command line.
         * 
         * @param arg
         *            the matched {@code OptionSpec} or {@code PositionalParamSpec}
         * @param position
         *            the command line position at which the {@code PositionalParamSpec} was
         *            matched. Ignored for {@code OptionSpec}s.
         * @return this builder for method chaining
         */
        public ParseResult.Builder add(ArgSpec arg, int position) {
            if (arg.isOption()) {
                addOption((OptionSpec) arg);
            } else {
                addPositionalParam((PositionalParamSpec) arg, position);
            }
            return this;
        }

        /**
         * Adds the specified {@code OptionSpec} to the list of options that were matched on the
         * command line.
         */
        public ParseResult.Builder addOption(OptionSpec option) {
            if (!isInitializingDefaultValues) {
                options.add(option);
            }
            return this;
        }

        /**
         * Adds the specified {@code PositionalParamSpec} to the list of parameters that were
         * matched on the command line.
         * 
         * @param positionalParam
         *            the matched {@code PositionalParamSpec}
         * @param position
         *            the command line position at which the {@code PositionalParamSpec} was
         *            matched.
         * @return this builder for method chaining
         */
        public ParseResult.Builder addPositionalParam(PositionalParamSpec positionalParam,
                int position) {
            if (isInitializingDefaultValues) {
                return this;
            }
            positionals.add(positionalParam);
            while (positionalParams.size() <= position) {
                positionalParams.add(new ArrayList<PositionalParamSpec>());
            }
            positionalParams.get(position).add(positionalParam);
            return this;
        }

        /**
         * Adds the specified command line argument to the list of unmatched command line arguments.
         */
        public ParseResult.Builder addUnmatched(String arg) {
            unmatched.add(arg);
            return this;
        }

        /**
         * Adds all elements of the specified command line arguments stack to the list of unmatched
         * command line arguments.
         */
        public ParseResult.Builder addUnmatched(Stack<String> args) {
            while (!args.isEmpty()) {
                addUnmatched(args.pop());
            }
            return this;
        }

        /**
         * Sets the specified {@code ParseResult} for a subcommand that was matched on the command
         * line.
         */
        public ParseResult.Builder subcommand(ParseResult subcommand) {
            this.subcommand = subcommand;
            return this;
        }

        /** Sets the specified command line arguments that were parsed. */
        public ParseResult.Builder originalArgs(String[] originalArgs) {
            originalArgList.addAll(Arrays.asList(originalArgs));
            return this;
        }

        void addStringValue(ArgSpec argSpec, String value) {
            if (!isInitializingDefaultValues) {
                argSpec.stringValues.add(value);
            }
        }

        void addOriginalStringValue(ArgSpec argSpec, String value) {
            if (!isInitializingDefaultValues) {
                argSpec.originalStringValues.add(value);
            }
        }

        void addTypedValues(ArgSpec argSpec, int position, Object typedValue) {
            if (!isInitializingDefaultValues) {
                argSpec.typedValues.add(typedValue);
                argSpec.typedValueAtPosition.put(position, typedValue);
            }
        }

        public void addError(PicocliException ex) {
            errors.add(Assert.notNull(ex, "exception"));
        }
    }

    private final CommandSpec commandSpec;
    private final List<OptionSpec> matchedOptions;
    private final List<PositionalParamSpec> matchedUniquePositionals;
    private final List<String> originalArgs;
    private final List<String> unmatched;
    private final List<List<PositionalParamSpec>> matchedPositionalParams;
    private final List<Exception> errors;
    public final List<Object> tentativeMatch;

    private final ParseResult subcommand;
    private final boolean usageHelpRequested;
    private final boolean versionHelpRequested;

    private ParseResult(ParseResult.Builder builder) {
        commandSpec = builder.commandSpec;
        subcommand = builder.subcommand;
        matchedOptions = new ArrayList<OptionSpec>(builder.options);
        unmatched = new ArrayList<String>(builder.unmatched);
        originalArgs = new ArrayList<String>(builder.originalArgList);
        matchedUniquePositionals = new ArrayList<PositionalParamSpec>(builder.positionals);
        matchedPositionalParams = new ArrayList<List<PositionalParamSpec>>(
                builder.positionalParams);
        errors = new ArrayList<Exception>(builder.errors);
        usageHelpRequested = builder.usageHelpRequested;
        versionHelpRequested = builder.versionHelpRequested;
        tentativeMatch = builder.nowProcessing;
    }

    /**
     * Returns the option with the specified short name, or {@code null} if no option with that name
     * was matched on the command line.
     * <p>
     * Use {@link OptionSpec#getValue() getValue} on the returned {@code OptionSpec} to get the
     * matched value (or values), converted to the type of the option. Alternatively, use
     * {@link OptionSpec#stringValues() stringValues} to get the matched String values after they
     * were {@linkplain OptionSpec#splitRegex() split} into parts, or
     * {@link OptionSpec#originalStringValues() originalStringValues} to get the original String
     * values that were matched on the command line, before any processing.
     * </p>
     * <p>
     * To get the {@linkplain OptionSpec#defaultValue() default value} of an option that was
     * {@linkplain #hasMatchedOption(char) <em>not</em> matched} on the command line, use
     * {@code parseResult.commandSpec().findOption(shortName).getValue()}.
     * </p>
     * 
     * @see CommandSpec#findOption(char)
     */
    public OptionSpec matchedOption(char shortName) {
        return CommandSpec.findOption(shortName, matchedOptions);
    }

    /**
     * Returns the option with the specified name, or {@code null} if no option with that name was
     * matched on the command line.
     * <p>
     * Use {@link OptionSpec#getValue() getValue} on the returned {@code OptionSpec} to get the
     * matched value (or values), converted to the type of the option. Alternatively, use
     * {@link OptionSpec#stringValues() stringValues} to get the matched String values after they
     * were {@linkplain OptionSpec#splitRegex() split} into parts, or
     * {@link OptionSpec#originalStringValues() originalStringValues} to get the original String
     * values that were matched on the command line, before any processing.
     * </p>
     * <p>
     * To get the {@linkplain OptionSpec#defaultValue() default value} of an option that was
     * {@linkplain #hasMatchedOption(String) <em>not</em> matched} on the command line, use
     * {@code parseResult.commandSpec().findOption(String).getValue()}.
     * </p>
     * 
     * @see CommandSpec#findOption(String)
     * @param name
     *            used to search the matched options. May be an alias of the option name that was
     *            actually specified on the command line. The specified name may include option name
     *            prefix characters or not.
     */
    public OptionSpec matchedOption(String name) {
        return CommandSpec.findOption(name, matchedOptions);
    }

    /**
     * Returns the first {@code PositionalParamSpec} that matched an argument at the specified
     * position, or {@code null} if no positional parameters were matched at that position.
     */
    public PositionalParamSpec matchedPositional(int position) {
        if (matchedPositionalParams.size() <= position
                || matchedPositionalParams.get(position).isEmpty()) {
            return null;
        }
        return matchedPositionalParams.get(position).get(0);
    }

    /**
     * Returns all {@code PositionalParamSpec} objects that matched an argument at the specified
     * position, or an empty list if no positional parameters were matched at that position.
     */
    public List<PositionalParamSpec> matchedPositionals(int position) {
        if (matchedPositionalParams.size() <= position) {
            return Collections.emptyList();
        }
        return matchedPositionalParams.get(position) == null
                ? Collections.<PositionalParamSpec>emptyList()
                : matchedPositionalParams.get(position);
    }

    /** Returns the {@code CommandSpec} for the matched command. */
    public CommandSpec commandSpec() {
        return commandSpec;
    }

    /**
     * Returns whether an option whose aliases include the specified short name was matched on the
     * command line.
     * 
     * @param shortName
     *            used to search the matched options. May be an alias of the option name that was
     *            actually specified on the command line.
     */
    public boolean hasMatchedOption(char shortName) {
        return matchedOption(shortName) != null;
    }

    /**
     * Returns whether an option whose aliases include the specified name was matched on the command
     * line.
     * 
     * @param name
     *            used to search the matched options. May be an alias of the option name that was
     *            actually specified on the command line. The specified name may include option name
     *            prefix characters or not.
     */
    public boolean hasMatchedOption(String name) {
        return matchedOption(name) != null;
    }

    /** Returns whether the specified option was matched on the command line. */
    public boolean hasMatchedOption(OptionSpec option) {
        return matchedOptions.contains(option);
    }

    /** Returns whether a positional parameter was matched at the specified position. */
    public boolean hasMatchedPositional(int position) {
        return matchedPositional(position) != null;
    }

    /** Returns whether the specified positional parameter was matched on the command line. */
    public boolean hasMatchedPositional(PositionalParamSpec positional) {
        return matchedUniquePositionals.contains(positional);
    }

    /** Returns a list of matched options, in the order they were found on the command line. */
    public List<OptionSpec> matchedOptions() {
        return Collections.unmodifiableList(matchedOptions);
    }

    /** Returns a list of matched positional parameters. */
    public List<PositionalParamSpec> matchedPositionals() {
        return Collections.unmodifiableList(matchedUniquePositionals);
    }

    /**
     * Returns a list of command line arguments that did not match any options or positional
     * parameters.
     */
    public List<String> unmatched() {
        return Collections.unmodifiableList(unmatched);
    }

    /** Returns the command line arguments that were parsed. */
    public List<String> originalArgs() {
        return Collections.unmodifiableList(originalArgs);
    }

    /**
     * If {@link ParserSpec#collectErrors} is {@code true}, returns the list of exceptions that were
     * encountered during parsing, otherwise, returns an empty list.
     * 
     * @since 3.2
     */
    public List<Exception> errors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Returns the command line argument value of the option with the specified name, converted to
     * the {@linkplain OptionSpec#type() type} of the option, or the specified default value if no
     * option with the specified name was matched.
     */
    public <T> T matchedOptionValue(char shortName, T defaultValue) {
        return matchedOptionValue(matchedOption(shortName), defaultValue);
    }

    /**
     * Returns the command line argument value of the option with the specified name, converted to
     * the {@linkplain OptionSpec#type() type} of the option, or the specified default value if no
     * option with the specified name was matched.
     */
    public <T> T matchedOptionValue(String name, T defaultValue) {
        return matchedOptionValue(matchedOption(name), defaultValue);
    }

    /**
     * Returns the command line argument value of the specified option, converted to the
     * {@linkplain OptionSpec#type() type} of the option, or the specified default value if the
     * specified option is {@code null}.
     */
    @SuppressWarnings("unchecked")
    private <T> T matchedOptionValue(OptionSpec option, T defaultValue) {
        return option == null ? defaultValue : (T) option.getValue();
    }

    /**
     * Returns the command line argument value of the positional parameter at the specified
     * position, converted to the {@linkplain PositionalParamSpec#type() type} of the positional
     * parameter, or the specified default value if no positional parameter was matched at that
     * position.
     */
    public <T> T matchedPositionalValue(int position, T defaultValue) {
        return matchedPositionalValue(matchedPositional(position), defaultValue);
    }

    /**
     * Returns the command line argument value of the specified positional parameter, converted to
     * the {@linkplain PositionalParamSpec#type() type} of the positional parameter, or the
     * specified default value if the specified positional parameter is {@code null}.
     */
    @SuppressWarnings("unchecked")
    private <T> T matchedPositionalValue(PositionalParamSpec positional, T defaultValue) {
        return positional == null ? defaultValue : (T) positional.getValue();
    }

    /**
     * Returns {@code true} if a subcommand was matched on the command line, {@code false}
     * otherwise.
     */
    public boolean hasSubcommand() {
        return subcommand != null;
    }

    /**
     * Returns the {@code ParseResult} for the subcommand of this command that was matched on the
     * command line, or {@code null} if no subcommand was matched.
     */
    public ParseResult subcommand() {
        return subcommand;
    }

    /**
     * Returns {@code true} if one of the options that was matched on the command line is a
     * {@link OptionSpec#usageHelp() usageHelp} option.
     */
    public boolean isUsageHelpRequested() {
        return usageHelpRequested;
    }

    /**
     * Returns {@code true} if one of the options that was matched on the command line is a
     * {@link OptionSpec#versionHelp() versionHelp} option.
     */
    public boolean isVersionHelpRequested() {
        return versionHelpRequested;
    }

    /**
     * Returns this {@code ParseResult} as a list of {@code CommandLine} objects, one for each
     * matched command/subcommand. For backwards compatibility with pre-3.0 methods.
     */
    public List<CommandLine> asCommandLineList() {
        List<CommandLine> result = new ArrayList<CommandLine>();
        ParseResult pr = this;
        while (pr != null) {
            result.add(pr.commandSpec().commandLine());
            pr = pr.hasSubcommand() ? pr.subcommand() : null;
        }
        return result;
    }
}