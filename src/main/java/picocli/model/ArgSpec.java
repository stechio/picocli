package picocli.model;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import picocli.CommandLine;
import picocli.annots.Command;
import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.excepts.InitializationException;
import picocli.excepts.PicocliException;
import picocli.help.Help;
import picocli.util.Assert;
import picocli.util.ClassUtilsExt;
import picocli.util.ObjectUtilsExt;
import picocli.util.Tracer;

/**
 * Models the shared attributes of {@link OptionSpec} and {@link PositionalParamSpec}.
 * 
 * @since 3.0
 */
public abstract class ArgSpec {
    static final String DESCRIPTION_VARIABLE_DEFAULT_VALUE = "${DEFAULT-VALUE}";
    static final String DESCRIPTION_VARIABLE_COMPLETION_CANDIDATES = "${COMPLETION-CANDIDATES}";
    private static final String NO_DEFAULT_VALUE = "__no_default_value__";

    // help-related fields
    private final boolean hidden;
    private final String paramLabel;
    private final boolean hideParamSyntax;
    private final String[] description;
    private final String descriptionKey;
    private final Help.Visibility showDefaultValue;
    private Messages messages;
    //TODO:private scope
    CommandSpec commandSpec;

    // parser fields
    private final boolean interactive;
    private final boolean required;
    private final String splitRegex;
    private final Class<?> type;
    private final Class<?>[] auxiliaryTypes;
    private final ITypeConverter<?>[] converters;
    private final Iterable<String> completionCandidates;
    private final String defaultValue;
    private final Object initialValue;
    private final boolean hasInitialValue;
    private final IGetter getter;
    private final ISetter setter;
    private final Range arity;
    List<String> stringValues = new ArrayList<String>();
    List<String> originalStringValues = new ArrayList<String>();
    protected String toString;
    List<Object> typedValues = new ArrayList<Object>();
    Map<Integer, Object> typedValueAtPosition = new TreeMap<Integer, Object>();

    /** Constructs a new {@code ArgSpec}. */
    protected <T extends Builder<T>> ArgSpec(Builder<T> builder) {
        description = builder.description == null ? new String[0] : builder.description;
        descriptionKey = builder.descriptionKey;
        splitRegex = builder.splitRegex == null ? "" : builder.splitRegex;
        paramLabel = StringUtils.isBlank(builder.paramLabel) ? "PARAM" : builder.paramLabel;
        hideParamSyntax = builder.hideParamSyntax;
        converters = builder.converters == null ? new ITypeConverter<?>[0] : builder.converters;
        showDefaultValue = builder.showDefaultValue == null ? Help.Visibility.ON_DEMAND
                : builder.showDefaultValue;
        hidden = builder.hidden;
        interactive = builder.interactive;
        required = builder.required && builder.defaultValue == null; //#261 not required if it has a default
        defaultValue = builder.defaultValue;
        initialValue = builder.initialValue;
        hasInitialValue = builder.hasInitialValue;
        toString = builder.toString;
        getter = builder.getter;
        setter = builder.setter;

        Range tempArity = builder.arity;
        if (tempArity == null) {
            if (isOption()) {
                tempArity = (builder.type == null || ClassUtilsExt.isBoolean(builder.type))
                        ? Range.valueOf("0")
                        : Range.valueOf("1");
            } else {
                tempArity = Range.valueOf("1");
            }
            tempArity = tempArity.unspecified(true);
        }
        arity = tempArity;

        if (builder.type == null) {
            if (builder.auxiliaryTypes == null || builder.auxiliaryTypes.length == 0) {
                if (arity.isVariable || arity.max > 1) {
                    type = String[].class;
                } else if (arity.max == 1) {
                    type = String.class;
                } else {
                    type = isOption() ? boolean.class : String.class;
                }
            } else {
                type = builder.auxiliaryTypes[0];
            }
        } else {
            type = builder.type;
        }
        if (builder.auxiliaryTypes == null || builder.auxiliaryTypes.length == 0) {
            if (type.isArray()) {
                auxiliaryTypes = new Class<?>[] { type.getComponentType() };
            } else if (Collection.class.isAssignableFrom(type)) { // type is a collection but element type is unspecified
                auxiliaryTypes = new Class<?>[] { String.class }; // use String elements
            } else if (Map.class.isAssignableFrom(type)) { // type is a map but element type is unspecified
                auxiliaryTypes = new Class<?>[] { String.class, String.class }; // use String keys and String values
            } else {
                auxiliaryTypes = new Class<?>[] { type };
            }
        } else {
            auxiliaryTypes = builder.auxiliaryTypes;
        }
        if (builder.completionCandidates == null && auxiliaryTypes[0].isEnum()) {
            List<String> list = new ArrayList<String>();
            for (Object c : auxiliaryTypes[0].getEnumConstants()) {
                list.add(c.toString());
            }
            completionCandidates = Collections.unmodifiableList(list);
        } else {
            completionCandidates = builder.completionCandidates;
        }
        if (interactive && (arity.min != 1 || arity.max != 1)) {
            throw new InitializationException(
                    "Interactive options and positional parameters are only supported for arity=1, not for arity="
                            + arity);
        }
    }

