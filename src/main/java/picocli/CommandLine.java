/*
   Copyright 2017 Remko Popma

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package picocli;

import static picocli.CommandLine.Model.ArgsReflection.abbreviate;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;

import picocli.CommandLine.IExceptionHandler;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.IParseResultHandler;
import picocli.CommandLine.Model;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.ArgsReflection;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.Messages;
import picocli.CommandLine.Model.MethodParam;
import picocli.CommandLine.Model.ParserSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Model.TypedMember;
import picocli.CommandLine.Model.UsageMessageSpec;
import picocli.annots.Command;
import picocli.annots.Mixin;
import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.annots.ParentCommand;
import picocli.annots.Spec;
import picocli.annots.Unmatched;
import picocli.excepts.DuplicateOptionAnnotationsException;
import picocli.excepts.ExecutionException;
import picocli.excepts.InitializationException;
import picocli.excepts.OverwrittenOptionException;
import picocli.excepts.ParameterException;
import picocli.excepts.ParameterIndexGapException;
import picocli.excepts.PicocliException;
import picocli.excepts.UnmatchedArgumentException;
import picocli.help.Ansi;
import picocli.help.AutoHelpMixin;
import picocli.help.ColorScheme;
import picocli.help.Help;
import picocli.help.HelpCommand;
import picocli.help.HelpFactory;
import picocli.help.IHelpCommandInitializable;
import picocli.help.IHelpFactory;
import picocli.help.Layout;
import picocli.help.Text;
import picocli.help.TextTable;
import picocli.util.Assert;
import picocli.util.CollectionUtilsExt;
import picocli.util.Comparators;

/**
 * <p>
 * CommandLine interpreter that uses reflection to initialize an annotated domain object with values
 * obtained from the command line arguments.
 * </p>
 * <h2>Example</h2>
 * 
 * <pre>
 * import static picocli.CommandLine.*;
 *
 * &#064;Command(mixinStandardHelpOptions = true, version = "v3.0.0", header = "Encrypt FILE(s), or standard input, to standard output or to the output file.")
 * public class Encrypt {
 *
 *     &#064;Parameters(type = File.class, description = "Any number of input files")
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
 * Use {@code CommandLine} to initialize a domain object as follows:
 * </p>
 * 
 * <pre>
 * public static void main(String... args) {
 *     Encrypt encrypt = new Encrypt();
 *     try {
 *         ParseResult parseResult = new CommandLine(encrypt).parseArgs(args);
 *         if (!CommandLine.printHelpIfRequested(parseResult)) {
 *             runProgram(encrypt);
 *         }
 *     } catch (ParameterException ex) { // command line arguments could not be parsed
 *         System.err.println(ex.getMessage());
 *         ex.getCommandLine().usage(System.err);
 *     }
 * }
 * </pre>
 * <p>
 * Invoke the above program with some command line arguments. The below are all equivalent:
 * </p>
 * 
 * <pre>
 * --verbose --out=outfile in1 in2
 * --verbose --out outfile in1 in2
 * -v --out=outfile in1 in2
 * -v -o outfile in1 in2
 * -v -o=outfile in1 in2
 * -vo outfile in1 in2
 * -vo=outfile in1 in2
 * -v -ooutfile in1 in2
 * -vooutfile in1 in2
 * </pre>
 * <p>
 * Another example that implements {@code Callable} and uses the {@link #call(Callable, String...)
 * CommandLine.call} convenience API to run in a single line of code:
 * </p>
 * 
 * <pre>
 * &#064;Command(description = "Prints the checksum (MD5 by default) of a file to STDOUT.", name = "checksum", mixinStandardHelpOptions = true, version = "checksum 3.0")
 * class CheckSum implements Callable&lt;Void&gt; {
 *
 *     &#064;Parameters(index = "0", description = "The file whose checksum to calculate.")
 *     private File file;
 *
 *     &#064;Option(names = { "-a", "--algorithm" }, description = "MD5, SHA-1, SHA-256, ...")
 *     private String algorithm = "MD5";
 *
 *     public static void main(String[] args) throws Exception {
 *         // CheckSum implements Callable, so parsing, error handling and handling user
 *         // requests for usage help or version help can be done with one line of code.
 *         CommandLine.call(new CheckSum(), args);
 *     }
 *
 *     &#064;Override
 *     public Void call() throws Exception {
 *         // your business logic goes here...
 *         byte[] fileContents = Files.readAllBytes(file.toPath());
 *         byte[] digest = MessageDigest.getInstance(algorithm).digest(fileContents);
 *         System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(digest));
 *         return null;
 *     }
 * }
 * </pre>
 * 
 * <h2>Classes and Interfaces for Defining a CommandSpec Model</h2>
 * <p>
 * <img src="doc-files/class-diagram-definition.png" alt="Classes and Interfaces for Defining a
 * CommandSpec Model">
 * </p>
 * <h2>Classes Related to Parsing Command Line Arguments</h2>
 * <p>
 * <img src="doc-files/class-diagram-parsing.png" alt="Classes Related to Parsing Command Line
 * Arguments">
 * </p>
 */
public class CommandLine {
    /** This is picocli version {@value}. */
    public static final String VERSION = "3.8.0-SNAPSHOT";

    final Tracer tracer = new Tracer();
    final CommandSpec commandSpec;
    final Interpreter interpreter;
    public final IFactory factory;
    private IHelpFactory helpFactory;

    /**
     * Constructs a new {@code CommandLine} interpreter with the specified object (which may be an
     * annotated user object or a {@link CommandSpec CommandSpec}) and a default subcommand factory.
     * <p>
     * The specified object may be a {@link CommandSpec CommandSpec} object, or it may be a
     * {@code @Command}-annotated user object with {@code @Option} and {@code @Parameters}-annotated
     * fields, in which case picocli automatically constructs a {@code CommandSpec} from this user
     * object.
     * </p>
     * <p>
     * When the {@link #parse(String...)} method is called, the {@link CommandSpec CommandSpec}
     * object will be initialized based on command line arguments. If the commandSpec is created
     * from an annotated user object, this user object will be initialized based on the command line
     * arguments.
     * </p>
     * 
     * @param command
     *            an annotated user object or a {@code CommandSpec} object to initialize from the
     *            command line arguments
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     */
    public CommandLine(Object command) {
        this(command, new DefaultFactory());
    }

    /**
     * Constructs a new {@code CommandLine} interpreter with the specified object (which may be an
     * annotated user object or a {@link CommandSpec CommandSpec}) and object factory.
     * <p>
     * The specified object may be a {@link CommandSpec CommandSpec} object, or it may be a
     * {@code @Command}-annotated user object with {@code @Option} and {@code @Parameters}-annotated
     * fields, in which case picocli automatically constructs a {@code CommandSpec} from this user
     * object.
     * </p>
     * <p>
     * If the specified command object is an interface {@code Class} with {@code @Option} and
     * {@code @Parameters}-annotated methods, picocli creates a {@link java.lang.reflect.Proxy
     * Proxy} whose methods return the matched command line values. If the specified command object
     * is a concrete {@code Class}, picocli delegates to the {@linkplain IFactory factory} to get an
     * instance.
     * </p>
     * <p>
     * When the {@link #parse(String...)} method is called, the {@link CommandSpec CommandSpec}
     * object will be initialized based on command line arguments. If the commandSpec is created
     * from an annotated user object, this user object will be initialized based on the command line
     * arguments.
     * </p>
     * 
     * @param command
     *            an annotated user object or a {@code CommandSpec} object to initialize from the
     *            command line arguments
     * @param factory
     *            the factory used to create instances of {@linkplain Command#subcommands()
     *            subcommands}, {@linkplain Option#converter() converters}, etc., that are
     *            registered declaratively with annotation attributes
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     * @since 2.2
     */
    public CommandLine(Object command, IFactory factory) {
        this.factory = Assert.notNull(factory, "factory");
        interpreter = new Interpreter(this);
        commandSpec = CommandSpec.forAnnotatedObject(command, factory);
        commandSpec.commandLine(this);
        commandSpec.validate();
        if (commandSpec.unmatchedArgsBindings().size() > 0) {
            setUnmatchedArgumentsAllowed(true);
        }
    }

    /**
     * Returns the {@code CommandSpec} model that this {@code CommandLine} was constructed with.
     * 
     * @return the {@code CommandSpec} model
     * @since 3.0
     */
    public CommandSpec getCommandSpec() {
        return commandSpec;
    }

    /**
     * Adds the options and positional parameters in the specified mixin to this command.
     * <p>
     * The specified object may be a {@link CommandSpec CommandSpec} object, or it may be a user
     * object with {@code @Option} and {@code @Parameters}-annotated fields, in which case picocli
     * automatically constructs a {@code CommandSpec} from this user object.
     * </p>
     * 
     * @param name
     *            the name by which the mixin object may later be retrieved
     * @param mixin
     *            an annotated user object or a {@link CommandSpec CommandSpec} object whose options
     *            and positional parameters to add to this command
     * @return this CommandLine object, to allow method chaining
     * @since 3.0
     */
    public CommandLine addMixin(String name, Object mixin) {
        getCommandSpec().addMixin(name, CommandSpec.forAnnotatedObject(mixin, factory));
        return this;
    }

    /**
     * Returns a map of user objects whose options and positional parameters were added to ("mixed
     * in" with) this command.
     * 
     * @return a new Map containing the user objects mixed in with this command. If
     *         {@code CommandSpec} objects without user objects were programmatically added, use the
     *         {@link CommandSpec#mixins() underlying model} directly.
     * @since 3.0
     */
    public Map<String, Object> getMixins() {
        Map<String, CommandSpec> mixins = getCommandSpec().mixins();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (String name : mixins.keySet()) {
            result.put(name, mixins.get(name).userObject);
        }
        return result;
    }

    /**
     * Registers a subcommand with the specified name. For example:
     * 
     * <pre>
     * CommandLine commandLine = new CommandLine(new Git())
     *         .addSubcommand("status",   new GitStatus())
     *         .addSubcommand("commit",   new GitCommit();
     *         .addSubcommand("add",      new GitAdd())
     *         .addSubcommand("branch",   new GitBranch())
     *         .addSubcommand("checkout", new GitCheckout())
     *         //...
     *         ;
     * </pre>
     *
     * <p>
     * The specified object can be an annotated object or a {@code CommandLine} instance with its
     * own nested subcommands. For example:
     * </p>
     * 
     * <pre>
     * CommandLine commandLine = new CommandLine(new MainCommand())
     *         .addSubcommand("cmd1", new ChildCommand1()) // subcommand
     *         .addSubcommand("cmd2", new ChildCommand2())
     *         .addSubcommand("cmd3", new CommandLine(new ChildCommand3()) // subcommand with nested sub-subcommands
     *                 .addSubcommand("cmd3sub1", new GrandChild3Command1())
     *                 .addSubcommand("cmd3sub2", new GrandChild3Command2())
     *                 .addSubcommand("cmd3sub3", new CommandLine(new GrandChild3Command3()) // deeper nesting
     *                         .addSubcommand("cmd3sub3sub1", new GreatGrandChild3Command3_1())
     *                         .addSubcommand("cmd3sub3sub2", new GreatGrandChild3Command3_2())));
     * </pre>
     * <p>
     * The default type converters are available on all subcommands and nested sub-subcommands, but
     * custom type converters are registered only with the subcommand hierarchy as it existed when
     * the custom type was registered. To ensure a custom type converter is available to all
     * subcommands, register the type converter last, after adding subcommands.
     * </p>
     * <p>
     * See also the {@link Command#subcommands()} annotation to register subcommands declaratively.
     * </p>
     *
     * @param name
     *            the string to recognize on the command line as a subcommand
     * @param command
     *            the object to initialize with command line arguments following the subcommand
     *            name. This may be a {@code CommandLine} instance with its own (nested) subcommands
     * @return this CommandLine object, to allow method chaining
     * @see #registerConverter(Class, ITypeConverter)
     * @since 0.9.7
     * @see Command#subcommands()
     */
    public CommandLine addSubcommand(String name, Object command) {
        return addSubcommand(name, command, new String[0]);
    }

    /**
     * Registers a subcommand with the specified name and all specified aliases. See also
     * {@link #addSubcommand(String, Object)}.
     *
     *
     * @param name
     *            the string to recognize on the command line as a subcommand
     * @param command
     *            the object to initialize with command line arguments following the subcommand
     *            name. This may be a {@code CommandLine} instance with its own (nested) subcommands
     * @param aliases
     *            zero or more alias names that are also recognized on the command line as this
     *            subcommand
     * @return this CommandLine object, to allow method chaining
     * @since 3.1
     * @see #addSubcommand(String, Object)
     */
    public CommandLine addSubcommand(String name, Object command, String... aliases) {
        CommandLine subcommandLine = toCommandLine(command, factory);
        subcommandLine.getCommandSpec().aliases.addAll(Arrays.asList(aliases));
        getCommandSpec().addSubcommand(name, subcommandLine);
        CommandLine.Model.CommandReflection.initParentCommand(
                subcommandLine.getCommandSpec().userObject(), getCommandSpec().userObject());
        return this;
    }

    /**
     * Returns a map with the subcommands {@linkplain #addSubcommand(String, Object) registered} on
     * this instance.
     * 
     * @return a map with the registered subcommands
     * @since 0.9.7
     */
    public Map<String, CommandLine> getSubcommands() {
        return new LinkedHashMap<String, CommandLine>(getCommandSpec().subcommands());
    }

    /**
     * Returns the command that this is a subcommand of, or {@code null} if this is a top-level
     * command.
     * 
     * @return the command that this is a subcommand of, or {@code null} if this is a top-level
     *         command
     * @see #addSubcommand(String, Object)
     * @see Command#subcommands()
     * @since 0.9.8
     */
    public CommandLine getParent() {
        CommandSpec parent = getCommandSpec().parent();
        return parent == null ? null : parent.commandLine();
    }

    /**
     * Returns the annotated user object that this {@code CommandLine} instance was constructed
     * with.
     * 
     * @param <T>
     *            the type of the variable that the return value is being assigned to
     * @return the annotated object that this {@code CommandLine} instance was constructed with
     * @since 0.9.7
     */
    @SuppressWarnings("unchecked")
    public <T> T getCommand() {
        return (T) getCommandSpec().userObject();
    }

    public IHelpFactory helpFactory() {
        return helpFactory != null ? helpFactory : (helpFactory = new HelpFactory());
    }

    public CommandLine helpFactory(IHelpFactory helpFactory) {
        this.helpFactory = helpFactory;
        return this;
    }

    /**
     * Returns {@code true} if an option annotated with {@link Option#usageHelp()} was specified on
     * the command line.
     * 
     * @return whether the parser encountered an option annotated with {@link Option#usageHelp()}.
     * @since 0.9.8
     */
    public boolean isUsageHelpRequested() {
        return interpreter.parseResult != null && interpreter.parseResult.usageHelpRequested;
    }

    /**
     * Returns {@code true} if an option annotated with {@link Option#versionHelp()} was specified
     * on the command line.
     * 
     * @return whether the parser encountered an option annotated with {@link Option#versionHelp()}.
     * @since 0.9.8
     */
    public boolean isVersionHelpRequested() {
        return interpreter.parseResult != null && interpreter.parseResult.versionHelpRequested;
    }

    /**
     * Returns whether the value of boolean flag options should be "toggled" when the option is
     * matched. By default, flags are toggled, so if the value is {@code true} it is set to
     * {@code false}, and when the value is {@code false} it is set to {@code true}. If toggling is
     * off, flags are simply set to {@code true}.
     * 
     * @return {@code true} the value of boolean flag options should be "toggled" when the option is
     *         matched, {@code false} otherwise
     * @since 3.0
     */
    public boolean isToggleBooleanFlags() {
        return getCommandSpec().parser().toggleBooleanFlags();
    }

    /**
     * Sets whether the value of boolean flag options should be "toggled" when the option is
     * matched. The default is {@code true}.
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param newValue
     *            the new setting
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 3.0
     */
    public CommandLine setToggleBooleanFlags(boolean newValue) {
        getCommandSpec().parser().toggleBooleanFlags(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setToggleBooleanFlags(newValue);
        }
        return this;
    }

    /**
     * Returns whether options for single-value fields can be specified multiple times on the
     * command line. The default is {@code false} and a {@link OverwrittenOptionException} is thrown
     * if this happens. When {@code true}, the last specified value is retained.
     * 
     * @return {@code true} if options for single-value fields can be specified multiple times on
     *         the command line, {@code false} otherwise
     * @since 0.9.7
     */
    public boolean isOverwrittenOptionsAllowed() {
        return getCommandSpec().parser().overwrittenOptionsAllowed();
    }

    /**
     * Sets whether options for single-value fields can be specified multiple times on the command
     * line without a {@link OverwrittenOptionException} being thrown. The default is {@code false}.
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param newValue
     *            the new setting
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 0.9.7
     */
    public CommandLine setOverwrittenOptionsAllowed(boolean newValue) {
        getCommandSpec().parser().overwrittenOptionsAllowed(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setOverwrittenOptionsAllowed(newValue);
        }
        return this;
    }

    /**
     * Returns whether the parser accepts clustered short options. The default is {@code true}.
     * 
     * @return {@code true} if short options like {@code -x -v -f SomeFile} can be clustered
     *         together like {@code -xvfSomeFile}, {@code false} otherwise
     * @since 3.0
     */
    public boolean isPosixClusteredShortOptionsAllowed() {
        return getCommandSpec().parser().posixClusteredShortOptionsAllowed();
    }

    /**
     * Sets whether short options like {@code -x -v -f SomeFile} can be clustered together like
     * {@code -xvfSomeFile}. The default is {@code true}.
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param newValue
     *            the new setting
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 3.0
     */
    public CommandLine setPosixClusteredShortOptionsAllowed(boolean newValue) {
        getCommandSpec().parser().posixClusteredShortOptionsAllowed(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setPosixClusteredShortOptionsAllowed(newValue);
        }
        return this;
    }

    /**
     * Returns whether the parser should ignore case when converting arguments to {@code enum}
     * values. The default is {@code false}.
     * 
     * @return {@code true} if enum values can be specified that don't match the {@code toString()}
     *         value of the enum constant, {@code false} otherwise; e.g., for an option of type
     *         <a href=
     *         "https://docs.oracle.com/javase/8/docs/api/java/time/DayOfWeek.html">java.time.DayOfWeek</a>,
     *         values {@code MonDaY}, {@code monday} and {@code MONDAY} are all recognized if
     *         {@code true}.
     * @since 3.4
     */
    public boolean isCaseInsensitiveEnumValuesAllowed() {
        return getCommandSpec().parser().caseInsensitiveEnumValuesAllowed();
    }

    /**
     * Sets whether the parser should ignore case when converting arguments to {@code enum} values.
     * The default is {@code false}. When set to true, for example, for an option of type <a href=
     * "https://docs.oracle.com/javase/8/docs/api/java/time/DayOfWeek.html">java.time.DayOfWeek</a>,
     * values {@code MonDaY}, {@code monday} and {@code MONDAY} are all recognized if {@code true}.
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param newValue
     *            the new setting
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 3.4
     */
    public CommandLine setCaseInsensitiveEnumValuesAllowed(boolean newValue) {
        getCommandSpec().parser().caseInsensitiveEnumValuesAllowed(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setCaseInsensitiveEnumValuesAllowed(newValue);
        }
        return this;
    }

    /**
     * Returns whether the parser should trim quotes from command line arguments before processing
     * them. The default is {@code false}.
     * 
     * @return {@code true} if the parser should trim quotes from command line arguments before
     *         processing them, {@code false} otherwise;
     * @since 3.7
     */
    public boolean isTrimQuotes() {
        return getCommandSpec().parser().trimQuotes();
    }

    /**
     * Sets whether the parser should trim quotes from command line arguments before processing
     * them. The default is {@code false}.
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param newValue
     *            the new setting
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 3.7
     */
    public CommandLine setTrimQuotes(boolean newValue) {
        getCommandSpec().parser().trimQuotes(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setTrimQuotes(newValue);
        }
        return this;
    }

    /**
     * Returns whether the parser is allowed to split quoted Strings or not. The default is
     * {@code false}, so quoted strings are treated as a single value that cannot be split.
     * 
     * @return {@code true} if the parser is allowed to split quoted Strings, {@code false}
     *         otherwise;
     * @see ArgSpec#splitRegex()
     * @since 3.7
     */
    public boolean isSplitQuotedStrings() {
        return getCommandSpec().parser().splitQuotedStrings();
    }

    /**
     * Sets whether the parser is allowed to split quoted Strings. The default is {@code false}.
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param newValue
     *            the new setting
     * @return this {@code CommandLine} object, to allow method chaining
     * @see ArgSpec#splitRegex()
     * @since 3.7
     */
    public CommandLine setSplitQuotedStrings(boolean newValue) {
        getCommandSpec().parser().splitQuotedStrings(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setSplitQuotedStrings(newValue);
        }
        return this;
    }

    /**
     * Returns the end-of-options delimiter that signals that the remaining command line arguments
     * should be treated as positional parameters.
     * 
     * @return the end-of-options delimiter. The default is {@code "--"}.
     * @since 3.5
     */
    public String getEndOfOptionsDelimiter() {
        return getCommandSpec().parser().endOfOptionsDelimiter();
    }

