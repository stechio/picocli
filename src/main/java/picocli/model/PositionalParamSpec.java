package picocli.model;

import picocli.CommandLine;
import picocli.CommandLine.ITypeConverter;
import picocli.annots.Parameters;
import picocli.excepts.InitializationException;
import picocli.util.Assert;

/**
 * The {@code PositionalParamSpec} class models aspects of a <em>positional parameter</em>
 * of a {@linkplain CommandSpec command}, including whether it is required or optional, and
 * attributes for the usage help message describing the positional parameter.
 * <p>
 * Positional parameters have an {@link #index() index} (or a range of indices). A
 * positional parameter is matched when the parser encounters a command line argument at
 * that index. Named options and their parameters do not change the index counter, so the
 * command line can contain a mixture of positional parameters and named options.
 * </p>
 * <p>
 * Depending on the positional parameter's {@link #arity() arity}, the parser may consume
 * multiple command line arguments starting from the current index. The parser will call
 * {@link #setValue(Object) setValue} on the {@code PositionalParamSpec} for each of the
 * parameters encountered. For multi-value positional parameters, the {@code type} may be an
 * array, a {@code Collection} or a {@code Map}. In this case the parser will get the data
 * structure by calling {@link #getValue() getValue} and modify the contents of this data
 * structure. (In the case of arrays, the array is replaced with a new instance with
 * additional elements.)
 * </p>
 * <p>
 * Before calling the setter, picocli converts the positional parameter value from a String
 * to the parameter's type.
 * </p>
 * <ul>
 * <li>If a positional parameter-specific {@link #converters() converter} is configured,
 * this will be used for type conversion. If the positional parameter's type is a
 * {@code Map}, the map may have different types for its keys and its values, so
 * {@link #converters() converters} should provide two converters: one for the map keys and
 * one for the map values.</li>
 * <li>Otherwise, the positional parameter's {@link #type() type} is used to look up a
 * converter in the list of {@linkplain CommandLine#registerConverter(Class, ITypeConverter)
 * registered converters}. For multi-value positional parameters, the {@code type} may be an
 * array, or a {@code Collection} or a {@code Map}. In that case the elements are converted
 * based on the positional parameter's {@link #auxiliaryTypes() auxiliaryTypes}. The
 * auxiliaryType is used to look up the converter(s) to use to convert the individual
 * parameter values. Maps may have different types for its keys and its values, so
 * {@link #auxiliaryTypes() auxiliaryTypes} should provide two types: one for the map keys
 * and one for the map values.</li>
 * </ul>
 * <p>
 * {@code PositionalParamSpec} objects are used by the picocli command line interpreter and
 * help message generator. Picocli can construct a {@code PositionalParamSpec} automatically
 * from fields and methods with {@link Parameters @Parameters} annotations. Alternatively a
 * {@code PositionalParamSpec} can be constructed programmatically.
 * </p>
 * <p>
 * When a {@code PositionalParamSpec} is created from a {@link Parameters @Parameters}
 * -annotated field or method, it is "bound" to that field or method: this field is set (or
 * the method is invoked) when the position is matched and {@link #setValue(Object)
 * setValue} is called. Programmatically constructed {@code PositionalParamSpec} instances
 * will remember the value passed to the {@link #setValue(Object) setValue} method so it can
 * be retrieved with the {@link #getValue() getValue} method. This behaviour can be
 * customized by installing a custom {@link IGetter} and {@link ISetter} on the
 * {@code PositionalParamSpec}.
 * </p>
 * 
 * @since 3.0
 */
public class PositionalParamSpec extends ArgSpec {
    private Range index;
    private Range capacity;

    /**
     * Ensures all attributes of this {@code PositionalParamSpec} have a valid value; throws
     * an {@link InitializationException} if this cannot be achieved.
     */
    private PositionalParamSpec(Builder builder) {
        super(builder);
        index = builder.index == null ? Range.valueOf("*") : builder.index;
        capacity = builder.capacity == null ? Range.parameterCapacity(arity(), index)
                : builder.capacity;
        if (toString == null) {
            toString = "positional parameter[" + index() + "]";
        }
    }

    /**
     * Returns a new Builder initialized with the attributes from this
     * {@code PositionalParamSpec}. Calling {@code build} immediately will return a copy of
     * this {@code PositionalParamSpec}.
     * 
     * @return a builder that can create a copy of this spec
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public boolean isOption() {
        return false;
    }

    @Override
    public boolean isPositional() {
        return true;
    }

    /**
     * Returns the description template of this positional parameter, before variables are
     * {@linkplain Parameters#description() rendered}. If a resource bundle has been
     * {@linkplain ArgSpec#messages(Messages) set}, this method will first try to find a
     * value in the resource bundle: If the resource bundle has no entry for the
     * {@code fully qualified commandName + "." + descriptionKey} or for the unqualified
     * {@code descriptionKey}, an attempt is made to find the positional parameter
     * description using {@code paramLabel() + "[" + index() + "]"} as key, first with the
     * {@code fully qualified commandName + "."} prefix, then without.
     * 
     * @see Parameters#description()
     * @see CommandSpec#qualifiedName(String)
     * @since 3.6
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
        newValue = messages().getStringArray(paramLabel() + "[" + index() + "]", null);
        if (newValue != null) {
            return newValue;
        }
        return super.description();
    }

    /**
     * Returns an index or range specifying which of the command line arguments should be
     * assigned to this positional parameter.
     * 
     * @see Parameters#index()
     */
    public Range index() {
        return index;
    }

    public Range capacity() {
        return capacity;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int hashCode() {
        return super.hashCodeImpl() + 37 * Assert.hashCode(capacity)
                + 37 * Assert.hashCode(index);
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof PositionalParamSpec)) {
            return false;
        }
        PositionalParamSpec other = (PositionalParamSpec) obj;
        return super.equalsImpl(other) && Assert.equals(this.capacity, other.capacity)
                && Assert.equals(this.index, other.index);
    }

    /**
     * Builder responsible for creating valid {@code PositionalParamSpec} objects.
     * 
     * @since 3.0
     */
    public static class Builder extends ArgSpec.Builder<Builder> {
        private Range capacity;
        private Range index;

        private Builder() {
        }

        private Builder(PositionalParamSpec original) {
            super(original);
            index = original.index;
            capacity = original.capacity;
        }

        /** Returns a valid {@code PositionalParamSpec} instance. */
        @Override
        public PositionalParamSpec build() {
            return new PositionalParamSpec(this);
        }

        /** Returns this builder. */
        @Override
        protected Builder self() {
            return this;
        }

        /**
         * Returns an index or range specifying which of the command line arguments should
         * be assigned to this positional parameter.
         * 
         * @see Parameters#index()
         */
        public Range index() {
            return index;
        }

        /**
         * Sets the index or range specifying which of the command line arguments should be
         * assigned to this positional parameter, and returns this builder.
         */
        public Builder index(String range) {
            return index(Range.valueOf(range));
        }

        /**
         * Sets the index or range specifying which of the command line arguments should be
         * assigned to this positional parameter, and returns this builder.
         */
        public Builder index(Range index) {
            this.index = index;
            return self();
        }

        public Builder capacity(Range capacity) {
            this.capacity = capacity;
            return self();
        }
    }
}