    /**
     * Returns whether this is a required option or positional parameter.
     * 
     * @see Option#required()
     */
    public boolean required() {
        return required;
    }

    /**
     * Returns whether this option will prompt the user to enter a value on the command line.
     * 
     * @see Option#interactive()
     */
    public boolean interactive() {
        return interactive;
    }

    /**
     * Returns the description template of this option, before variables are rendered.
     * 
     * @see Option#description()
     */
    public String[] description() {
        return description.clone();
    }

    /**
     * Returns the description key of this arg spec, used to get the description from a resource
     * bundle.
     * 
     * @see Option#descriptionKey()
     * @see Parameters#descriptionKey()
     * @since 3.6
     */
    public String descriptionKey() {
        return descriptionKey;
    }

    /**
     * Returns the description of this option, after variables are rendered. Used when generating
     * the usage documentation.
     * 
     * @see Option#description()
     * @since 3.2
     */
    public String[] renderedDescription() {
        String[] desc = description();
        if (desc == null || desc.length == 0) {
            return desc;
        }
        StringBuilder candidates = new StringBuilder();
        if (completionCandidates() != null) {
            for (String c : completionCandidates()) {
                if (candidates.length() > 0) {
                    candidates.append(", ");
                }
                candidates.append(c);
            }
        }
        String defaultValueString = defaultValueString();
        String[] result = new String[desc.length];
        for (int i = 0; i < desc.length; i++) {
            result[i] = String.format(
                    desc[i].replace(DESCRIPTION_VARIABLE_DEFAULT_VALUE, defaultValueString).replace(
                            DESCRIPTION_VARIABLE_COMPLETION_CANDIDATES, candidates.toString()));
        }
        return result;
    }

    /**
     * Returns how many arguments this option or positional parameter requires.
     * 
     * @see Option#arity()
     */
    public Range arity() {
        return arity;
    }

    /**
     * Returns the name of the option or positional parameter used in the usage help message.
     * 
     * @see Option#paramLabel() {@link Parameters#paramLabel()}
     */
    public String paramLabel() {
        return paramLabel;
    }

    /**
     * Returns whether usage syntax decorations around the {@linkplain #paramLabel() paramLabel}
     * should be suppressed. The default is {@code false}: by default, the paramLabel is surrounded
     * with {@code '['} and {@code ']'} characters if the value is optional and followed by ellipses
     * ("...") when multiple values can be specified.
     * 
     * @since 3.6.0
     */
    public boolean hideParamSyntax() {
        return hideParamSyntax;
    }

    /**
     * Returns auxiliary type information used when the {@link #type()} is a generic
     * {@code Collection}, {@code Map} or an abstract class.
     * 
     * @see Option#type()
     */
    public Class<?>[] auxiliaryTypes() {
        return auxiliaryTypes.clone();
    }

    /**
     * Returns one or more {@link ITypeConverter type converters} to use to convert the
     * command line argument into a strongly typed value (or key-value pair for map fields). This is
     * useful when a particular option or positional parameter should use a custom conversion that
     * is different from the normal conversion for the arg spec's type.
     * 
     * @see Option#converter()
     */
    public ITypeConverter<?>[] converters() {
        return converters.clone();
    }

