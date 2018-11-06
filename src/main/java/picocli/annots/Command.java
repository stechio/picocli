package picocli.annots;

import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ResourceBundle;

import picocli.CommandLine;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.NoDefaultProvider;
import picocli.CommandLine.NoVersionProvider;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.Messages;
import picocli.CommandLine.Model.UsageMessageSpec;
import picocli.help.Help;
import picocli.help.HelpCommand;

/**
 * <p>
 * Annotate your class with {@code @Command} when you want more control over the format of the
 * generated help message. From 3.6, methods can also be annotated with {@code @Command}, where
 * the method parameters define the command options and positional parameters.
 * </p>
 * 
 * <pre>
 * &#064;Command(name = "Encrypt", mixinStandardHelpOptions = true, description = "Encrypt FILE(s), or standard input, to standard output or to the output file.", version = "Encrypt version 1.0", footer = "Copyright (c) 2017")
 * public class Encrypt {
 *     &#064;Parameters(paramLabel = "FILE", description = "Any number of input files")
 *     private List&lt;File&gt; files = new ArrayList&lt;File&gt;();
 *
 *     &#064;Option(names = { "-o", "--out" }, description = "Output file (default: print to console)")
 *     private File outputFile;
 *
 *     &#064;Option(names = { "-v",
 *             "--verbose" }, description = "Verbose mode. Helpful for troubleshooting. Multiple -v options increase the verbosity.")
 *     private boolean[] verbose;
 * }
 * </pre>
 * <p>
 * The structure of a help message looks like this:
 * </p>
 * <ul>
 * <li>[header]</li>
 * <li>[synopsis]: {@code Usage: <commandName> [OPTIONS] [FILE...]}</li>
 * <li>[description]</li>
 * <li>[parameter list]: {@code      [FILE...]   Any number of input files}</li>
 * <li>[option list]: {@code   -h, --help   prints this help message and exits}</li>
 * <li>[footer]</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.PACKAGE,
        ElementType.METHOD })
public @interface Command {
    /**
     * Program name to show in the synopsis. If omitted, {@code "<main class>"} is used. For
     * {@linkplain #subcommands() declaratively added} subcommands, this attribute is also used
     * by the parser to recognize subcommands in the command line arguments.
     * 
     * @return the program name to show in the synopsis
     * @see CommandSpec#name()
     * @see Help#commandName()
     */
    String name() default "<main class>";

    /**
     * Alternative command names by which this subcommand is recognized on the command line.
     * 
     * @return one or more alternative command names
     * @since 3.1
     */
    String[] aliases() default {};

    /**
     * A list of classes to instantiate and register as subcommands. When registering
     * subcommands declaratively like this, you don't need to call the
     * {@link CommandLine#addSubcommand(String, Object)} method. For example, this:
     * 
     * <pre>
     * &#064;Command(subcommands = {
     *         GitStatus.class,
     *         GitCommit.class,
     *         GitBranch.class })
     * public class Git { ... }
     *
     * CommandLine commandLine = new CommandLine(new Git());
     * </pre>
     * 
     * is equivalent to this:
     * 
     * <pre>
     * // alternative: programmatically add subcommands.
     * // NOTE: in this case there should be no `subcommands` attribute on the @Command annotation.
     * &#064;Command public class Git { ... }
     *
     * CommandLine commandLine = new CommandLine(new Git())
     *         .addSubcommand("status",   new GitStatus())
     *         .addSubcommand("commit",   new GitCommit())
     *         .addSubcommand("branch",   new GitBranch());
     * </pre>
     * 
     * @return the declaratively registered subcommands of this command, or an empty array if
     *         none
     * @see CommandLine#addSubcommand(String, Object)
     * @see HelpCommand
     * @since 0.9.8
     */
    Class<?>[] subcommands() default {};

    /**
     * Specify whether methods annotated with {@code @Command} should be registered as
     * subcommands of their enclosing {@code @Command} class. The default is {@code true}. For
     * example:
     * 
     * <pre>
     * &#064;Command
     * public class Git {
     *     &#064;Command
     *     void status() { ... }
     * }
     *
     * CommandLine git = new CommandLine(new Git());
     * </pre>
     * 
     * is equivalent to this:
     * 
     * <pre>
     * // don't add command methods as subcommands automatically
     * &#064;Command(addMethodSubcommands = false)
     * public class Git {
     *     &#064;Command
     *     void status() { ... }
     * }
     *
     * // add command methods as subcommands programmatically
     * CommandLine git = new CommandLine(new Git());
     * CommandLine status = new CommandLine(CommandLine.getCommandMethods(Git.class, "status").get(0));
     * git.addSubcommand("status", status);
     * </pre>
     * 
     * @return whether methods annotated with {@code @Command} should be registered as
     *         subcommands
     * @see CommandLine#addSubcommand(String, Object)
     * @see CommandLine#getCommandMethods(Class, String)
     * @see CommandSpec#addMethodSubcommands()
     * @since 3.6.0
     */
    boolean addMethodSubcommands() default true;

    /**
     * String that separates options from option parameters. Default is {@code "="}. Spaces are
     * also accepted.
     * 
     * @return the string that separates options from option parameters, used both when parsing
     *         and when generating usage help
     * @see CommandLine#setSeparator(String)
     */
    String separator() default "=";

    /**
     * Version information for this command, to print to the console when the user specifies an
     * {@linkplain Option#versionHelp() option} to request version help. This is not part of the
     * usage help message.
     *
     * @return a string or an array of strings with version information about this command (each
     *         string in the array is displayed on a separate line).
     * @since 0.9.8
     * @see CommandLine#printVersionHelp(PrintStream)
     */
    String[] version() default {};

    /**
     * Class that can provide version information dynamically at runtime. An implementation may
     * return version information obtained from the JAR manifest, a properties file or some
     * other source.
     * 
     * @return a Class that can provide version information dynamically at runtime
     * @since 2.2
     */
    Class<? extends IVersionProvider> versionProvider() default NoVersionProvider.class;

    /**
     * Adds the standard {@code -h} and {@code --help} {@linkplain Option#usageHelp() usageHelp}
     * options and {@code -V} and {@code --version} {@linkplain Option#versionHelp()
     * versionHelp} options to the options of this command.
     * <p>
     * Note that if no {@link #version()} or {@link #versionProvider()} is specified, the
     * {@code --version} option will not print anything.
     * </p>
     * <p>
     * For {@linkplain #resourceBundle() internationalization}: the help option has
     * {@code descriptionKey = "mixinStandardHelpOptions.help"}, and the version option has
     * {@code descriptionKey = "mixinStandardHelpOptions.version"}.
     * </p>
     * 
     * @return whether the auto-help mixin should be added to this command
     * @since 3.0
     */
    boolean mixinStandardHelpOptions() default false;

    /**
     * Set this attribute to {@code true} if this subcommand is a help command, and required
     * options and positional parameters of the parent command should not be validated. If a
     * subcommand marked as {@code helpCommand} is specified on the command line, picocli will
     * not validate the parent arguments (so no "missing required option" errors) and the
     * {@link CommandLine#printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi)}
     * method will return {@code true}.
     * 
     * @return {@code true} if this subcommand is a help command and picocli should not check
     *         for missing required options and positional parameters on the parent command
     * @since 3.0
     */
    boolean helpCommand() default false;

    /**
     * Set the heading preceding the header section. May contain embedded
     * {@linkplain java.util.Formatter format specifiers}.
     * 
     * @return the heading preceding the header section
     * @see UsageMessageSpec#headerHeading()
     * @see Help#headerHeading(Object...)
     */
    String headerHeading() default "";

    /**
     * Optional summary description of the command, shown before the synopsis.
     * 
     * @return summary description of the command
     * @see UsageMessageSpec#header()
     * @see Help#header(Object...)
     */
    String[] header() default {};

    /**
     * Set the heading preceding the synopsis text. May contain embedded
     * {@linkplain java.util.Formatter format specifiers}. The default heading is
     * {@code "Usage: "} (without a line break between the heading and the synopsis text).
     * 
     * @return the heading preceding the synopsis text
     * @see Help#synopsisHeading(Object...)
     */
    String synopsisHeading() default "Usage: ";

    /**
     * Specify {@code true} to generate an abbreviated synopsis like
     * {@code "<main> [OPTIONS] [PARAMETERS...]"}. By default, a detailed synopsis with
     * individual option names and parameters is generated.
     * 
     * @return whether the synopsis should be abbreviated
     * @see Help#abbreviatedSynopsis()
     * @see Help#detailedSynopsis(Comparator, boolean)
     */
    boolean abbreviateSynopsis() default false;

    /**
     * Specify one or more custom synopsis lines to display instead of an auto-generated
     * synopsis.
     * 
     * @return custom synopsis text to replace the auto-generated synopsis
     * @see Help#customSynopsis(Object...)
     */
    String[] customSynopsis() default {};

    /**
     * Set the heading preceding the description section. May contain embedded
     * {@linkplain java.util.Formatter format specifiers}.
     * 
     * @return the heading preceding the description section
     * @see Help#descriptionHeading(Object...)
     */
    String descriptionHeading() default "";

    /**
     * Optional text to display between the synopsis line(s) and the list of options.
     * 
     * @return description of this command
     * @see Help#description(Object...)
     */
    String[] description() default {};

    /**
     * Set the heading preceding the parameters list. May contain embedded
     * {@linkplain java.util.Formatter format specifiers}.
     * 
     * @return the heading preceding the parameters list
     * @see Help#parameterListHeading(Object...)
     */
    String parameterListHeading() default "";

    /**
     * Set the heading preceding the options list. May contain embedded
     * {@linkplain java.util.Formatter format specifiers}.
     * 
     * @return the heading preceding the options list
     * @see Help#optionListHeading(Object...)
     */
    String optionListHeading() default "";

    /**
     * Specify {@code false} to show Options in declaration order. The default is to sort
     * alphabetically.
     * 
     * @return whether options should be shown in alphabetic order.
     */
    boolean sortOptions() default true;

    /**
     * Prefix required options with this character in the options list. The default is no
     * marker: the synopsis indicates which options and parameters are required.
     * 
     * @return the character to show in the options list to mark required options
     */
    char requiredOptionMarker() default ' ';

    /**
     * Class that can provide default values dynamically at runtime. An implementation may
     * return default value obtained from a configuration file like a properties file or some
     * other source.
     * 
     * @return a Class that can provide default values dynamically at runtime
     * @since 3.6
     */
    Class<? extends IDefaultValueProvider> defaultValueProvider() default NoDefaultProvider.class;

    /**
     * Specify {@code true} to show default values in the description column of the options list
     * (except for boolean options). False by default.
     * <p>
     * Note that picocli 3.2 allows {@linkplain Option#description() embedding default values}
     * anywhere in the option or positional parameter description that ignores this setting.
     * </p>
     * 
     * @return whether the default values for options and parameters should be shown in the
     *         description column
     */
    boolean showDefaultValues() default false;

    /**
     * Set the heading preceding the subcommands list. May contain embedded
     * {@linkplain java.util.Formatter format specifiers}. The default heading is
     * {@code "Commands:%n"} (with a line break at the end).
     * 
     * @return the heading preceding the subcommands list
     * @see Help#commandListHeading(Object...)
     */
    String commandListHeading() default "Commands:%n";

    /**
     * Set the heading preceding the footer section. May contain embedded
     * {@linkplain java.util.Formatter format specifiers}.
     * 
     * @return the heading preceding the footer section
     * @see Help#footerHeading(Object...)
     */
    String footerHeading() default "";

    /**
     * Optional text to display after the list of options.
     * 
     * @return text to display after the list of options
     * @see Help#footer(Object...)
     */
    String[] footer() default {};

    /**
     * Set {@code hidden=true} if this command should not be included in the list of commands in
     * the usage help of the parent command.
     * 
     * @return whether this command should be excluded from the usage message
     * @since 3.0
     */
    boolean hidden() default false;

    /**
     * Set the base name of the ResourceBundle to find option and positional parameters
     * descriptions, as well as usage help message sections and section headings.
     * <p>
     * See {@link Messages} for more details and an example.
     * </p>
     * 
     * @return the base name of the ResourceBundle for usage help strings
     * @see ArgSpec#messages()
     * @see UsageMessageSpec#messages()
     * @see CommandSpec#resourceBundle()
     * @see CommandLine#setResourceBundle(ResourceBundle)
     * @since 3.6
     */
    String resourceBundle() default "";

    /**
     * Set the {@link UsageMessageSpec#width(int) usage help message width}. The default is 80.
     * 
     * @since 3.7
     */
    int usageHelpWidth() default 80;
}