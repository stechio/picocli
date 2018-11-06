package picocli.model;

import java.util.Arrays;
import java.util.HashSet;

import picocli.CommandLine;
import picocli.CommandLine.ITypeConverter;
import picocli.annots.Option;
import picocli.excepts.InitializationException;
import picocli.util.Assert;
import picocli.util.Comparators;

/**
 * The {@code OptionSpec} class models aspects of a <em>named option</em> of a
 * {@linkplain CommandSpec command}, including whether it is required or optional, the
 * option parameters supported (or required) by the option, and attributes for the usage
 * help message describing the option.
 * <p>
 * An option has one or more names. The option is matched when the parser encounters one of
 * the option names in the command line arguments. Depending on the option's {@link #arity()
 * arity}, the parser may expect it to have option parameters. The parser will call
 * {@link #setValue(Object) setValue} on the matched option for each of the option
 * parameters encountered.
 * </p>
 * <p>
 * For multi-value options, the {@code type} may be an array, a {@code Collection} or a
 * {@code Map}. In this case the parser will get the data structure by calling
 * {@link #getValue() getValue} and modify the contents of this data structure. (In the case
 * of arrays, the array is replaced with a new instance with additional elements.)
 * </p>
 * <p>
 * Before calling the setter, picocli converts the option parameter value from a String to
 * the option parameter's type.
 * </p>
 * <ul>
 * <li>If a option-specific {@link #converters() converter} is configured, this will be used
 * for type conversion. If the option's type is a {@code Map}, the map may have different
 * types for its keys and its values, so {@link #converters() converters} should provide two
 * converters: one for the map keys and one for the map values.</li>
 * <li>Otherwise, the option's {@link #type() type} is used to look up a converter in the
 * list of {@linkplain CommandLine#registerConverter(Class, ITypeConverter) registered
 * converters}. For multi-value options, the {@code type} may be an array, or a
 * {@code Collection} or a {@code Map}. In that case the elements are converted based on the
 * option's {@link #auxiliaryTypes() auxiliaryTypes}. The auxiliaryType is used to look up
 * the converter(s) to use to convert the individual parameter values. Maps may have
 * different types for its keys and its values, so {@link #auxiliaryTypes() auxiliaryTypes}
 * should provide two types: one for the map keys and one for the map values.</li>
 * </ul>
 * <p>
 * {@code OptionSpec} objects are used by the picocli command line interpreter and help
 * message generator. Picocli can construct an {@code OptionSpec} automatically from fields
 * and methods with {@link Option @Option} annotations. Alternatively an {@code OptionSpec}
 * can be constructed programmatically.
 * </p>
 * <p>
 * When an {@code OptionSpec} is created from an {@link Option @Option} -annotated field or
 * method, it is "bound" to that field or method: this field is set (or the method is
 * invoked) when the option is matched and {@link #setValue(Object) setValue} is called.
 * Programmatically constructed {@code OptionSpec} instances will remember the value passed
 * to the {@link #setValue(Object) setValue} method so it can be retrieved with the
 * {@link #getValue() getValue} method. This behaviour can be customized by installing a
 * custom {@link IGetter} and {@link ISetter} on the {@code OptionSpec}.
 * </p>
 * 
 * @since 3.0
 */
public class OptionSpec extends ArgSpec {
    private String[] names;
    private boolean help;
    private boolean usageHelp;
    private boolean versionHelp;

    public static OptionSpec.Builder builder(String name, String... names) {
        String[] copy = new String[Assert.notNull(names, "names").length + 1];
        copy[0] = Assert.notNull(name, "name");
        System.arraycopy(names, 0, copy, 1, names.length);
        return new Builder(copy);
    }

    public static OptionSpec.Builder builder(String[] names) {
        return new Builder(names);
    }

    /**
     * Ensures all attributes of this {@code OptionSpec} have a valid value; throws an
     * {@link InitializationException} if this cannot be achieved.
     */
    private OptionSpec(Builder builder) {
        super(builder);
        names = builder.names;
        help = builder.help;
        usageHelp = builder.usageHelp;
        versionHelp = builder.versionHelp;

        if (names == null || names.length == 0 || Arrays.asList(names).contains("")) {
            throw new InitializationException("Invalid names: " + Arrays.toString(names));
        }
        if (toString() == null) {
            toString = "option " + longestName();
        }

        //                if (arity().max == 0 && !(isBoolean(type()) || (isMultiValue() && isBoolean(auxiliaryTypes()[0])))) {
        //                    throw new InitializationException("Option " + longestName() + " is not a boolean so should not be defined with arity=" + arity());
        //                }
    }