    /**
     * Returns a regular expression to split option parameter values or {@code ""} if the value
     * should not be split.
     * 
     * @see Option#split()
     */
    public String splitRegex() {
        return splitRegex;
    }

    /**
     * Returns whether this option should be excluded from the usage message.
     * 
     * @see Option#hidden()
     */
    public boolean hidden() {
        return hidden;
    }

    /**
     * Returns the type to convert the option or positional parameter to before
     * {@linkplain #setValue(Object) setting} the value.
     */
    public Class<?> type() {
        return type;
    }

    /**
     * Returns the default value of this option or positional parameter, before splitting and type
     * conversion. This method returns the programmatically set value; this may differ from the
     * default value that is actually used: if this ArgSpec is part of a CommandSpec with a
     * {@link IDefaultValueProvider}, picocli will first try to obtain the default value from the
     * default value provider, and this method is only called if the default provider is
     * {@code null} or returned a {@code null} value.
     * 
     * @return the programmatically set default value of this option/positional parameter, returning
     *         {@code null} means this option or positional parameter does not have a default
     * @see CommandSpec#defaultValueProvider()
     */
    public String defaultValue() {
        return defaultValue;
    }

    /**
     * Returns the initial value this option or positional parameter. If {@link #hasInitialValue()}
     * is true, the option will be reset to the initial value before parsing (regardless of whether
     * a default value exists), to clear values that would otherwise remain from parsing previous
     * input.
     */
    public Object initialValue() {
        return initialValue;
    }

    /**
     * Determines whether the option or positional parameter will be reset to the
     * {@link #initialValue()} before parsing new input.
     */
    public boolean hasInitialValue() {
        return hasInitialValue;
    }

    /**
     * Returns whether this option or positional parameter's default value should be shown in the
     * usage help.
     */
    public Help.Visibility showDefaultValue() {
        return showDefaultValue;
    }

