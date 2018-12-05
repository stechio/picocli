package picocli.model;

import java.lang.reflect.Field;

import picocli.annot.Option;
import picocli.annot.Parameters;
import picocli.except.InitializationException;
import picocli.util.ClassUtilsExt;
import picocli.util.ObjectUtilsExt;

/**
 * Describes the number of parameters required and accepted by an option or a positional
 * parameter.
 * 
 * @since 0.9.7
 */
public class Range implements Comparable<Range> {
    /** Required number of parameters for an option or positional parameter. */
    public final int min;
    /** Maximum accepted number of parameters for an option or positional parameter. */
    public final int max;
    public final boolean isVariable;
    //TODO:private scope
    final boolean isUnspecified;
    private final String originalValue;

    /**
     * Constructs a new Range object with the specified parameters.
     * 
     * @param min
     *            minimum number of required parameters
     * @param max
     *            maximum number of allowed parameters (or Integer.MAX_VALUE if variable)
     * @param variable
     *            {@code true} if any number or parameters is allowed, {@code false} otherwise
     * @param unspecified
     *            {@code true} if no arity was specified on the option/parameter (value is based
     *            on type)
     * @param originalValue
     *            the original value that was specified on the option or parameter
     */
    public Range(int min, int max, boolean variable, boolean unspecified,
            String originalValue) {
        if (min < 0 || max < 0) {
            throw new InitializationException(
                    "Invalid negative range (min=" + min + ", max=" + max + ")");
        }
        if (min > max) {
            throw new InitializationException(
                    "Invalid range (min=" + min + ", max=" + max + ")");
        }
        this.min = min;
        this.max = max;
        this.isVariable = variable;
        this.isUnspecified = unspecified;
        this.originalValue = originalValue;
    }

    /**
     * Returns a new {@code Range} based on the {@link Option#arity()} annotation on the
     * specified field, or the field type's default arity if no arity was specified.
     * 
     * @param field
     *            the field whose Option annotation to inspect
     * @return a new {@code Range} based on the Option arity annotation on the specified field
     */
    public static Range optionArity(Field field) {
        return optionArity(new TypedMember(field));
    }

    static Range optionArity(TypedMember member) {
        return member.isAnnotationPresent(Option.class)
                ? adjustForType(Range.valueOf(member.getAnnotation(Option.class).arity()),
                        member)
                : new Range(0, 0, false, true, "0");
    }

    /**
     * Returns a new {@code Range} based on the {@link Parameters#arity()} annotation on the
     * specified field, or the field type's default arity if no arity was specified.
     * 
     * @param field
     *            the field whose Parameters annotation to inspect
     * @return a new {@code Range} based on the Parameters arity annotation on the specified
     *         field
     */
    public static Range parameterArity(Field field) {
        return parameterArity(new TypedMember(field));
    }

    static Range parameterArity(TypedMember member) {
        if (member.isAnnotationPresent(Parameters.class)) {
            return adjustForType(Range.valueOf(member.getAnnotation(Parameters.class).arity()),
                    member);
        } else {
            return member.isMethodParameter() ? adjustForType(Range.valueOf(""), member)
                    : new Range(0, 0, false, true, "0");
        }
    }

    /**
     * Returns a new {@code Range} based on the {@link Parameters#index()} annotation on the
     * specified field.
     * 
     * @param field
     *            the field whose Parameters annotation to inspect
     * @return a new {@code Range} based on the Parameters index annotation on the specified
     *         field
     */
    public static Range parameterIndex(Field field) {
        return parameterIndex(new TypedMember(field));
    }

    static Range parameterIndex(TypedMember member) {
        if (member.isAnnotationPresent(Parameters.class)) {
            Range result = Range.valueOf(member.getAnnotation(Parameters.class).index());
            if (!result.isUnspecified) {
                return result;
            }
        }
        if (member.isMethodParameter()) {
            int min = ((MethodParam) member.accessible).position;
            int max = member.isMultiValue() ? Integer.MAX_VALUE : min;
            return new Range(min, max, member.isMultiValue(), false, "");
        }
        return Range.valueOf("*"); // the default
    }

    static Range adjustForType(Range result, TypedMember member) {
        return result.isUnspecified ? defaultArity(member) : result;
    }

    /**
     * Returns the default arity {@code Range}: for {@link Option options} this is 0 for
     * booleans and 1 for other types, for {@link Parameters parameters} booleans have arity 0,
     * arrays or Collections have arity "0..*", and other types have arity 1.
     * 
     * @param field
     *            the field whose default arity to return
     * @return a new {@code Range} indicating the default arity of the specified field
     * @since 2.0
     */
    public static Range defaultArity(Field field) {
        return defaultArity(new TypedMember(field));
    }

    private static Range defaultArity(TypedMember member) {
        Class<?> type = member.getType();
        if (member.isAnnotationPresent(Option.class)) {
            Class<?>[] typeAttribute = ArgsReflection.inferTypes(type,
                    member.getAnnotation(Option.class).type(), member.getGenericType());
            boolean zeroArgs = ClassUtilsExt.isBoolean(type)
                    || (ObjectUtilsExt.isGroup(type) && ClassUtilsExt.isBoolean(typeAttribute[0]));
            return zeroArgs ? Range.valueOf("0").unspecified(true)
                    : Range.valueOf("1").unspecified(true);
        }
        if (ObjectUtilsExt.isGroup(type)) {
            return Range.valueOf("0..1").unspecified(true);
        }
        return Range.valueOf("1").unspecified(true);// for single-valued fields (incl. boolean positional parameters)
    }

