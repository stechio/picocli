package picocli.annot;

import java.io.Console;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import picocli.CommandLine;
import picocli.except.MissingParameterException;
import picocli.help.Help;
import picocli.model.ITypeConverter;
import picocli.model.NoChoiceValues;
import picocli.model.PositionalParamSpec;

/**
 * <p>
 * Fields annotated with {@code @Parameters} will be initialized with positional parameters. By
 * specifying the {@link #index()} attribute you can pick the exact position or a range of
 * positional parameters to apply. If no index is specified, the field will get all positional
 * parameters (and so it should be an array or a collection).
 * </p>
 * <p>
 * In the case of command methods (annotated with {@code @Command}), method parameters may be
 * annotated with {@code @Parameters}, but are are considered positional parameters by default,
 * unless they are annotated with {@code @Option}.
 * </p>
 * <p>
 * Command class example:
 * </p>
 * 
 * <pre>
 * import static picocli.CommandLine.*;
 *
 * public class MyCalcParameters {
 *     &#064;Parameters(description = "Any number of input numbers")
 *     private List&lt;BigDecimal&gt; files = new ArrayList&lt;BigDecimal&gt;();
 *
 *     &#064;Option(names = { "-h",
 *             "--help" }, usageHelp = true, description = "Display this help and exit")
 *     private boolean help;
 * }
 * </pre>
 * <p>
 * A field cannot be annotated with both {@code @Parameters} and {@code @Option} or a
 * {@code ParameterException} is thrown.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
public @interface Parameters {
    /**
     * Specify an index ("0", or "1", etc.) to pick which of the command line arguments should be
     * assigned to this field. For array or Collection fields, you can also specify an index range
     * ("0..3", or "2..*", etc.) to assign a subset of the command line arguments to this field. The
     * default is "*", meaning all command line arguments.
     * 
     * @return an index or range specifying which of the command line arguments should be assigned
     *         to this field
     */
    String index() default "";

    /**
     * Description of the parameter(s), used when generating the usage documentation.
     * <p>
     * Embedded {@code %n} newline markers are converted to actual newlines.
     * </p>
     * 
     * @return the description of the parameter(s)
     */
    String[] description() default {};

    /**
     * Specifies the minimum number of required parameters and the maximum number of accepted
     * parameters. If a positive arity is declared, and the user specifies an insufficient number of
     * parameters on the command line, {@link MissingParameterException} is thrown by the
     * {@link #parse(String...)} method.
     * <p>
     * The default depends on the type of the parameter: booleans require no parameters, arrays and
     * Collections accept zero to any number of parameters, and any other type accepts one
     * parameter.
     * </p>
     * 
     * @return the range of minimum and maximum parameters accepted by this command
     */
    String arity() default "";

    /**
     * Specify a {@code paramLabel} for the parameter to be used in the usage help message. If
     * omitted, picocli uses the field name in fish brackets ({@code '<'} and {@code '>'}) by
     * default. Example:
     * 
     * <pre>
     * class Example {
     *     &#064;Parameters(paramLabel = "FILE", description = "path of the input FILE(s)")
     *     private File[] inputFiles;
     * }
     * </pre>
     * <p>
     * By default, the above gives a usage help message like the following:
     * </p>
     * 
     * <pre>
     * Usage: &lt;main class&gt; [FILE...]
     * [FILE...]       path of the input FILE(s)
     * </pre>
     * 
     * @return name of the positional parameter used in the usage help message
     */
    String paramLabel() default "";

    /**
     * Returns whether usage syntax decorations around the {@linkplain #paramLabel() paramLabel}
     * should be suppressed. The default is {@code false}: by default, the paramLabel is surrounded
     * with {@code '['} and {@code ']'} characters if the value is optional and followed by ellipses
     * ("...") when multiple values can be specified.
     * 
     * @since 3.6.0
     */
    boolean hideParamSyntax() default false;

    /**
     * <p>
     * Optionally specify a {@code type} to control exactly what Class the positional parameter
     * should be converted to. This may be useful when the field type is an interface or an abstract
     * class. For example, a field can be declared to have type {@code java.lang.Number}, and
     * annotating {@code @Parameters(type=Short.class)} ensures that the positional parameter value
     * is converted to a {@code Short} before setting the field value.
     * </p>
     * <p>
     * For array fields whose <em>component</em> type is an interface or abstract class, specify the
     * concrete <em>component</em> type. For example, a field with type {@code Number[]} may be
     * annotated with {@code @Parameters(type=Short.class)} to ensure that positional parameter
     * values are converted to {@code Short} before adding an element to the array.
     * </p>
     * <p>
     * Picocli will use the {@link ITypeConverter} that is
     * {@linkplain #registerConverter(Class, ITypeConverter) registered} for the specified type to
     * convert the raw String values before modifying the field value.
     * </p>
     * <p>
     * Prior to 2.0, the {@code type} attribute was necessary for {@code Collection} and {@code Map}
     * fields, but starting from 2.0 picocli will infer the component type from the generic type's
     * type arguments. For example, for a field of type {@code Map<TimeUnit, Long>} picocli will
     * know the positional parameter should be split up in key=value pairs, where the key should be
     * converted to a {@code java.util.concurrent.TimeUnit} enum value, and the value should be
     * converted to a {@code Long}. No {@code @Parameters(type=...)} type attribute is required for
     * this. For generic types with wildcards, picocli will take the specified upper or lower bound
     * as the Class to convert to, unless the {@code @Parameters} annotation specifies an explicit
     * {@code type} attribute.
     * </p>
     * <p>
     * If the field type is a raw collection or a raw map, and you want it to contain other values
     * than Strings, or if the generic type's type arguments are interfaces or abstract classes, you
     * may specify a {@code type} attribute to control the Class that the positional parameter
     * should be converted to.
     * 
     * @return the type(s) to convert the raw String values
     */
    Class<?>[] type() default {};

    /**
     * Optionally specify one or more {@link ITypeConverter} classes to use to convert the command
     * line argument into a strongly typed value (or key-value pair for map fields). This is useful
     * when a particular field should use a custom conversion that is different from the normal
     * conversion for the field's type.
     * <p>
     * For example, for a specific field you may want to use a converter that maps the constant
     * names defined in {@link java.sql.Types java.sql.Types} to the {@code int} value of these
     * constants, but any other {@code int} fields should not be affected by this and should
     * continue to use the standard int converter that parses numeric values.
     * </p>
     * 
     * @return the type converter(s) to use to convert String values to strongly typed values for
     *         this field
     * @see CommandLine#registerConverter(Class, ITypeConverter)
     */
    Class<? extends ITypeConverter<?>>[] converter() default {};

    /**
     * Specify a regular expression to use to split positional parameter values before applying them
     * to the field. All elements resulting from the split are added to the array or Collection.
     * Ignored for single-value fields.
     * 
     * @return a regular expression to split operand values or {@code ""} if the value should not be
     *         split
     * @see String#split(String)
     */
    String split() default "";

    /**
     * Set {@code hidden=true} if this parameter should not be included in the usage message.
     * 
     * @return whether this parameter should be excluded from the usage message
     */
    boolean hidden() default false;

    /**
     * Returns the default value of this positional parameter, before splitting and type conversion.
     * 
     * @return a String that (after type conversion) will be used as the value for this positional
     *         parameter if no value was specified on the command line
     * @since 3.2
     */
    String defaultValue() default "__no_default_value__";

    /**
     * Use this attribute to control for a specific positional parameter whether its default value
     * should be shown in the usage help message. If not specified, the default value is only shown
     * when the {@link Command#showDefaultValues()} is set {@code true} on the command. Use this
     * attribute to specify whether the default value for this specific positional parameter should
     * always be shown or never be shown, regardless of the command setting.
     * <p>
     * Note that picocli 3.2 allows {@linkplain #description() embedding default values} anywhere in
     * the description that ignores this setting.
     * </p>
     * 
     * @return whether this positional parameter's default value should be shown in the usage help
     *         message
     */
    Help.Visibility showDefaultValue() default Help.Visibility.ON_DEMAND;

    /**
     * Use this attribute to specify an {@code Iterable<String>} class that generates completion
     * candidates for this positional parameter. For map fields, completion candidates should be in
     * {@code key=value} form.
     * <p>
     * Completion candidates are used in bash completion scripts generated by the
     * {@code picocli.AutoComplete} class. Unfortunately, {@code picocli.AutoComplete} is not very
     * good yet at generating completions for positional parameters.
     * </p>
     *
     * @return a class whose instances can iterate over the completion candidates for this
     *         positional parameter
     * @see picocli.model.IFactory
     * @since 3.2
     */
    Class<? extends Iterable<String>> choiceValues() default NoChoiceValues.class;

    /**
     * Set {@code interactive=true} if this positional parameter will prompt the end user for a
     * value (like a password). Only supported for single-value positional parameters (not arrays,
     * collections or maps). When running on Java 6 or greater, this will use the
     * {@link Console#readPassword()} API to get a value without echoing input to the console.
     * 
     * @return whether this positional parameter prompts the end user for a value to be entered on
     *         the command line
     * @since 3.5
     */
    boolean interactive() default false;

    /**
     * ResourceBundle key for this option. If not specified, (and a ResourceBundle
     * {@linkplain Command#resourceBundle() exists for this command}) an attempt is made to find the
     * positional parameter description using {@code paramLabel() + "[" + index() + "]"} as key.
     *
     * @see PositionalParamSpec#description()
     * @since 3.6
     */
    String descriptionKey() default "";
}