    /**
     * Sets the end-of-options delimiter that signals that the remaining command line arguments
     * should be treated as positional parameters.
     * 
     * @param delimiter
     *            the end-of-options delimiter; must not be {@code null}. The default is
     *            {@code "--"}.
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 3.5
     */
    public CommandLine setEndOfOptionsDelimiter(String delimiter) {
        getCommandSpec().parser().endOfOptionsDelimiter(delimiter);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setEndOfOptionsDelimiter(delimiter);
        }
        return this;
    }

    /**
     * Returns the default value provider for the command, or {@code null} if none has been set.
     * 
     * @return the default value provider for this command, or {@code null}
     * @since 3.6
     * @see Command#defaultValueProvider()
     * @see CommandSpec#defaultValueProvider()
     * @see ArgSpec#defaultValueString()
     */
    public IDefaultValueProvider getDefaultValueProvider() {
        return getCommandSpec().defaultValueProvider();
    }

    /**
     * Sets a default value provider for the command and sub-commands
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its sub-commands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Sub-commands added later will have the default setting. To ensure a setting is applied to all
     * sub-commands, call the setter last, after adding sub-commands.
     * </p>
     * 
     * @param newValue
     *            the default value provider to use
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 3.6
     */
    public CommandLine setDefaultValueProvider(IDefaultValueProvider newValue) {
        getCommandSpec().defaultValueProvider(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setDefaultValueProvider(newValue);
        }
        return this;
    }

    /**
     * Returns whether the parser interprets the first positional parameter as "end of options" so
     * the remaining arguments are all treated as positional parameters. The default is
     * {@code false}.
     * 
     * @return {@code true} if all values following the first positional parameter should be treated
     *         as positional parameters, {@code false} otherwise
     * @since 2.3
     */
    public boolean isStopAtPositional() {
        return getCommandSpec().parser().stopAtPositional();
    }

    /**
     * Sets whether the parser interprets the first positional parameter as "end of options" so the
     * remaining arguments are all treated as positional parameters. The default is {@code false}.
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param newValue
     *            {@code true} if all values following the first positional parameter should be
     *            treated as positional parameters, {@code false} otherwise
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 2.3
     */
    public CommandLine setStopAtPositional(boolean newValue) {
        getCommandSpec().parser().stopAtPositional(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setStopAtPositional(newValue);
        }
        return this;
    }

    /**
     * Returns whether the parser should stop interpreting options and positional parameters as soon
     * as it encounters an unmatched option. Unmatched options are arguments that look like an
     * option but are not one of the known options, or positional arguments for which there is no
     * available slots (the command has no positional parameters or their size is limited). The
     * default is {@code false}.
     * <p>
     * Setting this flag to {@code true} automatically sets the
     * {@linkplain #isUnmatchedArgumentsAllowed() unmatchedArgumentsAllowed} flag to {@code true}
     * also.
     * </p>
     * 
     * @return {@code true} when an unmatched option should result in the remaining command line
     *         arguments to be added to the {@linkplain #getUnmatchedArguments() unmatchedArguments
     *         list}
     * @since 2.3
     */
    public boolean isStopAtUnmatched() {
        return getCommandSpec().parser().stopAtUnmatched();
    }

    /**
     * Sets whether the parser should stop interpreting options and positional parameters as soon as
     * it encounters an unmatched option. Unmatched options are arguments that look like an option
     * but are not one of the known options, or positional arguments for which there is no available
     * slots (the command has no positional parameters or their size is limited). The default is
     * {@code false}.
     * <p>
     * Setting this flag to {@code true} automatically sets the
     * {@linkplain #setUnmatchedArgumentsAllowed(boolean) unmatchedArgumentsAllowed} flag to
     * {@code true} also.
     * </p>
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param newValue
     *            {@code true} when an unmatched option should result in the remaining command line
     *            arguments to be added to the {@linkplain #getUnmatchedArguments()
     *            unmatchedArguments list}
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 2.3
     */
    public CommandLine setStopAtUnmatched(boolean newValue) {
        getCommandSpec().parser().stopAtUnmatched(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setStopAtUnmatched(newValue);
        }
        if (newValue) {
            setUnmatchedArgumentsAllowed(true);
        }
        return this;
    }

    /**
     * Returns whether arguments on the command line that resemble an option should be treated as
     * positional parameters. The default is {@code false} and the parser behaviour depends on
     * {@link #isUnmatchedArgumentsAllowed()}.
     * 
     * @return {@code true} arguments on the command line that resemble an option should be treated
     *         as positional parameters, {@code false} otherwise
     * @see #getUnmatchedArguments()
     * @since 3.0
     */
    public boolean isUnmatchedOptionsArePositionalParams() {
        return getCommandSpec().parser().unmatchedOptionsArePositionalParams();
    }

    /**
     * Sets whether arguments on the command line that resemble an option should be treated as
     * positional parameters. The default is {@code false}.
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param newValue
     *            the new setting. When {@code true}, arguments on the command line that resemble an
     *            option should be treated as positional parameters.
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 3.0
     * @see #getUnmatchedArguments()
     * @see #isUnmatchedArgumentsAllowed
     */
    public CommandLine setUnmatchedOptionsArePositionalParams(boolean newValue) {
        getCommandSpec().parser().unmatchedOptionsArePositionalParams(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setUnmatchedOptionsArePositionalParams(newValue);
        }
        return this;
    }

    /**
     * Returns whether the end user may specify arguments on the command line that are not matched
     * to any option or parameter fields. The default is {@code false} and a
     * {@link UnmatchedArgumentException} is thrown if this happens. When {@code true}, the last
     * unmatched arguments are available via the {@link #getUnmatchedArguments()} method.
     * 
     * @return {@code true} if the end use may specify unmatched arguments on the command line,
     *         {@code false} otherwise
     * @see #getUnmatchedArguments()
     * @since 0.9.7
     */
    public boolean isUnmatchedArgumentsAllowed() {
        return getCommandSpec().parser().unmatchedArgumentsAllowed();
    }

    /**
     * Sets whether the end user may specify unmatched arguments on the command line without a
     * {@link UnmatchedArgumentException} being thrown. The default is {@code false}.
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param newValue
     *            the new setting. When {@code true}, the last unmatched arguments are available via
     *            the {@link #getUnmatchedArguments()} method.
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 0.9.7
     * @see #getUnmatchedArguments()
     */
    public CommandLine setUnmatchedArgumentsAllowed(boolean newValue) {
        getCommandSpec().parser().unmatchedArgumentsAllowed(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setUnmatchedArgumentsAllowed(newValue);
        }
        return this;
    }

    /**
     * Returns the list of unmatched command line arguments, if any.
     * 
     * @return the list of unmatched command line arguments or an empty list
     * @see #isUnmatchedArgumentsAllowed()
     * @since 0.9.7
     */
    public List<String> getUnmatchedArguments() {
        return interpreter.parseResult == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(interpreter.parseResult.unmatched);
    }

    /**
     * <p>
     * Convenience method that initializes the specified annotated object from the specified command
     * line arguments.
     * </p>
     * <p>
     * This is equivalent to
     * </p>
     * 
     * <pre>
     * CommandLine cli = new CommandLine(command);
     * cli.parse(args);
     * return command;
     * </pre>
     *
     * @param command
     *            the object to initialize. This object contains fields annotated with
     *            {@code @Option} or {@code @Parameters}.
     * @param args
     *            the command line arguments to parse
     * @param <T>
     *            the type of the annotated object
     * @return the specified annotated object
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     * @throws ParameterException
     *             if the specified command line arguments are invalid
     * @since 0.9.7
     */
    public static <T> T populateCommand(T command, String... args) {
        CommandLine cli = toCommandLine(command, new DefaultFactory());
        cli.parse(args);
        return command;
    }

    /**
     * <p>
     * Convenience method that derives the command specification from the specified interface class,
     * and returns an instance of the specified interface. The interface is expected to have
     * annotated getter methods. Picocli will instantiate the interface and the getter methods will
     * return the option and positional parameter values matched on the command line.
     * </p>
     * <p>
     * This is equivalent to
     * </p>
     * 
     * <pre>
     * CommandLine cli = new CommandLine(spec);
     * cli.parse(args);
     * return cli.getCommand();
     * </pre>
     *
     * @param spec
     *            the interface that defines the command specification. This object contains getter
     *            methods annotated with {@code @Option} or {@code @Parameters}.
     * @param args
     *            the command line arguments to parse
     * @param <T>
     *            the type of the annotated object
     * @return an instance of the specified annotated interface
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     * @throws ParameterException
     *             if the specified command line arguments are invalid
     * @since 3.1
     */
    public static <T> T populateSpec(Class<T> spec, String... args) {
        CommandLine cli = toCommandLine(spec, new DefaultFactory());
        cli.parse(args);
        return cli.getCommand();
    }

    /**
     * Parses the specified command line arguments and returns a list of {@code CommandLine} objects
     * representing the top-level command and any subcommands (if any) that were recognized and
     * initialized during the parsing process.
     * <p>
     * If parsing succeeds, the first element in the returned list is always
     * {@code this CommandLine} object. The returned list may contain more elements if subcommands
     * were {@linkplain #addSubcommand(String, Object) registered} and these subcommands were
     * initialized by matching command line arguments. If parsing fails, a
     * {@link ParameterException} is thrown.
     * </p>
     *
     * @param args
     *            the command line arguments to parse
     * @return a list with the top-level command and any subcommands initialized by this method
     * @throws ParameterException
     *             if the specified command line arguments are invalid; use
     *             {@link ParameterException#getCommandLine()} to get the command or subcommand
     *             whose user input was invalid
     */
    public List<CommandLine> parse(String... args) {
        return interpreter.parse(args);
    }

    /**
     * Parses the specified command line arguments and returns a list of {@code ParseResult} with
     * the options, positional parameters, and subcommands (if any) that were recognized and
     * initialized during the parsing process.
     * <p>
     * If parsing fails, a {@link ParameterException} is thrown.
     * </p>
     *
     * @param args
     *            the command line arguments to parse
     * @return a list with the top-level command and any subcommands initialized by this method
     * @throws ParameterException
     *             if the specified command line arguments are invalid; use
     *             {@link ParameterException#getCommandLine()} to get the command or subcommand
     *             whose user input was invalid
     */
    public ParseResult parseArgs(String... args) {
        interpreter.parse(args);
        return interpreter.parseResult.build();
    }

    public ParseResult getParseResult() {
        return interpreter.parseResult == null ? null : interpreter.parseResult.build();
    }

    /**
     * Represents a function that can process a List of {@code CommandLine} objects resulting from
     * successfully {@linkplain #parse(String...) parsing} the command line arguments. This is a
     * <a href=
     * "https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">functional
     * interface</a> whose functional method is
     * {@link #handleParseResult(List, PrintStream, CommandLine.Help.Ansi)}.
     * <p>
     * Implementations of this functions can be passed to the
     * {@link #parseWithHandlers(IParseResultHandler, PrintStream, Help.Ansi, IExceptionHandler, String...)
     * CommandLine::parseWithHandler} methods to take some next step after the command line was
     * successfully parsed.
     * </p>
     * 
     * @see RunFirst
     * @see RunLast
     * @see RunAll
     * @deprecated Use {@link IParseResultHandler2} instead.
     * @since 2.0
     */
    @Deprecated
    public static interface IParseResultHandler {
        /**
         * Processes a List of {@code CommandLine} objects resulting from successfully
         * {@linkplain #parse(String...) parsing} the command line arguments and optionally returns
         * a list of results.
         * 
         * @param parsedCommands
         *            the {@code CommandLine} objects that resulted from successfully parsing the
         *            command line arguments
         * @param out
         *            the {@code PrintStream} to print help to if requested
         * @param ansi
         *            for printing help messages using ANSI styles and colors
         * @return a list of results, or an empty list if there are no results
         * @throws ParameterException
         *             if a help command was invoked for an unknown subcommand. Any
         *             {@code ParameterExceptions} thrown from this method are treated as if this
         *             exception was thrown during parsing and passed to the
         *             {@link IExceptionHandler}
         * @throws ExecutionException
         *             if a problem occurred while processing the parse results; use
         *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
         *             where processing failed
         */
        List<Object> handleParseResult(List<CommandLine> parsedCommands, PrintStream out, Ansi ansi)
                throws ExecutionException;
    }

    /**
     * Represents a function that can process the {@code ParseResult} object resulting from
     * successfully {@linkplain #parseArgs(String...) parsing} the command line arguments. This is a
     * <a href=
     * "https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">functional
     * interface</a> whose functional method is
     * {@link IParseResultHandler2#handleParseResult(CommandLine.ParseResult)}.
     * <p>
     * Implementations of this function can be passed to the
     * {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * CommandLine::parseWithHandlers} methods to take some next step after the command line was
     * successfully parsed.
     * </p>
     * <p>
     * This interface replaces the {@link IParseResultHandler} interface; it takes the parse result
     * as a {@code ParseResult} object instead of a List of {@code CommandLine} objects, and it has
     * the freedom to select the {@link Ansi} style to use and what {@code PrintStreams} to print
     * to.
     * </p>
     * 
     * @param <R>
     *            the return type of this handler
     * @see RunFirst
     * @see RunLast
     * @see RunAll
     * @since 3.0
     */
    public static interface IParseResultHandler2<R> {
        /**
         * Processes the {@code ParseResult} object resulting from successfully
         * {@linkplain CommandLine#parseArgs(String...) parsing} the command line arguments and
         * returns a return value.
         * 
         * @param parseResult
         *            the {@code ParseResult} that resulted from successfully parsing the command
         *            line arguments
         * @throws ParameterException
         *             if a help command was invoked for an unknown subcommand. Any
         *             {@code ParameterExceptions} thrown from this method are treated as if this
         *             exception was thrown during parsing and passed to the
         *             {@link IExceptionHandler2}
         * @throws ExecutionException
         *             if a problem occurred while processing the parse results; use
         *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
         *             where processing failed
         */
        R handleParseResult(ParseResult parseResult) throws ExecutionException;
    }

    /**
     * Represents a function that can handle a {@code ParameterException} that occurred while
     * {@linkplain #parse(String...) parsing} the command line arguments. This is a <a href=
     * "https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">functional
     * interface</a> whose functional method is
     * {@link #handleException(CommandLine.ParameterException, PrintStream, CommandLine.Help.Ansi, String...)}.
     * <p>
     * Implementations of this function can be passed to the
     * {@link #parseWithHandlers(IParseResultHandler, PrintStream, Help.Ansi, IExceptionHandler, String...)
     * CommandLine::parseWithHandlers} methods to handle situations when the command line could not
     * be parsed.
     * </p>
     * 
     * @deprecated Use {@link IExceptionHandler2} instead.
     * @see DefaultExceptionHandler
     * @since 2.0
     */
    @Deprecated
    public static interface IExceptionHandler {
        /**
         * Handles a {@code ParameterException} that occurred while {@linkplain #parse(String...)
         * parsing} the command line arguments and optionally returns a list of results.
         * 
         * @param ex
         *            the ParameterException describing the problem that occurred while parsing the
         *            command line arguments, and the CommandLine representing the command or
         *            subcommand whose input was invalid
         * @param out
         *            the {@code PrintStream} to print help to if requested
         * @param ansi
         *            for printing help messages using ANSI styles and colors
         * @param args
         *            the command line arguments that could not be parsed
         * @return a list of results, or an empty list if there are no results
         */
        List<Object> handleException(ParameterException ex, PrintStream out, Ansi ansi,
                String... args);
    }

    /**
     * Classes implementing this interface know how to handle {@code ParameterExceptions} (usually
     * from invalid user input) and {@code ExecutionExceptions} that occurred while executing the
     * {@code Runnable} or {@code Callable} command.
     * <p>
     * Implementations of this interface can be passed to the
     * {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * CommandLine::parseWithHandlers} method.
     * </p>
     * <p>
     * This interface replaces the {@link IParseResultHandler} interface.
     * </p>
     * 
     * @param <R>
     *            the return type of this handler
     * @see DefaultExceptionHandler
     * @since 3.0
     */
    public static interface IExceptionHandler2<R> {
        /**
         * Handles a {@code ParameterException} that occurred while
         * {@linkplain #parseArgs(String...) parsing} the command line arguments and optionally
         * returns a list of results.
         * 
         * @param ex
         *            the ParameterException describing the problem that occurred while parsing the
         *            command line arguments, and the CommandLine representing the command or
         *            subcommand whose input was invalid
         * @param args
         *            the command line arguments that could not be parsed
         * @return an object resulting from handling the exception
         */
        R handleParseException(ParameterException ex, String[] args);

        /**
         * Handles a {@code ExecutionException} that occurred while executing the {@code Runnable}
         * or {@code Callable} command and optionally returns a list of results.
         * 
         * @param ex
         *            the ExecutionException describing the problem that occurred while executing
         *            the {@code Runnable} or {@code Callable} command, and the CommandLine
         *            representing the command or subcommand that was being executed
         * @param parseResult
         *            the result of parsing the command line arguments
         * @return an object resulting from handling the exception
         */
        R handleExecutionException(ExecutionException ex, ParseResult parseResult);
    }

    /**
     * Abstract superclass for {@link IParseResultHandler2} and {@link IExceptionHandler2}
     * implementations.
     * <p>
     * Note that {@code AbstractHandler} is a generic type. This, along with the abstract
     * {@code self} method, allows method chaining to work properly in subclasses, without the need
     * for casts. An example subclass can look like this:
     * </p>
     * 
     * <pre>
     * {@code
     * class MyResultHandler extends AbstractHandler<MyReturnType, MyResultHandler> implements IParseResultHandler2<MyReturnType> {
     *
     *     public MyReturnType handleParseResult(ParseResult parseResult) { ... }
     *
     *     protected MyResultHandler self() { return this; }
     * }
     * }
     * </pre>
     * 
     * @param <R>
     *            the return type of this handler
     * @param <T>
     *            The type of the handler subclass; for fluent API method chaining
     * @since 3.0
     */
    public static abstract class AbstractHandler<R, T extends AbstractHandler<R, T>> {
        private Ansi ansi = Ansi.AUTO;
        private Integer exitCode;
        private PrintStream out = System.out;
        private PrintStream err = System.err;

        /**
         * Returns the stream to print command output to. Defaults to {@code System.out}, unless
         * {@link #useOut(PrintStream)} was called with a different stream.
         * <p>
         * {@code IParseResultHandler2} implementations should use this stream. By
         * <a href="http://www.gnu.org/prep/standards/html_node/_002d_002dhelp.html">convention</a>,
         * when the user requests help with a {@code --help} or similar option, the usage help
         * message is printed to the standard output stream so that it can be easily searched and
         * paged.
         * </p>
         */
        public PrintStream out() {
            return out;
        }

        /**
         * Returns the stream to print diagnostic messages to. Defaults to {@code System.err},
         * unless {@link #useErr(PrintStream)} was called with a different stream.
         * <p>
         * {@code IExceptionHandler2} implementations should use this stream to print error messages
         * (which may include a usage help message) when an unexpected error occurs.
         * </p>
         */
        public PrintStream err() {
            return err;
        }

        /**
         * Returns the ANSI style to use. Defaults to {@code Help.Ansi.AUTO}, unless
         * {@link #useAnsi(CommandLine.Help.Ansi)} was called with a different setting.
         */
        public Ansi ansi() {
            return ansi;
        }

        /**
         * Returns the exit code to use as the termination status, or {@code null} (the default) if
         * the handler should not call {@link System#exit(int)} after processing completes.
         * 
         * @see #andExit(int)
         */
        public Integer exitCode() {
            return exitCode;
        }

        /**
         * Returns {@code true} if an exit code was set with {@link #andExit(int)}, or {@code false}
         * (the default) if the handler should not call {@link System#exit(int)} after processing
         * completes.
         */
        public boolean hasExitCode() {
            return exitCode != null;
        }

        /**
         * Convenience method for subclasses that returns the specified result object if no exit
         * code was set, or otherwise, if an exit code {@linkplain #andExit(int) was set}, calls
         * {@code System.exit} with the configured exit code to terminate the currently running Java
         * virtual machine.
         */
        protected R returnResultOrExit(R result) {
            if (hasExitCode()) {
                System.exit(exitCode());
            }
            return result;
        }

        /**
         * Returns {@code this} to allow method chaining when calling the setters for a fluent API.
         */
        protected abstract T self();

        /**
         * Sets the stream to print command output to. For use by {@code IParseResultHandler2}
         * implementations.
         * 
         * @see #out()
         */
        public T useOut(PrintStream out) {
            this.out = Assert.notNull(out, "out");
            return self();
        }

        /**
         * Sets the stream to print diagnostic messages to. For use by {@code IExceptionHandler2}
         * implementations.
         * 
         * @see #err()
         */
        public T useErr(PrintStream err) {
            this.err = Assert.notNull(err, "err");
            return self();
        }

        /**
         * Sets the ANSI style to use.
         * 
         * @see #ansi()
         */
        public T useAnsi(Ansi ansi) {
            this.ansi = Assert.notNull(ansi, "ansi");
            return self();
        }

        /**
         * Indicates that the handler should call {@link System#exit(int)} after processing
         * completes and sets the exit code to use as the termination status.
         */
        public T andExit(int exitCode) {
            this.exitCode = exitCode;
            return self();
        }
    }

    /**
     * Default exception handler that handles invalid user input by printing the exception message,
     * followed by the usage message for the command or subcommand whose input was invalid.
     * <p>
     * {@code ParameterExceptions} (invalid user input) is handled like this:
     * </p>
     * 
     * <pre>
     * err().println(paramException.getMessage());
     * paramException.getCommandLine().usage(err(), ansi());
     * if (hasExitCode())
     *     System.exit(exitCode());
     * else
     *     return returnValue;
     * </pre>
     * <p>
     * {@code ExecutionExceptions} that occurred while executing the {@code Runnable} or
     * {@code Callable} command are simply rethrown and not handled.
     * </p>
     * 
     * @since 2.0
     */
    @SuppressWarnings("deprecation")
    public static class DefaultExceptionHandler<R>
            extends AbstractHandler<R, DefaultExceptionHandler<R>>
            implements IExceptionHandler, IExceptionHandler2<R> {
        public List<Object> handleException(ParameterException ex, PrintStream out, Ansi ansi,
                String... args) {
            internalHandleParseException(ex, out, ansi, args);
            return Collections.<Object>emptyList();
        }

        /**
         * Prints the message of the specified exception, followed by the usage message for the
         * command or subcommand whose input was invalid, to the stream returned by {@link #err()}.
         * 
         * @param ex
         *            the ParameterException describing the problem that occurred while parsing the
         *            command line arguments, and the CommandLine representing the command or
         *            subcommand whose input was invalid
         * @param args
         *            the command line arguments that could not be parsed
         * @return the empty list
         * @since 3.0
         */
        public R handleParseException(ParameterException ex, String[] args) {
            internalHandleParseException(ex, err(), ansi(), args);
            return returnResultOrExit(null);
        }

        private void internalHandleParseException(ParameterException ex, PrintStream out, Ansi ansi,
                String[] args) {
            out.println(ex.getMessage());
            if (!UnmatchedArgumentException.printSuggestions(ex, out)) {
                ex.getCommandLine().usage(out, ansi);
            }
        }

        /**
         * This implementation always simply rethrows the specified exception.
         * 
         * @param ex
         *            the ExecutionException describing the problem that occurred while executing
         *            the {@code Runnable} or {@code Callable} command
         * @param parseResult
         *            the result of parsing the command line arguments
         * @return nothing: this method always rethrows the specified exception
         * @throws ExecutionException
         *             always rethrows the specified exception
         * @since 3.0
         */
        public R handleExecutionException(ExecutionException ex, ParseResult parseResult) {
            throw ex;
        }

        @Override
        protected DefaultExceptionHandler<R> self() {
            return this;
        }
    }

    /** Convenience method that returns {@code new DefaultExceptionHandler<List<Object>>()}. */
    public static DefaultExceptionHandler<List<Object>> defaultExceptionHandler() {
        return new DefaultExceptionHandler<List<Object>>();
    }

    /**
     * @deprecated use {@link #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi)}
     *             instead
     * @since 2.0
     */
    @Deprecated
    public static boolean printHelpIfRequested(List<CommandLine> parsedCommands, PrintStream out,
            Ansi ansi) {
        return printHelpIfRequested(parsedCommands, out, out, ansi);
    }

    /**
     * Delegates to {@link #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi)} with
     * {@code parseResult.asCommandLineList(), System.out, System.err, Help.Ansi.AUTO}.
     * 
     * @since 3.0
     */
    public static boolean printHelpIfRequested(ParseResult parseResult) {
        return printHelpIfRequested(parseResult.asCommandLineList(), System.out, System.err,
                Ansi.AUTO);
    }

    /**
     * Helper method that may be useful when processing the list of {@code CommandLine} objects that
     * result from successfully {@linkplain #parse(String...) parsing} command line arguments. This
     * method prints out {@linkplain #usage(PrintStream, Help.Ansi) usage help} if
     * {@linkplain #isUsageHelpRequested() requested} or
     * {@linkplain #printVersionHelp(PrintStream, Help.Ansi) version help} if
     * {@linkplain #isVersionHelpRequested() requested} and returns {@code true}. If the command is
     * a {@link Command#helpCommand()} and {@code runnable} or {@code callable}, that command is
     * executed and this method returns {@code true}. Otherwise, if none of the specified
     * {@code CommandLine} objects have help requested, this method returns {@code false}.
     * <p>
     * Note that this method <em>only</em> looks at the {@link Option#usageHelp() usageHelp} and
     * {@link Option#versionHelp() versionHelp} attributes. The {@link Option#help() help} attribute
     * is ignored.
     * </p>
     * <p>
     * <b>Implementation note:</b>
     * </p>
     * <p>
     * When an error occurs while processing the help request, it is recommended custom Help
     * commands throw a {@link ParameterException} with a reference to the parent command. This will
     * print the error message and the usage for the parent command, and will use the exit code of
     * the exception handler if one was set.
     * </p>
     * 
     * @param parsedCommands
     *            the list of {@code CommandLine} objects to check if help was requested
     * @param out
     *            the {@code PrintStream} to print help to if requested
     * @param err
     *            the error string to print diagnostic messages to, in addition to the output from
     *            the exception handler
     * @param ansi
     *            for printing help messages using ANSI styles and colors
     * @return {@code true} if help was printed, {@code false} otherwise
     * @see IHelpCommandInitializable
     * @since 3.0
     */
    public static boolean printHelpIfRequested(List<CommandLine> parsedCommands, PrintStream out,
            PrintStream err, Ansi ansi) {
        return printHelpIfRequested(parsedCommands, out, err, ColorScheme.createDefault(ansi));
    }

    /**
     * Helper method that may be useful when processing the list of {@code CommandLine} objects that
     * result from successfully {@linkplain #parse(String...) parsing} command line arguments. This
     * method prints out {@linkplain #usage(PrintStream, Help.ColorScheme) usage help} if
     * {@linkplain #isUsageHelpRequested() requested} or
     * {@linkplain #printVersionHelp(PrintStream, Help.Ansi) version help} if
     * {@linkplain #isVersionHelpRequested() requested} and returns {@code true}. If the command is
     * a {@link Command#helpCommand()} and {@code runnable} or {@code callable}, that command is
     * executed and this method returns {@code true}. Otherwise, if none of the specified
     * {@code CommandLine} objects have help requested, this method returns {@code false}.
     * <p>
     * Note that this method <em>only</em> looks at the {@link Option#usageHelp() usageHelp} and
     * {@link Option#versionHelp() versionHelp} attributes. The {@link Option#help() help} attribute
     * is ignored.
     * </p>
     * <p>
     * <b>Implementation note:</b>
     * </p>
     * <p>
     * When an error occurs while processing the help request, it is recommended custom Help
     * commands throw a {@link ParameterException} with a reference to the parent command. This will
     * print the error message and the usage for the parent command, and will use the exit code of
     * the exception handler if one was set.
     * </p>
     * 
     * @param parsedCommands
     *            the list of {@code CommandLine} objects to check if help was requested
     * @param out
     *            the {@code PrintStream} to print help to if requested
     * @param err
     *            the error string to print diagnostic messages to, in addition to the output from
     *            the exception handler
     * @param colorScheme
     *            for printing help messages using ANSI styles and colors
     * @return {@code true} if help was printed, {@code false} otherwise
     * @see IHelpCommandInitializable
     * @since 3.6
     */
    public static boolean printHelpIfRequested(List<CommandLine> parsedCommands, PrintStream out,
            PrintStream err, ColorScheme colorScheme) {
        for (int i = 0; i < parsedCommands.size(); i++) {
            CommandLine parsed = parsedCommands.get(i);
            if (parsed.isUsageHelpRequested()) {
                parsed.usage(out, colorScheme);
                return true;
            } else if (parsed.isVersionHelpRequested()) {
                parsed.printVersionHelp(out, colorScheme.ansi());
                return true;
            } else if (parsed.getCommandSpec().helpCommand()) {
                if (parsed.getCommand() instanceof IHelpCommandInitializable) {
                    ((IHelpCommandInitializable) parsed.getCommand()).init(parsed,
                            colorScheme.ansi(), out, err);
                }
                execute(parsed, new ArrayList<Object>());
                return true;
            }
        }
        return false;
    }

    private static List<Object> execute(CommandLine parsed, List<Object> executionResult) {
        Object command = parsed.getCommand();
        if (command instanceof Runnable) {
            try {
                ((Runnable) command).run();
                executionResult.add(null); // for compatibility with picocli 2.x
                return executionResult;
            } catch (ParameterException ex) {
                throw ex;
            } catch (ExecutionException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ExecutionException(parsed,
                        "Error while running command (" + command + "): " + ex, ex);
            }
        } else if (command instanceof Callable) {
            try {
                @SuppressWarnings("unchecked")
                Callable<Object> callable = (Callable<Object>) command;
                executionResult.add(callable.call());
                return executionResult;
            } catch (ParameterException ex) {
                throw ex;
            } catch (ExecutionException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ExecutionException(parsed,
                        "Error while calling command (" + command + "): " + ex, ex);
            }
        } else if (command instanceof Method) {
            try {
                if (Modifier.isStatic(((Method) command).getModifiers())) {
                    // invoke static method
                    executionResult.add(
                            ((Method) command).invoke(null, parsed.getCommandSpec().argValues()));
                    return executionResult;
                } else if (parsed.getCommandSpec().parent() != null) {
                    executionResult.add(
                            ((Method) command).invoke(parsed.getCommandSpec().parent().userObject(),
                                    parsed.getCommandSpec().argValues()));
                    return executionResult;
                } else {
                    // TODO: allow ITypeConverter's to provide an instance?
                    for (Constructor<?> constructor : ((Method) command).getDeclaringClass()
                            .getDeclaredConstructors()) {
                        if (constructor.getParameterTypes().length == 0) {
                            executionResult.add(((Method) command).invoke(constructor.newInstance(),
                                    parsed.getCommandSpec().argValues()));
                            return executionResult;
                        }
                    }
                    throw new UnsupportedOperationException(
                            "Invoking non-static method without default constructor not implemented");
                }
            } catch (ParameterException ex) {
                throw ex;
            } catch (ExecutionException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ExecutionException(parsed,
                        "Error while calling command (" + command + "): " + ex, ex);
            }
        }
        throw new ExecutionException(parsed,
                "Parsed command (" + command + ") is not Method, Runnable or Callable");
    }

    /**
     * Command line parse result handler that returns a value. This handler prints help if
     * requested, and otherwise calls {@link #handle(CommandLine.ParseResult)} with the parse
     * result. Facilitates implementation of the {@link IParseResultHandler2} interface.
     * <p>
     * Note that {@code AbstractParseResultHandler} is a generic type. This, along with the abstract
     * {@code self} method, allows method chaining to work properly in subclasses, without the need
     * for casts. An example subclass can look like this:
     * </p>
     * 
     * <pre>
     * {@code
     * class MyResultHandler extends AbstractParseResultHandler<MyReturnType> {
     *
     *     protected MyReturnType handle(ParseResult parseResult) throws ExecutionException { ... }
     *
     *     protected MyResultHandler self() { return this; }
     * }
     * }
     * </pre>
     * 
     * @since 3.0
     */
    public abstract static class AbstractParseResultHandler<R> extends
            AbstractHandler<R, AbstractParseResultHandler<R>> implements IParseResultHandler2<R> {
        /**
         * Prints help if requested, and otherwise calls {@link #handle(CommandLine.ParseResult)}.
         * Finally, either a list of result objects is returned, or the JVM is terminated if an exit
         * code {@linkplain #andExit(int) was set}.
         *
         * @param parseResult
         *            the {@code ParseResult} that resulted from successfully parsing the command
         *            line arguments
         * @return the result of {@link #handle(ParseResult) processing parse results}
         * @throws ParameterException
         *             if the {@link HelpCommand HelpCommand} was invoked for an unknown subcommand.
         *             Any {@code ParameterExceptions} thrown from this method are treated as if
         *             this exception was thrown during parsing and passed to the
         *             {@link IExceptionHandler2}
         * @throws ExecutionException
         *             if a problem occurred while processing the parse results; client code can use
         *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
         *             where processing failed
         */
        public R handleParseResult(ParseResult parseResult) throws ExecutionException {
            if (printHelpIfRequested(parseResult.asCommandLineList(), out(), err(), ansi())) {
                return returnResultOrExit(null);
            }
            return returnResultOrExit(handle(parseResult));
        }

        /**
         * Processes the specified {@code ParseResult} and returns the result as a list of objects.
         * Implementations are responsible for catching any exceptions thrown in the {@code handle}
         * method, and rethrowing an {@code ExecutionException} that details the problem and
         * captures the offending {@code CommandLine} object.
         *
         * @param parseResult
         *            the {@code ParseResult} that resulted from successfully parsing the command
         *            line arguments
         * @return the result of processing parse results
         * @throws ExecutionException
         *             if a problem occurred while processing the parse results; client code can use
         *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
         *             where processing failed
         */
        protected abstract R handle(ParseResult parseResult) throws ExecutionException;
    }

    /**
     * Command line parse result handler that prints help if requested, and otherwise executes the
     * top-level {@code Runnable} or {@code Callable} command. For use in the
     * {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * parseWithHandler} methods.
     * 
     * @since 2.0
     */
    public static class RunFirst extends AbstractParseResultHandler<List<Object>>
            implements IParseResultHandler {
        /**
         * Prints help if requested, and otherwise executes the top-level {@code Runnable} or
         * {@code Callable} command. Finally, either a list of result objects is returned, or the
         * JVM is terminated if an exit code {@linkplain #andExit(int) was set}. If the top-level
         * command does not implement either {@code Runnable} or {@code Callable}, an
         * {@code ExecutionException} is thrown detailing the problem and capturing the offending
         * {@code CommandLine} object.
         *
         * @param parsedCommands
         *            the {@code CommandLine} objects that resulted from successfully parsing the
         *            command line arguments
         * @param out
         *            the {@code PrintStream} to print help to if requested
         * @param ansi
         *            for printing help messages using ANSI styles and colors
         * @return an empty list if help was requested, or a list containing a single element: the
         *         result of calling the {@code Callable}, or a {@code null} element if the
         *         top-level command was a {@code Runnable}
         * @throws ParameterException
         *             if the {@link HelpCommand HelpCommand} was invoked for an unknown subcommand.
         *             Any {@code ParameterExceptions} thrown from this method are treated as if
         *             this exception was thrown during parsing and passed to the
         *             {@link IExceptionHandler}
         * @throws ExecutionException
         *             if a problem occurred while processing the parse results; use
         *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
         *             where processing failed
         */
        public List<Object> handleParseResult(List<CommandLine> parsedCommands, PrintStream out,
                Ansi ansi) {
            if (printHelpIfRequested(parsedCommands, out, err(), ansi)) {
                return returnResultOrExit(Collections.emptyList());
            }
            return returnResultOrExit(execute(parsedCommands.get(0), new ArrayList<Object>()));
        }

        /**
         * Executes the top-level {@code Runnable} or {@code Callable} subcommand. If the top-level
         * command does not implement either {@code Runnable} or {@code Callable}, an
         * {@code ExecutionException} is thrown detailing the problem and capturing the offending
         * {@code CommandLine} object.
         *
         * @param parseResult
         *            the {@code ParseResult} that resulted from successfully parsing the command
         *            line arguments
         * @return an empty list if help was requested, or a list containing a single element: the
         *         result of calling the {@code Callable}, or a {@code null} element if the last
         *         (sub)command was a {@code Runnable}
         * @throws ExecutionException
         *             if a problem occurred while processing the parse results; use
         *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
         *             where processing failed
         * @since 3.0
         */
        protected List<Object> handle(ParseResult parseResult) throws ExecutionException {
            return execute(parseResult.commandSpec().commandLine(), new ArrayList<Object>()); // first
        }

        @Override
        protected RunFirst self() {
            return this;
        }
    }

    /**
     * Command line parse result handler that prints help if requested, and otherwise executes the
     * most specific {@code Runnable} or {@code Callable} subcommand. For use in the
     * {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * parseWithHandler} methods.
     * <p>
     * Something like this:
     * </p>
     * 
     * <pre>
     * {
     *     &#64;code
     *     // RunLast implementation: print help if requested, otherwise execute the most specific subcommand
     *     List<CommandLine> parsedCommands = parseResult.asCommandLineList();
     *     if (CommandLine.printHelpIfRequested(parsedCommands, out(), err(), ansi())) {
     *         return emptyList();
     *     }
     *     CommandLine last = parsedCommands.get(parsedCommands.size() - 1);
     *     Object command = last.getCommand();
     *     Object result = null;
     *     if (command instanceof Runnable) {
     *         try {
     *             ((Runnable) command).run();
     *         } catch (Exception ex) {
     *             throw new ExecutionException(last, "Error in runnable " + command, ex);
     *         }
     *     } else if (command instanceof Callable) {
     *         try {
     *             result = ((Callable) command).call();
     *         } catch (Exception ex) {
     *             throw new ExecutionException(last, "Error in callable " + command, ex);
     *         }
     *     } else {
     *         throw new ExecutionException(last,
     *                 "Parsed command (" + command + ") is not Runnable or Callable");
     *     }
     *     if (hasExitCode()) {
     *         System.exit(exitCode());
     *     }
     *     return Arrays.asList(result);
     * }
     * </pre>
     * <p>
     * From picocli v2.0, {@code RunLast} is used to implement the
     * {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...) run} and
     * {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...) call} convenience
     * methods.
     * </p>
     * 
     * @since 2.0
     */
    public static class RunLast extends AbstractParseResultHandler<List<Object>>
            implements IParseResultHandler {
        /**
         * Prints help if requested, and otherwise executes the most specific {@code Runnable} or
         * {@code Callable} subcommand. Finally, either a list of result objects is returned, or the
         * JVM is terminated if an exit code {@linkplain #andExit(int) was set}. If the last
         * (sub)command does not implement either {@code Runnable} or {@code Callable}, an
         * {@code ExecutionException} is thrown detailing the problem and capturing the offending
         * {@code CommandLine} object.
         *
         * @param parsedCommands
         *            the {@code CommandLine} objects that resulted from successfully parsing the
         *            command line arguments
         * @param out
         *            the {@code PrintStream} to print help to if requested
         * @param ansi
         *            for printing help messages using ANSI styles and colors
         * @return an empty list if help was requested, or a list containing a single element: the
         *         result of calling the {@code Callable}, or a {@code null} element if the last
         *         (sub)command was a {@code Runnable}
         * @throws ParameterException
         *             if the {@link HelpCommand HelpCommand} was invoked for an unknown subcommand.
         *             Any {@code ParameterExceptions} thrown from this method are treated as if
         *             this exception was thrown during parsing and passed to the
         *             {@link IExceptionHandler}
         * @throws ExecutionException
         *             if a problem occurred while processing the parse results; use
         *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
         *             where processing failed
         */
        public List<Object> handleParseResult(List<CommandLine> parsedCommands, PrintStream out,
                Ansi ansi) {
            if (printHelpIfRequested(parsedCommands, out, err(), ansi)) {
                return returnResultOrExit(Collections.emptyList());
            }
            return returnResultOrExit(execute(parsedCommands.get(parsedCommands.size() - 1),
                    new ArrayList<Object>()));
        }

        /**
         * Executes the most specific {@code Runnable} or {@code Callable} subcommand. If the last
         * (sub)command does not implement either {@code Runnable} or {@code Callable}, an
         * {@code ExecutionException} is thrown detailing the problem and capturing the offending
         * {@code CommandLine} object.
         *
         * @param parseResult
         *            the {@code ParseResult} that resulted from successfully parsing the command
         *            line arguments
         * @return an empty list if help was requested, or a list containing a single element: the
         *         result of calling the {@code Callable}, or a {@code null} element if the last
         *         (sub)command was a {@code Runnable}
         * @throws ExecutionException
         *             if a problem occurred while processing the parse results; use
         *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
         *             where processing failed
         * @since 3.0
         */
        protected List<Object> handle(ParseResult parseResult) throws ExecutionException {
            List<CommandLine> parsedCommands = parseResult.asCommandLineList();
            return execute(parsedCommands.get(parsedCommands.size() - 1), new ArrayList<Object>());
        }

        @Override
        protected RunLast self() {
            return this;
        }
    }

    /**
     * Command line parse result handler that prints help if requested, and otherwise executes the
     * top-level command and all subcommands as {@code Runnable} or {@code Callable}. For use in the
     * {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * parseWithHandler} methods.
     * 
     * @since 2.0
     */
    public static class RunAll extends AbstractParseResultHandler<List<Object>>
            implements IParseResultHandler {
        /**
         * Prints help if requested, and otherwise executes the top-level command and all
         * subcommands as {@code Runnable} or {@code Callable}. Finally, either a list of result
         * objects is returned, or the JVM is terminated if an exit code {@linkplain #andExit(int)
         * was set}. If any of the {@code CommandLine} commands does not implement either
         * {@code Runnable} or {@code Callable}, an {@code ExecutionException} is thrown detailing
         * the problem and capturing the offending {@code CommandLine} object.
         *
         * @param parsedCommands
         *            the {@code CommandLine} objects that resulted from successfully parsing the
         *            command line arguments
         * @param out
         *            the {@code PrintStream} to print help to if requested
         * @param ansi
         *            for printing help messages using ANSI styles and colors
         * @return an empty list if help was requested, or a list containing the result of executing
         *         all commands: the return values from calling the {@code Callable} commands,
         *         {@code null} elements for commands that implement {@code Runnable}
         * @throws ParameterException
         *             if the {@link HelpCommand HelpCommand} was invoked for an unknown subcommand.
         *             Any {@code ParameterExceptions} thrown from this method are treated as if
         *             this exception was thrown during parsing and passed to the
         *             {@link IExceptionHandler}
         * @throws ExecutionException
         *             if a problem occurred while processing the parse results; use
         *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
         *             where processing failed
         */
        public List<Object> handleParseResult(List<CommandLine> parsedCommands, PrintStream out,
                Ansi ansi) {
            if (printHelpIfRequested(parsedCommands, out, err(), ansi)) {
                return returnResultOrExit(Collections.emptyList());
            }
            List<Object> result = new ArrayList<Object>();
            for (CommandLine parsed : parsedCommands) {
                execute(parsed, result);
            }
            return returnResultOrExit(result);
        }

        /**
         * Executes the top-level command and all subcommands as {@code Runnable} or
         * {@code Callable}. If any of the {@code CommandLine} commands does not implement either
         * {@code Runnable} or {@code Callable}, an {@code ExecutionException} is thrown detailing
         * the problem and capturing the offending {@code CommandLine} object.
         *
         * @param parseResult
         *            the {@code ParseResult} that resulted from successfully parsing the command
         *            line arguments
         * @return an empty list if help was requested, or a list containing the result of executing
         *         all commands: the return values from calling the {@code Callable} commands,
         *         {@code null} elements for commands that implement {@code Runnable}
         * @throws ExecutionException
         *             if a problem occurred while processing the parse results; use
         *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
         *             where processing failed
         * @since 3.0
         */
        protected List<Object> handle(ParseResult parseResult) throws ExecutionException {
            List<Object> result = new ArrayList<Object>();
            execute(parseResult.commandSpec().commandLine(), result);
            while (parseResult.hasSubcommand()) {
                parseResult = parseResult.subcommand();
                execute(parseResult.commandSpec().commandLine(), result);
            }
            return returnResultOrExit(result);
        }

        @Override
        protected RunAll self() {
            return this;
        }
    }

    /**
     * @deprecated use {@link #parseWithHandler(IParseResultHandler2, String[])} instead
     * @since 2.0
     */
    @Deprecated
    public List<Object> parseWithHandler(IParseResultHandler handler, PrintStream out,
            String... args) {
        return parseWithHandlers(handler, out, Ansi.AUTO, defaultExceptionHandler(), args);
    }

    /**
     * Returns the result of calling
     * {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)} with a new
     * {@link DefaultExceptionHandler} in addition to the specified parse result handler and the
     * specified command line arguments.
     * <p>
     * This is a convenience method intended to offer the same ease of use as the
     * {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...) run} and
     * {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...) call} methods, but
     * with more flexibility and better support for nested subcommands.
     * </p>
     * <p>
     * Calling this method roughly expands to:
     * </p>
     * 
     * <pre>
     * {@code
     * try {
     *     ParseResult parseResult = parseArgs(args);
     *     return handler.handleParseResult(parseResult);
     * } catch (ParameterException ex) {
     *     return new DefaultExceptionHandler<R>().handleParseException(ex, args);
     * }
     * }
     * </pre>
     * <p>
     * Picocli provides some default handlers that allow you to accomplish some common tasks with
     * very little code. The following handlers are available:
     * </p>
     * <ul>
     * <li>{@link RunLast} handler prints help if requested, and otherwise gets the last specified
     * command or subcommand and tries to execute it as a {@code Runnable} or {@code Callable}.</li>
     * <li>{@link RunFirst} handler prints help if requested, and otherwise executes the top-level
     * command as a {@code Runnable} or {@code Callable}.</li>
     * <li>{@link RunAll} handler prints help if requested, and otherwise executes all recognized
     * commands and subcommands as {@code Runnable} or {@code Callable} tasks.</li>
     * <li>{@link DefaultExceptionHandler} prints the error message followed by usage help</li>
     * </ul>
     * 
     * @param <R>
     *            the return type of this handler
     * @param handler
     *            the function that will handle the result of successfully parsing the command line
     *            arguments
     * @param args
     *            the command line arguments
     * @return an object resulting from handling the parse result or the exception that occurred
     *         while parsing the input
     * @throws ExecutionException
     *             if the command line arguments were parsed successfully but a problem occurred
     *             while processing the parse results; use
     *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
     *             where processing failed
     * @see RunLast
     * @see RunAll
     * @since 3.0
     */
    public <R> R parseWithHandler(IParseResultHandler2<R> handler, String[] args) {
        return parseWithHandlers(handler, new DefaultExceptionHandler<R>(), args);
    }

    /**
     * @deprecated use
     *             {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)}
     *             instead
     * @since 2.0
     */
    @Deprecated
    public List<Object> parseWithHandlers(IParseResultHandler handler, PrintStream out, Ansi ansi,
            IExceptionHandler exceptionHandler, String... args) {
        try {
            List<CommandLine> result = parse(args);
            return handler.handleParseResult(result, out, ansi);
        } catch (ParameterException ex) {
            return exceptionHandler.handleException(ex, out, ansi, args);
        }
    }

    /**
     * Tries to {@linkplain #parseArgs(String...) parse} the specified command line arguments, and
     * if successful, delegates the processing of the resulting {@code ParseResult} object to the
     * specified {@linkplain IParseResultHandler2 handler}. If the command line arguments were
     * invalid, the {@code ParameterException} thrown from the {@code parse} method is caught and
     * passed to the specified {@link IExceptionHandler2}.
     * <p>
     * This is a convenience method intended to offer the same ease of use as the
     * {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...) run} and
     * {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...) call} methods, but
     * with more flexibility and better support for nested subcommands.
     * </p>
     * <p>
     * Calling this method roughly expands to:
     * </p>
     * 
     * <pre>
     * ParseResult parseResult = null;
     * try {
     *     parseResult = parseArgs(args);
     *     return handler.handleParseResult(parseResult);
     * } catch (ParameterException ex) {
     *     return exceptionHandler.handleParseException(ex, (String[]) args);
     * } catch (ExecutionException ex) {
     *     return exceptionHandler.handleExecutionException(ex, parseResult);
     * }
     * </pre>
     * <p>
     * Picocli provides some default handlers that allow you to accomplish some common tasks with
     * very little code. The following handlers are available:
     * </p>
     * <ul>
     * <li>{@link RunLast} handler prints help if requested, and otherwise gets the last specified
     * command or subcommand and tries to execute it as a {@code Runnable} or {@code Callable}.</li>
     * <li>{@link RunFirst} handler prints help if requested, and otherwise executes the top-level
     * command as a {@code Runnable} or {@code Callable}.</li>
     * <li>{@link RunAll} handler prints help if requested, and otherwise executes all recognized
     * commands and subcommands as {@code Runnable} or {@code Callable} tasks.</li>
     * <li>{@link DefaultExceptionHandler} prints the error message followed by usage help</li>
     * </ul>
     *
     * @param handler
     *            the function that will handle the result of successfully parsing the command line
     *            arguments
     * @param exceptionHandler
     *            the function that can handle the {@code ParameterException} thrown when the
     *            command line arguments are invalid
     * @param args
     *            the command line arguments
     * @return an object resulting from handling the parse result or the exception that occurred
     *         while parsing the input
     * @throws ExecutionException
     *             if the command line arguments were parsed successfully but a problem occurred
     *             while processing the parse result {@code ParseResult} object; use
     *             {@link ExecutionException#getCommandLine()} to get the command or subcommand
     *             where processing failed
     * @param <R>
     *            the return type of the result handler and exception handler
     * @see RunLast
     * @see RunAll
     * @see DefaultExceptionHandler
     * @since 3.0
     */
    public <R> R parseWithHandlers(IParseResultHandler2<R> handler,
            IExceptionHandler2<R> exceptionHandler, String... args) {
        ParseResult parseResult = null;
        try {
            parseResult = parseArgs(args);
            return handler.handleParseResult(parseResult);
        } catch (ParameterException ex) {
            return exceptionHandler.handleParseException(ex, args);
        } catch (ExecutionException ex) {
            return exceptionHandler.handleExecutionException(ex, parseResult);
        }
    }

    /**
     * Equivalent to {@code new CommandLine(command).usage(out)}. See {@link #usage(PrintStream)}
     * for details.
     * 
     * @param command
     *            the object annotated with {@link Command}, {@link Option} and {@link Parameters}
     * @param out
     *            the print stream to print the help message to
     * @throws IllegalArgumentException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     */
    public static void usage(Object command, PrintStream out) {
        toCommandLine(command, new DefaultFactory()).usage(out);
    }

    /**
     * Equivalent to {@code new CommandLine(command).usage(out, ansi)}. See
     * {@link #usage(PrintStream, Help.Ansi)} for details.
     * 
     * @param command
     *            the object annotated with {@link Command}, {@link Option} and {@link Parameters}
     * @param out
     *            the print stream to print the help message to
     * @param ansi
     *            whether the usage message should contain ANSI escape codes or not
     * @throws IllegalArgumentException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     */
    public static void usage(Object command, PrintStream out, Ansi ansi) {
        toCommandLine(command, new DefaultFactory()).usage(out, ansi);
    }

    /**
     * Equivalent to {@code new CommandLine(command).usage(out, colorScheme)}. See
     * {@link #usage(PrintStream, Help.ColorScheme)} for details.
     * 
     * @param command
     *            the object annotated with {@link Command}, {@link Option} and {@link Parameters}
     * @param out
     *            the print stream to print the help message to
     * @param colorScheme
     *            the {@code ColorScheme} defining the styles for options, parameters and commands
     *            when ANSI is enabled
     * @throws IllegalArgumentException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     */
    public static void usage(Object command, PrintStream out, ColorScheme colorScheme) {
        toCommandLine(command, new DefaultFactory()).usage(out, colorScheme);
    }

    /**
     * Delegates to {@link #usage(PrintStream, Help.Ansi)} with the {@linkplain Ansi#AUTO platform
     * default}.
     * 
     * @param out
     *            the printStream to print to
     * @see #usage(PrintStream, Help.ColorScheme)
     */
    public void usage(PrintStream out) {
        usage(out, Ansi.AUTO);
    }

    /**
     * Delegates to {@link #usage(PrintWriter, Help.Ansi)} with the {@linkplain Ansi#AUTO platform
     * default}.
     * 
     * @param writer
     *            the PrintWriter to print to
     * @see #usage(PrintWriter, Help.ColorScheme)
     * @since 3.0
     */
    public void usage(PrintWriter writer) {
        usage(writer, Ansi.AUTO);
    }

    /**
     * Delegates to {@link #usage(PrintStream, Help.ColorScheme)} with the
     * {@linkplain Help#defaultColorScheme(CommandLine.Help.Ansi) default color scheme}.
     * 
     * @param out
     *            the printStream to print to
     * @param ansi
     *            whether the usage message should include ANSI escape codes or not
     * @see #usage(PrintStream, Help.ColorScheme)
     */
    public void usage(PrintStream out, Ansi ansi) {
        usage(out, ColorScheme.createDefault(ansi));
    }

    /**
     * Similar to {@link #usage(PrintStream, Help.Ansi)} but with the specified {@code PrintWriter}
     * instead of a {@code PrintStream}.
     * 
     * @since 3.0
     */
    public void usage(PrintWriter writer, Ansi ansi) {
        usage(writer, ColorScheme.createDefault(ansi));
    }

    /**
     * Prints a usage help message for the annotated command class to the specified
     * {@code PrintStream}. Delegates construction of the usage help message to the {@link Help}
     * inner class and is equivalent to:
     * 
     * <pre>
     * Help help = new Help(command).addAllSubcommands(getSubcommands());
     * StringBuilder sb = new StringBuilder().append(help.headerHeading()).append(help.header())
     *         .append(help.synopsisHeading()) //e.g. Usage:
     *         .append(help.synopsis()) //e.g. &lt;main class&gt; [OPTIONS] &lt;command&gt; [COMMAND-OPTIONS] [ARGUMENTS]
     *         .append(help.descriptionHeading()) //e.g. %nDescription:%n%n
     *         .append(help.description()) //e.g. {"Converts foos to bars.", "Use options to control conversion mode."}
     *         .append(help.parameterListHeading()) //e.g. %nPositional parameters:%n%n
     *         .append(help.parameterList()) //e.g. [FILE...] the files to convert
     *         .append(help.optionListHeading()) //e.g. %nOptions:%n%n
     *         .append(help.optionList()) //e.g. -h, --help   displays this help and exits
     *         .append(help.commandListHeading()) //e.g. %nCommands:%n%n
     *         .append(help.commandList()) //e.g.    add       adds the frup to the frooble
     *         .append(help.footerHeading()).append(help.footer());
     * out.print(sb);
     * </pre>
     * <p>
     * Annotate your class with {@link Command} to control many aspects of the usage help message,
     * including the program name, text of section headings and section contents, and some aspects
     * of the auto-generated sections of the usage help message.
     * <p>
     * To customize the auto-generated sections of the usage help message, like how option details
     * are displayed, instantiate a {@link Help} object and use a {@link TextTable} with more of
     * fewer columns, a custom {@linkplain Layout layout}, and/or a custom option
     * {@linkplain Help.IOptionRenderer renderer} for ultimate control over which aspects of an
     * Option or Field are displayed where.
     * </p>
     * 
     * @param out
     *            the {@code PrintStream} to print the usage help message to
     * @param colorScheme
     *            the {@code ColorScheme} defining the styles for options, parameters and commands
     *            when ANSI is enabled
     */
    public void usage(PrintStream out, ColorScheme colorScheme) {
        out.print(getUsageMessage(colorScheme));
    }

    /**
     * Similar to {@link #usage(PrintStream, Help.ColorScheme)}, but with the specified
     * {@code PrintWriter} instead of a {@code PrintStream}.
     * 
     * @since 3.0
     */
    public void usage(PrintWriter writer, ColorScheme colorScheme) {
        writer.print(getUsageMessage(colorScheme));
    }

    /**
     * Similar to {@link #usage(PrintStream)}, but returns the usage help message as a String
     * instead of printing it to the {@code PrintStream}.
     * 
     * @since 3.2
     */
    public String getUsageMessage() {
        return helpFactory().createHelp(getCommandSpec(), ColorScheme.createDefault(Ansi.AUTO))
                .buildUsageMessage();
    }

    /**
     * Similar to {@link #usage(PrintStream, Help.Ansi)}, but returns the usage help message as a
     * String instead of printing it to the {@code PrintStream}.
     * 
     * @since 3.2
     */
    public String getUsageMessage(Ansi ansi) {
        return helpFactory().createHelp(getCommandSpec(), ColorScheme.createDefault(ansi))
                .buildUsageMessage();
    }

    /**
     * Similar to {@link #usage(PrintStream, Help.ColorScheme)}, but returns the usage help message
     * as a String instead of printing it to the {@code PrintStream}.
     * 
     * @since 3.2
     */
    public String getUsageMessage(ColorScheme colorScheme) {
        return helpFactory().createHelp(getCommandSpec(), colorScheme).buildUsageMessage();
    }

    /**
     * Delegates to {@link #printVersionHelp(PrintStream, Help.Ansi)} with the {@linkplain Ansi#AUTO
     * platform default}.
     * 
     * @param out
     *            the printStream to print to
     * @see #printVersionHelp(PrintStream, Help.Ansi)
     * @since 0.9.8
     */
    public void printVersionHelp(PrintStream out) {
        printVersionHelp(out, Ansi.AUTO);
    }

    /**
     * Prints version information from the {@link Command#version()} annotation to the specified
     * {@code PrintStream}. Each element of the array of version strings is printed on a separate
     * line. Version strings may contain
     * <a href="http://picocli.info/#_usage_help_with_styles_and_colors">markup for colors and
     * style</a>.
     * 
     * @param out
     *            the printStream to print to
     * @param ansi
     *            whether the usage message should include ANSI escape codes or not
     * @see Command#version()
     * @see Option#versionHelp()
     * @see #isVersionHelpRequested()
     * @since 0.9.8
     */
    public void printVersionHelp(PrintStream out, Ansi ansi) {
        for (String versionInfo : getCommandSpec().version()) {
            out.println(new Text(ansi, versionInfo));
        }
    }

    /**
     * Prints version information from the {@link Command#version()} annotation to the specified
     * {@code PrintStream}. Each element of the array of version strings is
     * {@linkplain String#format(String, Object...) formatted} with the specified parameters, and
     * printed on a separate line. Both version strings and parameters may contain
     * <a href="http://picocli.info/#_usage_help_with_styles_and_colors">markup for colors and
     * style</a>.
     * 
     * @param out
     *            the printStream to print to
     * @param ansi
     *            whether the usage message should include ANSI escape codes or not
     * @param params
     *            Arguments referenced by the format specifiers in the version strings
     * @see Command#version()
     * @see Option#versionHelp()
     * @see #isVersionHelpRequested()
     * @since 1.0.0
     */
    public void printVersionHelp(PrintStream out, Ansi ansi, Object... params) {
        for (String versionInfo : getCommandSpec().version()) {
            out.println(new Text(ansi, String.format(versionInfo, params)));
        }
    }

    /**
     * Delegates to {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)} with
     * {@code System.out} for requested usage help messages, {@code System.err} for diagnostic error
     * messages, and {@link Ansi#AUTO}.
     * 
     * @param callable
     *            the command to call when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param args
     *            the command line arguments to parse
     * @param <C>
     *            the annotated object must implement Callable
     * @param <T>
     *            the return type of the most specific command (must implement {@code Callable})
     * @see #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help
     *         was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.0
     */
    public static <C extends Callable<T>, T> T call(C callable, String... args) {
        return call(callable, System.out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)} with
     * {@code System.err} for diagnostic error messages and {@link Ansi#AUTO}.
     * 
     * @param callable
     *            the command to call when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param args
     *            the command line arguments to parse
     * @param <C>
     *            the annotated object must implement Callable
     * @param <T>
     *            the return type of the most specific command (must implement {@code Callable})
     * @see #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help
     *         was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     */
    public static <C extends Callable<T>, T> T call(C callable, PrintStream out, String... args) {
        return call(callable, out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)} with
     * {@code System.err} for diagnostic error messages.
     * 
     * @param callable
     *            the command to call when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param ansi
     *            the ANSI style to use
     * @param args
     *            the command line arguments to parse
     * @param <C>
     *            the annotated object must implement Callable
     * @param <T>
     *            the return type of the most specific command (must implement {@code Callable})
     * @see #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help
     *         was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     */
    public static <C extends Callable<T>, T> T call(C callable, PrintStream out, Ansi ansi,
            String... args) {
        return call(callable, out, System.err, ansi, args);
    }

    /**
     * Convenience method to allow command line application authors to avoid some boilerplate code
     * in their application. The annotated object needs to implement {@link Callable}. Calling this
     * method is equivalent to:
     * 
     * <pre>
     * {
     *     &#64;code
     *     CommandLine cmd = new CommandLine(callable);
     *     List<Object> results = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
     *             new DefaultExceptionHandler().useErr(err).useAnsi(ansi), args);
     *     T result = results == null || results.isEmpty() ? null : (T) results.get(0);
     *     return result;
     * }
     * </pre>
     * <p>
     * If the specified Callable command has subcommands, the {@linkplain RunLast last} subcommand
     * specified on the command line is executed. Commands with subcommands may be interested in
     * calling the {@link #parseWithHandler(IParseResultHandler2, String[]) parseWithHandler} method
     * with the {@link RunAll} handler or a custom handler.
     * </p>
     * <p>
     * Use {@link #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...) call(Class,
     * IFactory, ...)} instead of this method if you want to use a factory that performs Dependency
     * Injection.
     * </p>
     * 
     * @param callable
     *            the command to call when {@linkplain #parse(String...) parsing} succeeds.
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param err
     *            the printStream to print diagnostic messages to
     * @param ansi
     *            whether the usage message should include ANSI escape codes or not
     * @param args
     *            the command line arguments to parse
     * @param <C>
     *            the annotated object must implement Callable
     * @param <T>
     *            the return type of the specified {@code Callable}
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help
     *         was requested and printed. Otherwise returns the result of calling the Callable
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.0
     */
    public static <C extends Callable<T>, T> T call(C callable, PrintStream out, PrintStream err,
            Ansi ansi, String... args) {
        CommandLine cmd = new CommandLine(callable);
        List<Object> results = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
                new DefaultExceptionHandler<List<Object>>().useErr(err).useAnsi(ansi), args);
        @SuppressWarnings("unchecked")
        T result = results == null || results.isEmpty() ? null : (T) results.get(0);
        return result;
    }

    /**
     * Delegates to {@link #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)}
     * with {@code System.out} for requested usage help messages, {@code System.err} for diagnostic
     * error messages, and {@link Ansi#AUTO}.
     * 
     * @param callableClass
     *            class of the command to call when {@linkplain #parseArgs(String...) parsing}
     *            succeeds.
     * @param factory
     *            the factory responsible for instantiating the specified callable class and
     *            potentially inject other components
     * @param args
     *            the command line arguments to parse
     * @param <C>
     *            the annotated class must implement Callable
     * @param <T>
     *            the return type of the most specific command (must implement {@code Callable})
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified class cannot be instantiated by the factory, or does not have a
     *             {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help
     *         was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.2
     */
    public static <C extends Callable<T>, T> T call(Class<C> callableClass, IFactory factory,
            String... args) {
        return call(callableClass, factory, System.out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)}
     * with {@code System.err} for diagnostic error messages, and {@link Ansi#AUTO}.
     * 
     * @param callableClass
     *            class of the command to call when {@linkplain #parseArgs(String...) parsing}
     *            succeeds.
     * @param factory
     *            the factory responsible for instantiating the specified callable class and
     *            potentially injecting other components
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param args
     *            the command line arguments to parse
     * @param <C>
     *            the annotated class must implement Callable
     * @param <T>
     *            the return type of the most specific command (must implement {@code Callable})
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified class cannot be instantiated by the factory, or does not have a
     *             {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help
     *         was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.2
     */
    public static <C extends Callable<T>, T> T call(Class<C> callableClass, IFactory factory,
            PrintStream out, String... args) {
        return call(callableClass, factory, out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)}
     * with {@code System.err} for diagnostic error messages.
     * 
     * @param callableClass
     *            class of the command to call when {@linkplain #parseArgs(String...) parsing}
     *            succeeds.
     * @param factory
     *            the factory responsible for instantiating the specified callable class and
     *            potentially injecting other components
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param ansi
     *            the ANSI style to use
     * @param args
     *            the command line arguments to parse
     * @param <C>
     *            the annotated class must implement Callable
     * @param <T>
     *            the return type of the most specific command (must implement {@code Callable})
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified class cannot be instantiated by the factory, or does not have a
     *             {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help
     *         was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.2
     */
    public static <C extends Callable<T>, T> T call(Class<C> callableClass, IFactory factory,
            PrintStream out, Ansi ansi, String... args) {
        return call(callableClass, factory, out, System.err, ansi, args);
    }

    /**
     * Convenience method to allow command line application authors to avoid some boilerplate code
     * in their application. The specified {@linkplain IFactory factory} will create an instance of
     * the specified {@code callableClass}; use this method instead of
     * {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...) call(Callable, ...)}
     * if you want to use a factory that performs Dependency Injection. The annotated class needs to
     * implement {@link Callable}. Calling this method is equivalent to:
     * 
     * <pre>
     * {
     *     &#64;code
     *     CommandLine cmd = new CommandLine(callableClass, factory);
     *     List<Object> results = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
     *             new DefaultExceptionHandler().useErr(err).useAnsi(ansi), args);
     *     T result = results == null || results.isEmpty() ? null : (T) results.get(0);
     *     return result;
     * }
     * </pre>
     * <p>
     * If the specified Callable command has subcommands, the {@linkplain RunLast last} subcommand
     * specified on the command line is executed. Commands with subcommands may be interested in
     * calling the {@link #parseWithHandler(IParseResultHandler2, String[]) parseWithHandler} method
     * with the {@link RunAll} handler or a custom handler.
     * </p>
     * 
     * @param callableClass
     *            class of the command to call when {@linkplain #parseArgs(String...) parsing}
     *            succeeds.
     * @param factory
     *            the factory responsible for instantiating the specified callable class and
     *            potentially injecting other components
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param err
     *            the printStream to print diagnostic messages to
     * @param ansi
     *            the ANSI style to use
     * @param args
     *            the command line arguments to parse
     * @param <C>
     *            the annotated class must implement Callable
     * @param <T>
     *            the return type of the most specific command (must implement {@code Callable})
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified class cannot be instantiated by the factory, or does not have a
     *             {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help
     *         was requested and printed. Otherwise returns the result of calling the Callable
     * @see #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.2
     */
    public static <C extends Callable<T>, T> T call(Class<C> callableClass, IFactory factory,
            PrintStream out, PrintStream err, Ansi ansi, String... args) {
        CommandLine cmd = new CommandLine(callableClass, factory);
        List<Object> results = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
                new DefaultExceptionHandler<List<Object>>().useErr(err).useAnsi(ansi), args);
        @SuppressWarnings("unchecked")
        T result = results == null || results.isEmpty() ? null : (T) results.get(0);
        return result;
    }

    /**
     * Delegates to {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)} with
     * {@code System.out} for requested usage help messages, {@code System.err} for diagnostic error
     * messages, and {@link Ansi#AUTO}.
     * 
     * @param runnable
     *            the command to run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param args
     *            the command line arguments to parse
     * @param <R>
     *            the annotated object must implement Runnable
     * @see #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.0
     */
    public static <R extends Runnable> void run(R runnable, String... args) {
        run(runnable, System.out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)} with
     * {@code System.err} for diagnostic error messages and {@link Ansi#AUTO}.
     * 
     * @param runnable
     *            the command to run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param args
     *            the command line arguments to parse
     * @param <R>
     *            the annotated object must implement Runnable
     * @see #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Runnable throws an exception
     * @see #parseWithHandler(IParseResultHandler2, String[])
     * @see RunLast
     */
    public static <R extends Runnable> void run(R runnable, PrintStream out, String... args) {
        run(runnable, out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)} with
     * {@code System.err} for diagnostic error messages.
     * 
     * @param runnable
     *            the command to run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param ansi
     *            whether the usage message should include ANSI escape codes or not
     * @param args
     *            the command line arguments to parse
     * @param <R>
     *            the annotated object must implement Runnable
     * @see #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     */
    public static <R extends Runnable> void run(R runnable, PrintStream out, Ansi ansi,
            String... args) {
        run(runnable, out, System.err, ansi, args);
    }

    /**
     * Convenience method to allow command line application authors to avoid some boilerplate code
     * in their application. The annotated object needs to implement {@link Runnable}. Calling this
     * method is equivalent to:
     * 
     * <pre>
     * {
     *     &#64;code
     *     CommandLine cmd = new CommandLine(runnable);
     *     cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
     *             new DefaultExceptionHandler().useErr(err).useAnsi(ansi), args);
     * }
     * </pre>
     * <p>
     * If the specified Runnable command has subcommands, the {@linkplain RunLast last} subcommand
     * specified on the command line is executed. Commands with subcommands may be interested in
     * calling the {@link #parseWithHandler(IParseResultHandler2, String[]) parseWithHandler} method
     * with the {@link RunAll} handler or a custom handler.
     * </p>
     * <p>
     * From picocli v2.0, this method prints usage help or version help if
     * {@linkplain #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi) requested}, and
     * any exceptions thrown by the {@code Runnable} are caught and rethrown wrapped in an
     * {@code ExecutionException}.
     * </p>
     * <p>
     * Use {@link #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...) run(Class,
     * IFactory, ...)} instead of this method if you want to use a factory that performs Dependency
     * Injection.
     * </p>
     * 
     * @param runnable
     *            the command to run when {@linkplain #parse(String...) parsing} succeeds.
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param err
     *            the printStream to print diagnostic messages to
     * @param ansi
     *            whether the usage message should include ANSI escape codes or not
     * @param args
     *            the command line arguments to parse
     * @param <R>
     *            the annotated object must implement Runnable
     * @throws InitializationException
     *             if the specified command object does not have a {@link Command}, {@link Option}
     *             or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @since 3.0
     */
    public static <R extends Runnable> void run(R runnable, PrintStream out, PrintStream err,
            Ansi ansi, String... args) {
        CommandLine cmd = new CommandLine(runnable);
        cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
                new DefaultExceptionHandler<List<Object>>().useErr(err).useAnsi(ansi), args);
    }

    /**
     * Delegates to {@link #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)}
     * with {@code System.out} for requested usage help messages, {@code System.err} for diagnostic
     * error messages, and {@link Ansi#AUTO}.
     * 
     * @param runnableClass
     *            class of the command to run when {@linkplain #parseArgs(String...) parsing}
     *            succeeds.
     * @param factory
     *            the factory responsible for instantiating the specified Runnable class and
     *            potentially injecting other components
     * @param args
     *            the command line arguments to parse
     * @param <R>
     *            the annotated class must implement Runnable
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified class cannot be instantiated by the factory, or does not have a
     *             {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.2
     */
    public static <R extends Runnable> void run(Class<R> runnableClass, IFactory factory,
            String... args) {
        run(runnableClass, factory, System.out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)}
     * with {@code System.err} for diagnostic error messages, and {@link Ansi#AUTO}.
     * 
     * @param runnableClass
     *            class of the command to run when {@linkplain #parseArgs(String...) parsing}
     *            succeeds.
     * @param factory
     *            the factory responsible for instantiating the specified Runnable class and
     *            potentially injecting other components
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param args
     *            the command line arguments to parse
     * @param <R>
     *            the annotated class must implement Runnable
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified class cannot be instantiated by the factory, or does not have a
     *             {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.2
     */
    public static <R extends Runnable> void run(Class<R> runnableClass, IFactory factory,
            PrintStream out, String... args) {
        run(runnableClass, factory, out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)}
     * with {@code System.err} for diagnostic error messages.
     * 
     * @param runnableClass
     *            class of the command to run when {@linkplain #parseArgs(String...) parsing}
     *            succeeds.
     * @param factory
     *            the factory responsible for instantiating the specified Runnable class and
     *            potentially injecting other components
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param ansi
     *            whether the usage message should include ANSI escape codes or not
     * @param args
     *            the command line arguments to parse
     * @param <R>
     *            the annotated class must implement Runnable
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified class cannot be instantiated by the factory, or does not have a
     *             {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.2
     */
    public static <R extends Runnable> void run(Class<R> runnableClass, IFactory factory,
            PrintStream out, Ansi ansi, String... args) {
        run(runnableClass, factory, out, System.err, ansi, args);
    }

    /**
     * Convenience method to allow command line application authors to avoid some boilerplate code
     * in their application. The specified {@linkplain IFactory factory} will create an instance of
     * the specified {@code runnableClass}; use this method instead of
     * {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...) run(Runnable, ...)} if
     * you want to use a factory that performs Dependency Injection. The annotated class needs to
     * implement {@link Runnable}. Calling this method is equivalent to:
     * 
     * <pre>
     * {
     *     &#64;code
     *     CommandLine cmd = new CommandLine(runnableClass, factory);
     *     cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
     *             new DefaultExceptionHandler().useErr(err).useAnsi(ansi), args);
     * }
     * </pre>
     * <p>
     * If the specified Runnable command has subcommands, the {@linkplain RunLast last} subcommand
     * specified on the command line is executed. Commands with subcommands may be interested in
     * calling the {@link #parseWithHandler(IParseResultHandler2, String[]) parseWithHandler} method
     * with the {@link RunAll} handler or a custom handler.
     * </p>
     * <p>
     * This method prints usage help or version help if
     * {@linkplain #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi) requested}, and
     * any exceptions thrown by the {@code Runnable} are caught and rethrown wrapped in an
     * {@code ExecutionException}.
     * </p>
     * 
     * @param runnableClass
     *            class of the command to run when {@linkplain #parseArgs(String...) parsing}
     *            succeeds.
     * @param factory
     *            the factory responsible for instantiating the specified Runnable class and
     *            potentially injecting other components
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param err
     *            the printStream to print diagnostic messages to
     * @param ansi
     *            whether the usage message should include ANSI escape codes or not
     * @param args
     *            the command line arguments to parse
     * @param <R>
     *            the annotated class must implement Runnable
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified class cannot be instantiated by the factory, or does not have a
     *             {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException
     *             if the Runnable throws an exception
     * @see #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.2
     */
    public static <R extends Runnable> void run(Class<R> runnableClass, IFactory factory,
            PrintStream out, PrintStream err, Ansi ansi, String... args) {
        CommandLine cmd = new CommandLine(runnableClass, factory);
        cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
                new DefaultExceptionHandler<List<Object>>().useErr(err).useAnsi(ansi), args);
    }

    /**
     * Delegates to {@link #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)}
     * with {@code System.out} for requested usage help messages, {@code System.err} for diagnostic
     * error messages, and {@link Ansi#AUTO}.
     * 
     * @param methodName
     *            the {@code @Command}-annotated method to build a {@link CommandSpec} model from,
     *            and run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param cls
     *            the class where the {@code @Command}-annotated method is declared, or a subclass
     * @param args
     *            the command line arguments to parse
     * @see #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified method does not have a {@link Command} annotation, or if the
     *             specified class contains multiple {@code @Command}-annotated methods with the
     *             specified name
     * @throws ExecutionException
     *             if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.6
     */
    public static Object invoke(String methodName, Class<?> cls, String... args) {
        return invoke(methodName, cls, System.out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)}
     * with the specified stream for requested usage help messages, {@code System.err} for
     * diagnostic error messages, and {@link Ansi#AUTO}.
     * 
     * @param methodName
     *            the {@code @Command}-annotated method to build a {@link CommandSpec} model from,
     *            and run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param cls
     *            the class where the {@code @Command}-annotated method is declared, or a subclass
     * @param out
     *            the printstream to print requested help message to
     * @param args
     *            the command line arguments to parse
     * @see #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified method does not have a {@link Command} annotation, or if the
     *             specified class contains multiple {@code @Command}-annotated methods with the
     *             specified name
     * @throws ExecutionException
     *             if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.6
     */
    public static Object invoke(String methodName, Class<?> cls, PrintStream out, String... args) {
        return invoke(methodName, cls, out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)}
     * with the specified stream for requested usage help messages, {@code System.err} for
     * diagnostic error messages, and the specified Ansi mode.
     * 
     * @param methodName
     *            the {@code @Command}-annotated method to build a {@link CommandSpec} model from,
     *            and run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param cls
     *            the class where the {@code @Command}-annotated method is declared, or a subclass
     * @param out
     *            the printstream to print requested help message to
     * @param ansi
     *            whether the usage message should include ANSI escape codes or not
     * @param args
     *            the command line arguments to parse
     * @see #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException
     *             if the specified method does not have a {@link Command} annotation, or if the
     *             specified class contains multiple {@code @Command}-annotated methods with the
     *             specified name
     * @throws ExecutionException
     *             if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.6
     */
    public static Object invoke(String methodName, Class<?> cls, PrintStream out, Ansi ansi,
            String... args) {
        return invoke(methodName, cls, out, System.err, ansi, args);
    }

    /**
     * Convenience method to allow command line application authors to avoid some boilerplate code
     * in their application. Constructs a {@link CommandSpec} model from the {@code @Option} and
     * {@code @Parameters}-annotated method parameters of the {@code @Command}-annotated method,
     * parses the specified command line arguments and invokes the specified method. Calling this
     * method is equivalent to:
     * 
     * <pre>
     * {
     *     &#64;code
     *     Method commandMethod = getCommandMethods(cls, methodName).get(0);
     *     CommandLine cmd = new CommandLine(commandMethod);
     *     List<Object> list = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
     *             new DefaultExceptionHandler().useErr(err).useAnsi(ansi), args);
     *     return list == null ? null : list.get(0);
     * }
     * </pre>
     * 
     * @param methodName
     *            the {@code @Command}-annotated method to build a {@link CommandSpec} model from,
     *            and run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param cls
     *            the class where the {@code @Command}-annotated method is declared, or a subclass
     * @param out
     *            the printStream to print the usage help message to when the user requested help
     * @param err
     *            the printStream to print diagnostic messages to
     * @param ansi
     *            whether the usage message should include ANSI escape codes or not
     * @param args
     *            the command line arguments to parse
     * @throws InitializationException
     *             if the specified method does not have a {@link Command} annotation, or if the
     *             specified class contains multiple {@code @Command}-annotated methods with the
     *             specified name
     * @throws ExecutionException
     *             if the method throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.6
     */
    public static Object invoke(String methodName, Class<?> cls, PrintStream out, PrintStream err,
            Ansi ansi, String... args) {
        List<Method> candidates = getCommandMethods(cls, methodName);
        if (candidates.size() != 1) {
            throw new InitializationException("Expected exactly one @Command-annotated method for "
                    + cls.getName() + "::" + methodName + "(...), but got: " + candidates);
        }
        Method method = candidates.get(0);
        CommandLine cmd = new CommandLine(method);
        List<Object> list = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
                new DefaultExceptionHandler<List<Object>>().useErr(err).useAnsi(ansi), args);
        return list == null ? null : list.get(0);
    }

    /**
     * Helper to get methods of a class annotated with {@link Command @Command} via reflection,
     * optionally filtered by method name (not {@link Command#name() @Command.name}). Methods have
     * to be either public (inherited) members or be declared by {@code cls}, that is "inherited"
     * static or protected methods will not be picked up.
     *
     * @param cls
     *            the class to search for methods annotated with {@code @Command}
     * @param methodName
     *            if not {@code null}, return only methods whose method name (not
     *            {@link Command#name() @Command.name}) equals this string. Ignored if {@code null}.
     * @return the matching command methods, or an empty list
     * @see #invoke(String, Class, String...)
     * @since 3.6.0
     */
    public static List<Method> getCommandMethods(Class<?> cls, String methodName) {
        Set<Method> candidates = new TreeSet<Method>(new Comparator<Method>() {
            public int compare(Method o1, Method o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        // traverse public member methods (excludes static/non-public, includes inherited)
        candidates.addAll(Arrays.asList(Assert.notNull(cls, "class").getMethods()));
        // traverse directly declared methods (includes static/non-public, excludes inherited)
        candidates.addAll(Arrays.asList(Assert.notNull(cls, "class").getDeclaredMethods()));

        List<Method> result = new ArrayList<Method>();
        for (Method method : candidates) {
            if (method.isAnnotationPresent(Command.class)) {
                if (methodName == null || methodName.equals(method.getName())) {
                    result.add(method);
                }
            }
        }
        return result;
    }

    /**
     * Registers the specified type converter for the specified class. When initializing fields
     * annotated with {@link Option}, the field's type is used as a lookup key to find the
     * associated type converter, and this type converter converts the original command line
     * argument string value to the correct type.
     * <p>
     * Java 8 lambdas make it easy to register custom type converters:
     * </p>
     * 
     * <pre>
     * commandLine.registerConverter(java.nio.file.Path.class, s -&gt; java.nio.file.Paths.get(s));
     * commandLine.registerConverter(java.time.Duration.class, s -&gt; java.time.Duration.parse(s));
     * </pre>
     * <p>
     * Built-in type converters are pre-registered for the following java 1.5 types:
     * </p>
     * <ul>
     * <li>all primitive types</li>
     * <li>all primitive wrapper types: Boolean, Byte, Character, Double, Float, Integer, Long,
     * Short</li>
     * <li>any enum</li>
     * <li>java.io.File</li>
     * <li>java.math.BigDecimal</li>
     * <li>java.math.BigInteger</li>
     * <li>java.net.InetAddress</li>
     * <li>java.net.URI</li>
     * <li>java.net.URL</li>
     * <li>java.nio.charset.Charset</li>
     * <li>java.sql.Time</li>
     * <li>java.util.Date</li>
     * <li>java.util.UUID</li>
     * <li>java.util.regex.Pattern</li>
     * <li>StringBuilder</li>
     * <li>CharSequence</li>
     * <li>String</li>
     * </ul>
     * <p>
     * The specified converter will be registered with this {@code CommandLine} and the full
     * hierarchy of its subcommands and nested sub-subcommands <em>at the moment the converter is
     * registered</em>. Subcommands added later will not have this converter added automatically. To
     * ensure a custom type converter is available to all subcommands, register the type converter
     * last, after adding subcommands.
     * </p>
     *
     * @param cls
     *            the target class to convert parameter string values to
     * @param converter
     *            the class capable of converting string values to the specified target type
     * @param <K>
     *            the target type
     * @return this CommandLine object, to allow method chaining
     * @see #addSubcommand(String, Object)
     */
    public <K> CommandLine registerConverter(Class<K> cls, ITypeConverter<K> converter) {
        interpreter.converterRegistry.put(Assert.notNull(cls, "class"),
                Assert.notNull(converter, "converter"));
        for (CommandLine command : getCommandSpec().commands.values()) {
            command.registerConverter(cls, converter);
        }
        return this;
    }

    /**
     * Returns the String that separates option names from option values when parsing command line
     * options.
     * 
     * @return the String the parser uses to separate option names from option values
     * @see ParserSpec#separator()
     */
    public String getSeparator() {
        return getCommandSpec().parser().separator();
    }

    /**
     * Sets the String the parser uses to separate option names from option values to the specified
     * value. The separator may also be set declaratively with the
     * {@link Command#separator()} annotation attribute.
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param separator
     *            the String that separates option names from option values
     * @see ParserSpec#separator(String)
     * @return this {@code CommandLine} object, to allow method chaining
     */
    public CommandLine setSeparator(String separator) {
        getCommandSpec().parser().separator(Assert.notNull(separator, "separator"));
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setSeparator(separator);
        }
        return this;
    }

    /**
     * Returns the ResourceBundle of this command or {@code null} if no resource bundle is set.
     * 
     * @see Command#resourceBundle()
     * @see CommandSpec#resourceBundle()
     * @since 3.6
     */
    public ResourceBundle getResourceBundle() {
        return getCommandSpec().resourceBundle();
    }

    /**
     * Sets the ResourceBundle containing usage help message strings.
     * <p>
     * The specified bundle will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will not be impacted. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param bundle
     *            the ResourceBundle containing usage help message strings
     * @return this {@code CommandLine} object, to allow method chaining
     * @see Command#resourceBundle()
     * @see CommandSpec#resourceBundle(ResourceBundle)
     * @since 3.6
     */
    public CommandLine setResourceBundle(ResourceBundle bundle) {
        getCommandSpec().resourceBundle(bundle);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.getCommandSpec().resourceBundle(bundle);
        }
        return this;
    }

    /**
     * Returns the maximum width of the usage help message. The default is 80.
     * 
     * @see UsageMessageSpec#width()
     */
    public int getUsageHelpWidth() {
        return getCommandSpec().usageMessage().width();
    }

    /**
     * Sets the maximum width of the usage help message. Longer lines are wrapped.
     * <p>
     * The specified setting will be registered with this {@code CommandLine} and the full hierarchy
     * of its subcommands and nested sub-subcommands <em>at the moment this method is called</em>.
     * Subcommands added later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.
     * </p>
     * 
     * @param width
     *            the maximum width of the usage help message
     * @see UsageMessageSpec#width(int)
     * @return this {@code CommandLine} object, to allow method chaining
     */
    public CommandLine setUsageHelpWidth(int width) {
        getCommandSpec().usageMessage().width(width);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setUsageHelpWidth(width);
        }
        return this;
    }

    /**
     * Returns the command name (also called program name) displayed in the usage help synopsis.
     * 
     * @return the command name (also called program name) displayed in the usage
     * @see CommandSpec#name()
     * @since 2.0
     */
    public String getCommandName() {
        return getCommandSpec().name();
    }

    /**
     * Sets the command name (also called program name) displayed in the usage help synopsis to the
     * specified value. Note that this method only modifies the usage help message, it does not
     * impact parsing behaviour. The command name may also be set declaratively with the
     * {@link Command#name()} annotation attribute.
     * 
     * @param commandName
     *            command name (also called program name) displayed in the usage help synopsis
     * @return this {@code CommandLine} object, to allow method chaining
     * @see CommandSpec#name(String)
     * @since 2.0
     */
    public CommandLine setCommandName(String commandName) {
        getCommandSpec().name(Assert.notNull(commandName, "commandName"));
        return this;
    }

    /**
     * Returns whether arguments starting with {@code '@'} should be treated as the path to an
     * argument file and its contents should be expanded into separate arguments for each line in
     * the specified file. This property is {@code true} by default.
     * 
     * @return whether "argument files" or {@code @files} should be expanded into their content
     * @since 2.1
     */
    public boolean isExpandAtFiles() {
        return getCommandSpec().parser().expandAtFiles();
    }

    /**
     * Sets whether arguments starting with {@code '@'} should be treated as the path to an argument
     * file and its contents should be expanded into separate arguments for each line in the
     * specified file. ({@code true} by default.)
     * 
     * @param expandAtFiles
     *            whether "argument files" or {@code @files} should be expanded into their content
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 2.1
     */
    public CommandLine setExpandAtFiles(boolean expandAtFiles) {
        getCommandSpec().parser().expandAtFiles(expandAtFiles);
        return this;
    }

    /**
     * Returns the character that starts a single-line comment or {@code null} if all content of
     * argument files should be interpreted as arguments (without comments). If specified, all
     * characters from the comment character to the end of the line are ignored.
     * 
     * @return the character that starts a single-line comment or {@code null}. The default is
     *         {@code '#'}.
     * @since 3.5
     */
    public Character getAtFileCommentChar() {
        return getCommandSpec().parser().atFileCommentChar();
    }

    /**
     * Sets the character that starts a single-line comment or {@code null} if all content of
     * argument files should be interpreted as arguments (without comments). If specified, all
     * characters from the comment character to the end of the line are ignored.
     * 
     * @param atFileCommentChar
     *            the character that starts a single-line comment or {@code null}. The default is
     *            {@code '#'}.
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 3.5
     */
    public CommandLine setAtFileCommentChar(Character atFileCommentChar) {
        getCommandSpec().parser().atFileCommentChar(atFileCommentChar);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setAtFileCommentChar(atFileCommentChar);
        }
        return this;
    }

    static boolean isBoolean(Class<?> type) {
        return type == Boolean.class || type == Boolean.TYPE;
    }

    private static CommandLine toCommandLine(Object obj, IFactory factory) {
        return obj instanceof CommandLine ? (CommandLine) obj : new CommandLine(obj, factory);
    }

    private static boolean isMultiValue(Class<?> cls) {
        return cls.isArray() || Collection.class.isAssignableFrom(cls)
                || Map.class.isAssignableFrom(cls);
    }

    public static class NoCompletionCandidates implements Iterable<String> {
        public Iterator<String> iterator() {
            return Collections.<String>emptyList().iterator();
        }
    }

    /**
     * <p>
     * When parsing command line arguments and initializing fields annotated with
     * {@link Option @Option} or {@link Parameters @Parameters}, String values can be converted to
     * any type for which a {@code ITypeConverter} is registered.
     * </p>
     * <p>
     * This interface defines the contract for classes that know how to convert a String into some
     * domain object. Custom converters can be registered with the
     * {@link #registerConverter(Class, ITypeConverter)} method.
     * </p>
     * <p>
     * Java 8 lambdas make it easy to register custom type converters:
     * </p>
     * 
     * <pre>
     * commandLine.registerConverter(java.nio.file.Path.class, s -&gt; java.nio.file.Paths.get(s));
     * commandLine.registerConverter(java.time.Duration.class, s -&gt; java.time.Duration.parse(s));
     * </pre>
     * <p>
     * Built-in type converters are pre-registered for the following java 1.5 types:
     * </p>
     * <ul>
     * <li>all primitive types</li>
     * <li>all primitive wrapper types: Boolean, Byte, Character, Double, Float, Integer, Long,
     * Short</li>
     * <li>any enum</li>
     * <li>java.io.File</li>
     * <li>java.math.BigDecimal</li>
     * <li>java.math.BigInteger</li>
     * <li>java.net.InetAddress</li>
     * <li>java.net.URI</li>
     * <li>java.net.URL</li>
     * <li>java.nio.charset.Charset</li>
     * <li>java.sql.Time</li>
     * <li>java.util.Date</li>
     * <li>java.util.UUID</li>
     * <li>java.util.regex.Pattern</li>
     * <li>StringBuilder</li>
     * <li>CharSequence</li>
     * <li>String</li>
     * </ul>
     * 
     * @param <K>
     *            the type of the object that is the result of the conversion
     */
    public interface ITypeConverter<K> {
        /**
         * Converts the specified command line argument value to some domain object.
         * 
         * @param value
         *            the command line argument String value
         * @return the resulting domain object
         * @throws Exception
         *             an exception detailing what went wrong during the conversion
         */
        K convert(String value) throws Exception;
    }

    /**
     * Provides version information for a command. Commands may configure a provider with the
     * {@link Command#versionProvider()} annotation attribute.
     * 
     * @since 2.2
     */
    public interface IVersionProvider {
        /**
         * Returns version information for a command.
         * 
         * @return version information (each string in the array is displayed on a separate line)
         * @throws Exception
         *             an exception detailing what went wrong when obtaining version information
         */
        String[] getVersion() throws Exception;
    }

    public static class NoVersionProvider implements IVersionProvider {
        public String[] getVersion() throws Exception {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Provides default value for a command. Commands may configure a provider with the
     * {@link Command#defaultValueProvider()} annotation attribute.
     * 
     * @since 3.6
     */
    public interface IDefaultValueProvider {

        /**
         * Returns the default value for an option or positional parameter or {@code null}. The
         * returned value is converted to the type of the option/positional parameter via the same
         * type converter used when populating this option/positional parameter from a command line
         * argument.
         * 
         * @param argSpec
         *            the option or positional parameter, never {@code null}
         * @return the default value for the option or positional parameter, or {@code null} if this
         *         provider has no default value for the specified option or positional parameter
         * @throws Exception
         *             when there was a problem obtaining the default value
         */
        String defaultValue(ArgSpec argSpec) throws Exception;
    }

    public static class NoDefaultProvider implements IDefaultValueProvider {
        public String defaultValue(ArgSpec argSpec) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Factory for instantiating classes that are registered declaratively with annotation
     * attributes, like {@link Command#subcommands()}, {@link Option#converter()},
     * {@link Parameters#converter()} and {@link Command#versionProvider()}.
     * <p>
     * The default factory implementation simply creates a new instance of the specified class when
     * {@link #create(Class)} is invoked.
     * </p>
     * <p>
     * You may provide a custom implementation of this interface. For example, a custom factory
     * implementation could delegate to a dependency injection container that provides the requested
     * instance.
     * </p>
     * 
     * @see picocli.CommandLine#CommandLine(Object, IFactory)
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @since 2.2
     */
    public interface IFactory {
        /**
         * Returns an instance of the specified class.
         * 
         * @param cls
         *            the class of the object to return
         * @param <K>
         *            the type of the object to return
         * @return the instance
         * @throws Exception
         *             an exception detailing what went wrong when creating or obtaining the
         *             instance
         */
        <K> K create(Class<K> cls) throws Exception;
    }

    /**
     * Returns a default {@link IFactory} implementation. Package-protected for testing purposes.
     */
    public static IFactory defaultFactory() {
        return new DefaultFactory();
    }

    public static class DefaultFactory implements IFactory {
        public <T> T create(Class<T> cls) throws Exception {
            try {
                return cls.newInstance();
            } catch (Exception ex) {
                Constructor<T> constructor = cls.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            }
        }

        private static ITypeConverter<?>[] createConverter(IFactory factory,
                Class<? extends ITypeConverter<?>>[] classes) {
            ITypeConverter<?>[] result = new ITypeConverter<?>[classes.length];
            for (int i = 0; i < classes.length; i++) {
                result[i] = create(factory, classes[i]);
            }
            return result;
        }

        static IVersionProvider createVersionProvider(IFactory factory,
                Class<? extends IVersionProvider> cls) {
            return create(factory, cls);
        }

        static IDefaultValueProvider createDefaultValueProvider(IFactory factory,
                Class<? extends IDefaultValueProvider> cls) {
            return create(factory, cls);
        }

        static Iterable<String> createCompletionCandidates(IFactory factory,
                Class<? extends Iterable<String>> cls) {
            return create(factory, cls);
        }

        static <T> T create(IFactory factory, Class<T> cls) {
            try {
                return factory.create(cls);
            } catch (Exception ex) {
                throw new InitializationException("Could not instantiate " + cls + ": " + ex, ex);
            }
        }
    }

    /**
     * Describes the number of parameters required and accepted by an option or a positional
     * parameter.
     * 
     * @since 0.9.7
     */
    public static class Range implements Comparable<Range> {
        /** Required number of parameters for an option or positional parameter. */
        public final int min;
        /** Maximum accepted number of parameters for an option or positional parameter. */
        public final int max;
        public final boolean isVariable;
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

        private static Range optionArity(TypedMember member) {
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

        private static Range parameterArity(TypedMember member) {
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

        private static Range parameterIndex(TypedMember member) {
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
                boolean zeroArgs = isBoolean(type)
                        || (isMultiValue(type) && isBoolean(typeAttribute[0]));
                return zeroArgs ? Range.valueOf("0").unspecified(true)
                        : Range.valueOf("1").unspecified(true);
            }
            if (isMultiValue(type)) {
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
            return isBoolean(type) ? Range.valueOf("0").unspecified(true)
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

        private static Range parameterCapacity(Range arity, Range index) {
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

    private static void validatePositionalParameters(
            List<PositionalParamSpec> positionalParametersFields) {
        int min = 0;
        for (PositionalParamSpec positional : positionalParametersFields) {
            Range index = positional.index();
            if (index.min > min) {
                throw new ParameterIndexGapException(
                        "Command definition should have a positional parameter with index=" + min
                                + ". Nearest positional parameter '" + positional.paramLabel()
                                + "' has index=" + index.min);
            }
            min = Math.max(min, index.max);
            min = min == Integer.MAX_VALUE ? min : min + 1;
        }
    }

    /**
     * This class provides a namespace for classes and interfaces that model concepts and attributes
     * of command line interfaces in picocli.
     * 
     * @since 3.0
     */
    public static final class Model {
        private Model() {
        }

        /**
         * Customizable getter for obtaining the current value of an option or positional parameter.
         * When an option or positional parameter is matched on the command line, its getter or
         * setter is invoked to capture the value. For example, an option can be bound to a field or
         * a method, and when the option is matched on the command line, the field's value is set or
         * the method is invoked with the option parameter value.
         * 
         * @since 3.0
         */
        public static interface IGetter {
            /**
             * Returns the current value of the binding. For multi-value options and positional
             * parameters, this method returns an array, collection or map to add values to.
             * 
             * @throws PicocliException
             *             if a problem occurred while obtaining the current value
             * @throws Exception
             *             internally, picocli call sites will catch any exceptions thrown from here
             *             and rethrow them wrapped in a PicocliException
             */
            <T> T get() throws Exception;
        }

        /**
         * Customizable setter for modifying the value of an option or positional parameter. When an
         * option or positional parameter is matched on the command line, its setter is invoked to
         * capture the value. For example, an option can be bound to a field or a method, and when
         * the option is matched on the command line, the field's value is set or the method is
         * invoked with the option parameter value.
         * 
         * @since 3.0
         */
        public static interface ISetter {
            /**
             * Sets the new value of the option or positional parameter.
             *
             * @param value
             *            the new value of the option or positional parameter
             * @param <T>
             *            type of the value
             * @return the previous value of the binding (if supported by this binding)
             * @throws PicocliException
             *             if a problem occurred while setting the new value
             * @throws Exception
             *             internally, picocli call sites will catch any exceptions thrown from here
             *             and rethrow them wrapped in a PicocliException
             */
            <T> T set(T value) throws Exception;
        }

        /**
         * The {@code CommandSpec} class models a command specification, including the options,
         * positional parameters and subcommands supported by the command, as well as attributes for
         * the version help message and the usage help message of the command.
         * <p>
         * Picocli views a command line application as a hierarchy of commands: there is a top-level
         * command (usually the Java class with the {@code main} method) with optionally a set of
         * command line options, positional parameters and subcommands. Subcommands themselves can
         * have options, positional parameters and nested sub-subcommands to any level of depth.
         * </p>
         * <p>
         * The object model has a corresponding hierarchy of {@code CommandSpec} objects, each with
         * a set of {@link OptionSpec}, {@link PositionalParamSpec} and {@linkplain CommandLine
         * subcommands} associated with it. This object model is used by the picocli command line
         * interpreter and help message generator.
         * </p>
         * <p>
         * Picocli can construct a {@code CommandSpec} automatically from classes with
         * {@link Command @Command}, {@link Option @Option} and {@link Parameters @Parameters}
         * annotations. Alternatively a {@code CommandSpec} can be constructed programmatically.
         * </p>
         * 
         * @since 3.0
         */
        public static class CommandSpec {
            /** Constant String holding the default program name: {@code "<main class>" }. */
            public static final String DEFAULT_COMMAND_NAME = "<main class>";

            /**
             * Constant Boolean holding the default setting for whether this is a help command:
             * <code>{@value}</code>.
             */
            static final Boolean DEFAULT_IS_HELP_COMMAND = Boolean.FALSE;

            private final Map<String, CommandLine> commands = new LinkedHashMap<String, CommandLine>();
            private final Map<String, OptionSpec> optionsByNameMap = new LinkedHashMap<String, OptionSpec>();
            private final Map<Character, OptionSpec> posixOptionsByKeyMap = new LinkedHashMap<Character, OptionSpec>();
            private final Map<String, CommandSpec> mixins = new LinkedHashMap<String, CommandSpec>();
            private final List<ArgSpec> requiredArgs = new ArrayList<ArgSpec>();
            private final List<ArgSpec> args = new ArrayList<ArgSpec>();
            private final List<OptionSpec> options = new ArrayList<OptionSpec>();
            private final List<PositionalParamSpec> positionalParameters = new ArrayList<PositionalParamSpec>();
            private final List<UnmatchedArgsBinding> unmatchedArgs = new ArrayList<UnmatchedArgsBinding>();
            final ParserSpec parser = new ParserSpec();
            private final UsageMessageSpec usageMessage = new UsageMessageSpec();

            private final Object userObject;
            private CommandLine commandLine;
            private CommandSpec parent;

            private String name;
            private Set<String> aliases = new LinkedHashSet<String>();
            private Boolean isHelpCommand;
            private IVersionProvider versionProvider;
            private IDefaultValueProvider defaultValueProvider;
            private String[] version;
            private String toString;

            private CommandSpec(Object userObject) {
                this.userObject = userObject;
            }

            /** Creates and returns a new {@code CommandSpec} without any associated user object. */
            public static CommandSpec create() {
                return wrapWithoutInspection(null);
            }

            /**
             * Creates and returns a new {@code CommandSpec} with the specified associated user
             * object. The specified user object is <em>not</em> inspected for annotations.
             * 
             * @param userObject
             *            the associated user object. May be any object, may be {@code null}.
             */
            public static CommandSpec wrapWithoutInspection(Object userObject) {
                return new CommandSpec(userObject);
            }

            /**
             * Creates and returns a new {@code CommandSpec} initialized from the specified
             * associated user object. The specified user object must have at least one
             * {@link Command}, {@link Option} or {@link Parameters} annotation.
             * 
             * @param userObject
             *            the user object annotated with {@link Command}, {@link Option} and/or
             *            {@link Parameters} annotations.
             * @throws InitializationException
             *             if the specified object has no picocli annotations or has invalid
             *             annotations
             */
            public static CommandSpec forAnnotatedObject(Object userObject) {
                return forAnnotatedObject(userObject, new DefaultFactory());
            }

            /**
             * Creates and returns a new {@code CommandSpec} initialized from the specified
             * associated user object. The specified user object must have at least one
             * {@link Command}, {@link Option} or {@link Parameters} annotation.
             * 
             * @param userObject
             *            the user object annotated with {@link Command}, {@link Option} and/or
             *            {@link Parameters} annotations.
             * @param factory
             *            the factory used to create instances of {@linkplain Command#subcommands()
             *            subcommands}, {@linkplain Option#converter() converters}, etc., that are
             *            registered declaratively with annotation attributes
             * @throws InitializationException
             *             if the specified object has no picocli annotations or has invalid
             *             annotations
             */
            public static CommandSpec forAnnotatedObject(Object userObject, IFactory factory) {
                return CommandReflection.extractCommandSpec(userObject, factory, true);
            }

            /**
             * Creates and returns a new {@code CommandSpec} initialized from the specified
             * associated user object. If the specified user object has no {@link Command},
             * {@link Option} or {@link Parameters} annotations, an empty {@code CommandSpec} is
             * returned.
             * 
             * @param userObject
             *            the user object annotated with {@link Command}, {@link Option} and/or
             *            {@link Parameters} annotations.
             * @throws InitializationException
             *             if the specified object has invalid annotations
             */
            public static CommandSpec forAnnotatedObjectLenient(Object userObject) {
                return forAnnotatedObjectLenient(userObject, new DefaultFactory());
            }

            /**
             * Creates and returns a new {@code CommandSpec} initialized from the specified
             * associated user object. If the specified user object has no {@link Command},
             * {@link Option} or {@link Parameters} annotations, an empty {@code CommandSpec} is
             * returned.
             * 
             * @param userObject
             *            the user object annotated with {@link Command}, {@link Option} and/or
             *            {@link Parameters} annotations.
             * @param factory
             *            the factory used to create instances of {@linkplain Command#subcommands()
             *            subcommands}, {@linkplain Option#converter() converters}, etc., that are
             *            registered declaratively with annotation attributes
             * @throws InitializationException
             *             if the specified object has invalid annotations
             */
            public static CommandSpec forAnnotatedObjectLenient(Object userObject,
                    IFactory factory) {
                return CommandReflection.extractCommandSpec(userObject, factory, false);
            }

            /**
             * Ensures all attributes of this {@code CommandSpec} have a valid value; throws an
             * {@link InitializationException} if this cannot be achieved.
             */
            void validate() {
                Collections.sort(positionalParameters, new PositionalParametersSorter());
                validatePositionalParameters(positionalParameters);
                List<String> wrongUsageHelpAttr = new ArrayList<String>();
                List<String> wrongVersionHelpAttr = new ArrayList<String>();
                List<String> usageHelpAttr = new ArrayList<String>();
                List<String> versionHelpAttr = new ArrayList<String>();
                for (OptionSpec option : options()) {
                    if (option.usageHelp()) {
                        usageHelpAttr.add(option.longestName());
                        if (!isBoolean(option.type())) {
                            wrongUsageHelpAttr.add(option.longestName());
                        }
                    }
                    if (option.versionHelp()) {
                        versionHelpAttr.add(option.longestName());
                        if (!isBoolean(option.type())) {
                            wrongVersionHelpAttr.add(option.longestName());
                        }
                    }
                }
                String wrongType = "Non-boolean options like %s should not be marked as '%s=true'. Usually a command has one %s boolean flag that triggers display of the %s. Alternatively, consider using @Command(mixinStandardHelpOptions = true) on your command instead.";
                String multiple = "Multiple options %s are marked as '%s=true'. Usually a command has only one %s option that triggers display of the %s. Alternatively, consider using @Command(mixinStandardHelpOptions = true) on your command instead.%n";
                if (!wrongUsageHelpAttr.isEmpty()) {
                    throw new InitializationException(String.format(wrongType, wrongUsageHelpAttr,
                            "usageHelp", "--help", "usage help message"));
                }
                if (!wrongVersionHelpAttr.isEmpty()) {
                    throw new InitializationException(String.format(wrongType, wrongVersionHelpAttr,
                            "versionHelp", "--version", "version information"));
                }
                if (usageHelpAttr.size() > 1) {
                    new Tracer().warn(multiple, usageHelpAttr, "usageHelp", "--help",
                            "usage help message");
                }
                if (versionHelpAttr.size() > 1) {
                    new Tracer().warn(multiple, versionHelpAttr, "versionHelp", "--version",
                            "version information");
                }
            }

            /**
             * Returns the user object associated with this command.
             * 
             * @see CommandLine#getCommand()
             */
            public Object userObject() {
                return userObject;
            }

            /** Returns the CommandLine constructed with this {@code CommandSpec} model. */
            public CommandLine commandLine() {
                return commandLine;
            }

            /** Sets the CommandLine constructed with this {@code CommandSpec} model. */
            protected CommandSpec commandLine(CommandLine commandLine) {
                this.commandLine = commandLine;
                for (CommandSpec mixedInSpec : mixins.values()) {
                    mixedInSpec.commandLine(commandLine);
                }
                for (CommandLine sub : commands.values()) {
                    sub.getCommandSpec().parent(this);
                }
                return this;
            }

            /** Returns the parser specification for this command. */
            public ParserSpec parser() {
                return parser;
            }

            /**
             * Initializes the parser specification for this command from the specified settings and
             * returns this commandSpec.
             */
            public CommandSpec parser(ParserSpec settings) {
                parser.initFrom(settings);
                return this;
            }

            /** Returns the usage help message specification for this command. */
            public UsageMessageSpec usageMessage() {
                return usageMessage;
            }

            /**
             * Initializes the usageMessage specification for this command from the specified
             * settings and returns this commandSpec.
             */
            public CommandSpec usageMessage(UsageMessageSpec settings) {
                usageMessage.initFrom(settings, this);
                return this;
            }

            /**
             * Returns the resource bundle for this command.
             * 
             * @return the resource bundle from the {@linkplain UsageMessageSpec#messages()}
             * @since 3.6
             */
            public ResourceBundle resourceBundle() {
                return Messages.resourceBundle(usageMessage.messages());
            }

            /**
             * Initializes the resource bundle for this command: sets the
             * {@link UsageMessageSpec#messages(Messages) UsageMessageSpec.messages} to a
             * {@link Messages Messages} object created from this command spec and the specified
             * bundle, and then sets the {@link ArgSpec#messages(Messages) ArgSpec.messages} of all
             * options and positional parameters in this command to the same {@code Messages}
             * instance. Subcommands are not modified.
             * 
             * @param bundle
             *            the ResourceBundle to set, may be {@code null}
             * @return this commandSpec
             * @see #addSubcommand(String, CommandLine)
             * @since 3.6
             */
            public CommandSpec resourceBundle(ResourceBundle bundle) {
                usageMessage().messages(new Messages(this, bundle));
                updateArgSpecMessages();
                return this;
            }

            private void updateArgSpecMessages() {
                for (OptionSpec opt : options()) {
                    opt.messages(usageMessage().messages());
                }
                for (PositionalParamSpec pos : positionalParameters()) {
                    pos.messages(usageMessage().messages());
                }
            }

            /** Returns a read-only view of the subcommand map. */
            public Map<String, CommandLine> subcommands() {
                return Collections.unmodifiableMap(commands);
            }

            /**
             * Adds the specified subcommand with the specified name. If the specified subcommand
             * does not have a ResourceBundle set, it is initialized to the ResourceBundle of this
             * command spec.
             * 
             * @param name
             *            subcommand name - when this String is encountered in the command line
             *            arguments the subcommand is invoked
             * @param subcommand
             *            describes the subcommand to envoke when the name is encountered on the
             *            command line
             * @return this {@code CommandSpec} object for method chaining
             */
            public CommandSpec addSubcommand(String name, CommandSpec subcommand) {
                return addSubcommand(name, new CommandLine(subcommand));
            }

            /**
             * Adds the specified subcommand with the specified name. If the specified subcommand
             * does not have a ResourceBundle set, it is initialized to the ResourceBundle of this
             * command spec.
             * 
             * @param name
             *            subcommand name - when this String is encountered in the command line
             *            arguments the subcommand is invoked
             * @param subCommandLine
             *            the subcommand to envoke when the name is encountered on the command line
             * @return this {@code CommandSpec} object for method chaining
             */
            public CommandSpec addSubcommand(String name, CommandLine subCommandLine) {
                CommandLine previous = commands.put(name, subCommandLine);
                if (previous != null && previous != subCommandLine) {
                    throw new InitializationException("Another subcommand named '" + name
                            + "' already exists for command '" + this.name() + "'");
                }
                CommandSpec subSpec = subCommandLine.getCommandSpec();
                if (subSpec.name == null) {
                    subSpec.name(name);
                }
                subSpec.parent(this);
                for (String alias : subSpec.aliases()) {
                    previous = commands.put(alias, subCommandLine);
                    if (previous != null && previous != subCommandLine) {
                        throw new InitializationException("Alias '" + alias + "' for subcommand '"
                                + name + "' is already used by another subcommand of '"
                                + this.name() + "'");
                    }
                }
                subSpec.initResourceBundle(resourceBundle());
                return this;
            }

            private void initResourceBundle(ResourceBundle bundle) {
                if (resourceBundle() == null) {
                    resourceBundle(bundle);
                }
                for (CommandLine sub : commands.values()) { // percolate down the hierarchy
                    sub.getCommandSpec().initResourceBundle(resourceBundle());
                }
            }

            /**
             * Reflects on the class of the {@linkplain #userObject() user object} and registers any
             * command methods (class methods annotated with {@code @Command}) as subcommands.
             *
             * @return this {@link CommandSpec} object for method chaining
             * @see #addMethodSubcommands(IFactory)
             * @see #addSubcommand(String, CommandLine)
             * @since 3.6.0
             */
            public CommandSpec addMethodSubcommands() {
                return addMethodSubcommands(new DefaultFactory());
            }

            /**
             * Reflects on the class of the {@linkplain #userObject() user object} and registers any
             * command methods (class methods annotated with {@code @Command}) as subcommands.
             * 
             * @param factory
             *            the factory used to create instances of subcommands, converters, etc.,
             *            that are registered declaratively with annotation attributes
             * @return this {@link CommandSpec} object for method chaining
             * @see #addSubcommand(String, CommandLine)
             * @since 3.7.0
             */
            public CommandSpec addMethodSubcommands(IFactory factory) {
                if (userObject() instanceof Method) {
                    throw new UnsupportedOperationException(
                            "cannot discover methods of non-class: " + userObject());
                }
                for (Method method : getCommandMethods(userObject().getClass(), null)) {
                    CommandLine cmd = new CommandLine(method, factory);
                    addSubcommand(cmd.getCommandName(), cmd);
                }
                return this;
            }

            /**
             * Returns the parent command of this subcommand, or {@code null} if this is a top-level
             * command.
             */
            public CommandSpec parent() {
                return parent;
            }

            /**
             * Sets the parent command of this subcommand.
             * 
             * @return this CommandSpec for method chaining
             */
            public CommandSpec parent(CommandSpec parent) {
                this.parent = parent;
                return this;
            }

            /**
             * Adds the specified option spec or positional parameter spec to the list of configured
             * arguments to expect.
             * 
             * @param arg
             *            the option spec or positional parameter spec to add
             * @return this CommandSpec for method chaining
             */
            public CommandSpec add(ArgSpec arg) {
                return arg.isOption() ? addOption((OptionSpec) arg)
                        : addPositional((PositionalParamSpec) arg);
            }

            /**
             * Adds the specified option spec to the list of configured arguments to expect. The
             * option's {@linkplain OptionSpec#description()} may now return Strings from this
             * CommandSpec's {@linkplain UsageMessageSpec#messages() messages}. The option
             * parameter's {@linkplain OptionSpec#defaultValueString()} may now return Strings from
             * this CommandSpec's {@link CommandSpec#defaultValueProvider()} IDefaultValueProvider}.
             * 
             * @param option
             *            the option spec to add
             * @return this CommandSpec for method chaining
             * @throws DuplicateOptionAnnotationsException
             *             if any of the names of the specified option is the same as the name of
             *             another option
             */
            public CommandSpec addOption(OptionSpec option) {
                args.add(option);
                options.add(option);
                for (String name : option.names()) { // cannot be null or empty
                    OptionSpec existing = optionsByNameMap.put(name, option);
                    if (existing != null && !existing.equals(option)) {
                        throw DuplicateOptionAnnotationsException.create(name, option, existing);
                    }
                    if (name.length() == 2 && name.startsWith("-")) {
                        posixOptionsByKeyMap.put(name.charAt(1), option);
                    }
                }
                if (option.required()) {
                    requiredArgs.add(option);
                }
                option.messages(usageMessage().messages());
                option.commandSpec = this;
                return this;
            }

            /**
             * Adds the specified positional parameter spec to the list of configured arguments to
             * expect. The positional parameter's {@linkplain PositionalParamSpec#description()} may
             * now return Strings from this CommandSpec's {@linkplain UsageMessageSpec#messages()
             * messages}. The positional parameter's
             * {@linkplain PositionalParamSpec#defaultValueString()} may now return Strings from
             * this CommandSpec's {@link CommandSpec#defaultValueProvider()} IDefaultValueProvider}.
             * 
             * @param positional
             *            the positional parameter spec to add
             * @return this CommandSpec for method chaining
             */
            public CommandSpec addPositional(PositionalParamSpec positional) {
                args.add(positional);
                positionalParameters.add(positional);
                if (positional.required()) {
                    requiredArgs.add(positional);
                }
                positional.messages(usageMessage().messages());
                positional.commandSpec = this;
                return this;
            }

            /**
             * Adds the specified mixin {@code CommandSpec} object to the map of mixins for this
             * command.
             * 
             * @param name
             *            the name that can be used to later retrieve the mixin
             * @param mixin
             *            the mixin whose options and positional parameters and other attributes to
             *            add to this command
             * @return this CommandSpec for method chaining
             */
            public CommandSpec addMixin(String name, CommandSpec mixin) {
                mixins.put(name, mixin);

                parser.initSeparator(mixin.parser.separator());
                initName(mixin.name());
                initVersion(mixin.version());
                initHelpCommand(mixin.helpCommand());
                initVersionProvider(mixin.versionProvider());
                initDefaultValueProvider(mixin.defaultValueProvider());
                usageMessage.initFromMixin(mixin.usageMessage, this);

                for (Map.Entry<String, CommandLine> entry : mixin.subcommands().entrySet()) {
                    addSubcommand(entry.getKey(), entry.getValue());
                }
                for (OptionSpec optionSpec : mixin.options()) {
                    addOption(optionSpec);
                }
                for (PositionalParamSpec paramSpec : mixin.positionalParameters()) {
                    addPositional(paramSpec);
                }
                return this;
            }

            /**
             * Adds the specified {@code UnmatchedArgsBinding} to the list of model objects to
             * capture unmatched arguments for this command.
             * 
             * @param spec
             *            the unmatched arguments binding to capture unmatched arguments
             * @return this CommandSpec for method chaining
             */
            public CommandSpec addUnmatchedArgsBinding(UnmatchedArgsBinding spec) {
                unmatchedArgs.add(spec);
                parser().unmatchedArgumentsAllowed(true);
                return this;
            }

            /**
             * Returns a map of the mixin names to mixin {@code CommandSpec} objects configured for
             * this command.
             * 
             * @return an immutable map of mixins added to this command.
             */
            public Map<String, CommandSpec> mixins() {
                return Collections.unmodifiableMap(mixins);
            }

            /**
             * Returns the list of options configured for this command.
             * 
             * @return an immutable list of options that this command recognizes.
             */
            public List<OptionSpec> options() {
                return Collections.unmodifiableList(options);
            }

            /**
             * Returns the list of positional parameters configured for this command.
             * 
             * @return an immutable list of positional parameters that this command recognizes.
             */
            public List<PositionalParamSpec> positionalParameters() {
                return Collections.unmodifiableList(positionalParameters);
            }

            /**
             * Returns a map of the option names to option spec objects configured for this command.
             * 
             * @return an immutable map of options that this command recognizes.
             */
            public Map<String, OptionSpec> optionsMap() {
                return Collections.unmodifiableMap(optionsByNameMap);
            }

            /**
             * Returns a map of the short (single character) option names to option spec objects
             * configured for this command.
             * 
             * @return an immutable map of options that this command recognizes.
             */
            public Map<Character, OptionSpec> posixOptionsMap() {
                return Collections.unmodifiableMap(posixOptionsByKeyMap);
            }

            /**
             * Returns the list of required options and positional parameters configured for this
             * command.
             * 
             * @return an immutable list of the required options and positional parameters for this
             *         command.
             */
            public List<ArgSpec> requiredArgs() {
                return Collections.unmodifiableList(requiredArgs);
            }

            /**
             * Returns the list of {@link UnmatchedArgsBinding UnmatchedArgumentsBindings}
             * configured for this command; each {@code UnmatchedArgsBinding} captures the arguments
             * that could not be matched to any options or positional parameters.
             */
            public List<UnmatchedArgsBinding> unmatchedArgsBindings() {
                return Collections.unmodifiableList(unmatchedArgs);
            }

            /**
             * Returns name of this command. Used in the synopsis line of the help message.
             * {@link #DEFAULT_COMMAND_NAME} by default, initialized from {@link Command#name()} if
             * defined.
             * 
             * @see #qualifiedName()
             */
            public String name() {
                return (name == null) ? DEFAULT_COMMAND_NAME : name;
            }

            /**
             * Returns the alias command names of this subcommand.
             * 
             * @since 3.1
             */
            public String[] aliases() {
                return aliases.toArray(new String[0]);
            }

            /**
             * Returns the list of all options and positional parameters configured for this
             * command.
             * 
             * @return an immutable list of all options and positional parameters for this command.
             */
            public List<ArgSpec> args() {
                return Collections.unmodifiableList(args);
            }

            Object[] argValues() {
                Map<Class<?>, CommandSpec> allMixins = null;
                int argsLength = args.size();
                int shift = 0;
                for (Map.Entry<String, CommandSpec> mixinEntry : mixins.entrySet()) {
                    if (mixinEntry.getKey().equals(AutoHelpMixin.KEY)) {
                        shift = 2;
                        argsLength -= shift;
                        continue;
                    }
                    CommandSpec mixin = mixinEntry.getValue();
                    int mixinArgs = mixin.args.size();
                    argsLength -= (mixinArgs - 1); // subtract 1 because that's the mixin
                    if (allMixins == null) {
                        allMixins = new IdentityHashMap<Class<?>, CommandSpec>(mixins.size());
                    }
                    allMixins.put(mixin.userObject.getClass(), mixin);
                }

                Object[] values = new Object[argsLength];
                if (allMixins == null) {
                    for (int i = 0; i < values.length; i++) {
                        values[i] = args.get(i + shift).getValue();
                    }
                } else {
                    int argIndex = shift;
                    Class<?>[] methodParams = ((Method) userObject).getParameterTypes();
                    for (int i = 0; i < methodParams.length; i++) {
                        final Class<?> param = methodParams[i];
                        CommandSpec mixin = allMixins.remove(param);
                        if (mixin == null) {
                            values[i] = args.get(argIndex++).getValue();
                        } else {
                            values[i] = mixin.userObject;
                            argIndex += mixin.args.size();
                        }
                    }
                }
                return values;
            }

            /**
             * Returns the String to use as the program name in the synopsis line of the help
             * message: this command's {@link #name() name}, preceded by the qualified name of the
             * parent command, if any, separated by a space.
             * 
             * @return {@link #DEFAULT_COMMAND_NAME} by default, initialized from
             *         {@link Command#name()} and the parent command if defined.
             * @since 3.0.1
             */
            public String qualifiedName() {
                return qualifiedName(" ");
            }

            /**
             * Returns this command's fully qualified name, which is its {@link #name() name},
             * preceded by the qualified name of the parent command, if this command has a parent
             * command.
             * 
             * @return {@link #DEFAULT_COMMAND_NAME} by default, initialized from
             *         {@link Command#name()} and the parent command if any.
             * @param separator
             *            the string to put between the names of the commands in the hierarchy
             * @since 3.6
             */
            public String qualifiedName(String separator) {
                String result = name();
                if (parent() != null) {
                    result = parent().qualifiedName(separator) + separator + result;
                }
                return result;
            }

            /**
             * Returns version information for this command, to print to the console when the user
             * specifies an {@linkplain OptionSpec#versionHelp() option} to request version help.
             * This is not part of the usage help message.
             * 
             * @return the version strings generated by the {@link #versionProvider() version
             *         provider} if one is set, otherwise the {@linkplain #version(String...)
             *         version literals}
             */
            public String[] version() {
                if (versionProvider != null) {
                    try {
                        return versionProvider.getVersion();
                    } catch (Exception ex) {
                        String msg = "Could not get version info from " + versionProvider + ": "
                                + ex;
                        throw new ExecutionException(this.commandLine, msg, ex);
                    }
                }
                return version == null ? UsageMessageSpec.DEFAULT_MULTI_LINE : version;
            }

            /**
             * Returns the version provider for this command, to generate the {@link #version()}
             * strings.
             * 
             * @return the version provider or {@code null} if the version strings should be
             *         returned from the {@linkplain #version(String...) version literals}.
             */
            public IVersionProvider versionProvider() {
                return versionProvider;
            }

            /**
             * Returns whether this subcommand is a help command, and required options and
             * positional parameters of the parent command should not be validated.
             * 
             * @return {@code true} if this subcommand is a help command and picocli should not
             *         check for missing required options and positional parameters on the parent
             *         command
             * @see Command#helpCommand()
             */
            public boolean helpCommand() {
                return (isHelpCommand == null) ? DEFAULT_IS_HELP_COMMAND : isHelpCommand;
            }

            /**
             * Returns {@code true} if the standard help options have been mixed in with this
             * command, {@code false} otherwise.
             */
            public boolean mixinStandardHelpOptions() {
                return mixins.containsKey(AutoHelpMixin.KEY);
            }

            /**
             * Returns a string representation of this command, used in error messages and trace
             * messages.
             */
            public String toString() {
                return toString;
            }

            /**
             * Sets the String to use as the program name in the synopsis line of the help message.
             * 
             * @return this CommandSpec for method chaining
             */
            public CommandSpec name(String name) {
                this.name = name;
                return this;
            }

            /**
             * Sets the alternative names by which this subcommand is recognized on the command
             * line.
             * 
             * @return this CommandSpec for method chaining
             * @since 3.1
             */
            public CommandSpec aliases(String... aliases) {
                this.aliases = new LinkedHashSet<String>(
                        Arrays.asList(aliases == null ? new String[0] : aliases));
                return this;
            }

            /**
             * Returns the default value provider for this command.
             * 
             * @return the default value provider or {@code null}
             * @since 3.6
             */
            public IDefaultValueProvider defaultValueProvider() {
                return defaultValueProvider;
            }

            /**
             * Sets default value provider for this command.
             * 
             * @param defaultValueProvider
             *            the default value provider to use, or {@code null}.
             * @return this CommandSpec for method chaining
             * @since 3.6
             */
            public CommandSpec defaultValueProvider(IDefaultValueProvider defaultValueProvider) {
                this.defaultValueProvider = defaultValueProvider;
                return this;
            }

            /**
             * Sets version information literals for this command, to print to the console when the
             * user specifies an {@linkplain OptionSpec#versionHelp() option} to request version
             * help. Only used if no {@link #versionProvider() versionProvider} is set.
             * 
             * @return this CommandSpec for method chaining
             */
            public CommandSpec version(String... version) {
                this.version = version;
                return this;
            }

            /**
             * Sets version provider for this command, to generate the {@link #version()} strings.
             * 
             * @param versionProvider
             *            the version provider to use to generate the version strings, or
             *            {@code null} if the {@linkplain #version(String...) version literals}
             *            should be used.
             * @return this CommandSpec for method chaining
             */
            public CommandSpec versionProvider(IVersionProvider versionProvider) {
                this.versionProvider = versionProvider;
                return this;
            }

            /**
             * Sets whether this is a help command and required parameter checking should be
             * suspended.
             * 
             * @return this CommandSpec for method chaining
             * @see Command#helpCommand()
             */
            public CommandSpec helpCommand(boolean newValue) {
                isHelpCommand = newValue;
                return this;
            }

            /**
             * Sets whether the standard help options should be mixed in with this command.
             * 
             * @return this CommandSpec for method chaining
             * @see Command#mixinStandardHelpOptions()
             */
            public CommandSpec mixinStandardHelpOptions(boolean newValue) {
                if (newValue) {
                    CommandSpec mixin = CommandSpec.forAnnotatedObject(new AutoHelpMixin(),
                            new DefaultFactory());
                    addMixin(AutoHelpMixin.KEY, mixin);
                } else {
                    CommandSpec helpMixin = mixins.remove(AutoHelpMixin.KEY);
                    if (helpMixin != null) {
                        options.removeAll(helpMixin.options);
                        for (OptionSpec option : helpMixin.options()) {
                            for (String name : option.names) {
                                optionsByNameMap.remove(name);
                                if (name.length() == 2 && name.startsWith("-")) {
                                    posixOptionsByKeyMap.remove(name.charAt(1));
                                }
                            }
                        }
                    }
                }
                return this;
            }

            /**
             * Sets the string representation of this command, used in error messages and trace
             * messages.
             * 
             * @param newValue
             *            the string representation
             * @return this CommandSpec for method chaining
             */
            public CommandSpec withToString(String newValue) {
                this.toString = newValue;
                return this;
            }

            void initName(String value) {
                if (initializable(name, value, DEFAULT_COMMAND_NAME)) {
                    name = value;
                }
            }

            void initHelpCommand(boolean value) {
                if (initializable(isHelpCommand, value, DEFAULT_IS_HELP_COMMAND)) {
                    isHelpCommand = value;
                }
            }

            void initVersion(String[] value) {
                if (initializable(version, value, UsageMessageSpec.DEFAULT_MULTI_LINE)) {
                    version = value.clone();
                }
            }

            void initVersionProvider(IVersionProvider value) {
                if (versionProvider == null) {
                    versionProvider = value;
                }
            }

            void initVersionProvider(Class<? extends IVersionProvider> value, IFactory factory) {
                if (initializable(versionProvider, value, NoVersionProvider.class)) {
                    versionProvider = (DefaultFactory.createVersionProvider(factory, value));
                }
            }

            void initDefaultValueProvider(IDefaultValueProvider value) {
                if (defaultValueProvider == null) {
                    defaultValueProvider = value;
                }
            }

            void initDefaultValueProvider(Class<? extends IDefaultValueProvider> value,
                    IFactory factory) {
                if (initializable(defaultValueProvider, value, NoDefaultProvider.class)) {
                    defaultValueProvider = (DefaultFactory.createDefaultValueProvider(factory,
                            value));
                }
            }

            void updateName(String value) {
                if (isNonDefault(value, DEFAULT_COMMAND_NAME)) {
                    name = value;
                }
            }

            void updateHelpCommand(boolean value) {
                if (isNonDefault(value, DEFAULT_IS_HELP_COMMAND)) {
                    isHelpCommand = value;
                }
            }

            void updateVersion(String[] value) {
                if (isNonDefault(value, UsageMessageSpec.DEFAULT_MULTI_LINE)) {
                    version = value.clone();
                }
            }

            void updateVersionProvider(Class<? extends IVersionProvider> value, IFactory factory) {
                if (isNonDefault(value, NoVersionProvider.class)) {
                    versionProvider = (DefaultFactory.createVersionProvider(factory, value));
                }
            }

            /**
             * Returns the option with the specified short name, or {@code null} if no option with
             * that name is defined for this command.
             */
            public OptionSpec findOption(char shortName) {
                return findOption(shortName, options());
            }

            /**
             * Returns the option with the specified name, or {@code null} if no option with that
             * name is defined for this command.
             * 
             * @param name
             *            used to search the options. May include option name prefix characters or
             *            not.
             */
            public OptionSpec findOption(String name) {
                return findOption(name, options());
            }

            static OptionSpec findOption(char shortName, Iterable<OptionSpec> options) {
                for (OptionSpec option : options) {
                    for (String name : option.names()) {
                        if (name.length() == 2 && name.charAt(0) == '-'
                                && name.charAt(1) == shortName) {
                            return option;
                        }
                        if (name.length() == 1 && name.charAt(0) == shortName) {
                            return option;
                        }
                    }
                }
                return null;
            }

            static OptionSpec findOption(String name, List<OptionSpec> options) {
                for (OptionSpec option : options) {
                    for (String prefixed : option.names()) {
                        if (prefixed.equals(name) || stripPrefix(prefixed).equals(name)) {
                            return option;
                        }
                    }
                }
                return null;
            }

            public static String stripPrefix(String prefixed) {
                for (int i = 0; i < prefixed.length(); i++) {
                    if (Character.isJavaIdentifierPart(prefixed.charAt(i))) {
                        return prefixed.substring(i);
                    }
                }
                return prefixed;
            }

            public List<String> findOptionNamesWithPrefix(String prefix) {
                List<String> result = new ArrayList<String>();
                for (OptionSpec option : options()) {
                    for (String name : option.names()) {
                        if (stripPrefix(name).startsWith(prefix)) {
                            result.add(name);
                        }
                    }
                }
                return result;
            }

            public boolean resemblesOption(String arg, Tracer tracer) {
                if (parser().unmatchedOptionsArePositionalParams()) {
                    if (tracer != null && tracer.isDebug()) {
                        tracer.debug(
                                "Parser is configured to treat all unmatched options as positional parameter%n",
                                arg);
                    }
                    return false;
                }
                if (options().isEmpty()) {
                    boolean result = arg.startsWith("-");
                    if (tracer != null && tracer.isDebug()) {
                        tracer.debug("%s %s an option%n", arg,
                                (result ? "resembles" : "doesn't resemble"));
                    }
                    return result;
                }
                int count = 0;
                for (String optionName : optionsMap().keySet()) {
                    for (int i = 0; i < arg.length(); i++) {
                        if (optionName.length() > i && arg.charAt(i) == optionName.charAt(i)) {
                            count++;
                        } else {
                            break;
                        }
                    }
                }
                boolean result = count > 0 && count * 10 >= optionsMap().size() * 9; // at least one prefix char in common with 9 out of 10 options
                if (tracer != null && tracer.isDebug()) {
                    tracer.debug(
                            "%s %s an option: %d matching prefix chars out of %d option names%n",
                            arg, (result ? "resembles" : "doesn't resemble"), count,
                            optionsMap().size());
                }
                return result;
            }
        }

        private static boolean initializable(Object current, Object candidate,
                Object defaultValue) {
            return current == null && isNonDefault(candidate, defaultValue);
        }

        private static boolean initializable(Object current, Object[] candidate,
                Object[] defaultValue) {
            return current == null && isNonDefault(candidate, defaultValue);
        }

        private static boolean isNonDefault(Object candidate, Object defaultValue) {
            return !Assert.notNull(defaultValue, "defaultValue").equals(candidate);
        }

        private static boolean isNonDefault(Object[] candidate, Object[] defaultValue) {
            return !Arrays.equals(Assert.notNull(defaultValue, "defaultValue"), candidate);
        }

        /**
         * Models the usage help message specification.
         * 
         * @since 3.0
         */
        public static class UsageMessageSpec {
            /** Constant holding the default usage message width: <code>{@value}</code>. */
            public final static int DEFAULT_USAGE_WIDTH = 80;
            private final static int MINIMUM_USAGE_WIDTH = 55;

            /** Constant String holding the default synopsis heading: <code>{@value}</code>. */
            static final String DEFAULT_SYNOPSIS_HEADING = "Usage: ";

            /** Constant String holding the default command list heading: <code>{@value}</code>. */
            static final String DEFAULT_COMMAND_LIST_HEADING = "Commands:%n";

            /**
             * Constant String holding the default string that separates options from option
             * parameters: {@code ' '} ({@value}).
             */
            static final char DEFAULT_REQUIRED_OPTION_MARKER = ' ';

            /**
             * Constant Boolean holding the default setting for whether to abbreviate the synopsis:
             * <code>{@value}</code>.
             */
            static final Boolean DEFAULT_ABBREVIATE_SYNOPSIS = Boolean.FALSE;

            /**
             * Constant Boolean holding the default setting for whether to sort the options
             * alphabetically: <code>{@value}</code>.
             */
            static final Boolean DEFAULT_SORT_OPTIONS = Boolean.TRUE;

            /**
             * Constant Boolean holding the default setting for whether to show default values in
             * the usage help message: <code>{@value}</code>.
             */
            static final Boolean DEFAULT_SHOW_DEFAULT_VALUES = Boolean.FALSE;

            /**
             * Constant Boolean holding the default setting for whether this command should be
             * listed in the usage help of the parent command: <code>{@value}</code>.
             */
            static final Boolean DEFAULT_HIDDEN = Boolean.FALSE;

            static final String DEFAULT_SINGLE_VALUE = "";
            static final String[] DEFAULT_MULTI_LINE = {};

            private String[] description;
            private String[] customSynopsis;
            private String[] header;
            private String[] footer;
            private Boolean abbreviateSynopsis;
            private Boolean sortOptions;
            private Boolean showDefaultValues;
            private Boolean hidden;
            private Character requiredOptionMarker;
            private String headerHeading;
            private String synopsisHeading;
            private String descriptionHeading;
            private String parameterListHeading;
            private String optionListHeading;
            private String commandListHeading;
            private String footerHeading;
            private int width = DEFAULT_USAGE_WIDTH;

            private Messages messages;

            private static int getSysPropertyWidthOrDefault(int defaultWidth) {
                String userValue = System.getProperty("picocli.usage.width");
                if (userValue == null) {
                    return defaultWidth;
                }
                try {
                    int width = Integer.parseInt(userValue);
                    if (width < MINIMUM_USAGE_WIDTH) {
                        new Tracer().warn(
                                "Invalid picocli.usage.width value %d. Using minimum usage width %d.%n",
                                width, MINIMUM_USAGE_WIDTH);
                        return MINIMUM_USAGE_WIDTH;
                    }
                    return width;
                } catch (NumberFormatException ex) {
                    new Tracer().warn(
                            "Invalid picocli.usage.width value '%s'. Using usage width %d.%n",
                            userValue, defaultWidth);
                    return defaultWidth;
                }
            }

            /**
             * Returns the maximum usage help message width. Derived from system property
             * {@code "picocli.usage.width"} if set, otherwise returns the value set via the
             * {@link #width(int)} method, or if not set, the {@linkplain #DEFAULT_USAGE_WIDTH
             * default width}.
             * 
             * @return the maximum usage help message width. Never returns less than 55.
             */
            public int width() {
                return getSysPropertyWidthOrDefault(width);
            }

            /**
             * Sets the maximum usage help message width to the specified value. Longer values are
             * wrapped.
             * 
             * @param newValue
             *            the new maximum usage help message width. Must be 55 or greater.
             * @return this {@code UsageMessageSpec} for method chaining
             * @throws IllegalArgumentException
             *             if the specified width is less than 55
             */
            public UsageMessageSpec width(int newValue) {
                if (newValue < MINIMUM_USAGE_WIDTH) {
                    throw new InitializationException("Invalid usage message width " + newValue
                            + ". Minimum value is " + MINIMUM_USAGE_WIDTH);
                }
                width = newValue;
                return this;
            }

            private String str(String localized, String value, String defaultValue) {
                return localized != null ? localized : (value != null ? value : defaultValue);
            }

            private String[] arr(String[] localized, String[] value, String[] defaultValue) {
                return localized != null ? localized
                        : (value != null ? value.clone() : defaultValue);
            }

            private String resourceStr(String key) {
                return messages == null ? null : messages.getString(key, null);
            }

            private String[] resourceArr(String key) {
                return messages == null ? null : messages.getStringArray(key, null);
            }

            /**
             * Returns the optional heading preceding the header section. Initialized from
             * {@link Command#headerHeading()}, or null.
             */
            public String headerHeading() {
                return str(resourceStr("usage.headerHeading"), headerHeading, DEFAULT_SINGLE_VALUE);
            }

            /**
             * Returns the optional header lines displayed at the top of the help message. For
             * subcommands, the first header line is displayed in the list of commands. Values are
             * initialized from {@link Command#header()} if the {@code Command} annotation is
             * present, otherwise this is an empty array and the help message has no header.
             * Applications may programmatically set this field to create a custom help message.
             */
            public String[] header() {
                return arr(resourceArr("usage.header"), header, DEFAULT_MULTI_LINE);
            }

            /**
             * Returns the optional heading preceding the synopsis. Initialized from
             * {@link Command#synopsisHeading()}, {@code "Usage: "} by default.
             */
            public String synopsisHeading() {
                return str(resourceStr("usage.synopsisHeading"), synopsisHeading,
                        DEFAULT_SYNOPSIS_HEADING);
            }

            /**
             * Returns whether the synopsis line(s) should show an abbreviated synopsis without
             * detailed option names.
             */
            public boolean abbreviateSynopsis() {
                return (abbreviateSynopsis == null) ? DEFAULT_ABBREVIATE_SYNOPSIS
                        : abbreviateSynopsis;
            }

            /**
             * Returns the optional custom synopsis lines to use instead of the auto-generated
             * synopsis. Initialized from {@link Command#customSynopsis()} if the {@code Command}
             * annotation is present, otherwise this is an empty array and the synopsis is
             * generated. Applications may programmatically set this field to create a custom help
             * message.
             */
            public String[] customSynopsis() {
                return arr(resourceArr("usage.customSynopsis"), customSynopsis, DEFAULT_MULTI_LINE);
            }

            /**
             * Returns the optional heading preceding the description section. Initialized from
             * {@link Command#descriptionHeading()}, or null.
             */
            public String descriptionHeading() {
                return str(resourceStr("usage.descriptionHeading"), descriptionHeading,
                        DEFAULT_SINGLE_VALUE);
            }

            /**
             * Returns the optional text lines to use as the description of the help message,
             * displayed between the synopsis and the options list. Initialized from
             * {@link Command#description()} if the {@code Command} annotation is present, otherwise
             * this is an empty array and the help message has no description. Applications may
             * programmatically set this field to create a custom help message.
             */
            public String[] description() {
                return arr(resourceArr("usage.description"), description, DEFAULT_MULTI_LINE);
            }

            /**
             * Returns the optional heading preceding the parameter list. Initialized from
             * {@link Command#parameterListHeading()}, or null.
             */
            public String parameterListHeading() {
                return str(resourceStr("usage.parameterListHeading"), parameterListHeading,
                        DEFAULT_SINGLE_VALUE);
            }

            /**
             * Returns the optional heading preceding the options list. Initialized from
             * {@link Command#optionListHeading()}, or null.
             */
            public String optionListHeading() {
                return str(resourceStr("usage.optionListHeading"), optionListHeading,
                        DEFAULT_SINGLE_VALUE);
            }

            /**
             * Returns whether the options list in the usage help message should be sorted
             * alphabetically.
             */
            public boolean sortOptions() {
                return (sortOptions == null) ? DEFAULT_SORT_OPTIONS : sortOptions;
            }

            /** Returns the character used to prefix required options in the options list. */
            public char requiredOptionMarker() {
                return (requiredOptionMarker == null) ? DEFAULT_REQUIRED_OPTION_MARKER
                        : requiredOptionMarker;
            }

            /**
             * Returns whether the options list in the usage help message should show default values
             * for all non-boolean options.
             */
            public boolean showDefaultValues() {
                return (showDefaultValues == null) ? DEFAULT_SHOW_DEFAULT_VALUES
                        : showDefaultValues;
            }

            /**
             * Returns whether this command should be hidden from the usage help message of the
             * parent command.
             * 
             * @return {@code true} if this command should not appear in the usage help message of
             *         the parent command
             */
            public boolean hidden() {
                return (hidden == null) ? DEFAULT_HIDDEN : hidden;
            }

            /**
             * Returns the optional heading preceding the subcommand list. Initialized from
             * {@link Command#commandListHeading()}. {@code "Commands:%n"} by default.
             */
            public String commandListHeading() {
                return str(resourceStr("usage.commandListHeading"), commandListHeading,
                        DEFAULT_COMMAND_LIST_HEADING);
            }

            /**
             * Returns the optional heading preceding the footer section. Initialized from
             * {@link Command#footerHeading()}, or null.
             */
            public String footerHeading() {
                return str(resourceStr("usage.footerHeading"), footerHeading, DEFAULT_SINGLE_VALUE);
            }

            /**
             * Returns the optional footer text lines displayed at the bottom of the help message.
             * Initialized from {@link Command#footer()} if the {@code Command} annotation is
             * present, otherwise this is an empty array and the help message has no footer.
             * Applications may programmatically set this field to create a custom help message.
             */
            public String[] footer() {
                return arr(resourceArr("usage.footer"), footer, DEFAULT_MULTI_LINE);
            }

            /**
             * Sets the heading preceding the header section. Initialized from
             * {@link Command#headerHeading()}, or null.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec headerHeading(String headerHeading) {
                this.headerHeading = headerHeading;
                return this;
            }

            /**
             * Sets the optional header lines displayed at the top of the help message. For
             * subcommands, the first header line is displayed in the list of commands.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec header(String... header) {
                this.header = header;
                return this;
            }

            /**
             * Sets the optional heading preceding the synopsis.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec synopsisHeading(String newValue) {
                synopsisHeading = newValue;
                return this;
            }

            /**
             * Sets whether the synopsis line(s) should show an abbreviated synopsis without
             * detailed option names.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec abbreviateSynopsis(boolean newValue) {
                abbreviateSynopsis = newValue;
                return this;
            }

            /**
             * Sets the optional custom synopsis lines to use instead of the auto-generated
             * synopsis.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec customSynopsis(String... customSynopsis) {
                this.customSynopsis = customSynopsis;
                return this;
            }

            /**
             * Sets the heading preceding the description section.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec descriptionHeading(String newValue) {
                descriptionHeading = newValue;
                return this;
            }

            /**
             * Sets the optional text lines to use as the description of the help message, displayed
             * between the synopsis and the options list.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec description(String... description) {
                this.description = description;
                return this;
            }

            /**
             * Sets the optional heading preceding the parameter list.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec parameterListHeading(String newValue) {
                parameterListHeading = newValue;
                return this;
            }

            /**
             * Sets the heading preceding the options list.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec optionListHeading(String newValue) {
                optionListHeading = newValue;
                return this;
            }

            /**
             * Sets whether the options list in the usage help message should be sorted
             * alphabetically.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec sortOptions(boolean newValue) {
                sortOptions = newValue;
                return this;
            }

            /**
             * Sets the character used to prefix required options in the options list.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec requiredOptionMarker(char newValue) {
                requiredOptionMarker = newValue;
                return this;
            }

            /**
             * Sets whether the options list in the usage help message should show default values
             * for all non-boolean options.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec showDefaultValues(boolean newValue) {
                showDefaultValues = newValue;
                return this;
            }

            /**
             * Set the hidden flag on this command to control whether to show or hide it in the help
             * usage text of the parent command.
             * 
             * @param value
             *            enable or disable the hidden flag
             * @return this UsageMessageSpec for method chaining
             * @see Command#hidden()
             */
            public UsageMessageSpec hidden(boolean value) {
                hidden = value;
                return this;
            }

            /**
             * Sets the optional heading preceding the subcommand list.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec commandListHeading(String newValue) {
                commandListHeading = newValue;
                return this;
            }

            /**
             * Sets the optional heading preceding the footer section.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec footerHeading(String newValue) {
                footerHeading = newValue;
                return this;
            }

            /**
             * Sets the optional footer text lines displayed at the bottom of the help message.
             * 
             * @return this UsageMessageSpec for method chaining
             */
            public UsageMessageSpec footer(String... footer) {
                this.footer = footer;
                return this;
            }

            /**
             * Returns the Messages for this usage help message specification, or {@code null}.
             * 
             * @return the Messages object that encapsulates this
             *         {@linkplain CommandSpec#resourceBundle() command's resource bundle}
             * @since 3.6
             */
            public Messages messages() {
                return messages;
            }

            /**
             * Sets the Messages for this usageMessage specification, and returns this
             * UsageMessageSpec.
             * 
             * @param msgs
             *            the new Messages value that encapsulates this
             *            {@linkplain CommandSpec#resourceBundle() command's resource bundle}, may
             *            be {@code null}
             * @since 3.6
             */
            public UsageMessageSpec messages(Messages msgs) {
                messages = msgs;
                return this;
            }

            void updateFromCommand(Command cmd, CommandSpec commandSpec) {
                if (isNonDefault(cmd.synopsisHeading(), DEFAULT_SYNOPSIS_HEADING)) {
                    synopsisHeading = cmd.synopsisHeading();
                }
                if (isNonDefault(cmd.commandListHeading(), DEFAULT_COMMAND_LIST_HEADING)) {
                    commandListHeading = cmd.commandListHeading();
                }
                if (isNonDefault(cmd.requiredOptionMarker(), DEFAULT_REQUIRED_OPTION_MARKER)) {
                    requiredOptionMarker = cmd.requiredOptionMarker();
                }
                if (isNonDefault(cmd.abbreviateSynopsis(), DEFAULT_ABBREVIATE_SYNOPSIS)) {
                    abbreviateSynopsis = cmd.abbreviateSynopsis();
                }
                if (isNonDefault(cmd.sortOptions(), DEFAULT_SORT_OPTIONS)) {
                    sortOptions = cmd.sortOptions();
                }
                if (isNonDefault(cmd.showDefaultValues(), DEFAULT_SHOW_DEFAULT_VALUES)) {
                    showDefaultValues = cmd.showDefaultValues();
                }
                if (isNonDefault(cmd.hidden(), DEFAULT_HIDDEN)) {
                    hidden = cmd.hidden();
                }
                if (isNonDefault(cmd.customSynopsis(), DEFAULT_MULTI_LINE)) {
                    customSynopsis = cmd.customSynopsis().clone();
                }
                if (isNonDefault(cmd.description(), DEFAULT_MULTI_LINE)) {
                    description = cmd.description().clone();
                }
                if (isNonDefault(cmd.descriptionHeading(), DEFAULT_SINGLE_VALUE)) {
                    descriptionHeading = cmd.descriptionHeading();
                }
                if (isNonDefault(cmd.header(), DEFAULT_MULTI_LINE)) {
                    header = cmd.header().clone();
                }
                if (isNonDefault(cmd.headerHeading(), DEFAULT_SINGLE_VALUE)) {
                    headerHeading = cmd.headerHeading();
                }
                if (isNonDefault(cmd.footer(), DEFAULT_MULTI_LINE)) {
                    footer = cmd.footer().clone();
                }
                if (isNonDefault(cmd.footerHeading(), DEFAULT_SINGLE_VALUE)) {
                    footerHeading = cmd.footerHeading();
                }
                if (isNonDefault(cmd.parameterListHeading(), DEFAULT_SINGLE_VALUE)) {
                    parameterListHeading = cmd.parameterListHeading();
                }
                if (isNonDefault(cmd.optionListHeading(), DEFAULT_SINGLE_VALUE)) {
                    optionListHeading = cmd.optionListHeading();
                }
                if (isNonDefault(cmd.usageHelpWidth(), DEFAULT_USAGE_WIDTH)) {
                    width(cmd.usageHelpWidth());
                } // validate

                ResourceBundle rb = StringUtils.isBlank(cmd.resourceBundle()) ? null
                        : ResourceBundle.getBundle(cmd.resourceBundle());
                if (rb != null) {
                    messages(new Messages(commandSpec, rb));
                } // else preserve superclass bundle
            }

            void initFromMixin(UsageMessageSpec mixin, CommandSpec commandSpec) {
                if (initializable(synopsisHeading, mixin.synopsisHeading(),
                        DEFAULT_SYNOPSIS_HEADING)) {
                    synopsisHeading = mixin.synopsisHeading();
                }
                if (initializable(commandListHeading, mixin.commandListHeading(),
                        DEFAULT_COMMAND_LIST_HEADING)) {
                    commandListHeading = mixin.commandListHeading();
                }
                if (initializable(requiredOptionMarker, mixin.requiredOptionMarker(),
                        DEFAULT_REQUIRED_OPTION_MARKER)) {
                    requiredOptionMarker = mixin.requiredOptionMarker();
                }
                if (initializable(abbreviateSynopsis, mixin.abbreviateSynopsis(),
                        DEFAULT_ABBREVIATE_SYNOPSIS)) {
                    abbreviateSynopsis = mixin.abbreviateSynopsis();
                }
                if (initializable(sortOptions, mixin.sortOptions(), DEFAULT_SORT_OPTIONS)) {
                    sortOptions = mixin.sortOptions();
                }
                if (initializable(showDefaultValues, mixin.showDefaultValues(),
                        DEFAULT_SHOW_DEFAULT_VALUES)) {
                    showDefaultValues = mixin.showDefaultValues();
                }
                if (initializable(hidden, mixin.hidden(), DEFAULT_HIDDEN)) {
                    hidden = mixin.hidden();
                }
                if (initializable(customSynopsis, mixin.customSynopsis(), DEFAULT_MULTI_LINE)) {
                    customSynopsis = mixin.customSynopsis().clone();
                }
                if (initializable(description, mixin.description(), DEFAULT_MULTI_LINE)) {
                    description = mixin.description().clone();
                }
                if (initializable(descriptionHeading, mixin.descriptionHeading(),
                        DEFAULT_SINGLE_VALUE)) {
                    descriptionHeading = mixin.descriptionHeading();
                }
                if (initializable(header, mixin.header(), DEFAULT_MULTI_LINE)) {
                    header = mixin.header().clone();
                }
                if (initializable(headerHeading, mixin.headerHeading(), DEFAULT_SINGLE_VALUE)) {
                    headerHeading = mixin.headerHeading();
                }
                if (initializable(footer, mixin.footer(), DEFAULT_MULTI_LINE)) {
                    footer = mixin.footer().clone();
                }
                if (initializable(footerHeading, mixin.footerHeading(), DEFAULT_SINGLE_VALUE)) {
                    footerHeading = mixin.footerHeading();
                }
                if (initializable(parameterListHeading, mixin.parameterListHeading(),
                        DEFAULT_SINGLE_VALUE)) {
                    parameterListHeading = mixin.parameterListHeading();
                }
                if (initializable(optionListHeading, mixin.optionListHeading(),
                        DEFAULT_SINGLE_VALUE)) {
                    optionListHeading = mixin.optionListHeading();
                }
                if (Messages.empty(messages)) {
                    messages(Messages.copy(commandSpec, mixin.messages()));
                }
            }

            void initFrom(UsageMessageSpec settings, CommandSpec commandSpec) {
                description = settings.description;
                customSynopsis = settings.customSynopsis;
                header = settings.header;
                footer = settings.footer;
                abbreviateSynopsis = settings.abbreviateSynopsis;
                sortOptions = settings.sortOptions;
                showDefaultValues = settings.showDefaultValues;
                hidden = settings.hidden;
                requiredOptionMarker = settings.requiredOptionMarker;
                headerHeading = settings.headerHeading;
                synopsisHeading = settings.synopsisHeading;
                descriptionHeading = settings.descriptionHeading;
                parameterListHeading = settings.parameterListHeading;
                optionListHeading = settings.optionListHeading;
                commandListHeading = settings.commandListHeading;
                footerHeading = settings.footerHeading;
                width = settings.width;
                messages = Messages.copy(commandSpec, settings.messages());
            }
        }

        /**
         * Models parser configuration specification.
         * 
         * @since 3.0
         */
        public static class ParserSpec {

            /**
             * Constant String holding the default separator between options and option parameters:
             * <code>{@value}</code>.
             */
            public static final String DEFAULT_SEPARATOR = "=";
            private String separator;
            private boolean stopAtUnmatched = false;
            private boolean stopAtPositional = false;
            private String endOfOptionsDelimiter = "--";
            private boolean toggleBooleanFlags = true;
            private boolean overwrittenOptionsAllowed = false;
            private boolean unmatchedArgumentsAllowed = false;
            private boolean expandAtFiles = true;
            private Character atFileCommentChar = '#';
            private boolean posixClusteredShortOptionsAllowed = true;
            private boolean unmatchedOptionsArePositionalParams = false;
            private boolean limitSplit = false;
            private boolean aritySatisfiedByAttachedOptionParam = false;
            boolean collectErrors = false;
            private boolean caseInsensitiveEnumValuesAllowed = false;
            private boolean trimQuotes = false;
            private boolean splitQuotedStrings = false;

            /**
             * Returns the String to use as the separator between options and option parameters.
             * {@code "="} by default, initialized from {@link Command#separator()} if defined.
             */
            public String separator() {
                return (separator == null) ? DEFAULT_SEPARATOR : separator;
            }

            /** @see CommandLine#isStopAtUnmatched() */
            public boolean stopAtUnmatched() {
                return stopAtUnmatched;
            }

            /** @see CommandLine#isStopAtPositional() */
            public boolean stopAtPositional() {
                return stopAtPositional;
            }

            /**
             * @see CommandLine#getEndOfOptionsDelimiter()
             * @since 3.5
             */
            public String endOfOptionsDelimiter() {
                return endOfOptionsDelimiter;
            }

            /** @see CommandLine#isToggleBooleanFlags() */
            public boolean toggleBooleanFlags() {
                return toggleBooleanFlags;
            }

            /** @see CommandLine#isOverwrittenOptionsAllowed() */
            public boolean overwrittenOptionsAllowed() {
                return overwrittenOptionsAllowed;
            }

            /** @see CommandLine#isUnmatchedArgumentsAllowed() */
            public boolean unmatchedArgumentsAllowed() {
                return unmatchedArgumentsAllowed;
            }

            /** @see CommandLine#isExpandAtFiles() */
            public boolean expandAtFiles() {
                return expandAtFiles;
            }

            /**
             * @see CommandLine#getAtFileCommentChar()
             * @since 3.5
             */
            public Character atFileCommentChar() {
                return atFileCommentChar;
            }

            /** @see CommandLine#isPosixClusteredShortOptionsAllowed() */
            public boolean posixClusteredShortOptionsAllowed() {
                return posixClusteredShortOptionsAllowed;
            }

            /**
             * @see CommandLine#isCaseInsensitiveEnumValuesAllowed()
             * @since 3.4
             */
            public boolean caseInsensitiveEnumValuesAllowed() {
                return caseInsensitiveEnumValuesAllowed;
            }

            /**
             * @see CommandLine#isTrimQuotes()
             * @since 3.7
             */
            public boolean trimQuotes() {
                return trimQuotes;
            }

            /**
             * @see CommandLine#isSplitQuotedStrings()
             * @since 3.7
             */
            public boolean splitQuotedStrings() {
                return splitQuotedStrings;
            }

            /** @see CommandLine#isUnmatchedOptionsArePositionalParams() */
            public boolean unmatchedOptionsArePositionalParams() {
                return unmatchedOptionsArePositionalParams;
            }

            boolean splitFirst() {
                return limitSplit();
            }

            /**
             * Returns true if arguments should be split first before any further processing and the
             * number of parts resulting from the split is limited to the max arity of the argument.
             */
            public boolean limitSplit() {
                return limitSplit;
            }

            /**
             * Returns true if options with attached arguments should not consume subsequent
             * arguments and should not validate arity.
             */
            public boolean aritySatisfiedByAttachedOptionParam() {
                return aritySatisfiedByAttachedOptionParam;
            }

            /**
             * Returns true if exceptions during parsing should be collected instead of thrown.
             * Multiple errors may be encountered during parsing. These can be obtained from
             * {@link ParseResult#errors()}.
             * 
             * @since 3.2
             */
            public boolean collectErrors() {
                return collectErrors;
            }

            /**
             * Sets the String to use as the separator between options and option parameters.
             * 
             * @return this ParserSpec for method chaining
             */
            public ParserSpec separator(String separator) {
                this.separator = separator;
                return this;
            }

            /** @see CommandLine#setStopAtUnmatched(boolean) */
            public ParserSpec stopAtUnmatched(boolean stopAtUnmatched) {
                this.stopAtUnmatched = stopAtUnmatched;
                return this;
            }

            /** @see CommandLine#setStopAtPositional(boolean) */
            public ParserSpec stopAtPositional(boolean stopAtPositional) {
                this.stopAtPositional = stopAtPositional;
                return this;
            }

            /**
             * @see CommandLine#setEndOfOptionsDelimiter(String)
             * @since 3.5
             */
            public ParserSpec endOfOptionsDelimiter(String delimiter) {
                this.endOfOptionsDelimiter = Assert.notNull(delimiter, "end-of-options delimiter");
                return this;
            }

            /** @see CommandLine#setToggleBooleanFlags(boolean) */
            public ParserSpec toggleBooleanFlags(boolean toggleBooleanFlags) {
                this.toggleBooleanFlags = toggleBooleanFlags;
                return this;
            }

            /** @see CommandLine#setOverwrittenOptionsAllowed(boolean) */
            public ParserSpec overwrittenOptionsAllowed(boolean overwrittenOptionsAllowed) {
                this.overwrittenOptionsAllowed = overwrittenOptionsAllowed;
                return this;
            }

            /** @see CommandLine#setUnmatchedArgumentsAllowed(boolean) */
            public ParserSpec unmatchedArgumentsAllowed(boolean unmatchedArgumentsAllowed) {
                this.unmatchedArgumentsAllowed = unmatchedArgumentsAllowed;
                return this;
            }

            /** @see CommandLine#setExpandAtFiles(boolean) */
            public ParserSpec expandAtFiles(boolean expandAtFiles) {
                this.expandAtFiles = expandAtFiles;
                return this;
            }

            /**
             * @see CommandLine#setAtFileCommentChar(Character)
             * @since 3.5
             */
            public ParserSpec atFileCommentChar(Character atFileCommentChar) {
                this.atFileCommentChar = atFileCommentChar;
                return this;
            }

            /** @see CommandLine#setPosixClusteredShortOptionsAllowed(boolean) */
            public ParserSpec posixClusteredShortOptionsAllowed(
                    boolean posixClusteredShortOptionsAllowed) {
                this.posixClusteredShortOptionsAllowed = posixClusteredShortOptionsAllowed;
                return this;
            }

            /**
             * @see CommandLine#setCaseInsensitiveEnumValuesAllowed(boolean)
             * @since 3.4
             */
            public ParserSpec caseInsensitiveEnumValuesAllowed(
                    boolean caseInsensitiveEnumValuesAllowed) {
                this.caseInsensitiveEnumValuesAllowed = caseInsensitiveEnumValuesAllowed;
                return this;
            }

            /**
             * @see CommandLine#setTrimQuotes(boolean)
             * @since 3.7
             */
            public ParserSpec trimQuotes(boolean trimQuotes) {
                this.trimQuotes = trimQuotes;
                return this;
            }

            /**
             * @see CommandLine#setSplitQuotedStrings(boolean)
             * @since 3.7
             */
            public ParserSpec splitQuotedStrings(boolean splitQuotedStrings) {
                this.splitQuotedStrings = splitQuotedStrings;
                return this;
            }

            /** @see CommandLine#setUnmatchedOptionsArePositionalParams(boolean) */
            public ParserSpec unmatchedOptionsArePositionalParams(
                    boolean unmatchedOptionsArePositionalParams) {
                this.unmatchedOptionsArePositionalParams = unmatchedOptionsArePositionalParams;
                return this;
            }

            /**
             * Sets whether exceptions during parsing should be collected instead of thrown.
             * Multiple errors may be encountered during parsing. These can be obtained from
             * {@link ParseResult#errors()}.
             * 
             * @since 3.2
             */
            public ParserSpec collectErrors(boolean collectErrors) {
                this.collectErrors = collectErrors;
                return this;
            }

            /**
             * Returns true if options with attached arguments should not consume subsequent
             * arguments and should not validate arity.
             */
            public ParserSpec aritySatisfiedByAttachedOptionParam(boolean newValue) {
                aritySatisfiedByAttachedOptionParam = newValue;
                return this;
            }

            /**
             * Sets whether arguments should be {@linkplain ArgSpec#splitRegex() split} first before
             * any further processing. If true, the original argument will only be split into as
             * many parts as allowed by max arity.
             */
            public ParserSpec limitSplit(boolean limitSplit) {
                this.limitSplit = limitSplit;
                return this;
            }

            void initSeparator(String value) {
                if (initializable(separator, value, DEFAULT_SEPARATOR)) {
                    separator = value;
                }
            }

            void updateSeparator(String value) {
                if (isNonDefault(value, DEFAULT_SEPARATOR)) {
                    separator = value;
                }
            }

            public String toString() {
                return String.format(
                        "posixClusteredShortOptionsAllowed=%s, stopAtPositional=%s, stopAtUnmatched=%s, "
                                + "separator=%s, overwrittenOptionsAllowed=%s, unmatchedArgumentsAllowed=%s, expandAtFiles=%s, "
                                + "atFileCommentChar=%s, endOfOptionsDelimiter=%s, limitSplit=%s, aritySatisfiedByAttachedOptionParam=%s, "
                                + "toggleBooleanFlags=%s, unmatchedOptionsArePositionalParams=%s, collectErrors=%s,"
                                + "caseInsensitiveEnumValuesAllowed=%s, trimQuotes=%s, splitQuotedStrings=%s",
                        posixClusteredShortOptionsAllowed, stopAtPositional, stopAtUnmatched,
                        separator, overwrittenOptionsAllowed, unmatchedArgumentsAllowed,
                        expandAtFiles, atFileCommentChar, endOfOptionsDelimiter, limitSplit,
                        aritySatisfiedByAttachedOptionParam, toggleBooleanFlags,
                        unmatchedOptionsArePositionalParams, collectErrors,
                        caseInsensitiveEnumValuesAllowed, trimQuotes, splitQuotedStrings);
            }

            void initFrom(ParserSpec settings) {
                separator = settings.separator;
                stopAtUnmatched = settings.stopAtUnmatched;
                stopAtPositional = settings.stopAtPositional;
                endOfOptionsDelimiter = settings.endOfOptionsDelimiter;
                toggleBooleanFlags = settings.toggleBooleanFlags;
                overwrittenOptionsAllowed = settings.overwrittenOptionsAllowed;
                unmatchedArgumentsAllowed = settings.unmatchedArgumentsAllowed;
                expandAtFiles = settings.expandAtFiles;
                atFileCommentChar = settings.atFileCommentChar;
                posixClusteredShortOptionsAllowed = settings.posixClusteredShortOptionsAllowed;
                unmatchedOptionsArePositionalParams = settings.unmatchedOptionsArePositionalParams;
                limitSplit = settings.limitSplit;
                aritySatisfiedByAttachedOptionParam = settings.aritySatisfiedByAttachedOptionParam;
                collectErrors = settings.collectErrors;
                caseInsensitiveEnumValuesAllowed = settings.caseInsensitiveEnumValuesAllowed;
                trimQuotes = settings.trimQuotes;
                splitQuotedStrings = settings.splitQuotedStrings;
            }
        }

        /**
         * Models the shared attributes of {@link OptionSpec} and {@link PositionalParamSpec}.
         * 
         * @since 3.0
         */
        public abstract static class ArgSpec {
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
            private <T extends Builder<T>> ArgSpec(Builder<T> builder) {
                description = builder.description == null ? new String[0] : builder.description;
                descriptionKey = builder.descriptionKey;
                splitRegex = builder.splitRegex == null ? "" : builder.splitRegex;
                paramLabel = StringUtils.isBlank(builder.paramLabel) ? "PARAM" : builder.paramLabel;
                hideParamSyntax = builder.hideParamSyntax;
                converters = builder.converters == null ? new ITypeConverter<?>[0]
                        : builder.converters;
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
                        tempArity = (builder.type == null || isBoolean(builder.type))
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
             * Returns whether this option will prompt the user to enter a value on the command
             * line.
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
             * Returns the description key of this arg spec, used to get the description from a
             * resource bundle.
             * 
             * @see Option#descriptionKey()
             * @see Parameters#descriptionKey()
             * @since 3.6
             */
            public String descriptionKey() {
                return descriptionKey;
            }

            /**
             * Returns the description of this option, after variables are rendered. Used when
             * generating the usage documentation.
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
                            desc[i].replace(DESCRIPTION_VARIABLE_DEFAULT_VALUE, defaultValueString)
                                    .replace(DESCRIPTION_VARIABLE_COMPLETION_CANDIDATES,
                                            candidates.toString()));
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
             * Returns the name of the option or positional parameter used in the usage help
             * message.
             * 
             * @see Option#paramLabel() {@link Parameters#paramLabel()}
             */
            public String paramLabel() {
                return paramLabel;
            }

            /**
             * Returns whether usage syntax decorations around the {@linkplain #paramLabel()
             * paramLabel} should be suppressed. The default is {@code false}: by default, the
             * paramLabel is surrounded with {@code '['} and {@code ']'} characters if the value is
             * optional and followed by ellipses ("...") when multiple values can be specified.
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
             * Returns one or more {@link CommandLine.ITypeConverter type converters} to use to
             * convert the command line argument into a strongly typed value (or key-value pair for
             * map fields). This is useful when a particular option or positional parameter should
             * use a custom conversion that is different from the normal conversion for the arg
             * spec's type.
             * 
             * @see Option#converter()
             */
            public ITypeConverter<?>[] converters() {
                return converters.clone();
            }

            /**
             * Returns a regular expression to split option parameter values or {@code ""} if the
             * value should not be split.
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
             * Returns the default value of this option or positional parameter, before splitting
             * and type conversion. This method returns the programmatically set value; this may
             * differ from the default value that is actually used: if this ArgSpec is part of a
             * CommandSpec with a {@link IDefaultValueProvider}, picocli will first try to obtain
             * the default value from the default value provider, and this method is only called if
             * the default provider is {@code null} or returned a {@code null} value.
             * 
             * @return the programmatically set default value of this option/positional parameter,
             *         returning {@code null} means this option or positional parameter does not
             *         have a default
             * @see CommandSpec#defaultValueProvider()
             */
            public String defaultValue() {
                return defaultValue;
            }

            /**
             * Returns the initial value this option or positional parameter. If
             * {@link #hasInitialValue()} is true, the option will be reset to the initial value
             * before parsing (regardless of whether a default value exists), to clear values that
             * would otherwise remain from parsing previous input.
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
             * Returns whether this option or positional parameter's default value should be shown
             * in the usage help.
             */
            public Help.Visibility showDefaultValue() {
                return showDefaultValue;
            }

            /**
             * Returns the default value String displayed in the description. If this ArgSpec is
             * part of a CommandSpec with a {@link IDefaultValueProvider}, this method will first
             * try to obtain the default value from the default value provider; if the provider is
             * {@code null} or if it returns a {@code null} value, then next any value set to
             * {@link ArgSpec#defaultValue()} is returned, and if this is also {@code null}, finally
             * the {@linkplain ArgSpec#initialValue() initial value} is returned.
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
             * Returns the explicitly set completion candidates for this option or positional
             * parameter, valid enum constant names, or {@code null} if this option or positional
             * parameter does not have any completion candidates and its type is not an enum.
             * 
             * @return the completion candidates for this option or positional parameter, valid enum
             *         constant names, or {@code null}
             * @since 3.2
             */
            public Iterable<String> completionCandidates() {
                return completionCandidates;
            }

            /**
             * Returns the {@link IGetter} that is responsible for supplying the value of this
             * argument.
             */
            public IGetter getter() {
                return getter;
            }

            /**
             * Returns the {@link ISetter} that is responsible for modifying the value of this
             * argument.
             */
            public ISetter setter() {
                return setter;
            }

            /**
             * Returns the current value of this argument. Delegates to the current
             * {@link #getter()}.
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
             * Sets the value of this argument to the specified value and returns the previous
             * value. Delegates to the current {@link #setter()}.
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
             * Sets the value of this argument to the specified value and returns the previous
             * value. Delegates to the current {@link #setter()}.
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
             * Returns {@code true} if this argument's {@link #type()} is an array, a
             * {@code Collection} or a {@code Map}, {@code false} otherwise.
             */
            public boolean isMultiValue() {
                return CommandLine.isMultiValue(type());
            }

            /** Returns {@code true} if this argument is a named option, {@code false} otherwise. */
            public abstract boolean isOption();

            /**
             * Returns {@code true} if this argument is a positional parameter, {@code false}
             * otherwise.
             */
            public abstract boolean isPositional();

            /**
             * Returns the untyped command line arguments matched by this option or positional
             * parameter spec.
             * 
             * @return the matched arguments after {@linkplain #splitRegex() splitting}, but before
             *         type conversion. For map properties, {@code "key=value"} values are split
             *         into the key and the value part.
             */
            public List<String> stringValues() {
                return Collections.unmodifiableList(stringValues);
            }

            /**
             * Returns the typed command line arguments matched by this option or positional
             * parameter spec.
             * 
             * @return the matched arguments after {@linkplain #splitRegex() splitting} and type
             *         conversion. For map properties, {@code "key=value"} values are split into the
             *         key and the value part.
             */
            public List<Object> typedValues() {
                return Collections.unmodifiableList(typedValues);
            }

            /** Sets the {@code stringValues} to a new list instance. */
            protected void resetStringValues() {
                stringValues = new ArrayList<String>();
            }

            /**
             * Returns the original command line arguments matched by this option or positional
             * parameter spec.
             * 
             * @return the matched arguments as found on the command line: empty Strings for options
             *         without value, the values have not been {@linkplain #splitRegex() split}, and
             *         for map properties values may look like {@code "key=value"}
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
             *            whether the command's UsageMessageSpec is configured to show default
             *            values.
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
                return usageHelpShowDefaults && !isBoolean(type());
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
            private String[] splitRespectingQuotedStrings(String value, int limit,
                    ParserSpec parser) {
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
                    new Tracer().warn("Unbalanced quotes in [%s] for %s (value=%s)%n", temp, this,
                            value);
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

            private String restoreQuotedValues(String part, Queue<String> quotedValues,
                    ParserSpec parser) {
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
                        && Assert.equals(this.type, other.type)
                        && Assert.equals(this.arity, other.arity)
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
                 * Returns whether this option prompts the user to enter a value on the command
                 * line.
                 * 
                 * @see Option#interactive()
                 */
                public boolean interactive() {
                    return interactive;
                }

                /**
                 * Returns the description of this option, used when generating the usage
                 * documentation.
                 * 
                 * @see Option#description()
                 */
                public String[] description() {
                    return description;
                }

                /**
                 * Returns the description key of this arg spec, used to get the description from a
                 * resource bundle.
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
                 * Returns the name of the option or positional parameter used in the usage help
                 * message.
                 * 
                 * @see Option#paramLabel() {@link Parameters#paramLabel()}
                 */
                public String paramLabel() {
                    return paramLabel;
                }

                /**
                 * Returns whether usage syntax decorations around the {@linkplain #paramLabel()
                 * paramLabel} should be suppressed. The default is {@code false}: by default, the
                 * paramLabel is surrounded with {@code '['} and {@code ']'} characters if the value
                 * is optional and followed by ellipses ("...") when multiple values can be
                 * specified.
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
                 * Returns one or more {@link CommandLine.ITypeConverter type converters} to use to
                 * convert the command line argument into a strongly typed value (or key-value pair
                 * for map fields). This is useful when a particular option or positional parameter
                 * should use a custom conversion that is different from the normal conversion for
                 * the arg spec's type.
                 * 
                 * @see Option#converter()
                 */
                public ITypeConverter<?>[] converters() {
                    return converters;
                }

                /**
                 * Returns a regular expression to split option parameter values or {@code ""} if
                 * the value should not be split.
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
                 * Returns the default value of this option or positional parameter, before
                 * splitting and type conversion. A value of {@code null} means this option or
                 * positional parameter does not have a default.
                 */
                public String defaultValue() {
                    return defaultValue;
                }

                /**
                 * Returns the initial value this option or positional parameter. If
                 * {@link #hasInitialValue()} is true, the option will be reset to the initial value
                 * before parsing (regardless of whether a default value exists), to clear values
                 * that would otherwise remain from parsing previous input.
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
                 * Returns whether this option or positional parameter's default value should be
                 * shown in the usage help.
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
                 * Returns the {@link IGetter} that is responsible for supplying the value of this
                 * argument.
                 */
                public IGetter getter() {
                    return getter;
                }

                /**
                 * Returns the {@link ISetter} that is responsible for modifying the value of this
                 * argument.
                 */
                public ISetter setter() {
                    return setter;
                }

                public String toString() {
                    return toString;
                }

                /**
                 * Sets whether this is a required option or positional parameter, and returns this
                 * builder.
                 */
                public T required(boolean required) {
                    this.required = required;
                    return self();
                }

                /**
                 * Sets whether this option prompts the user to enter a value on the command line,
                 * and returns this builder.
                 */
                public T interactive(boolean interactive) {
                    this.interactive = interactive;
                    return self();
                }

                /**
                 * Sets the description of this option, used when generating the usage
                 * documentation, and returns this builder.
                 * 
                 * @see Option#description()
                 */
                public T description(String... description) {
                    this.description = Assert.notNull(description, "description").clone();
                    return self();
                }

                /**
                 * Sets the description key that is used to look up the description in a resource
                 * bundle, and returns this builder.
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
                 * Sets how many arguments this option or positional parameter requires, and returns
                 * this builder.
                 */
                public T arity(String range) {
                    return arity(Range.valueOf(range));
                }

                /**
                 * Sets how many arguments this option or positional parameter requires, and returns
                 * this builder.
                 */
                public T arity(Range arity) {
                    this.arity = Assert.notNull(arity, "arity");
                    return self();
                }

                /**
                 * Sets the name of the option or positional parameter used in the usage help
                 * message, and returns this builder.
                 */
                public T paramLabel(String paramLabel) {
                    this.paramLabel = Assert.notNull(paramLabel, "paramLabel");
                    return self();
                }

                /**
                 * Sets whether usage syntax decorations around the {@linkplain #paramLabel()
                 * paramLabel} should be suppressed. The default is {@code false}: by default, the
                 * paramLabel is surrounded with {@code '['} and {@code ']'} characters if the value
                 * is optional and followed by ellipses ("...") when multiple values can be
                 * specified.
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
                 *            the element type(s) when the {@link #type()} is a generic
                 *            {@code Collection} or a {@code Map}; or the concrete type when the
                 *            {@link #type()} is an abstract class.
                 */
                public T auxiliaryTypes(Class<?>... types) {
                    this.auxiliaryTypes = Assert.notNull(types, "types").clone();
                    return self();
                }

                /**
                 * Sets option/positional param-specific converter (or converters for Maps), and
                 * returns this builder.
                 */
                public T converters(ITypeConverter<?>... cs) {
                    this.converters = Assert.notNull(cs, "type converters").clone();
                    return self();
                }

                /**
                 * Sets a regular expression to split option parameter values or {@code ""} if the
                 * value should not be split, and returns this builder.
                 */
                public T splitRegex(String splitRegex) {
                    this.splitRegex = Assert.notNull(splitRegex, "splitRegex");
                    return self();
                }

                /**
                 * Sets whether this option or positional parameter's default value should be shown
                 * in the usage help, and returns this builder.
                 */
                public T showDefaultValue(Help.Visibility visibility) {
                    showDefaultValue = Assert.notNull(visibility, "visibility");
                    return self();
                }

                /**
                 * Sets the completion candidates for this option or positional parameter, and
                 * returns this builder.
                 * 
                 * @since 3.2
                 */
                public T completionCandidates(Iterable<String> completionCandidates) {
                    this.completionCandidates = Assert.notNull(completionCandidates,
                            "completionCandidates");
                    return self();
                }

                /**
                 * Sets whether this option should be excluded from the usage message, and returns
                 * this builder.
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
                 *            the type of this option or parameter. For multi-value options and
                 *            positional parameters this can be an array, or a (sub-type of)
                 *            Collection or Map.
                 */
                public T type(Class<?> propertyType) {
                    this.type = Assert.notNull(propertyType, "type");
                    return self();
                }

                /**
                 * Sets the default value of this option or positional parameter to the specified
                 * value, and returns this builder. Before parsing the command line, the result of
                 * {@linkplain #splitRegex() splitting} and {@linkplain #converters() type
                 * converting} this default value is applied to the option or positional parameter.
                 * A value of {@code null} or {@code "__no_default_value__"} means no default.
                 */
                public T defaultValue(String defaultValue) {
                    this.defaultValue = NO_DEFAULT_VALUE.equals(defaultValue) ? null : defaultValue;
                    return self();
                }

                /**
                 * Sets the initial value of this option or positional parameter to the specified
                 * value, and returns this builder. If {@link #hasInitialValue()} is true, the
                 * option will be reset to the initial value before parsing (regardless of whether a
                 * default value exists), to clear values that would otherwise remain from parsing
                 * previous input.
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
                 * Sets the {@link IGetter} that is responsible for getting the value of this
                 * argument, and returns this builder.
                 */
                public T getter(IGetter getter) {
                    this.getter = getter;
                    return self();
                }

                /**
                 * Sets the {@link ISetter} that is responsible for modifying the value of this
                 * argument, and returns this builder.
                 */
                public T setter(ISetter setter) {
                    this.setter = setter;
                    return self();
                }

                /**
                 * Sets the string respresentation of this option or positional parameter to the
                 * specified value, and returns this builder.
                 */
                public T withToString(String toString) {
                    this.toString = toString;
                    return self();
                }
            }
        }

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
        public static class OptionSpec extends ArgSpec {
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
        public static class PositionalParamSpec extends ArgSpec {
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

        /**
         * This class allows applications to specify a custom binding that will be invoked for
         * unmatched arguments. A binding can be created with a {@code ISetter} that consumes the
         * unmatched arguments {@code String[]}, or with a {@code IGetter} that produces a
         * {@code Collection<String>} that the unmatched arguments can be added to.
         * 
         * @since 3.0
         */
        public static class UnmatchedArgsBinding {
            private final IGetter getter;
            private final ISetter setter;

            /**
             * Creates a {@code UnmatchedArgsBinding} for a setter that consumes {@code String[]}
             * objects.
             * 
             * @param setter
             *            consumes the String[] array with unmatched arguments.
             */
            public static UnmatchedArgsBinding forStringArrayConsumer(ISetter setter) {
                return new UnmatchedArgsBinding(null, setter);
            }

            /**
             * Creates a {@code UnmatchedArgsBinding} for a getter that produces a
             * {@code Collection<String>} that the unmatched arguments can be added to.
             * 
             * @param getter
             *            supplies a {@code Collection<String>} that the unmatched arguments can be
             *            added to.
             */
            public static UnmatchedArgsBinding forStringCollectionSupplier(IGetter getter) {
                return new UnmatchedArgsBinding(getter, null);
            }

            private UnmatchedArgsBinding(IGetter getter, ISetter setter) {
                if (getter == null && setter == null) {
                    throw new IllegalArgumentException("Getter and setter cannot both be null");
                }
                this.setter = setter;
                this.getter = getter;
            }

            /**
             * Returns the getter responsible for producing a {@code Collection} that the unmatched
             * arguments can be added to.
             */
            public IGetter getter() {
                return getter;
            }

            /** Returns the setter responsible for consuming the unmatched arguments. */
            public ISetter setter() {
                return setter;
            }

            void addAll(String[] unmatched) {
                if (setter != null) {
                    try {
                        setter.set(unmatched);
                    } catch (Exception ex) {
                        throw new PicocliException(String.format(
                                "Could not invoke setter (%s) with unmatched argument array '%s': %s",
                                setter, Arrays.toString(unmatched), ex), ex);
                    }
                }
                if (getter != null) {
                    try {
                        Collection<String> collection = getter.get();
                        Assert.notNull(collection, "getter returned null Collection");
                        collection.addAll(Arrays.asList(unmatched));
                    } catch (Exception ex) {
                        throw new PicocliException(String.format(
                                "Could not add unmatched argument array '%s' to collection returned by getter (%s): %s",
                                Arrays.toString(unmatched), getter, ex), ex);
                    }
                }
            }
        }

        /** mock java.lang.reflect.Parameter (not available before Java 8) */
        static class MethodParam extends AccessibleObject {
            final Method method;
            final int paramIndex;
            final String name;
            int position;

            public MethodParam(Method method, int paramIndex) {
                this.method = method;
                this.paramIndex = paramIndex;
                String tmp = "arg" + paramIndex;
                try {
                    Method getParameters = Method.class.getMethod("getParameters");
                    Object parameters = getParameters.invoke(method);
                    Object parameter = Array.get(parameters, paramIndex);
                    tmp = (String) Class.forName("java.lang.reflect.Parameter")
                            .getDeclaredMethod("getName").invoke(parameter);
                } catch (Exception ignored) {
                }
                this.name = tmp;
            }

            public Type getParameterizedType() {
                return method.getGenericParameterTypes()[paramIndex];
            }

            public String getName() {
                return name;
            }

            public Class<?> getType() {
                return method.getParameterTypes()[paramIndex];
            }

            public Method getDeclaringExecutable() {
                return method;
            }

            @Override
            public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
                for (Annotation annotation : getDeclaredAnnotations()) {
                    if (annotationClass.isAssignableFrom(annotation.getClass())) {
                        return annotationClass.cast(annotation);
                    }
                }
                return null;
            }

            @Override
            public Annotation[] getDeclaredAnnotations() {
                return method.getParameterAnnotations()[paramIndex];
            }

            @Override
            public void setAccessible(boolean flag) throws SecurityException {
                method.setAccessible(flag);
            }

            @Override
            public boolean isAccessible() throws SecurityException {
                return method.isAccessible();
            }

            @Override
            public String toString() {
                return method.toString() + ":" + getName();
            }
        }

        static class TypedMember {
            final AccessibleObject accessible;
            final String name;
            final Class<?> type;
            final Type genericType;
            final boolean hasInitialValue;
            private IGetter getter;
            private ISetter setter;

            TypedMember(Field field) {
                accessible = Assert.notNull(field, "field");
                accessible.setAccessible(true);
                name = field.getName();
                type = field.getType();
                genericType = field.getGenericType();
                hasInitialValue = true;
            }

            static TypedMember createIfAnnotated(Field field, Object scope) {
                return isAnnotated(field) ? new TypedMember(field, scope) : null;
            }

            private TypedMember(Field field, Object scope) {
                this(field);
                if (Proxy.isProxyClass(scope.getClass())) {
                    throw new InitializationException(
                            "Invalid picocli annotation on interface field");
                }
                FieldBinding binding = new FieldBinding(scope, field);
                getter = binding;
                setter = binding;
            }

            static TypedMember createIfAnnotated(Method method, Object scope) {
                return isAnnotated(method) ? new TypedMember(method, scope) : null;
            }

            private TypedMember(Method method, Object scope) {
                accessible = Assert.notNull(method, "method");
                accessible.setAccessible(true);
                name = propertyName(method.getName());
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean isGetter = parameterTypes.length == 0 && method.getReturnType() != Void.TYPE
                        && method.getReturnType() != Void.class;
                boolean isSetter = parameterTypes.length > 0;
                if (isSetter == isGetter) {
                    throw new InitializationException(
                            "Invalid method, must be either getter or setter: " + method);
                }
                if (isGetter) {
                    hasInitialValue = true;
                    type = method.getReturnType();
                    genericType = method.getGenericReturnType();
                    if (Proxy.isProxyClass(scope.getClass())) {
                        PicocliInvocationHandler handler = (PicocliInvocationHandler) Proxy
                                .getInvocationHandler(scope);
                        PicocliInvocationHandler.ProxyBinding binding = handler.new ProxyBinding(
                                method);
                        getter = binding;
                        setter = binding;
                        initializeInitialValue(method);
                    } else {
                        //throw new IllegalArgumentException("Getter method but not a proxy: " + scope + ": " + method);
                        MethodBinding binding = new MethodBinding(scope, method);
                        getter = binding;
                        setter = binding;
                    }
                } else {
                    hasInitialValue = false;
                    type = parameterTypes[0];
                    genericType = method.getGenericParameterTypes()[0];
                    MethodBinding binding = new MethodBinding(scope, method);
                    getter = binding;
                    setter = binding;
                }
            }

            private TypedMember(MethodParam param, Object scope) {
                accessible = Assert.notNull(param, "command method parameter");
                accessible.setAccessible(true);
                name = param.getName();
                type = param.getType();
                genericType = param.getParameterizedType();

                // bind parameter
                ObjectBinding binding = new ObjectBinding();
                getter = binding;
                setter = binding;
                hasInitialValue = initializeInitialValue(param);
            }

            private boolean initializeInitialValue(Object arg) {
                boolean initialized = true;
                try {
                    if (type == Boolean.TYPE || type == Boolean.class) {
                        setter.set(false);
                    } else if (type == Byte.TYPE || type == Byte.class) {
                        setter.set(Byte.valueOf((byte) 0));
                    } else if (type == Short.TYPE || type == Short.class) {
                        setter.set(Short.valueOf((short) 0));
                    } else if (type == Integer.TYPE || type == Integer.class) {
                        setter.set(Integer.valueOf(0));
                    } else if (type == Long.TYPE || type == Long.class) {
                        setter.set(Long.valueOf(0L));
                    } else if (type == Float.TYPE || type == Float.class) {
                        setter.set(Float.valueOf(0f));
                    } else if (type == Double.TYPE || type == Double.class) {
                        setter.set(Double.valueOf(0d));
                    } else {
                        initialized = false;
                    }
                } catch (Exception ex) {
                    throw new InitializationException(
                            "Could not set initial value for " + arg + ": " + ex.toString(), ex);
                }
                return initialized;
            }

            static boolean isAnnotated(AnnotatedElement e) {
                return false || e.isAnnotationPresent(Option.class)
                        || e.isAnnotationPresent(Parameters.class)
                        || e.isAnnotationPresent(Unmatched.class)
                        || e.isAnnotationPresent(Mixin.class) || e.isAnnotationPresent(Spec.class)
                        || e.isAnnotationPresent(ParentCommand.class);
            }

            boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
                return accessible.isAnnotationPresent(annotationClass);
            }

            <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
                return accessible.getAnnotation(annotationClass);
            }

            String name() {
                return name;
            }

            boolean isArgSpec() {
                return isOption() || isParameter() || (isMethodParameter() && !isMixin());
            }

            boolean isOption() {
                return isAnnotationPresent(Option.class);
            }

            boolean isParameter() {
                return isAnnotationPresent(Parameters.class);
            }

            boolean isMixin() {
                return isAnnotationPresent(Mixin.class);
            }

            boolean isUnmatched() {
                return isAnnotationPresent(Unmatched.class);
            }

            boolean isInjectSpec() {
                return isAnnotationPresent(Spec.class);
            }

            boolean isMultiValue() {
                return CommandLine.isMultiValue(getType());
            }

            IGetter getter() {
                return getter;
            }

            ISetter setter() {
                return setter;
            }

            Class<?> getType() {
                return type;
            }

            Type getGenericType() {
                return genericType;
            }

            public String toString() {
                return accessible.toString();
            }

            String toGenericString() {
                return accessible instanceof Field ? ((Field) accessible).toGenericString()
                        : accessible instanceof Method ? ((Method) accessible).toGenericString()
                                : ((MethodParam) accessible).toString();
            }

            boolean isMethodParameter() {
                return accessible instanceof MethodParam;
            }

            String mixinName() {
                String annotationName = getAnnotation(Mixin.class).name();
                return StringUtils.isBlank(annotationName) ? name() : annotationName;
            }

            static String propertyName(String methodName) {
                if (methodName.length() > 3
                        && (methodName.startsWith("get") || methodName.startsWith("set"))) {
                    return decapitalize(methodName.substring(3));
                }
                return decapitalize(methodName);
            }

            private static String decapitalize(String name) {
                if (name == null || name.length() == 0) {
                    return name;
                }
                char[] chars = name.toCharArray();
                chars[0] = Character.toLowerCase(chars[0]);
                return new String(chars);
            }
        }

        /**
         * Utility class for getting resource bundle strings. Enhances the standard <a href=
         * "https://docs.oracle.com/javase/8/docs/api/java/util/ResourceBundle.html">ResourceBundle</a>
         * with support for String arrays and qualified keys: keys that may or may not be prefixed
         * with the fully qualified command name.
         * <p>
         * Example properties resource bundle:
         * </p>
         * 
         * <pre>
         * # Usage Help Message Sections
         * # ---------------------------
         * # Numbered resource keys can be used to create multi-line sections.
         * usage.headerHeading = This is my app. There are other apps like it but this one is mine.%n
         * usage.header   = header first line
         * usage.header.0 = header second line
         * usage.descriptionHeading = Description:%n
         * usage.description.0 = first line
         * usage.description.1 = second line
         * usage.description.2 = third line
         * usage.synopsisHeading = Usage:&#92;u0020
         * # Leading whitespace is removed by default. Start with &#92;u0020 to keep the leading whitespace.
         * usage.customSynopsis.0 =      Usage: ln [OPTION]... [-T] TARGET LINK_NAME   (1st form)
         * usage.customSynopsis.1 = &#92;u0020 or:  ln [OPTION]... TARGET                  (2nd form)
         * usage.customSynopsis.2 = &#92;u0020 or:  ln [OPTION]... TARGET... DIRECTORY     (3rd form)
         * # Headings can contain the %n character to create multi-line values.
         * usage.parameterListHeading = %nPositional parameters:%n
         * usage.optionListHeading = %nOptions:%n
         * usage.commandListHeading = %nCommands:%n
         * usage.footerHeading = Powered by picocli%n
         * usage.footer = footer
         *
         * # Option Descriptions
         * # -------------------
         * # Use numbered keys to create multi-line descriptions.
         * help = Show this help message and exit.
         * version = Print version information and exit.
         * </pre>
         * <p>
         * Resources for multiple commands can be specified in a single ResourceBundle. Keys and
         * their value can be shared by multiple commands (so you don't need to repeat them for
         * every command), but keys can be prefixed with {@code fully qualified command name + "."}
         * to specify different values for different commands. The most specific key wins. For
         * example:
         * </p>
         * 
         * <pre>
         * jfrog.rt.usage.header = Artifactory commands
         * jfrog.rt.config.usage.header = Configure Artifactory details.
         * jfrog.rt.upload.usage.header = Upload files.
         *
         * jfrog.bt.usage.header = Bintray commands
         * jfrog.bt.config.usage.header = Configure Bintray details.
         * jfrog.bt.upload.usage.header = Upload files.
         *
         * # shared between all commands
         * usage.footerHeading = Environment Variables:
         * usage.footer.0 = footer line 0
         * usage.footer.1 = footer line 1
         * </pre>
         * 
         * @see Command#resourceBundle()
         * @see Option#descriptionKey()
         * @see OptionSpec#description()
         * @see PositionalParamSpec#description()
         * @see CommandSpec#qualifiedName(String)
         * @since 3.6
         */
        public static class Messages {
            private final CommandSpec spec;
            private final ResourceBundle rb;
            private final Set<String> keys;

            public Messages(CommandSpec spec, ResourceBundle rb) {
                this.spec = Assert.notNull(spec, "CommandSpec");
                this.rb = rb;
                this.keys = keys(rb);
            }

            private static Set<String> keys(ResourceBundle rb) {
                if (rb == null) {
                    return Collections.emptySet();
                }
                Set<String> keys = new LinkedHashSet<String>();
                for (Enumeration<String> k = rb.getKeys(); k.hasMoreElements(); keys
                        .add(k.nextElement()))
                    ;
                return keys;
            }

            /**
             * Returns a copy of the specified Messages object with the CommandSpec replaced by the
             * specified one.
             * 
             * @param spec
             *            the CommandSpec of the returned Messages
             * @param original
             *            the Messages object whose ResourceBundle to reference
             * @return a Messages object with the specified CommandSpec and the ResourceBundle of
             *         the specified Messages object
             */
            public static Messages copy(CommandSpec spec, Messages original) {
                return original == null ? null : new Messages(spec, original.rb);
            }

            /**
             * Returns {@code true} if the specified {@code Messages} is {@code null} or has a
             * {@code null ResourceBundle}.
             */
            public static boolean empty(Messages messages) {
                return messages == null || messages.rb == null;
            }

            /**
             * Returns the String value found in the resource bundle for the specified key, or the
             * specified default value if not found.
             * 
             * @param key
             *            unqualified resource bundle key. This method will first try to find a
             *            value by qualifying the key with the command's fully qualified name, and
             *            if not found, it will try with the unqualified key.
             * @param defaultValue
             *            value to return if the resource bundle is null or empty, or if no value
             *            was found by the qualified or unqualified key
             * @return the String value found in the resource bundle for the specified key, or the
             *         specified default value
             */
            public String getString(String key, String defaultValue) {
                if (rb == null || keys.isEmpty()) {
                    return defaultValue;
                }
                String cmd = spec.qualifiedName(".");
                if (keys.contains(cmd + "." + key)) {
                    return rb.getString(cmd + "." + key);
                }
                if (keys.contains(key)) {
                    return rb.getString(key);
                }
                return defaultValue;
            }

            /**
             * Returns the String array value found in the resource bundle for the specified key, or
             * the specified default value if not found. Multi-line strings can be specified in the
             * resource bundle with {@code key.0}, {@code key.1}, {@code key.2}, etc.
             * 
             * @param key
             *            unqualified resource bundle key. This method will first try to find a
             *            value by qualifying the key with the command's fully qualified name, and
             *            if not found, it will try with the unqualified key.
             * @param defaultValues
             *            value to return if the resource bundle is null or empty, or if no value
             *            was found by the qualified or unqualified key
             * @return the String array value found in the resource bundle for the specified key, or
             *         the specified default value
             */
            public String[] getStringArray(String key, String[] defaultValues) {
                if (rb == null || keys.isEmpty()) {
                    return defaultValues;
                }
                String cmd = spec.qualifiedName(".");
                List<String> result = addAllWithPrefix(rb, cmd + "." + key, keys,
                        new ArrayList<String>());
                if (!result.isEmpty()) {
                    return result.toArray(new String[0]);
                }
                addAllWithPrefix(rb, key, keys, result);
                return result.isEmpty() ? defaultValues : result.toArray(new String[0]);
            }

            private static List<String> addAllWithPrefix(ResourceBundle rb, String key,
                    Set<String> keys, List<String> result) {
                if (keys.contains(key)) {
                    result.add(rb.getString(key));
                }
                for (int i = 0; true; i++) {
                    String elementKey = key + "." + i;
                    if (keys.contains(elementKey)) {
                        result.add(rb.getString(elementKey));
                    } else {
                        return result;
                    }
                }
            }

            /**
             * Returns the ResourceBundle of the specified Messages object or {@code null} if the
             * specified Messages object is {@code null}.
             */
            public static ResourceBundle resourceBundle(Messages messages) {
                return messages == null ? null : messages.resourceBundle();
            }

            /** Returns the ResourceBundle of this object or {@code null}. */
            public ResourceBundle resourceBundle() {
                return rb;
            }

            /** Returns the CommandSpec of this object, never {@code null}. */
            public CommandSpec commandSpec() {
                return spec;
            }
        }

        private static class CommandReflection {
            static CommandSpec extractCommandSpec(Object command, IFactory factory,
                    boolean annotationsAreMandatory) {
                Class<?> cls = command.getClass();
                Tracer t = new Tracer();
                t.debug("Creating CommandSpec for object of class %s with factory %s%n",
                        cls.getName(), factory.getClass().getName());
                if (command instanceof CommandSpec) {
                    return (CommandSpec) command;
                }
                Object instance = command;
                String commandClassName = cls.getName();
                if (command instanceof Class) {
                    cls = (Class) command;
                    commandClassName = cls.getName();
                    try {
                        t.debug("Getting a %s instance from the factory%n", cls.getName());
                        instance = DefaultFactory.create(factory, cls);
                        cls = instance.getClass();
                        commandClassName = cls.getName();
                        t.debug("Factory returned a %s instance%n", commandClassName);
                    } catch (InitializationException ex) {
                        if (cls.isInterface()) {
                            t.debug("%s. Creating Proxy for interface %s%n", ex.getCause(),
                                    cls.getName());
                            instance = Proxy.newProxyInstance(cls.getClassLoader(),
                                    new Class<?>[] { cls }, new PicocliInvocationHandler());
                        } else {
                            throw ex;
                        }
                    }
                } else if (command instanceof Method) {
                    cls = null; // don't mix in options/positional params from outer class @Command
                }

                CommandSpec result = CommandSpec
                        .wrapWithoutInspection(Assert.notNull(instance, "command"));

                Stack<Class<?>> hierarchy = new Stack<Class<?>>();
                while (cls != null) {
                    hierarchy.add(cls);
                    cls = cls.getSuperclass();
                }
                boolean hasCommandAnnotation = false;
                boolean mixinStandardHelpOptions = false;
                while (!hierarchy.isEmpty()) {
                    cls = hierarchy.pop();
                    boolean thisCommandHasAnnotation = updateCommandAttributes(cls, result,
                            factory);
                    hasCommandAnnotation |= thisCommandHasAnnotation;
                    hasCommandAnnotation |= initFromAnnotatedFields(instance, cls, result, factory);
                    if (cls.isAnnotationPresent(Command.class)) {
                        mixinStandardHelpOptions |= cls.getAnnotation(Command.class)
                                .mixinStandardHelpOptions();
                    }
                }
                result.mixinStandardHelpOptions(mixinStandardHelpOptions); //#377 Standard help options should be added last
                if (command instanceof Method) {
                    Method method = (Method) command;
                    t.debug("Using method %s as command %n", method);
                    commandClassName = method.toString();
                    hasCommandAnnotation |= updateCommandAttributes(method, result, factory);
                    result.mixinStandardHelpOptions(
                            method.getAnnotation(Command.class).mixinStandardHelpOptions());
                    initFromMethodParameters(instance, method, result, factory);
                    // set command name to method name, unless @Command#name is set
                    result.initName(((Method) command).getName());
                }
                result.updateArgSpecMessages();

                if (annotationsAreMandatory) {
                    validateCommandSpec(result, hasCommandAnnotation, commandClassName);
                }
                result.withToString(commandClassName).validate();
                return result;
            }

            private static boolean updateCommandAttributes(Class<?> cls, CommandSpec commandSpec,
                    IFactory factory) {
                // superclass values should not overwrite values if both class and superclass have a @Command annotation
                if (!cls.isAnnotationPresent(Command.class)) {
                    return false;
                }

                Command cmd = cls.getAnnotation(Command.class);
                return updateCommandAttributes(cmd, commandSpec, factory);
            }

            private static boolean updateCommandAttributes(Method method, CommandSpec commandSpec,
                    IFactory factory) {
                Command cmd = method.getAnnotation(Command.class);
                return updateCommandAttributes(cmd, commandSpec, factory);
            }

            private static boolean updateCommandAttributes(Command cmd, CommandSpec commandSpec,
                    IFactory factory) {
                commandSpec.aliases(cmd.aliases());
                commandSpec.parser().updateSeparator(cmd.separator());
                commandSpec.updateName(cmd.name());
                commandSpec.updateVersion(cmd.version());
                commandSpec.updateHelpCommand(cmd.helpCommand());
                commandSpec.updateVersionProvider(cmd.versionProvider(), factory);
                commandSpec.initDefaultValueProvider(cmd.defaultValueProvider(), factory);
                commandSpec.usageMessage().updateFromCommand(cmd, commandSpec);

                initSubcommands(cmd, commandSpec, factory);
                return true;
            }

            private static void initSubcommands(Command cmd, CommandSpec parent, IFactory factory) {
                for (Class<?> sub : cmd.subcommands()) {
                    try {
                        if (Help.class == sub) {
                            throw new InitializationException(Help.class.getName()
                                    + " is not a valid subcommand. Did you mean "
                                    + HelpCommand.class.getName() + "?");
                        }
                        CommandLine subcommandLine = toCommandLine(factory.create(sub), factory);
                        parent.addSubcommand(subcommandName(sub), subcommandLine);
                        initParentCommand(subcommandLine.getCommandSpec().userObject(),
                                parent.userObject());
                    } catch (InitializationException ex) {
                        throw ex;
                    } catch (NoSuchMethodException ex) {
                        throw new InitializationException("Cannot instantiate subcommand "
                                + sub.getName() + ": the class has no constructor", ex);
                    } catch (Exception ex) {
                        throw new InitializationException(
                                "Could not instantiate and add subcommand " + sub.getName() + ": "
                                        + ex,
                                ex);
                    }
                }
                if (cmd.addMethodSubcommands() && !(parent.userObject() instanceof Method)) {
                    parent.addMethodSubcommands(factory);
                }
            }

            static void initParentCommand(Object subcommand, Object parent) {
                if (subcommand == null) {
                    return;
                }
                try {
                    Class<?> cls = subcommand.getClass();
                    while (cls != null) {
                        for (Field f : cls.getDeclaredFields()) {
                            if (f.isAnnotationPresent(ParentCommand.class)) {
                                f.setAccessible(true);
                                f.set(subcommand, parent);
                            }
                        }
                        cls = cls.getSuperclass();
                    }
                } catch (Exception ex) {
                    throw new InitializationException(
                            "Unable to initialize @ParentCommand field: " + ex, ex);
                }
            }

            private static String subcommandName(Class<?> sub) {
                Command subCommand = sub.getAnnotation(Command.class);
                if (subCommand == null || Help.DEFAULT_COMMAND_NAME.equals(subCommand.name())) {
                    throw new InitializationException("Subcommand " + sub.getName()
                            + " is missing the mandatory @Command annotation with a 'name' attribute");
                }
                return subCommand.name();
            }

            private static boolean initFromAnnotatedFields(Object scope, Class<?> cls,
                    CommandSpec receiver, IFactory factory) {
                boolean result = false;
                for (Field field : cls.getDeclaredFields()) {
                    result |= initFromAnnotatedTypedMembers(
                            TypedMember.createIfAnnotated(field, scope), receiver, factory);
                }
                for (Method method : cls.getDeclaredMethods()) {
                    result |= initFromAnnotatedTypedMembers(
                            TypedMember.createIfAnnotated(method, scope), receiver, factory);
                }
                return result;
            }

            private static boolean initFromAnnotatedTypedMembers(TypedMember member,
                    CommandSpec receiver, IFactory factory) {
                boolean result = false;
                if (member == null) {
                    return result;
                }
                if (member.isMixin()) {
                    validateMixin(member);
                    receiver.addMixin(member.mixinName(), buildMixinForField(member, factory));
                    result = true;
                }
                if (member.isUnmatched()) {
                    validateUnmatched(member);
                    receiver.addUnmatchedArgsBinding(buildUnmatchedForField(member));
                }
                if (member.isArgSpec()) {
                    validateArgSpecField(member);
                    Messages msg = receiver.usageMessage.messages();
                    if (member.isOption()) {
                        receiver.addOption(ArgsReflection.extractOptionSpec(member, factory));
                    } else if (member.isParameter()) {
                        receiver.addPositional(
                                ArgsReflection.extractPositionalParamSpec(member, factory));
                    } else {
                        receiver.addPositional(ArgsReflection
                                .extractUnannotatedPositionalParamSpec(member, factory));
                    }
                }
                if (member.isInjectSpec()) {
                    validateInjectSpec(member);
                    try {
                        member.setter().set(receiver);
                    } catch (Exception ex) {
                        throw new InitializationException("Could not inject spec", ex);
                    }
                }
                return result;
            }

            private static boolean initFromMethodParameters(Object scope, Method method,
                    CommandSpec receiver, IFactory factory) {
                boolean result = false;
                int optionCount = 0;
                for (int i = 0, count = method.getParameterTypes().length; i < count; i++) {
                    MethodParam param = new MethodParam(method, i);
                    if (param.isAnnotationPresent(Option.class)
                            || param.isAnnotationPresent(Mixin.class)) {
                        optionCount++;
                    } else {
                        param.position = i - optionCount;
                    }
                    result |= initFromAnnotatedTypedMembers(new TypedMember(param, scope), receiver,
                            factory);
                }
                return result;
            }

            private static void validateMixin(TypedMember member) {
                if (member.isMixin() && member.isArgSpec()) {
                    throw new DuplicateOptionAnnotationsException(
                            "A member cannot be both a @Mixin command and an @Option or @Parameters, but '"
                                    + member + "' is both.");
                }
                if (member.isMixin() && member.isUnmatched()) {
                    throw new DuplicateOptionAnnotationsException(
                            "A member cannot be both a @Mixin command and an @Unmatched but '"
                                    + member + "' is both.");
                }
            }

            private static void validateUnmatched(TypedMember member) {
                if (member.isUnmatched() && member.isArgSpec()) {
                    throw new DuplicateOptionAnnotationsException(
                            "A member cannot have both @Unmatched and @Option or @Parameters annotations, but '"
                                    + member + "' has both.");
                }
            }

            private static void validateArgSpecField(TypedMember member) {
                if (member.isOption() && member.isParameter()) {
                    throw new DuplicateOptionAnnotationsException(
                            "A member can be either @Option or @Parameters, but '" + member
                                    + "' is both.");
                }
                if (member.isMixin() && member.isArgSpec()) {
                    throw new DuplicateOptionAnnotationsException(
                            "A member cannot be both a @Mixin command and an @Option or @Parameters, but '"
                                    + member + "' is both.");
                }
                if (!(member.accessible instanceof Field)) {
                    return;
                }
                Field field = (Field) member.accessible;
                if (Modifier.isFinal(field.getModifiers()) && (field.getType().isPrimitive()
                        || String.class.isAssignableFrom(field.getType()))) {
                    throw new InitializationException(
                            "Constant (final) primitive and String fields like " + field
                                    + " cannot be used as "
                                    + (member.isOption() ? "an @Option" : "a @Parameter")
                                    + ": compile-time constant inlining may hide new values written to it.");
                }
            }

            private static void validateCommandSpec(CommandSpec result,
                    boolean hasCommandAnnotation, String commandClassName) {
                if (!hasCommandAnnotation && result.positionalParameters.isEmpty()
                        && result.optionsByNameMap.isEmpty() && result.unmatchedArgs.isEmpty()) {
                    throw new InitializationException(commandClassName
                            + " is not a command: it has no @Command, @Option, @Parameters or @Unmatched annotations");
                }
            }

            private static void validateInjectSpec(TypedMember member) {
                if (member.isInjectSpec() && (member.isOption() || member.isParameter())) {
                    throw new DuplicateOptionAnnotationsException(
                            "A member cannot have both @Spec and @Option or @Parameters annotations, but '"
                                    + member + "' has both.");
                }
                if (member.isInjectSpec() && member.isUnmatched()) {
                    throw new DuplicateOptionAnnotationsException(
                            "A member cannot have both @Spec and @Unmatched annotations, but '"
                                    + member + "' has both.");
                }
                if (member.isInjectSpec() && member.isMixin()) {
                    throw new DuplicateOptionAnnotationsException(
                            "A member cannot have both @Spec and @Mixin annotations, but '" + member
                                    + "' has both.");
                }
                if (member.getType() != CommandSpec.class) {
                    throw new InitializationException(
                            "@picocli.CommandLine.Spec annotation is only supported on fields of type "
                                    + CommandSpec.class.getName());
                }
            }

            private static CommandSpec buildMixinForField(TypedMember member, IFactory factory) {
                try {
                    Object userObject = member.getter().get();
                    if (userObject == null) {
                        userObject = factory.create(member.getType());
                        member.setter().set(userObject);
                    }
                    CommandSpec result = CommandSpec.forAnnotatedObject(userObject, factory);
                    return result.withToString(
                            abbreviate("mixin from member " + member.toGenericString()));
                } catch (InitializationException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new InitializationException(
                            "Could not access or modify mixin member " + member + ": " + ex, ex);
                }
            }

            private static UnmatchedArgsBinding buildUnmatchedForField(final TypedMember member) {
                if (!(member.getType().equals(String[].class) || (List.class.isAssignableFrom(
                        member.getType()) && member.getGenericType() instanceof ParameterizedType
                        && ((ParameterizedType) member.getGenericType()).getActualTypeArguments()[0]
                                .equals(String.class)))) {
                    throw new InitializationException("Invalid type for " + member
                            + ": must be either String[] or List<String>");
                }
                if (member.getType().equals(String[].class)) {
                    return UnmatchedArgsBinding.forStringArrayConsumer(member.setter());
                } else {
                    return UnmatchedArgsBinding.forStringCollectionSupplier(new IGetter() {
                        @SuppressWarnings("unchecked")
                        public <T> T get() throws Exception {
                            List<String> result = (List<String>) member.getter().get();
                            if (result == null) {
                                result = new ArrayList<String>();
                                member.setter().set(result);
                            }
                            return (T) result;
                        }
                    });
                }
            }
        }

        /**
         * Helper class to reflectively create OptionSpec and PositionalParamSpec objects from
         * annotated elements. Package protected for testing. CONSIDER THIS CLASS PRIVATE.
         */
        static class ArgsReflection {
            static OptionSpec extractOptionSpec(TypedMember member, IFactory factory) {
                Option option = member.getAnnotation(Option.class);
                OptionSpec.Builder builder = OptionSpec.builder(option.names());
                initCommon(builder, member);

                builder.help(option.help());
                builder.usageHelp(option.usageHelp());
                builder.versionHelp(option.versionHelp());
                builder.showDefaultValue(option.showDefaultValue());
                if (!NoCompletionCandidates.class.equals(option.completionCandidates())) {
                    builder.completionCandidates(DefaultFactory.createCompletionCandidates(factory,
                            option.completionCandidates()));
                }

                builder.arity(Range.optionArity(member));
                builder.required(option.required());
                builder.interactive(option.interactive());
                Class<?>[] elementTypes = inferTypes(member.getType(), option.type(),
                        member.getGenericType());
                builder.auxiliaryTypes(elementTypes);
                builder.paramLabel(inferLabel(option.paramLabel(), member.name(), member.getType(),
                        elementTypes));
                builder.hideParamSyntax(option.hideParamSyntax());
                builder.description(option.description());
                builder.descriptionKey(option.descriptionKey());
                builder.splitRegex(option.split());
                builder.hidden(option.hidden());
                builder.defaultValue(option.defaultValue());
                builder.converters(DefaultFactory.createConverter(factory, option.converter()));
                return builder.build();
            }

            static PositionalParamSpec extractPositionalParamSpec(TypedMember member,
                    IFactory factory) {
                PositionalParamSpec.Builder builder = PositionalParamSpec.builder();
                initCommon(builder, member);
                Range arity = Range.parameterArity(member);
                builder.arity(arity);
                builder.index(Range.parameterIndex(member));
                builder.capacity(Range.parameterCapacity(member));
                builder.required(arity.min > 0);

                Parameters parameters = member.getAnnotation(Parameters.class);
                builder.interactive(parameters.interactive());
                Class<?>[] elementTypes = inferTypes(member.getType(), parameters.type(),
                        member.getGenericType());
                builder.auxiliaryTypes(elementTypes);
                builder.paramLabel(inferLabel(parameters.paramLabel(), member.name(),
                        member.getType(), elementTypes));
                builder.hideParamSyntax(parameters.hideParamSyntax());
                builder.description(parameters.description());
                builder.descriptionKey(parameters.descriptionKey());
                builder.splitRegex(parameters.split());
                builder.hidden(parameters.hidden());
                builder.defaultValue(parameters.defaultValue());
                builder.converters(DefaultFactory.createConverter(factory, parameters.converter()));
                builder.showDefaultValue(parameters.showDefaultValue());
                if (!NoCompletionCandidates.class.equals(parameters.completionCandidates())) {
                    builder.completionCandidates(DefaultFactory.createCompletionCandidates(factory,
                            parameters.completionCandidates()));
                }
                return builder.build();
            }

            static PositionalParamSpec extractUnannotatedPositionalParamSpec(TypedMember member,
                    IFactory factory) {
                PositionalParamSpec.Builder builder = PositionalParamSpec.builder();
                initCommon(builder, member);
                Range arity = Range.parameterArity(member);
                builder.arity(arity);
                builder.index(Range.parameterIndex(member));
                builder.capacity(Range.parameterCapacity(member));
                builder.required(arity.min > 0);

                builder.interactive(false);
                Class<?>[] elementTypes = inferTypes(member.getType(), new Class<?>[] {},
                        member.getGenericType());
                builder.auxiliaryTypes(elementTypes);
                builder.paramLabel(inferLabel(null, member.name(), member.getType(), elementTypes));
                builder.hideParamSyntax(false);
                builder.description(new String[0]);
                builder.splitRegex("");
                builder.hidden(false);
                builder.defaultValue(null);
                builder.converters();
                builder.showDefaultValue(Help.Visibility.ON_DEMAND);
                return builder.build();
            }

            private static void initCommon(ArgSpec.Builder<?> builder, TypedMember member) {
                builder.type(member.getType());
                builder.withToString((member.accessible instanceof Field ? "field "
                        : member.accessible instanceof Method ? "method "
                                : member.accessible.getClass().getSimpleName() + " ")
                        + abbreviate(member.toGenericString()));

                builder.getter(member.getter()).setter(member.setter());
                builder.hasInitialValue(member.hasInitialValue);
                try {
                    builder.initialValue(member.getter().get());
                } catch (Exception ex) {
                    builder.initialValue(null);
                }
            }

            static String abbreviate(String text) {
                return text.replace("private ", "").replace("protected ", "").replace("public ", "")
                        .replace("java.lang.", "");
            }

            private static String inferLabel(String label, String fieldName, Class<?> fieldType,
                    Class<?>[] types) {
                if (!StringUtils.isBlank(label)) {
                    return label.trim();
                }
                String name = fieldName;
                if (Map.class.isAssignableFrom(fieldType)) { // #195 better param labels for map fields
                    Class<?>[] paramTypes = types;
                    if (paramTypes.length < 2 || paramTypes[0] == null || paramTypes[1] == null) {
                        name = "String=String";
                    } else {
                        name = paramTypes[0].getSimpleName() + "=" + paramTypes[1].getSimpleName();
                    }
                }
                return "<" + name + ">";
            }

            private static Class<?>[] inferTypes(Class<?> propertyType, Class<?>[] annotationTypes,
                    Type genericType) {
                if (annotationTypes.length > 0) {
                    return annotationTypes;
                }
                if (propertyType.isArray()) {
                    return new Class<?>[] { propertyType.getComponentType() };
                }
                if (CommandLine.isMultiValue(propertyType)) {
                    if (genericType instanceof ParameterizedType) {// e.g. Map<Long, ? extends Number>
                        ParameterizedType parameterizedType = (ParameterizedType) genericType;
                        Type[] paramTypes = parameterizedType.getActualTypeArguments(); // e.g. ? extends Number
                        Class<?>[] result = new Class<?>[paramTypes.length];
                        for (int i = 0; i < paramTypes.length; i++) {
                            if (paramTypes[i] instanceof Class) {
                                result[i] = (Class<?>) paramTypes[i];
                                continue;
                            } // e.g. Long
                            if (paramTypes[i] instanceof WildcardType) { // e.g. ? extends Number
                                WildcardType wildcardType = (WildcardType) paramTypes[i];
                                Type[] lower = wildcardType.getLowerBounds(); // e.g. []
                                if (lower.length > 0 && lower[0] instanceof Class) {
                                    result[i] = (Class<?>) lower[0];
                                    continue;
                                }
                                Type[] upper = wildcardType.getUpperBounds(); // e.g. Number
                                if (upper.length > 0 && upper[0] instanceof Class) {
                                    result[i] = (Class<?>) upper[0];
                                    continue;
                                }
                            }
                            Arrays.fill(result, String.class);
                            return result; // too convoluted generic type, giving up
                        }
                        return result; // we inferred all types from ParameterizedType
                    }
                    return new Class<?>[] { String.class, String.class }; // field is multi-value but not ParameterizedType
                }
                return new Class<?>[] { propertyType }; // not a multi-value field
            }
        }

        private static class FieldBinding implements IGetter, ISetter {
            private final Object scope;
            private final Field field;

            FieldBinding(Object scope, Field field) {
                this.scope = scope;
                this.field = field;
            }

            public <T> T get() throws PicocliException {
                try {
                    @SuppressWarnings("unchecked")
                    T result = (T) field.get(scope);
                    return result;
                } catch (Exception ex) {
                    throw new PicocliException("Could not get value for field " + field, ex);
                }
            }

            public <T> T set(T value) throws PicocliException {
                try {
                    @SuppressWarnings("unchecked")
                    T result = (T) field.get(scope);
                    field.set(scope, value);
                    return result;
                } catch (Exception ex) {
                    throw new PicocliException(
                            "Could not set value for field " + field + " to " + value, ex);
                }
            }
        }

        static class MethodBinding implements IGetter, ISetter {
            private final Object scope;
            private final Method method;
            private Object currentValue;
            CommandLine commandLine;

            MethodBinding(Object scope, Method method) {
                this.scope = scope;
                this.method = method;
            }

            @SuppressWarnings("unchecked")
            public <T> T get() {
                return (T) currentValue;
            }

            public <T> T set(T value) throws PicocliException {
                try {
                    @SuppressWarnings("unchecked")
                    T result = (T) currentValue;
                    method.invoke(scope, value);
                    currentValue = value;
                    return result;
                } catch (InvocationTargetException ex) {
                    if (ex.getCause() instanceof PicocliException) {
                        throw (PicocliException) ex.getCause();
                    }
                    throw new ParameterException(commandLine,
                            "Could not invoke " + method + " with " + value, ex.getCause());
                } catch (Exception ex) {
                    throw new ParameterException(commandLine,
                            "Could not invoke " + method + " with " + value, ex);
                }
            }
        }

        private static class PicocliInvocationHandler implements InvocationHandler {
            final Map<String, Object> map = new HashMap<String, Object>();

            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return map.get(method.getName());
            }

            class ProxyBinding implements IGetter, ISetter {
                private final Method method;

                ProxyBinding(Method method) {
                    this.method = Assert.notNull(method, "method");
                }

                @SuppressWarnings("unchecked")
                public <T> T get() {
                    return (T) map.get(method.getName());
                }

                public <T> T set(T value) {
                    T result = get();
                    map.put(method.getName(), value);
                    return result;
                }
            }
        }

        private static class ObjectBinding implements IGetter, ISetter {
            private Object value;

            @SuppressWarnings("unchecked")
            public <T> T get() {
                return (T) value;
            }

            public <T> T set(T value) {
                @SuppressWarnings("unchecked")
                T result = value;
                this.value = value;
                return result;
            }
        }
    }

    static class PositionalParametersSorter implements Comparator<ArgSpec> {
        private static final Range OPTION_INDEX = new Range(0, 0, false, true, "0");

        public int compare(ArgSpec p1, ArgSpec p2) {
            int result = index(p1).compareTo(index(p2));
            return (result == 0) ? p1.arity().compareTo(p2.arity()) : result;
        }

        private Range index(ArgSpec arg) {
            return arg.isOption() ? OPTION_INDEX : ((PositionalParamSpec) arg).index();
        }
    }

    /**
     * Uses cosine similarity to find matches from a candidate set for a specified input. Based on
     * code from
     * http://www.nearinfinity.com/blogs/seth_schroeder/groovy_cosine_similarity_in_grails.html
     *
     * @author Burt Beckwith
     */
    public static class CosineSimilarity {
        public static List<String> mostSimilar(String pattern, Iterable<String> candidates) {
            return mostSimilar(pattern, candidates, 0);
        }

        static List<String> mostSimilar(String pattern, Iterable<String> candidates,
                double threshold) {
            pattern = pattern.toLowerCase();
            SortedMap<Double, String> sorted = new TreeMap<Double, String>();
            for (String candidate : candidates) {
                double score = similarity(pattern, candidate.toLowerCase(), 2);
                if (score > threshold) {
                    sorted.put(score, candidate);
                }
            }
            return CollectionUtilsExt.reverse(new ArrayList<String>(sorted.values()));
        }

        private static double similarity(String sequence1, String sequence2, int degree) {
            Map<String, Integer> m1 = countNgramFrequency(sequence1, degree);
            Map<String, Integer> m2 = countNgramFrequency(sequence2, degree);
            return dotProduct(m1, m2) / Math.sqrt(dotProduct(m1, m1) * dotProduct(m2, m2));
        }

        private static Map<String, Integer> countNgramFrequency(String sequence, int degree) {
            Map<String, Integer> m = new HashMap<String, Integer>();
            for (int i = 0; i + degree <= sequence.length(); i++) {
                String gram = sequence.substring(i, i + degree);
                m.put(gram, 1 + (m.containsKey(gram) ? m.get(gram) : 0));
            }
            return m;
        }

        private static double dotProduct(Map<String, Integer> m1, Map<String, Integer> m2) {
            double result = 0;
            for (String key : m1.keySet()) {
                result += m1.get(key) * (m2.containsKey(key) ? m2.get(key) : 0);
            }
            return result;
        }
    }
}