    /**
     * Returns the default arity {@code Range} for {@link Option options}: booleans have arity
     * 0, other types have arity 1.
     * 
     * @param type
     *            the type whose default arity to return
     * @return a new {@code Range} indicating the default arity of the specified type
     * @deprecated use {@link #defaultArity(Field)} instead
     */
    @Deprecated
    public static Range defaultArity(Class<?> type) {
        return ClassUtilsExt.isBoolean(type) ? Range.valueOf("0").unspecified(true)
                : Range.valueOf("1").unspecified(true);
    }

    private int size() {
        return 1 + max - min;
    }

    static Range parameterCapacity(TypedMember member) {
        Range arity = parameterArity(member);
        if (!member.isMultiValue()) {
            return arity;
        }
        Range index = parameterIndex(member);
        return parameterCapacity(arity, index);
    }

    static Range parameterCapacity(Range arity, Range index) {
        if (arity.max == 0) {
            return arity;
        }
        if (index.size() == 1) {
            return arity;
        }
        if (index.isVariable) {
            return Range.valueOf(arity.min + "..*");
        }
        if (arity.size() == 1) {
            return Range.valueOf(arity.min * index.size() + "");
        }
        if (arity.isVariable) {
            return Range.valueOf(arity.min * index.size() + "..*");
        }
        return Range.valueOf(arity.min * index.size() + ".." + arity.max * index.size());
    }

    /**
     * Leniently parses the specified String as an {@code Range} value and return the result. A
     * range string can be a fixed integer value or a range of the form
     * {@code MIN_VALUE + ".." + MAX_VALUE}. If the {@code MIN_VALUE} string is not numeric, the
     * minimum is zero. If the {@code MAX_VALUE} is not numeric, the range is taken to be
     * variable and the maximum is {@code Integer.MAX_VALUE}.
     * 
     * @param range
     *            the value range string to parse
     * @return a new {@code Range} value
     */
    public static Range valueOf(String range) {
        range = range.trim();
        boolean unspecified = range.length() == 0 || range.startsWith(".."); // || range.endsWith("..");
        int min = -1, max = -1;
        boolean variable = false;
        int dots = -1;
        if ((dots = range.indexOf("..")) >= 0) {
            min = parseInt(range.substring(0, dots), 0);
            max = parseInt(range.substring(dots + 2), Integer.MAX_VALUE);
            variable = max == Integer.MAX_VALUE;
        } else {
            max = parseInt(range, Integer.MAX_VALUE);
            variable = max == Integer.MAX_VALUE;
            min = variable ? 0 : max;
        }
        Range result = new Range(min, max, variable, unspecified, range);
        return result;
    }

    private static int parseInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    /**
     * Returns a new Range object with the {@code min} value replaced by the specified value.
     * The {@code max} of the returned Range is guaranteed not to be less than the new
     * {@code min} value.
     * 
     * @param newMin
     *            the {@code min} value of the returned Range object
     * @return a new Range object with the specified {@code min} value
     */
    public Range min(int newMin) {
        return new Range(newMin, Math.max(newMin, max), isVariable, isUnspecified,
                originalValue);
    }

    /**
     * Returns a new Range object with the {@code max} value replaced by the specified value.
     * The {@code min} of the returned Range is guaranteed not to be greater than the new
     * {@code max} value.
     * 
     * @param newMax
     *            the {@code max} value of the returned Range object
     * @return a new Range object with the specified {@code max} value
     */
    public Range max(int newMax) {
        return new Range(Math.min(min, newMax), newMax, isVariable, isUnspecified,
                originalValue);
    }

    /**
     * Returns a new Range object with the {@code isUnspecified} value replaced by the specified
     * value.
     * 
     * @param unspecified
     *            the {@code unspecified} value of the returned Range object
     * @return a new Range object with the specified {@code unspecified} value
     */
    public Range unspecified(boolean unspecified) {
        return new Range(min, max, isVariable, unspecified, originalValue);
    }

    /**
     * Returns {@code true} if this Range includes the specified value, {@code false} otherwise.
     * 
     * @param value
     *            the value to check
     * @return {@code true} if the specified value is not less than the minimum and not greater
     *         than the maximum of this Range
     */
    public boolean contains(int value) {
        return min <= value && max >= value;
    }

    public boolean equals(Object object) {
        if (!(object instanceof Range)) {
            return false;
        }
        Range other = (Range) object;
        return other.max == this.max && other.min == this.min
                && other.isVariable == this.isVariable;
    }

    public int hashCode() {
        return ((17 * 37 + max) * 37 + min) * 37 + (isVariable ? 1 : 0);
    }

    public String toString() {
        return min == max ? String.valueOf(min) : min + ".." + (isVariable ? "*" : max);
    }

    public int compareTo(Range other) {
        int result = min - other.min;
        return (result == 0) ? max - other.max : result;
    }
}
