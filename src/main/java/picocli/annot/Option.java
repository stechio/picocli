package picocli.annot;

import java.io.Console;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Callable;

import picocli.annot.Command;
import picocli.except.MissingParameterException;
import picocli.handler.IParseResultHandler2;
import picocli.help.Help;
import picocli.model.ITypeConverter;
import picocli.model.NoChoiceValues;
import picocli.model.OptionSpec;

/**
 * <p>
 * Annotate fields in your class with {@code @Option} and picocli will initialize these fields when
 * matching arguments are specified on the command line. In the case of command methods (annotated
 * with {@code @Command}), command options can be defined by annotating method parameters with
 * {@code @Option}.
 * </p>
 * <p>
 * Command class example:
 * </p>
 * 
 * <pre>
 * import static picocli.CommandLine.*;
 *
 * public class MyClass {
 *     &#064;Parameters(description = "Any number of input files")
 *     private List&lt;File&gt; files = new ArrayList&lt;File&gt;();
 *
 *     &#064;Option(names = { "-o", "--out" }, description = "Output file (default: print to console)")
 *     private File outputFile;
 *
 *     &#064;Option(names = { "-v",
 *             "--verbose" }, description = "Verbose mode. Helpful for troubleshooting. Multiple -v options increase the verbosity.")
 *     private boolean[] verbose;
 *
 *     &#064;Option(names = { "-h", "--help", "-?",
 *             "-help" }, usageHelp = true, description = "Display this help and exit")
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
public @interface Option {
    /**
     * One or more option names. At least one option name is required.
     * <p>
     * Different environments have different conventions for naming options, but usually options
     * have a prefix that sets them apart from parameters. Picocli supports all of the below styles.
     * The default separator is {@code '='}, but this can be configured.
     * </p>
     * <p>
     * <b>*nix</b>
     * </p>
     * <p>
     * In Unix and Linux, options have a short (single-character) name, a long name or both. Short
     * options (<a href=
     * "http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html#tag_12_02">POSIX
     * style</a> are single-character and are preceded by the {@code '-'} character, e.g.,
     * {@code `-v'}.
     * <a href= "https://www.gnu.org/software/tar/manual/html_node/Long-Options.html">GNU-style</a>
     * long (or <em>mnemonic</em>) options start with two dashes in a row, e.g., {@code `--file'}.
     * </p>
     * <p>
     * Picocli supports the POSIX convention that short options can be grouped, with the last option
     * optionally taking a parameter, which may be attached to the option name or separated by a
     * space or a {@code '='} character. The below examples are all equivalent:
     * </p>
     * 
     * <pre>
     * -xvfFILE
     * -xvf FILE
     * -xvf=FILE
     * -xv --file FILE
     * -xv --file=FILE
     * -x -v --file FILE
     * -x -v --file=FILE
     * </pre>
     * <p>
     * <b>DOS</b>
     * </p>
     * <p>
     * DOS options mostly have upper case single-character names and start with a single slash
     * {@code '/'} character. Option parameters are separated by a {@code ':'} character. Options
     * cannot be grouped together but must be specified separately. For example:
     * </p>
     * 
     * <pre>
     * DIR /S /A:D /T:C
     * </pre>
     * <p>
     * <b>PowerShell</b>
     * </p>
     * <p>
     * Windows PowerShell options generally are a word preceded by a single {@code '-'} character,
     * e.g., {@code `-Help'}. Option parameters are separated by a space or by a {@code ':'}
     * character.
     * </p>
     * 
     * @return one or more option names
     */
    String[] names();

    /**
     * Indicates whether this option is required. By default this is false. If an option is
     * required, but a user invokes the program without specifying the required option, a
     * {@link MissingParameterException} is thrown from the {@link #parse(String...)} method.
     * 
     * @return whether this option is required
     */
    boolean required() default false;

    /**
     * Set {@code help=true} if this option should disable validation of the remaining arguments: If
     * the {@code help} option is specified, no error message is generated for missing required
     * options.
     * <p>
     * This attribute is useful for special options like help ({@code -h} and {@code --help} on
     * unix, {@code -?} and {@code -Help} on Windows) or version ({@code -V} and {@code --version}
     * on unix, {@code -Version} on Windows).
     * </p>
     * <p>
     * Note that the {@link #parse(String...)} method will not print help documentation. It will
     * only set the value of the annotated field. It is the responsibility of the caller to inspect
     * the annotated fields and take the appropriate action.
     * </p>
     * 
     * @return whether this option disables validation of the other arguments
     * @deprecated Use {@link #usageHelp()} and {@link #versionHelp()} instead. See
     *             {@link #printHelpIfRequested(List, PrintStream, CommandLine.Help.Ansi)}
     */
    @Deprecated
    boolean help() default false;

    /**
     * Set {@code usageHelp=true} for the {@code --help} option that triggers display of the usage
     * help message. The <a href="http://picocli.info/#_printing_help_automatically">convenience
     * methods</a> {@code Commandline.call}, {@code Commandline.run}, and
     * {@code Commandline.parseWithHandler(s)} will automatically print usage help when an option
     * with {@code usageHelp=true} was specified on the command line.
     * <p>
     * By default, <em>all</em> options and positional parameters are included in the usage help
     * message <em>except when explicitly marked {@linkplain #hidden() hidden}.</em>
     * </p>
     * <p>
     * If this option is specified on the command line, picocli will not validate the remaining
     * arguments (so no "missing required option" errors) and the
     * {@link CommandLine#isUsageHelpRequested()} method will return {@code true}.
     * </p>
     * <p>
     * Alternatively, consider annotating your command with
     * {@linkplain Command#mixinStandardHelpOptions() @Command(mixinStandardHelpOptions = true)}.
     * </p>
     * 
     * @return whether this option allows the user to request usage help
     * @since 0.9.8
     * @see #hidden()
     * @see #run(Runnable, String...)
     * @see #call(Callable, String...)
     * @see #parseWithHandler(IParseResultHandler2, String[])
     * @see #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi)
     */
    boolean usageHelp() default false;

    /**
     * Set {@code versionHelp=true} for the {@code --version} option that triggers display of the
     * version information. The
     * <a href="http://picocli.info/#_printing_help_automatically">convenience methods</a>
     * {@code Commandline.call}, {@code Commandline.run}, and
     * {@code Commandline.parseWithHandler(s)} will automatically print version information when an
     * option with {@code versionHelp=true} was specified on the command line.
     * <p>
     * The version information string is obtained from the command's {@linkplain Command#version()
     * version} annotation or from the {@linkplain Command#versionProvider() version provider}.
     * </p>
     * <p>
     * If this option is specified on the command line, picocli will not validate the remaining
     * arguments (so no "missing required option" errors) and the
     * {@link CommandLine#isUsageHelpRequested()} method will return {@code true}.
     * </p>
     * <p>
     * Alternatively, consider annotating your command with
     * {@linkplain Command#mixinStandardHelpOptions() @Command(mixinStandardHelpOptions = true)}.
     * </p>
     * 
     * @return whether this option allows the user to request version information
     * @since 0.9.8
     * @see #hidden()
     * @see #run(Runnable, String...)
     * @see #call(Callable, String...)
     * @see #parseWithHandler(IParseResultHandler2, String[])
     * @see #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi)
     */
    boolean versionHelp() default false;

    /**
     * Description of this option, used when generating the usage documentation.
     * <p>
     * Embedded {@code %n} newline markers are converted to actual newlines.
     * </p>
     * 
     * @return the description of this option
     */
    String[] description() default {};

    /**
     * Specifies the minimum number of required parameters and the maximum number of accepted
     * parameters. If an option declares a positive arity, and the user specifies an insufficient
     * number of parameters on the command line, a {@link MissingParameterException} is thrown by
     * the {@link #parse(String...)} method.
     * <p>
     * In many cases picocli can deduce the number of required parameters from the field's type. By
     * default, flags (boolean options) have arity zero, and single-valued type fields (String, int,
     * Integer, double, Double, File, Date, etc) have arity one. Generally, fields with types that
     * cannot hold multiple values can omit the {@code arity} attribute.
     * </p>
     * <p>
     * Fields used to capture options with arity two or higher should have a type that can hold
     * multiple values, like arrays or Collections. See {@link #type()} for strongly-typed
     * Collection fields.
     * </p>
     * <p>
     * For example, if an option has 2 required parameters and any number of optional parameters,
     * specify {@code @Option(names = "-example", arity = "2..*")}.
     * </p>
     * <b>A note on boolean options</b>
     * <p>
     * By default picocli does not expect boolean options (also called "flags" or "switches") to
     * have a parameter. You can make a boolean option take a required parameter by annotating your
     * field with {@code arity="1"}. For example:
     * </p>
     * 
     * <pre>
     * &#064;Option(names = "-v", arity = "1")
     * boolean verbose;
     * </pre>
     * <p>
     * Because this boolean field is defined with arity 1, the user must specify either
     * {@code <program> -v false} or {@code <program> -v true} on the command line, or a
     * {@link MissingParameterException} is thrown by the {@link #parse(String...)} method.
     * </p>
     * <p>
     * To make the boolean parameter possible but optional, define the field with
     * {@code arity = "0..1"}. For example:
     * </p>
     * 
     * <pre>
     * &#064;Option(names = "-v", arity = "0..1")
     * boolean verbose;
     * </pre>
     * <p>
     * This will accept any of the below without throwing an exception:
     * </p>
     * 
     * <pre>
     * -v
     * -v true
     * -v false
     * </pre>
     * 
     * @return how many arguments this option requires
     */
    String arity() default "";

    /**
     * Specify a {@code paramLabel} for the option parameter to be used in the usage help message.
     * If omitted, picocli uses the field name in fish brackets ({@code '<'} and {@code '>'}) by
     * default. Example:
     * 
     * <pre>
     * class Example {
     *     &#064;Option(names = { "-o",
     *             "--output" }, paramLabel = "FILE", description = "path of the output file")
     *     private File out;
     *     &#064;Option(names = { "-j",
     *             "--jobs" }, arity = "0..1", description = "Allow N jobs at once; infinite jobs with no arg.")
     *     private int maxJobs = -1;
     * }
     * </pre>
     * <p>
     * By default, the above gives a usage help message like the following:
     * </p>
     * 
     * <pre>
     * Usage: &lt;main class&gt; [OPTIONS]
     * -o, --output FILE       path of the output file
     * -j, --jobs [&lt;maxJobs&gt;]  Allow N jobs at once; infinite jobs with no arg.
     * </pre>
     * 
     * @return name of the option parameter used in the usage help message
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
     * Optionally specify a {@code type} to control exactly what Class the option parameter should
     * be converted to. This may be useful when the field type is an interface or an abstract class.
     * For example, a field can be declared to have type {@code java.lang.Number}, and annotating
     * {@code @Option(type=Short.class)} ensures that the option parameter value is converted to a
     * {@code Short} before setting the field value.
     * </p>
     * <p>
     * For array fields whose <em>component</em> type is an interface or abstract class, specify the
     * concrete <em>component</em> type. For example, a field with type {@code Number[]} may be
     * annotated with {@code @Option(type=Short.class)} to ensure that option parameter values are
     * converted to {@code Short} before adding an element to the array.
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
     * know the option parameter should be split up in key=value pairs, where the key should be
     * converted to a {@code java.util.concurrent.TimeUnit} enum value, and the value should be
     * converted to a {@code Long}. No {@code @Option(type=...)} type attribute is required for
     * this. For generic types with wildcards, picocli will take the specified upper or lower bound
     * as the Class to convert to, unless the {@code @Option} annotation specifies an explicit
     * {@code type} attribute.
     * </p>
     * <p>
     * If the field type is a raw collection or a raw map, and you want it to contain other values
     * than Strings, or if the generic type's type arguments are interfaces or abstract classes, you
     * may specify a {@code type} attribute to control the Class that the option parameter should be
     * converted to.
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
     * Specify a regular expression to use to split option parameter values before applying them to
     * the field. All elements resulting from the split are added to the array or Collection.
     * Ignored for single-value fields.
     * 
     * @return a regular expression to split option parameter values or {@code ""} if the value
     *         should not be split
     * @see String#split(String)
     */
    String split() default "";

    /**
     * Set {@code hidden=true} if this option should not be included in the usage help message.
     * 
     * @return whether this option should be excluded from the usage documentation
     */
    boolean hidden() default false;

    /**
     * Returns the default value of this option, before splitting and type conversion.
     * 
     * @return a String that (after type conversion) will be used as the value for this option if no
     *         value was specified on the command line
     * @since 3.2
     */
    String defaultValue() default "__no_default_value__";

    /**
     * Use this attribute to control for a specific option whether its default value should be shown
     * in the usage help message. If not specified, the default value is only shown when the
     * {@link Command#showDefaultValues()} is set {@code true} on the command. Use this attribute to
     * specify whether the default value for this specific option should always be shown or never be
     * shown, regardless of the command setting.
     * 
     * @return whether this option's default value should be shown in the usage help message
     */
    Help.Visibility showDefaultValue() default Help.Visibility.ON_DEMAND;

    /**
     * Use this attribute to specify an {@code Iterable<String>} class that generates completion
     * candidates for this option. For map fields, completion candidates should be in
     * {@code key=value} form.
     * <p>
     * Completion candidates are used in bash completion scripts generated by the
     * {@code picocli.AutoComplete} class. Bash has special completion options to generate file
     * names and host names, and the bash completion scripts generated by {@code AutoComplete}
     * delegate to these bash built-ins for {@code @Options} whose {@code type} is
     * {@code java.io.File}, {@code java.nio.file.Path} or {@code java.net.InetAddress}.
     * </p>
     * <p>
     * For {@code @Options} whose {@code type} is a Java {@code enum}, {@code AutoComplete} can
     * generate completion candidates from the type. For other types, use this attribute to specify
     * completion candidates.
     * </p>
     *
     * @return a class whose instances can iterate over the completion candidates for this option
     * @see picocli.model.IFactory
     * @since 3.2
     */
    Class<? extends Iterable<String>> choiceValues() default NoChoiceValues.class;

    /**
     * Set {@code interactive=true} if this option will prompt the end user for a value (like a
     * password). Only supported for single-value options (not arrays, collections or maps). When
     * running on Java 6 or greater, this will use the {@link Console#readPassword()} API to get a
     * value without echoing input to the console.
     * 
     * @return whether this option prompts the end user for a value to be entered on the command
     *         line
     * @since 3.5
     */
    boolean interactive() default false;

    /**
     * ResourceBundle key for this option. If not specified, (and a ResourceBundle
     * {@linkplain Command#resourceBundle() exists for this command}) an attempt is made to find the
     * option description using any of the option names (without leading hyphens) as key.
     * 
     * @see OptionSpec#description()
     * @since 3.6
     */
    String descriptionKey() default "";
}