    /**
     * Returns a new Builder initialized with the attributes from this {@code OptionSpec}.
     * Calling {@code build} immediately will return a copy of this {@code OptionSpec}.
     * 
     * @return a builder that can create a copy of this spec
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public boolean isOption() {
        return true;
    }

    @Override
    public boolean isPositional() {
        return false;
    }

    public boolean internalShowDefaultValue(boolean usageMessageShowDefaults) {
        return super.internalShowDefaultValue(usageMessageShowDefaults) && !help()
                && !versionHelp() && !usageHelp();
    }

    /**
     * Returns the description template of this option, before variables are
     * {@linkplain Option#description() rendered}. If a resource bundle has been
     * {@linkplain ArgSpec#messages(Messages) set}, this method will first try to find a
     * value in the resource bundle: If the resource bundle has no entry for the
     * {@code fully qualified commandName + "." + descriptionKey} or for the unqualified
     * {@code descriptionKey}, an attempt is made to find the option description using any
     * of the option names (without leading hyphens) as key, first with the
     * {@code fully qualified commandName + "."} prefix, then without.
     * 
     * @see CommandSpec#qualifiedName(String)
     * @see Option#description()
     */
    @Override
    public String[] description() {
        if (messages() == null) {
            return super.description();
        }
        String[] newValue = messages().getStringArray(descriptionKey(), null);
        if (newValue != null) {
            return newValue;
        }
        for (String name : names()) {
            newValue = messages().getStringArray(CommandSpec.stripPrefix(name), null);
            if (newValue != null) {
                return newValue;
            }
        }
        return super.description();
    }

    /**
     * Returns one or more option names. The returned array will contain at least one option
     * name.
     * 
     * @see Option#names()
     */
    public String[] names() {
        return names.clone();
    }

    /** Returns the longest {@linkplain #names() option name}. */
    public String longestName() {
        return Comparators.Length.sortDesc(names.clone())[0];
    }

    /** Returns the shortest {@linkplain #names() option name}. */
    public String shortestName() {
        return Comparators.Length.sortAsc(names.clone())[0];
    }

    /**
     * Returns whether this option disables validation of the other arguments.
     * 
     * @see Option#help()
     * @deprecated Use {@link #usageHelp()} and {@link #versionHelp()} instead.
     */
    @Deprecated
    public boolean help() {
        return help;
    }

    /**
     * Returns whether this option allows the user to request usage help.
     * 
     * @see Option#usageHelp()
     */
    public boolean usageHelp() {
        return usageHelp;
    }

    /**
     * Returns whether this option allows the user to request version information.
     * 
     * @see Option#versionHelp()
     */
    public boolean versionHelp() {
        return versionHelp;
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof OptionSpec)) {
            return false;
        }
        OptionSpec other = (OptionSpec) obj;
        boolean result = super.equalsImpl(other) && help == other.help
                && usageHelp == other.usageHelp && versionHelp == other.versionHelp
                && new HashSet<String>(Arrays.asList(names))
                        .equals(new HashSet<String>(Arrays.asList(other.names)));
        return result;
    }

    public int hashCode() {
        return super.hashCodeImpl() + 37 * Assert.hashCode(help)
                + 37 * Assert.hashCode(usageHelp) + 37 * Assert.hashCode(versionHelp)
                + 37 * Arrays.hashCode(names);
    }

    /**
     * Builder responsible for creating valid {@code OptionSpec} objects.
     * 
     * @since 3.0
     */
    public static class Builder extends ArgSpec.Builder<Builder> {
        private String[] names;
        private boolean help;
        private boolean usageHelp;
        private boolean versionHelp;

        private Builder(String[] names) {
            this.names = names.clone();
        }

        private Builder(OptionSpec original) {
            super(original);
            names = original.names;
            help = original.help;
            usageHelp = original.usageHelp;
            versionHelp = original.versionHelp;
        }

        /** Returns a valid {@code OptionSpec} instance. */
        @Override
        public OptionSpec build() {
            return new OptionSpec(this);
        }

        /** Returns this builder. */
        @Override
        protected Builder self() {
            return this;
        }

        /**
         * Returns one or more option names. At least one option name is required.
         * 
         * @see Option#names()
         */
        public String[] names() {
            return names;
        }

        /**
         * Returns whether this option disables validation of the other arguments.
         * 
         * @see Option#help()
         * @deprecated Use {@link #usageHelp()} and {@link #versionHelp()} instead.
         */
        @Deprecated
        public boolean help() {
            return help;
        }

        /**
         * Returns whether this option allows the user to request usage help.
         * 
         * @see Option#usageHelp()
         */
        public boolean usageHelp() {
            return usageHelp;
        }

        /**
         * Returns whether this option allows the user to request version information.
         * 
         * @see Option#versionHelp()
         */
        public boolean versionHelp() {
            return versionHelp;
        }

        /**
         * Replaces the option names with the specified values. At least one option name is
         * required, and returns this builder.
         * 
         * @return this builder instance to provide a fluent interface
         */
        public Builder names(String... names) {
            this.names = Assert.notNull(names, "names").clone();
            return self();
        }

        /**
         * Sets whether this option disables validation of the other arguments, and returns
         * this builder.
         */
        public Builder help(boolean help) {
            this.help = help;
            return self();
        }

        /**
         * Sets whether this option allows the user to request usage help, and returns this
         * builder.
         */
        public Builder usageHelp(boolean usageHelp) {
            this.usageHelp = usageHelp;
            return self();
        }

        /**
         * Sets whether this option allows the user to request version information, and
         * returns this builder.
         */
        public Builder versionHelp(boolean versionHelp) {
            this.versionHelp = versionHelp;
            return self();
        }
    }
}