    /**
     * Returns the default value String displayed in the description. If this ArgSpec is part of a
     * CommandSpec with a {@link IDefaultValueProvider}, this method will first try to obtain the
     * default value from the default value provider; if the provider is {@code null} or if it
     * returns a {@code null} value, then next any value set to {@link ArgSpec#defaultValue()} is
     * returned, and if this is also {@code null}, finally the {@linkplain ArgSpec#initialValue()
     * initial value} is returned.
     * 
     * @see CommandSpec#defaultValueProvider()
     * @see ArgSpec#defaultValue()
     */
    public String defaultValueString() {
        String fromProvider = null;
        IDefaultValueProvider defaultValueProvider = null;
        try {
            defaultValueProvider = commandSpec.defaultValueProvider();
            fromProvider = defaultValueProvider == null ? null
                    : defaultValueProvider.defaultValue(this);
        } catch (Exception ex) {
            new Tracer().info("Error getting default value for %s from %s: %s", this,
                    defaultValueProvider, ex);
        }
        String defaultVal = fromProvider == null ? this.defaultValue() : fromProvider;
        Object value = defaultVal == null ? initialValue() : defaultVal;
        if (value != null && value.getClass().isArray()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Array.getLength(value); i++) {
                sb.append(i > 0 ? ", " : "").append(Array.get(value, i));
            }
            return sb.insert(0, "[").append("]").toString();
        }
        return String.valueOf(value);
    }

    /**
     * Returns the explicitly set completion candidates for this option or positional parameter,
     * valid enum constant names, or {@code null} if this option or positional parameter does not
     * have any completion candidates and its type is not an enum.
     * 
     * @return the completion candidates for this option or positional parameter, valid enum
     *         constant names, or {@code null}
     * @since 3.2
     */
    public Iterable<String> completionCandidates() {
        return completionCandidates;
    }

    /**
     * Returns the {@link IGetter} that is responsible for supplying the value of this argument.
     */
    public IGetter getter() {
        return getter;
    }

    /**
     * Returns the {@link ISetter} that is responsible for modifying the value of this argument.
     */
    public ISetter setter() {
        return setter;
    }

    /**
     * Returns the current value of this argument. Delegates to the current {@link #getter()}.
     */
    public <T> T getValue() throws PicocliException {
        try {
            return getter.get();
        } catch (PicocliException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PicocliException("Could not get value for " + this + ": " + ex, ex);
        }
    }

    /**
     * Sets the value of this argument to the specified value and returns the previous value.
     * Delegates to the current {@link #setter()}.
     */
    public <T> T setValue(T newValue) throws PicocliException {
        try {
            return setter.set(newValue);
        } catch (PicocliException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PicocliException(
                    "Could not set value (" + newValue + ") for " + this + ": " + ex, ex);
        }
    }

    /**
     * Sets the value of this argument to the specified value and returns the previous value.
     * Delegates to the current {@link #setter()}.
     * 
     * @since 3.5
     */
    public <T> T setValue(T newValue, CommandLine commandLine) throws PicocliException {
        if (setter instanceof MethodBinding) {
            ((MethodBinding) setter).commandLine = commandLine;
        }
        try {
            return setter.set(newValue);
        } catch (PicocliException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PicocliException(
                    "Could not set value (" + newValue + ") for " + this + ": " + ex, ex);
        }
    }

    /**
     * Returns {@code true} if this argument's {@link #type()} is an array, a {@code Collection} or
     * a {@code Map}, {@code false} otherwise.
     */
    public boolean isMultiValue() {
        return ObjectUtilsExt.isGroup(type());
    }

    /** Returns {@code true} if this argument is a named option, {@code false} otherwise. */
    public abstract boolean isOption();

    /**
     * Returns {@code true} if this argument is a positional parameter, {@code false} otherwise.
     */
    public abstract boolean isPositional();

    /**
     * Returns the untyped command line arguments matched by this option or positional parameter
     * spec.
     * 
     * @return the matched arguments after {@linkplain #splitRegex() splitting}, but before type
     *         conversion. For map properties, {@code "key=value"} values are split into the key and
     *         the value part.
     */
    public List<String> stringValues() {
        return Collections.unmodifiableList(stringValues);
    }

    /**
     * Returns the typed command line arguments matched by this option or positional parameter spec.
     * 
     * @return the matched arguments after {@linkplain #splitRegex() splitting} and type conversion.
     *         For map properties, {@code "key=value"} values are split into the key and the value
     *         part.
     */
    public List<Object> typedValues() {
        return Collections.unmodifiableList(typedValues);
    }

    /** Sets the {@code stringValues} to a new list instance. */
    protected void resetStringValues() {
        stringValues = new ArrayList<String>();
    }

    /**
     * Returns the original command line arguments matched by this option or positional parameter
     * spec.
     * 
     * @return the matched arguments as found on the command line: empty Strings for options without
     *         value, the values have not been {@linkplain #splitRegex() split}, and for map
     *         properties values may look like {@code "key=value"}
     */
    public List<String> originalStringValues() {
        return Collections.unmodifiableList(originalStringValues);
    }

    /** Sets the {@code originalStringValues} to a new list instance. */
    protected void resetOriginalStringValues() {
        originalStringValues = new ArrayList<String>();
    }

    /**
     * Returns whether the default for this option or positional parameter should be shown,
     * potentially overriding the specified global setting.
     * 
     * @param usageHelpShowDefaults
     *            whether the command's UsageMessageSpec is configured to show default values.
     */
    public boolean internalShowDefaultValue(boolean usageHelpShowDefaults) {
        if (showDefaultValue() == Help.Visibility.ALWAYS) {
            return true;
        } // override global usage help setting
        if (showDefaultValue() == Help.Visibility.NEVER) {
            return false;
        } // override global usage help setting
        if (initialValue == null && defaultValue() == null) {
            return false;
        } // no default value to show
        return usageHelpShowDefaults && !ClassUtilsExt.isBoolean(type());
    }

    /**
     * Returns the Messages for this arg specification, or {@code null}.
     * 
     * @since 3.6
     */
    public Messages messages() {
        return messages;
    }

    /**
     * Sets the Messages for this ArgSpec, and returns this ArgSpec.
     * 
     * @param msgs
     *            the new Messages value, may be {@code null}
     * @see Command#resourceBundle()
     * @see OptionSpec#description()
     * @see PositionalParamSpec#description()
     * @since 3.6
     */
    public ArgSpec messages(Messages msgs) {
        messages = msgs;
        return this;
    }

    /** Returns a string respresentation of this option or positional parameter. */
    public String toString() {
        return toString;
    }

    String[] splitValue(String value, ParserSpec parser, Range arity, int consumed) {
        if (splitRegex().length() == 0) {
            return new String[] { value };
        }
        int limit = parser.limitSplit() ? Math.max(arity.max - consumed, 0) : 0;
        if (parser.splitQuotedStrings()) {
            return value.split(splitRegex(), limit);
        }
        return splitRespectingQuotedStrings(value, limit, parser);
    }

    // @since 3.7
    private String[] splitRespectingQuotedStrings(String value, int limit, ParserSpec parser) {
        StringBuilder splittable = new StringBuilder();
        StringBuilder temp = new StringBuilder();
        StringBuilder current = splittable;
        Queue<String> quotedValues = new LinkedList<String>();
        boolean escaping = false, inQuote = false;
        for (int ch = 0, i = 0; i < value.length(); i += Character.charCount(ch)) {
            ch = value.codePointAt(i);
            switch (ch) {
                case '\\':
                    escaping = !escaping;
                    break;
                case '\"':
                    if (!escaping) {
                        inQuote = !inQuote;
                        current = inQuote ? temp : splittable;
                        if (inQuote) {
                            splittable.appendCodePoint(ch);
                            continue;
                        } else {
                            quotedValues.add(temp.toString());
                            temp.setLength(0);
                        }
                    }
                    break;
                default:
                    escaping = false;
                    break;
            }
            current.appendCodePoint(ch);
        }
        if (temp.length() > 0) {
            new Tracer().warn("Unbalanced quotes in [%s] for %s (value=%s)%n", temp, this, value);
            quotedValues.add(temp.toString());
            temp.setLength(0);
        }
        String[] result = splittable.toString().split(splitRegex(), limit);
        for (int i = 0; i < result.length; i++) {
            result[i] = restoreQuotedValues(result[i], quotedValues, parser);
        }
        if (!quotedValues.isEmpty()) {
            new Tracer().warn(
                    "Unable to respect quotes while splitting value %s for %s (unprocessed remainder: %s)%n",
                    value, this, quotedValues);
            return value.split(splitRegex(), limit);
        }
        return result;
    }

    private String restoreQuotedValues(String part, Queue<String> quotedValues, ParserSpec parser) {
        StringBuilder result = new StringBuilder();
        boolean escaping = false, inQuote = false, skip = false;
        for (int ch = 0, i = 0; i < part.length(); i += Character.charCount(ch)) {
            ch = part.codePointAt(i);
            switch (ch) {
                case '\\':
                    escaping = !escaping;
                    break;
                case '\"':
                    if (!escaping) {
                        inQuote = !inQuote;
                        if (!inQuote) {
                            result.append(quotedValues.remove());
                        }
                        skip = parser.trimQuotes();
                    }
                    break;
                default:
                    escaping = false;
                    break;
            }
            if (!skip) {
                result.appendCodePoint(ch);
            }
            skip = false;
        }
        return result.toString();
    }

    protected boolean equalsImpl(ArgSpec other) {
        if (other == this) {
            return true;
        }
        boolean result = Assert.equals(this.defaultValue, other.defaultValue)
                && Assert.equals(this.type, other.type) && Assert.equals(this.arity, other.arity)
                && Assert.equals(this.hidden, other.hidden)
                && Assert.equals(this.paramLabel, other.paramLabel)
                && Assert.equals(this.hideParamSyntax, other.hideParamSyntax)
                && Assert.equals(this.required, other.required)
                && Assert.equals(this.splitRegex, other.splitRegex)
                && Arrays.equals(this.description, other.description)
                && Assert.equals(this.descriptionKey, other.descriptionKey)
                && Arrays.equals(this.auxiliaryTypes, other.auxiliaryTypes);
        return result;
    }

    protected int hashCodeImpl() {
        return 17 + 37 * Assert.hashCode(defaultValue) + 37 * Assert.hashCode(type)
                + 37 * Assert.hashCode(arity) + 37 * Assert.hashCode(hidden)
                + 37 * Assert.hashCode(paramLabel) + 37 * Assert.hashCode(hideParamSyntax)
                + 37 * Assert.hashCode(required) + 37 * Assert.hashCode(splitRegex)
                + 37 * Arrays.hashCode(description) + 37 * Assert.hashCode(descriptionKey)
                + 37 * Arrays.hashCode(auxiliaryTypes);
    }

    abstract static class Builder<T extends Builder<T>> {
        private Range arity;
        private String[] description;
        private String descriptionKey;
        private boolean required;
        private boolean interactive;
        private String paramLabel;
        private boolean hideParamSyntax;
        private String splitRegex;
        private boolean hidden;
        private Class<?> type;
        private Class<?>[] auxiliaryTypes;
        private ITypeConverter<?>[] converters;
        private String defaultValue;
        private Object initialValue;
        private boolean hasInitialValue = true;
        private Help.Visibility showDefaultValue;
        private Iterable<String> completionCandidates;
        private String toString;
        private IGetter getter = new ObjectBinding();
        private ISetter setter = (ISetter) getter;

        Builder() {
        }

        Builder(ArgSpec original) {
            arity = original.arity;
            auxiliaryTypes = original.auxiliaryTypes;
            converters = original.converters;
            defaultValue = original.defaultValue;
            description = original.description;
            getter = original.getter;
            setter = original.setter;
            hidden = original.hidden;
            paramLabel = original.paramLabel;
            hideParamSyntax = original.hideParamSyntax;
            required = original.required;
            interactive = original.interactive;
            showDefaultValue = original.showDefaultValue;
            completionCandidates = original.completionCandidates;
            splitRegex = original.splitRegex;
            toString = original.toString;
            type = original.type;
            descriptionKey = original.descriptionKey;
        }

        public abstract ArgSpec build();

        protected abstract T self(); // subclasses must override to return "this"

        /**
         * Returns whether this is a required option or positional parameter.
         * 
         * @see Option#required()
         */
        public boolean required() {
            return required;
        }

        /**
         * Returns whether this option prompts the user to enter a value on the command line.
         * 
         * @see Option#interactive()
         */
        public boolean interactive() {
            return interactive;
        }

        /**
         * Returns the description of this option, used when generating the usage documentation.
         * 
         * @see Option#description()
         */
        public String[] description() {
            return description;
        }

        /**
         * Returns the description key of this arg spec, used to get the description from a resource
         * bundle.
         * 
         * @see Option#descriptionKey()
         * @see Parameters#descriptionKey()
         * @since 3.6
         */
        public String descriptionKey() {
            return descriptionKey;
        }

        /**
         * Returns how many arguments this option or positional parameter requires.
         * 
         * @see Option#arity()
         */
        public Range arity() {
            return arity;
        }

        /**
         * Returns the name of the option or positional parameter used in the usage help message.
         * 
         * @see Option#paramLabel() {@link Parameters#paramLabel()}
         */
        public String paramLabel() {
            return paramLabel;
        }

        /**
         * Returns whether usage syntax decorations around the {@linkplain #paramLabel() paramLabel}
         * should be suppressed. The default is {@code false}: by default, the paramLabel is
         * surrounded with {@code '['} and {@code ']'} characters if the value is optional and
         * followed by ellipses ("...") when multiple values can be specified.
         * 
         * @since 3.6.0
         */
        public boolean hideParamSyntax() {
            return hideParamSyntax;
        }

        /**
         * Returns auxiliary type information used when the {@link #type()} is a generic
         * {@code Collection}, {@code Map} or an abstract class.
         * 
         * @see Option#type()
         */
        public Class<?>[] auxiliaryTypes() {
            return auxiliaryTypes;
        }

        /**
         * Returns one or more {@link ITypeConverter type converters} to use to convert
         * the command line argument into a strongly typed value (or key-value pair for map fields).
         * This is useful when a particular option or positional parameter should use a custom
         * conversion that is different from the normal conversion for the arg spec's type.
         * 
         * @see Option#converter()
         */
        public ITypeConverter<?>[] converters() {
            return converters;
        }

        /**
         * Returns a regular expression to split option parameter values or {@code ""} if the value
         * should not be split.
         * 
         * @see Option#split()
         */
        public String splitRegex() {
            return splitRegex;
        }

        /**
         * Returns whether this option should be excluded from the usage message.
         * 
         * @see Option#hidden()
         */
        public boolean hidden() {
            return hidden;
        }

        /**
         * Returns the type to convert the option or positional parameter to before
         * {@linkplain #setValue(Object) setting} the value.
         */
        public Class<?> type() {
            return type;
        }

        /**
         * Returns the default value of this option or positional parameter, before splitting and
         * type conversion. A value of {@code null} means this option or positional parameter does
         * not have a default.
         */
        public String defaultValue() {
            return defaultValue;
        }

        /**
         * Returns the initial value this option or positional parameter. If
         * {@link #hasInitialValue()} is true, the option will be reset to the initial value before
         * parsing (regardless of whether a default value exists), to clear values that would
         * otherwise remain from parsing previous input.
         */
        public Object initialValue() {
            return initialValue;
        }

        /**
         * Determines whether the option or positional parameter will be reset to the
         * {@link #initialValue()} before parsing new input.
         */
        public boolean hasInitialValue() {
            return hasInitialValue;
        }

        /**
         * Returns whether this option or positional parameter's default value should be shown in
         * the usage help.
         */
        public Help.Visibility showDefaultValue() {
            return showDefaultValue;
        }

        /**
         * Returns the completion candidates for this option or positional parameter, or
         * {@code null}.
         * 
         * @since 3.2
         */
        public Iterable<String> completionCandidates() {
            return completionCandidates;
        }

        /**
         * Returns the {@link IGetter} that is responsible for supplying the value of this argument.
         */
        public IGetter getter() {
            return getter;
        }

        /**
         * Returns the {@link ISetter} that is responsible for modifying the value of this argument.
         */
        public ISetter setter() {
            return setter;
        }

        public String toString() {
            return toString;
        }

        /**
         * Sets whether this is a required option or positional parameter, and returns this builder.
         */
        public T required(boolean required) {
            this.required = required;
            return self();
        }

        /**
         * Sets whether this option prompts the user to enter a value on the command line, and
         * returns this builder.
         */
        public T interactive(boolean interactive) {
            this.interactive = interactive;
            return self();
        }

        /**
         * Sets the description of this option, used when generating the usage documentation, and
         * returns this builder.
         * 
         * @see Option#description()
         */
        public T description(String... description) {
            this.description = Assert.notNull(description, "description").clone();
            return self();
        }

        /**
         * Sets the description key that is used to look up the description in a resource bundle,
         * and returns this builder.
         * 
         * @see Option#descriptionKey()
         * @see Parameters#descriptionKey()
         * @since 3.6
         */
        public T descriptionKey(String descriptionKey) {
            this.descriptionKey = descriptionKey;
            return self();
        }

        /**
         * Sets how many arguments this option or positional parameter requires, and returns this
         * builder.
         */
        public T arity(String range) {
            return arity(Range.valueOf(range));
        }

        /**
         * Sets how many arguments this option or positional parameter requires, and returns this
         * builder.
         */
        public T arity(Range arity) {
            this.arity = Assert.notNull(arity, "arity");
            return self();
        }

        /**
         * Sets the name of the option or positional parameter used in the usage help message, and
         * returns this builder.
         */
        public T paramLabel(String paramLabel) {
            this.paramLabel = Assert.notNull(paramLabel, "paramLabel");
            return self();
        }

        /**
         * Sets whether usage syntax decorations around the {@linkplain #paramLabel() paramLabel}
         * should be suppressed. The default is {@code false}: by default, the paramLabel is
         * surrounded with {@code '['} and {@code ']'} characters if the value is optional and
         * followed by ellipses ("...") when multiple values can be specified.
         * 
         * @since 3.6.0
         */
        public T hideParamSyntax(boolean hideParamSyntax) {
            this.hideParamSyntax = hideParamSyntax;
            return self();
        }

        /**
         * Sets auxiliary type information, and returns this builder.
         * 
         * @param types
         *            the element type(s) when the {@link #type()} is a generic {@code Collection}
         *            or a {@code Map}; or the concrete type when the {@link #type()} is an abstract
         *            class.
         */
        public T auxiliaryTypes(Class<?>... types) {
            this.auxiliaryTypes = Assert.notNull(types, "types").clone();
            return self();
        }

        /**
         * Sets option/positional param-specific converter (or converters for Maps), and returns
         * this builder.
         */
        public T converters(ITypeConverter<?>... cs) {
            this.converters = Assert.notNull(cs, "type converters").clone();
            return self();
        }

        /**
         * Sets a regular expression to split option parameter values or {@code ""} if the value
         * should not be split, and returns this builder.
         */
        public T splitRegex(String splitRegex) {
            this.splitRegex = Assert.notNull(splitRegex, "splitRegex");
            return self();
        }

        /**
         * Sets whether this option or positional parameter's default value should be shown in the
         * usage help, and returns this builder.
         */
        public T showDefaultValue(Help.Visibility visibility) {
            showDefaultValue = Assert.notNull(visibility, "visibility");
            return self();
        }

        /**
         * Sets the completion candidates for this option or positional parameter, and returns this
         * builder.
         * 
         * @since 3.2
         */
        public T completionCandidates(Iterable<String> completionCandidates) {
            this.completionCandidates = Assert.notNull(completionCandidates,
                    "completionCandidates");
            return self();
        }

        /**
         * Sets whether this option should be excluded from the usage message, and returns this
         * builder.
         */
        public T hidden(boolean hidden) {
            this.hidden = hidden;
            return self();
        }

        /**
         * Sets the type to convert the option or positional parameter to before
         * {@linkplain #setValue(Object) setting} the value, and returns this builder.
         * 
         * @param propertyType
         *            the type of this option or parameter. For multi-value options and positional
         *            parameters this can be an array, or a (sub-type of) Collection or Map.
         */
        public T type(Class<?> propertyType) {
            this.type = Assert.notNull(propertyType, "type");
            return self();
        }

        /**
         * Sets the default value of this option or positional parameter to the specified value, and
         * returns this builder. Before parsing the command line, the result of
         * {@linkplain #splitRegex() splitting} and {@linkplain #converters() type converting} this
         * default value is applied to the option or positional parameter. A value of {@code null}
         * or {@code "__no_default_value__"} means no default.
         */
        public T defaultValue(String defaultValue) {
            this.defaultValue = NO_DEFAULT_VALUE.equals(defaultValue) ? null : defaultValue;
            return self();
        }

        /**
         * Sets the initial value of this option or positional parameter to the specified value, and
         * returns this builder. If {@link #hasInitialValue()} is true, the option will be reset to
         * the initial value before parsing (regardless of whether a default value exists), to clear
         * values that would otherwise remain from parsing previous input.
         */
        public T initialValue(Object initialValue) {
            this.initialValue = initialValue;
            return self();
        }

        /**
         * Determines whether the option or positional parameter will be reset to the
         * {@link #initialValue()} before parsing new input.
         */
        public T hasInitialValue(boolean hasInitialValue) {
            this.hasInitialValue = hasInitialValue;
            return self();
        }

        /**
         * Sets the {@link IGetter} that is responsible for getting the value of this argument, and
         * returns this builder.
         */
        public T getter(IGetter getter) {
            this.getter = getter;
            return self();
        }

        /**
         * Sets the {@link ISetter} that is responsible for modifying the value of this argument,
         * and returns this builder.
         */
        public T setter(ISetter setter) {
            this.setter = setter;
            return self();
        }

        /**
         * Sets the string respresentation of this option or positional parameter to the specified
         * value, and returns this builder.
         */
        public T withToString(String toString) {
            this.toString = toString;
            return self();
        }
    }
}
