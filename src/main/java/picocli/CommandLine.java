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

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StreamTokenizer;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.Date;
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
import java.util.SortedSet;
import java.util.Stack;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import picocli.CommandLine.IExceptionHandler;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.IParseResultHandler;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.ArgsReflection;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.Messages;
import picocli.CommandLine.Model.MethodParam;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.ParserSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Model.TypedMember;
import picocli.CommandLine.Model.UnmatchedArgsBinding;
import picocli.CommandLine.Model.UsageMessageSpec;
import picocli.help.Ansi;
import picocli.help.ColorScheme;
import picocli.help.Help;
import picocli.help.Layout;
import picocli.help.Text;
import picocli.help.TextTable;
import picocli.util.Assert;
import picocli.util.Utils;

/**
 * <p>
 * CommandLine interpreter that uses reflection to initialize an annotated domain object with values obtained from the
 * command line arguments.
 * </p><h2>Example</h2>
 * <pre>import static picocli.CommandLine.*;
 *
 * &#064;Command(mixinStandardHelpOptions = true, version = "v3.0.0",
 *         header = "Encrypt FILE(s), or standard input, to standard output or to the output file.")
 * public class Encrypt {
 *
 *     &#064;Parameters(type = File.class, description = "Any number of input files")
 *     private List&lt;File&gt; files = new ArrayList&lt;File&gt;();
 *
 *     &#064;Option(names = { "-o", "--out" }, description = "Output file (default: print to console)")
 *     private File outputFile;
 *
 *     &#064;Option(names = { "-v", "--verbose"}, description = "Verbose mode. Helpful for troubleshooting. Multiple -v options increase the verbosity.")
 *     private boolean[] verbose;
 * }
 * </pre>
 * <p>
 * Use {@code CommandLine} to initialize a domain object as follows:
 * </p><pre>
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
 * </pre><p>
 * Invoke the above program with some command line arguments. The below are all equivalent:
 * </p>
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
 * Another example that implements {@code Callable} and uses the {@link #call(Callable, String...) CommandLine.call} convenience API to run in a single line of code:
 * </p>
 * <pre>
 *  &#064;Command(description = "Prints the checksum (MD5 by default) of a file to STDOUT.",
 *          name = "checksum", mixinStandardHelpOptions = true, version = "checksum 3.0")
 * class CheckSum implements Callable&lt;Void&gt; {
 *
 *     &#064;Parameters(index = "0", description = "The file whose checksum to calculate.")
 *     private File file;
 *
 *     &#064;Option(names = {"-a", "--algorithm"}, description = "MD5, SHA-1, SHA-256, ...")
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
 * <h2>Classes and Interfaces for Defining a CommandSpec Model</h2>
 * <p>
 * <img src="doc-files/class-diagram-definition.png" alt="Classes and Interfaces for Defining a CommandSpec Model">
 * </p>
 * <h2>Classes Related to Parsing Command Line Arguments</h2>
 * <p>
 * <img src="doc-files/class-diagram-parsing.png" alt="Classes Related to Parsing Command Line Arguments">
 * </p>
 */
public class CommandLine {
    /** This is picocli version {@value}. */
    public static final String VERSION = "3.8.0-SNAPSHOT";

    private final Tracer tracer = new Tracer();
    private final CommandSpec commandSpec;
    private final Interpreter interpreter;
    public final IFactory factory;
    private IHelpFactory helpFactory;

    /**
     * Constructs a new {@code CommandLine} interpreter with the specified object (which may be an annotated user object or a {@link CommandSpec CommandSpec}) and a default subcommand factory.
     * <p>The specified object may be a {@link CommandSpec CommandSpec} object, or it may be a {@code @Command}-annotated
     * user object with {@code @Option} and {@code @Parameters}-annotated fields, in which case picocli automatically
     * constructs a {@code CommandSpec} from this user object.
     * </p><p>
     * When the {@link #parse(String...)} method is called, the {@link CommandSpec CommandSpec} object will be
     * initialized based on command line arguments. If the commandSpec is created from an annotated user object, this
     * user object will be initialized based on the command line arguments.</p>
     * @param command an annotated user object or a {@code CommandSpec} object to initialize from the command line arguments
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     */
    public CommandLine(Object command) {
        this(command, new DefaultFactory());
    }
    /**
     * Constructs a new {@code CommandLine} interpreter with the specified object (which may be an annotated user object or a {@link CommandSpec CommandSpec}) and object factory.
     * <p>The specified object may be a {@link CommandSpec CommandSpec} object, or it may be a {@code @Command}-annotated
     * user object with {@code @Option} and {@code @Parameters}-annotated fields, in which case picocli automatically
     * constructs a {@code CommandSpec} from this user object.
     *  </p><p> If the specified command object is an interface {@code Class} with {@code @Option} and {@code @Parameters}-annotated methods,
     * picocli creates a {@link java.lang.reflect.Proxy Proxy} whose methods return the matched command line values.
     * If the specified command object is a concrete {@code Class}, picocli delegates to the {@linkplain IFactory factory} to get an instance.
     * </p><p>
     * When the {@link #parse(String...)} method is called, the {@link CommandSpec CommandSpec} object will be
     * initialized based on command line arguments. If the commandSpec is created from an annotated user object, this
     * user object will be initialized based on the command line arguments.</p>
     * @param command an annotated user object or a {@code CommandSpec} object to initialize from the command line arguments
     * @param factory the factory used to create instances of {@linkplain Command#subcommands() subcommands}, {@linkplain Option#converter() converters}, etc., that are registered declaratively with annotation attributes
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @since 2.2 */
    public CommandLine(Object command, IFactory factory) {
        this.factory = Assert.notNull(factory, "factory");
        interpreter = new Interpreter();
        commandSpec = CommandSpec.forAnnotatedObject(command, factory);
        commandSpec.commandLine(this);
        commandSpec.validate();
        if (commandSpec.unmatchedArgsBindings().size() > 0) { setUnmatchedArgumentsAllowed(true); }
    }

    /**
     * Returns the {@code CommandSpec} model that this {@code CommandLine} was constructed with.
     * @return the {@code CommandSpec} model
     * @since 3.0 */
    public CommandSpec getCommandSpec() { return commandSpec; }

    /**
     * Adds the options and positional parameters in the specified mixin to this command.
     * <p>The specified object may be a {@link CommandSpec CommandSpec} object, or it may be a user object with
     * {@code @Option} and {@code @Parameters}-annotated fields, in which case picocli automatically
     * constructs a {@code CommandSpec} from this user object.
     * </p>
     * @param name the name by which the mixin object may later be retrieved
     * @param mixin an annotated user object or a {@link CommandSpec CommandSpec} object whose options and positional parameters to add to this command
     * @return this CommandLine object, to allow method chaining
     * @since 3.0 */
    public CommandLine addMixin(String name, Object mixin) {
        getCommandSpec().addMixin(name, CommandSpec.forAnnotatedObject(mixin, factory));
        return this;
    }

    /**
     * Returns a map of user objects whose options and positional parameters were added to ("mixed in" with) this command.
     * @return a new Map containing the user objects mixed in with this command. If {@code CommandSpec} objects without
     *          user objects were programmatically added, use the {@link CommandSpec#mixins() underlying model} directly.
     * @since 3.0 */
    public Map<String, Object> getMixins() {
        Map<String, CommandSpec> mixins = getCommandSpec().mixins();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (String name : mixins.keySet()) { result.put(name, mixins.get(name).userObject); }
        return result;
    }

    /** Registers a subcommand with the specified name. For example:
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
     * <p>The specified object can be an annotated object or a
     * {@code CommandLine} instance with its own nested subcommands. For example:</p>
     * <pre>
     * CommandLine commandLine = new CommandLine(new MainCommand())
     *         .addSubcommand("cmd1",                 new ChildCommand1()) // subcommand
     *         .addSubcommand("cmd2",                 new ChildCommand2())
     *         .addSubcommand("cmd3", new CommandLine(new ChildCommand3()) // subcommand with nested sub-subcommands
     *                 .addSubcommand("cmd3sub1",                 new GrandChild3Command1())
     *                 .addSubcommand("cmd3sub2",                 new GrandChild3Command2())
     *                 .addSubcommand("cmd3sub3", new CommandLine(new GrandChild3Command3()) // deeper nesting
     *                         .addSubcommand("cmd3sub3sub1", new GreatGrandChild3Command3_1())
     *                         .addSubcommand("cmd3sub3sub2", new GreatGrandChild3Command3_2())
     *                 )
     *         );
     * </pre>
     * <p>The default type converters are available on all subcommands and nested sub-subcommands, but custom type
     * converters are registered only with the subcommand hierarchy as it existed when the custom type was registered.
     * To ensure a custom type converter is available to all subcommands, register the type converter last, after
     * adding subcommands.</p>
     * <p>See also the {@link Command#subcommands()} annotation to register subcommands declaratively.</p>
     *
     * @param name the string to recognize on the command line as a subcommand
     * @param command the object to initialize with command line arguments following the subcommand name.
     *          This may be a {@code CommandLine} instance with its own (nested) subcommands
     * @return this CommandLine object, to allow method chaining
     * @see #registerConverter(Class, ITypeConverter)
     * @since 0.9.7
     * @see Command#subcommands()
     */
    public CommandLine addSubcommand(String name, Object command) {
        return addSubcommand(name, command, new String[0]);
    }

    /** Registers a subcommand with the specified name and all specified aliases. See also {@link #addSubcommand(String, Object)}.
     *
     *
     * @param name the string to recognize on the command line as a subcommand
     * @param command the object to initialize with command line arguments following the subcommand name.
     *          This may be a {@code CommandLine} instance with its own (nested) subcommands
     * @param aliases zero or more alias names that are also recognized on the command line as this subcommand
     * @return this CommandLine object, to allow method chaining
     * @since 3.1
     * @see #addSubcommand(String, Object)
     */
    public CommandLine addSubcommand(String name, Object command, String... aliases) {
        CommandLine subcommandLine = toCommandLine(command, factory);
        subcommandLine.getCommandSpec().aliases.addAll(Arrays.asList(aliases));
        getCommandSpec().addSubcommand(name, subcommandLine);
        CommandLine.Model.CommandReflection.initParentCommand(subcommandLine.getCommandSpec().userObject(), getCommandSpec().userObject());
        return this;
    }
    /** Returns a map with the subcommands {@linkplain #addSubcommand(String, Object) registered} on this instance.
     * @return a map with the registered subcommands
     * @since 0.9.7
     */
    public Map<String, CommandLine> getSubcommands() {
        return new LinkedHashMap<String, CommandLine>(getCommandSpec().subcommands());
    }
    /**
     * Returns the command that this is a subcommand of, or {@code null} if this is a top-level command.
     * @return the command that this is a subcommand of, or {@code null} if this is a top-level command
     * @see #addSubcommand(String, Object)
     * @see Command#subcommands()
     * @since 0.9.8
     */
    public CommandLine getParent() {
        CommandSpec parent = getCommandSpec().parent();
        return parent == null ? null : parent.commandLine();
    }

    /** Returns the annotated user object that this {@code CommandLine} instance was constructed with.
     * @param <T> the type of the variable that the return value is being assigned to
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

    /** Returns {@code true} if an option annotated with {@link Option#usageHelp()} was specified on the command line.
     * @return whether the parser encountered an option annotated with {@link Option#usageHelp()}.
     * @since 0.9.8 */
    public boolean isUsageHelpRequested() { return interpreter.parseResult != null && interpreter.parseResult.usageHelpRequested; }

    /** Returns {@code true} if an option annotated with {@link Option#versionHelp()} was specified on the command line.
     * @return whether the parser encountered an option annotated with {@link Option#versionHelp()}.
     * @since 0.9.8 */
    public boolean isVersionHelpRequested() { return interpreter.parseResult != null && interpreter.parseResult.versionHelpRequested; }

    /** Returns whether the value of boolean flag options should be "toggled" when the option is matched.
     * By default, flags are toggled, so if the value is {@code true} it is set to {@code false}, and when the value is
     * {@code false} it is set to {@code true}. If toggling is off, flags are simply set to {@code true}.
     * @return {@code true} the value of boolean flag options should be "toggled" when the option is matched, {@code false} otherwise
     * @since 3.0
     */
    public boolean isToggleBooleanFlags() {
        return getCommandSpec().parser().toggleBooleanFlags();
    }

    /** Sets whether the value of boolean flag options should be "toggled" when the option is matched. The default is {@code true}.
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param newValue the new setting
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

    /** Returns whether options for single-value fields can be specified multiple times on the command line.
     * The default is {@code false} and a {@link OverwrittenOptionException} is thrown if this happens.
     * When {@code true}, the last specified value is retained.
     * @return {@code true} if options for single-value fields can be specified multiple times on the command line, {@code false} otherwise
     * @since 0.9.7
     */
    public boolean isOverwrittenOptionsAllowed() {
        return getCommandSpec().parser().overwrittenOptionsAllowed();
    }

    /** Sets whether options for single-value fields can be specified multiple times on the command line without a {@link OverwrittenOptionException} being thrown.
     * The default is {@code false}.
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param newValue the new setting
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

    /** Returns whether the parser accepts clustered short options. The default is {@code true}.
     * @return {@code true} if short options like {@code -x -v -f SomeFile} can be clustered together like {@code -xvfSomeFile}, {@code false} otherwise
     * @since 3.0 */
    public boolean isPosixClusteredShortOptionsAllowed() { return getCommandSpec().parser().posixClusteredShortOptionsAllowed(); }

    /** Sets whether short options like {@code -x -v -f SomeFile} can be clustered together like {@code -xvfSomeFile}. The default is {@code true}.
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param newValue the new setting
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

    /** Returns whether the parser should ignore case when converting arguments to {@code enum} values. The default is {@code false}.
     * @return {@code true} if enum values can be specified that don't match the {@code toString()} value of the enum constant, {@code false} otherwise;
     * e.g., for an option of type <a href="https://docs.oracle.com/javase/8/docs/api/java/time/DayOfWeek.html">java.time.DayOfWeek</a>,
     * values {@code MonDaY}, {@code monday} and {@code MONDAY} are all recognized if {@code true}.
     * @since 3.4 */
    public boolean isCaseInsensitiveEnumValuesAllowed() { return getCommandSpec().parser().caseInsensitiveEnumValuesAllowed(); }

    /** Sets whether the parser should ignore case when converting arguments to {@code enum} values. The default is {@code false}.
     * When set to true, for example, for an option of type <a href="https://docs.oracle.com/javase/8/docs/api/java/time/DayOfWeek.html">java.time.DayOfWeek</a>,
     * values {@code MonDaY}, {@code monday} and {@code MONDAY} are all recognized if {@code true}.
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param newValue the new setting
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

    /** Returns whether the parser should trim quotes from command line arguments before processing them. The default is {@code false}.
     * @return {@code true} if the parser should trim quotes from command line arguments before processing them, {@code false} otherwise;
     * @since 3.7 */
    public boolean isTrimQuotes() { return getCommandSpec().parser().trimQuotes(); }

    /** Sets whether the parser should trim quotes from command line arguments before processing them. The default is {@code false}.
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param newValue the new setting
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

    /** Returns whether the parser is allowed to split quoted Strings or not. The default is {@code false},
     * so quoted strings are treated as a single value that cannot be split.
     * @return {@code true} if the parser is allowed to split quoted Strings, {@code false} otherwise;
     * @see ArgSpec#splitRegex()
     * @since 3.7 */
    public boolean isSplitQuotedStrings() { return getCommandSpec().parser().splitQuotedStrings(); }

    /** Sets whether the parser is allowed to split quoted Strings. The default is {@code false}.
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param newValue the new setting
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

    /** Returns the end-of-options delimiter that signals that the remaining command line arguments should be treated as positional parameters.
     * @return the end-of-options delimiter. The default is {@code "--"}.
     * @since 3.5 */
    public String getEndOfOptionsDelimiter() { return getCommandSpec().parser().endOfOptionsDelimiter(); }

    /** Sets the end-of-options delimiter that signals that the remaining command line arguments should be treated as positional parameters.
     * @param delimiter the end-of-options delimiter; must not be {@code null}. The default is {@code "--"}.
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 3.5 */
    public CommandLine setEndOfOptionsDelimiter(String delimiter) {
        getCommandSpec().parser().endOfOptionsDelimiter(delimiter);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setEndOfOptionsDelimiter(delimiter);
        }
        return this;
    }

    /** Returns the default value provider for the command, or {@code null} if none has been set.
     * @return the default value provider for this command, or {@code null}
     * @since 3.6
     * @see Command#defaultValueProvider()
     * @see CommandSpec#defaultValueProvider()
     * @see ArgSpec#defaultValueString()
     */
    public IDefaultValueProvider getDefaultValueProvider() {
        return getCommandSpec().defaultValueProvider();
    }

    /** Sets a default value provider for the command and sub-commands
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * sub-commands and nested sub-subcommands <em>at the moment this method is called</em>. Sub-commands added
     * later will have the default setting. To ensure a setting is applied to all
     * sub-commands, call the setter last, after adding sub-commands.</p>
     * @param newValue the default value provider to use
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

    /** Returns whether the parser interprets the first positional parameter as "end of options" so the remaining
     * arguments are all treated as positional parameters. The default is {@code false}.
     * @return {@code true} if all values following the first positional parameter should be treated as positional parameters, {@code false} otherwise
     * @since 2.3
     */
    public boolean isStopAtPositional() {
        return getCommandSpec().parser().stopAtPositional();
    }

    /** Sets whether the parser interprets the first positional parameter as "end of options" so the remaining
     * arguments are all treated as positional parameters. The default is {@code false}.
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param newValue {@code true} if all values following the first positional parameter should be treated as positional parameters, {@code false} otherwise
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

    /** Returns whether the parser should stop interpreting options and positional parameters as soon as it encounters an
     * unmatched option. Unmatched options are arguments that look like an option but are not one of the known options, or
     * positional arguments for which there is no available slots (the command has no positional parameters or their size is limited).
     * The default is {@code false}.
     * <p>Setting this flag to {@code true} automatically sets the {@linkplain #isUnmatchedArgumentsAllowed() unmatchedArgumentsAllowed} flag to {@code true} also.</p>
     * @return {@code true} when an unmatched option should result in the remaining command line arguments to be added to the
     *      {@linkplain #getUnmatchedArguments() unmatchedArguments list}
     * @since 2.3
     */
    public boolean isStopAtUnmatched() {
        return getCommandSpec().parser().stopAtUnmatched();
    }

    /** Sets whether the parser should stop interpreting options and positional parameters as soon as it encounters an
     * unmatched option. Unmatched options are arguments that look like an option but are not one of the known options, or
     * positional arguments for which there is no available slots (the command has no positional parameters or their size is limited).
     * The default is {@code false}.
     * <p>Setting this flag to {@code true} automatically sets the {@linkplain #setUnmatchedArgumentsAllowed(boolean) unmatchedArgumentsAllowed} flag to {@code true} also.</p>
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param newValue {@code true} when an unmatched option should result in the remaining command line arguments to be added to the
     *      {@linkplain #getUnmatchedArguments() unmatchedArguments list}
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 2.3
     */
    public CommandLine setStopAtUnmatched(boolean newValue) {
        getCommandSpec().parser().stopAtUnmatched(newValue);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setStopAtUnmatched(newValue);
        }
        if (newValue) { setUnmatchedArgumentsAllowed(true); }
        return this;
    }

    /** Returns whether arguments on the command line that resemble an option should be treated as positional parameters.
     * The default is {@code false} and the parser behaviour depends on {@link #isUnmatchedArgumentsAllowed()}.
     * @return {@code true} arguments on the command line that resemble an option should be treated as positional parameters, {@code false} otherwise
     * @see #getUnmatchedArguments()
     * @since 3.0
     */
    public boolean isUnmatchedOptionsArePositionalParams() {
        return getCommandSpec().parser().unmatchedOptionsArePositionalParams();
    }

    /** Sets whether arguments on the command line that resemble an option should be treated as positional parameters.
     * The default is {@code false}.
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param newValue the new setting. When {@code true}, arguments on the command line that resemble an option should be treated as positional parameters.
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

    /** Returns whether the end user may specify arguments on the command line that are not matched to any option or parameter fields.
     * The default is {@code false} and a {@link UnmatchedArgumentException} is thrown if this happens.
     * When {@code true}, the last unmatched arguments are available via the {@link #getUnmatchedArguments()} method.
     * @return {@code true} if the end use may specify unmatched arguments on the command line, {@code false} otherwise
     * @see #getUnmatchedArguments()
     * @since 0.9.7
     */
    public boolean isUnmatchedArgumentsAllowed() {
        return getCommandSpec().parser().unmatchedArgumentsAllowed();
    }

    /** Sets whether the end user may specify unmatched arguments on the command line without a {@link UnmatchedArgumentException} being thrown.
     * The default is {@code false}.
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param newValue the new setting. When {@code true}, the last unmatched arguments are available via the {@link #getUnmatchedArguments()} method.
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

    /** Returns the list of unmatched command line arguments, if any.
     * @return the list of unmatched command line arguments or an empty list
     * @see #isUnmatchedArgumentsAllowed()
     * @since 0.9.7
     */
    public List<String> getUnmatchedArguments() {
        return interpreter.parseResult == null ? Collections.<String>emptyList() : Collections.unmodifiableList(interpreter.parseResult.unmatched);
    }

    /**
     * <p>
     * Convenience method that initializes the specified annotated object from the specified command line arguments.
     * </p><p>
     * This is equivalent to
     * </p><pre>
     * CommandLine cli = new CommandLine(command);
     * cli.parse(args);
     * return command;
     * </pre>
     *
     * @param command the object to initialize. This object contains fields annotated with
     *          {@code @Option} or {@code @Parameters}.
     * @param args the command line arguments to parse
     * @param <T> the type of the annotated object
     * @return the specified annotated object
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ParameterException if the specified command line arguments are invalid
     * @since 0.9.7
     */
    public static <T> T populateCommand(T command, String... args) {
        CommandLine cli = toCommandLine(command, new DefaultFactory());
        cli.parse(args);
        return command;
    }

    /**
     * <p>
     * Convenience method that derives the command specification from the specified interface class, and returns an
     * instance of the specified interface. The interface is expected to have annotated getter methods. Picocli will
     * instantiate the interface and the getter methods will return the option and positional parameter values matched on the command line.
     * </p><p>
     * This is equivalent to
     * </p><pre>
     * CommandLine cli = new CommandLine(spec);
     * cli.parse(args);
     * return cli.getCommand();
     * </pre>
     *
     * @param spec the interface that defines the command specification. This object contains getter methods annotated with
     *          {@code @Option} or {@code @Parameters}.
     * @param args the command line arguments to parse
     * @param <T> the type of the annotated object
     * @return an instance of the specified annotated interface
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ParameterException if the specified command line arguments are invalid
     * @since 3.1
     */
    public static <T> T populateSpec(Class<T> spec, String... args) {
        CommandLine cli = toCommandLine(spec, new DefaultFactory());
        cli.parse(args);
        return cli.getCommand();
    }

    /** Parses the specified command line arguments and returns a list of {@code CommandLine} objects representing the
     * top-level command and any subcommands (if any) that were recognized and initialized during the parsing process.
     * <p>
     * If parsing succeeds, the first element in the returned list is always {@code this CommandLine} object. The
     * returned list may contain more elements if subcommands were {@linkplain #addSubcommand(String, Object) registered}
     * and these subcommands were initialized by matching command line arguments. If parsing fails, a
     * {@link ParameterException} is thrown.
     * </p>
     *
     * @param args the command line arguments to parse
     * @return a list with the top-level command and any subcommands initialized by this method
     * @throws ParameterException if the specified command line arguments are invalid; use
     *      {@link ParameterException#getCommandLine()} to get the command or subcommand whose user input was invalid
     */
    public List<CommandLine> parse(String... args) {
        return interpreter.parse(args);
    }
    /** Parses the specified command line arguments and returns a list of {@code ParseResult} with the options, positional
     * parameters, and subcommands (if any) that were recognized and initialized during the parsing process.
     * <p>If parsing fails, a {@link ParameterException} is thrown.</p>
     *
     * @param args the command line arguments to parse
     * @return a list with the top-level command and any subcommands initialized by this method
     * @throws ParameterException if the specified command line arguments are invalid; use
     *      {@link ParameterException#getCommandLine()} to get the command or subcommand whose user input was invalid
     */
    public ParseResult parseArgs(String... args) {
        interpreter.parse(args);
        return interpreter.parseResult.build();
    }
    public ParseResult getParseResult() { return interpreter.parseResult == null ? null : interpreter.parseResult.build(); }
    /**
     * Represents a function that can process a List of {@code CommandLine} objects resulting from successfully
     * {@linkplain #parse(String...) parsing} the command line arguments. This is a
     * <a href="https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">functional interface</a>
     * whose functional method is {@link #handleParseResult(List, PrintStream, CommandLine.Help.Ansi)}.
     * <p>
     * Implementations of this functions can be passed to the {@link #parseWithHandlers(IParseResultHandler, PrintStream, Help.Ansi, IExceptionHandler, String...) CommandLine::parseWithHandler}
     * methods to take some next step after the command line was successfully parsed.
     * </p>
     * @see RunFirst
     * @see RunLast
     * @see RunAll
     * @deprecated Use {@link IParseResultHandler2} instead.
     * @since 2.0 */
    @Deprecated public static interface IParseResultHandler {
        /** Processes a List of {@code CommandLine} objects resulting from successfully
         * {@linkplain #parse(String...) parsing} the command line arguments and optionally returns a list of results.
         * @param parsedCommands the {@code CommandLine} objects that resulted from successfully parsing the command line arguments
         * @param out the {@code PrintStream} to print help to if requested
         * @param ansi for printing help messages using ANSI styles and colors
         * @return a list of results, or an empty list if there are no results
         * @throws ParameterException if a help command was invoked for an unknown subcommand. Any {@code ParameterExceptions}
         *      thrown from this method are treated as if this exception was thrown during parsing and passed to the {@link IExceptionHandler}
         * @throws ExecutionException if a problem occurred while processing the parse results; use
         *      {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
         */
        List<Object> handleParseResult(List<CommandLine> parsedCommands, PrintStream out, Ansi ansi) throws ExecutionException;
    }

    /**
     * Represents a function that can process the {@code ParseResult} object resulting from successfully
     * {@linkplain #parseArgs(String...) parsing} the command line arguments. This is a
     * <a href="https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">functional interface</a>
     * whose functional method is {@link IParseResultHandler2#handleParseResult(CommandLine.ParseResult)}.
     * <p>
     * Implementations of this function can be passed to the {@link #parseWithHandlers(IParseResultHandler2,  IExceptionHandler2, String...) CommandLine::parseWithHandlers}
     * methods to take some next step after the command line was successfully parsed.
     * </p><p>
     * This interface replaces the {@link IParseResultHandler} interface; it takes the parse result as a {@code ParseResult}
     * object instead of a List of {@code CommandLine} objects, and it has the freedom to select the {@link Ansi} style
     * to use and what {@code PrintStreams} to print to.
     * </p>
     * @param <R> the return type of this handler
     * @see RunFirst
     * @see RunLast
     * @see RunAll
     * @since 3.0 */
    public static interface IParseResultHandler2<R> {
        /** Processes the {@code ParseResult} object resulting from successfully
         * {@linkplain CommandLine#parseArgs(String...) parsing} the command line arguments and returns a return value.
         * @param parseResult the {@code ParseResult} that resulted from successfully parsing the command line arguments
         * @throws ParameterException if a help command was invoked for an unknown subcommand. Any {@code ParameterExceptions}
         *      thrown from this method are treated as if this exception was thrown during parsing and passed to the {@link IExceptionHandler2}
         * @throws ExecutionException if a problem occurred while processing the parse results; use
         *      {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
         */
        R handleParseResult(ParseResult parseResult) throws ExecutionException;
    }
    /**
     * Represents a function that can handle a {@code ParameterException} that occurred while
     * {@linkplain #parse(String...) parsing} the command line arguments. This is a
     * <a href="https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">functional interface</a>
     * whose functional method is {@link #handleException(CommandLine.ParameterException, PrintStream, CommandLine.Help.Ansi, String...)}.
     * <p>
     * Implementations of this function can be passed to the {@link #parseWithHandlers(IParseResultHandler, PrintStream, Help.Ansi, IExceptionHandler, String...) CommandLine::parseWithHandlers}
     * methods to handle situations when the command line could not be parsed.
     * </p>
     * @deprecated Use {@link IExceptionHandler2} instead.
     * @see DefaultExceptionHandler
     * @since 2.0 */
    @Deprecated public static interface IExceptionHandler {
        /** Handles a {@code ParameterException} that occurred while {@linkplain #parse(String...) parsing} the command
         * line arguments and optionally returns a list of results.
         * @param ex the ParameterException describing the problem that occurred while parsing the command line arguments,
         *           and the CommandLine representing the command or subcommand whose input was invalid
         * @param out the {@code PrintStream} to print help to if requested
         * @param ansi for printing help messages using ANSI styles and colors
         * @param args the command line arguments that could not be parsed
         * @return a list of results, or an empty list if there are no results
         */
        List<Object> handleException(ParameterException ex, PrintStream out, Ansi ansi, String... args);
    }
    /**
     * Classes implementing this interface know how to handle {@code ParameterExceptions} (usually from invalid user input)
     * and {@code ExecutionExceptions} that occurred while executing the {@code Runnable} or {@code Callable} command.
     * <p>
     * Implementations of this interface can be passed to the
     * {@link #parseWithHandlers(IParseResultHandler2,  IExceptionHandler2, String...) CommandLine::parseWithHandlers} method.
     * </p><p>
     * This interface replaces the {@link IParseResultHandler} interface.
     * </p>
     * @param <R> the return type of this handler
     * @see DefaultExceptionHandler
     * @since 3.0 */
    public static interface IExceptionHandler2<R> {
        /** Handles a {@code ParameterException} that occurred while {@linkplain #parseArgs(String...) parsing} the command
         * line arguments and optionally returns a list of results.
         * @param ex the ParameterException describing the problem that occurred while parsing the command line arguments,
         *           and the CommandLine representing the command or subcommand whose input was invalid
         * @param args the command line arguments that could not be parsed
         * @return an object resulting from handling the exception
         */
        R handleParseException(ParameterException ex, String[] args);
        /** Handles a {@code ExecutionException} that occurred while executing the {@code Runnable} or
         * {@code Callable} command and optionally returns a list of results.
         * @param ex the ExecutionException describing the problem that occurred while executing the {@code Runnable} or
         *          {@code Callable} command, and the CommandLine representing the command or subcommand that was being executed
         * @param parseResult the result of parsing the command line arguments
         * @return an object resulting from handling the exception
         */
        R handleExecutionException(ExecutionException ex, ParseResult parseResult);
    }

    /** Abstract superclass for {@link IParseResultHandler2} and {@link IExceptionHandler2} implementations.
     * <p>Note that {@code AbstractHandler} is a generic type. This, along with the abstract {@code self} method,
     * allows method chaining to work properly in subclasses, without the need for casts. An example subclass can look like this:</p>
     * <pre>{@code
     * class MyResultHandler extends AbstractHandler<MyReturnType, MyResultHandler> implements IParseResultHandler2<MyReturnType> {
     *
     *     public MyReturnType handleParseResult(ParseResult parseResult) { ... }
     *
     *     protected MyResultHandler self() { return this; }
     * }
     * }</pre>
     * @param <R> the return type of this handler
     * @param <T> The type of the handler subclass; for fluent API method chaining
     * @since 3.0 */
    public static abstract class AbstractHandler<R, T extends AbstractHandler<R, T>> {
        private Ansi ansi = Ansi.AUTO;
        private Integer exitCode;
        private PrintStream out = System.out;
        private PrintStream err = System.err;

        /** Returns the stream to print command output to. Defaults to {@code System.out}, unless {@link #useOut(PrintStream)}
         * was called with a different stream.
         * <p>{@code IParseResultHandler2} implementations should use this stream.
         * By <a href="http://www.gnu.org/prep/standards/html_node/_002d_002dhelp.html">convention</a>, when the user requests
         * help with a {@code --help} or similar option, the usage help message is printed to the standard output stream so that it can be easily searched and paged.</p> */
        public PrintStream out()     { return out; }
        /** Returns the stream to print diagnostic messages to. Defaults to {@code System.err}, unless {@link #useErr(PrintStream)}
         * was called with a different stream. <p>{@code IExceptionHandler2} implementations should use this stream to print error
         * messages (which may include a usage help message) when an unexpected error occurs.</p> */
        public PrintStream err()     { return err; }
        /** Returns the ANSI style to use. Defaults to {@code Help.Ansi.AUTO}, unless {@link #useAnsi(CommandLine.Help.Ansi)} was called with a different setting. */
        public Ansi ansi()      { return ansi; }
        /** Returns the exit code to use as the termination status, or {@code null} (the default) if the handler should
         * not call {@link System#exit(int)} after processing completes.
         * @see #andExit(int) */
        public Integer exitCode()    { return exitCode; }
        /** Returns {@code true} if an exit code was set with {@link #andExit(int)}, or {@code false} (the default) if
         * the handler should not call {@link System#exit(int)} after processing completes. */
        public boolean hasExitCode() { return exitCode != null; }

        /** Convenience method for subclasses that returns the specified result object if no exit code was set,
         * or otherwise, if an exit code {@linkplain #andExit(int) was set}, calls {@code System.exit} with the configured
         * exit code to terminate the currently running Java virtual machine. */
        protected R returnResultOrExit(R result) {
            if (hasExitCode()) { System.exit(exitCode()); }
            return result;
        }

        /** Returns {@code this} to allow method chaining when calling the setters for a fluent API. */
        protected abstract T self();

        /** Sets the stream to print command output to. For use by {@code IParseResultHandler2} implementations.
         * @see #out() */
        public T useOut(PrintStream out)   { this.out =  Assert.notNull(out, "out");   return self(); }
        /** Sets the stream to print diagnostic messages to. For use by {@code IExceptionHandler2} implementations.
         * @see #err()*/
        public T useErr(PrintStream err)   { this.err =  Assert.notNull(err, "err");   return self(); }
        /** Sets the ANSI style to use.
         * @see #ansi() */
        public T useAnsi(Ansi ansi)   { this.ansi = Assert.notNull(ansi, "ansi"); return self(); }
        /** Indicates that the handler should call {@link System#exit(int)} after processing completes and sets the exit code to use as the termination status. */
        public T andExit(int exitCode)     { this.exitCode = exitCode; return self(); }
    }

    /**
     * Default exception handler that handles invalid user input by printing the exception message, followed by the usage
     * message for the command or subcommand whose input was invalid.
     * <p>{@code ParameterExceptions} (invalid user input) is handled like this:</p>
     * <pre>
     *     err().println(paramException.getMessage());
     *     paramException.getCommandLine().usage(err(), ansi());
     *     if (hasExitCode()) System.exit(exitCode()); else return returnValue;
     * </pre>
     * <p>{@code ExecutionExceptions} that occurred while executing the {@code Runnable} or {@code Callable} command are simply rethrown and not handled.</p>
     * @since 2.0 */
    @SuppressWarnings("deprecation")
    public static class DefaultExceptionHandler<R> extends AbstractHandler<R, DefaultExceptionHandler<R>> implements IExceptionHandler, IExceptionHandler2<R> {
        public List<Object> handleException(ParameterException ex, PrintStream out, Ansi ansi, String... args) {
            internalHandleParseException(ex, out, ansi, args); return Collections.<Object>emptyList(); }

        /** Prints the message of the specified exception, followed by the usage message for the command or subcommand
         * whose input was invalid, to the stream returned by {@link #err()}.
         * @param ex the ParameterException describing the problem that occurred while parsing the command line arguments,
         *           and the CommandLine representing the command or subcommand whose input was invalid
         * @param args the command line arguments that could not be parsed
         * @return the empty list
         * @since 3.0 */
        public R handleParseException(ParameterException ex, String[] args) {
            internalHandleParseException(ex, err(), ansi(), args); return returnResultOrExit(null); }

        private void internalHandleParseException(ParameterException ex, PrintStream out, Ansi ansi, String[] args) {
            out.println(ex.getMessage());
            if (!UnmatchedArgumentException.printSuggestions(ex, out)) {
                ex.getCommandLine().usage(out, ansi);
            }
        }
        /** This implementation always simply rethrows the specified exception.
         * @param ex the ExecutionException describing the problem that occurred while executing the {@code Runnable} or {@code Callable} command
         * @param parseResult the result of parsing the command line arguments
         * @return nothing: this method always rethrows the specified exception
         * @throws ExecutionException always rethrows the specified exception
         * @since 3.0 */
        public R handleExecutionException(ExecutionException ex, ParseResult parseResult) { throw ex; }
        @Override protected DefaultExceptionHandler<R> self() { return this; }
    }
    /** Convenience method that returns {@code new DefaultExceptionHandler<List<Object>>()}. */
    public static DefaultExceptionHandler<List<Object>> defaultExceptionHandler() { return new DefaultExceptionHandler<List<Object>>(); }

    /** @deprecated use {@link #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi)} instead
     * @since 2.0 */
    @Deprecated public static boolean printHelpIfRequested(List<CommandLine> parsedCommands, PrintStream out, Ansi ansi) {
        return printHelpIfRequested(parsedCommands, out, out, ansi);
    }

    /** Delegates to {@link #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi)} with
     * {@code parseResult.asCommandLineList(), System.out, System.err, Help.Ansi.AUTO}.
     * @since 3.0 */
    public static boolean printHelpIfRequested(ParseResult parseResult) {
        return printHelpIfRequested(parseResult.asCommandLineList(), System.out, System.err, Ansi.AUTO);
    }
    /**
     * Helper method that may be useful when processing the list of {@code CommandLine} objects that result from successfully
     * {@linkplain #parse(String...) parsing} command line arguments. This method prints out
     * {@linkplain #usage(PrintStream, Help.Ansi) usage help} if {@linkplain #isUsageHelpRequested() requested}
     * or {@linkplain #printVersionHelp(PrintStream, Help.Ansi) version help} if {@linkplain #isVersionHelpRequested() requested}
     * and returns {@code true}. If the command is a {@link Command#helpCommand()} and {@code runnable} or {@code callable},
     * that command is executed and this method returns {@code true}.
     * Otherwise, if none of the specified {@code CommandLine} objects have help requested,
     * this method returns {@code false}.<p>
     * Note that this method <em>only</em> looks at the {@link Option#usageHelp() usageHelp} and
     * {@link Option#versionHelp() versionHelp} attributes. The {@link Option#help() help} attribute is ignored.
     * </p><p><b>Implementation note:</b></p><p>
     * When an error occurs while processing the help request, it is recommended custom Help commands throw a
     * {@link ParameterException} with a reference to the parent command. This will print the error message and the
     * usage for the parent command, and will use the exit code of the exception handler if one was set.
     * </p>
     * @param parsedCommands the list of {@code CommandLine} objects to check if help was requested
     * @param out the {@code PrintStream} to print help to if requested
     * @param err the error string to print diagnostic messages to, in addition to the output from the exception handler
     * @param ansi for printing help messages using ANSI styles and colors
     * @return {@code true} if help was printed, {@code false} otherwise
     * @see IHelpCommandInitializable
     * @since 3.0 */
    public static boolean printHelpIfRequested(List<CommandLine> parsedCommands, PrintStream out, PrintStream err, Ansi ansi) {
        return printHelpIfRequested(parsedCommands, out, err, Help.defaultColorScheme(ansi));
    }
    /**
     * Helper method that may be useful when processing the list of {@code CommandLine} objects that result from successfully
     * {@linkplain #parse(String...) parsing} command line arguments. This method prints out
     * {@linkplain #usage(PrintStream, Help.ColorScheme) usage help} if {@linkplain #isUsageHelpRequested() requested}
     * or {@linkplain #printVersionHelp(PrintStream, Help.Ansi) version help} if {@linkplain #isVersionHelpRequested() requested}
     * and returns {@code true}. If the command is a {@link Command#helpCommand()} and {@code runnable} or {@code callable},
     * that command is executed and this method returns {@code true}.
     * Otherwise, if none of the specified {@code CommandLine} objects have help requested,
     * this method returns {@code false}.<p>
     * Note that this method <em>only</em> looks at the {@link Option#usageHelp() usageHelp} and
     * {@link Option#versionHelp() versionHelp} attributes. The {@link Option#help() help} attribute is ignored.
     * </p><p><b>Implementation note:</b></p><p>
     * When an error occurs while processing the help request, it is recommended custom Help commands throw a
     * {@link ParameterException} with a reference to the parent command. This will print the error message and the
     * usage for the parent command, and will use the exit code of the exception handler if one was set.
     * </p>
     * @param parsedCommands the list of {@code CommandLine} objects to check if help was requested
     * @param out the {@code PrintStream} to print help to if requested
     * @param err the error string to print diagnostic messages to, in addition to the output from the exception handler
     * @param colorScheme for printing help messages using ANSI styles and colors
     * @return {@code true} if help was printed, {@code false} otherwise
     * @see IHelpCommandInitializable
     * @since 3.6 */
    public static boolean printHelpIfRequested(List<CommandLine> parsedCommands, PrintStream out, PrintStream err, ColorScheme colorScheme) {
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
                    ((IHelpCommandInitializable) parsed.getCommand()).init(parsed, colorScheme.ansi(), out, err);
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
                throw new ExecutionException(parsed, "Error while running command (" + command + "): " + ex, ex);
            }
        } else if (command instanceof Callable) {
            try {
                @SuppressWarnings("unchecked") Callable<Object> callable = (Callable<Object>) command;
                executionResult.add(callable.call());
                return executionResult;
            } catch (ParameterException ex) {
                throw ex;
            } catch (ExecutionException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ExecutionException(parsed, "Error while calling command (" + command + "): " + ex, ex);
            }
        } else if (command instanceof Method) {
            try {
                if (Modifier.isStatic(((Method) command).getModifiers())) {
                    // invoke static method
                    executionResult.add(((Method) command).invoke(null, parsed.getCommandSpec().argValues()));
                    return executionResult;
                } else if (parsed.getCommandSpec().parent() != null) {
                    executionResult.add(((Method) command).invoke(parsed.getCommandSpec().parent().userObject(), parsed.getCommandSpec().argValues()));
                    return executionResult;
                } else {
                    // TODO: allow ITypeConverter's to provide an instance?
                    for (Constructor<?> constructor : ((Method) command).getDeclaringClass().getDeclaredConstructors()) {
                        if (constructor.getParameterTypes().length == 0) {
                            executionResult.add(((Method) command).invoke(constructor.newInstance(), parsed.getCommandSpec().argValues()));
                            return executionResult;
                        }
                    }
                    throw new UnsupportedOperationException("Invoking non-static method without default constructor not implemented");
                }
            } catch (ParameterException ex) {
                throw ex;
            } catch (ExecutionException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ExecutionException(parsed, "Error while calling command (" + command + "): " + ex, ex);
            }
        }
        throw new ExecutionException(parsed, "Parsed command (" + command + ") is not Method, Runnable or Callable");
    }
    /** Command line parse result handler that returns a value. This handler prints help if requested, and otherwise calls
     * {@link #handle(CommandLine.ParseResult)} with the parse result. Facilitates implementation of the {@link IParseResultHandler2} interface.
     * <p>Note that {@code AbstractParseResultHandler} is a generic type. This, along with the abstract {@code self} method,
     * allows method chaining to work properly in subclasses, without the need for casts. An example subclass can look like this:</p>
     * <pre>{@code
     * class MyResultHandler extends AbstractParseResultHandler<MyReturnType> {
     *
     *     protected MyReturnType handle(ParseResult parseResult) throws ExecutionException { ... }
     *
     *     protected MyResultHandler self() { return this; }
     * }
     * }</pre>
     * @since 3.0 */
    public abstract static class AbstractParseResultHandler<R> extends AbstractHandler<R, AbstractParseResultHandler<R>> implements IParseResultHandler2<R> {
        /** Prints help if requested, and otherwise calls {@link #handle(CommandLine.ParseResult)}.
         * Finally, either a list of result objects is returned, or the JVM is terminated if an exit code {@linkplain #andExit(int) was set}.
         *
         * @param parseResult the {@code ParseResult} that resulted from successfully parsing the command line arguments
         * @return the result of {@link #handle(ParseResult) processing parse results}
         * @throws ParameterException if the {@link HelpCommand HelpCommand} was invoked for an unknown subcommand. Any {@code ParameterExceptions}
         *      thrown from this method are treated as if this exception was thrown during parsing and passed to the {@link IExceptionHandler2}
         * @throws ExecutionException if a problem occurred while processing the parse results; client code can use
         *      {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
         */
        public R handleParseResult(ParseResult parseResult) throws ExecutionException {
            if (printHelpIfRequested(parseResult.asCommandLineList(), out(), err(), ansi())) {
                return returnResultOrExit(null);
            }
            return returnResultOrExit(handle(parseResult));
        }

        /** Processes the specified {@code ParseResult} and returns the result as a list of objects.
         * Implementations are responsible for catching any exceptions thrown in the {@code handle} method, and
         * rethrowing an {@code ExecutionException} that details the problem and captures the offending {@code CommandLine} object.
         *
         * @param parseResult the {@code ParseResult} that resulted from successfully parsing the command line arguments
         * @return the result of processing parse results
         * @throws ExecutionException if a problem occurred while processing the parse results; client code can use
         *      {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
         */
        protected abstract R handle(ParseResult parseResult) throws ExecutionException;
    }
    /**
     * Command line parse result handler that prints help if requested, and otherwise executes the top-level
     * {@code Runnable} or {@code Callable} command.
     * For use in the {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...) parseWithHandler} methods.
     * @since 2.0 */
    public static class RunFirst extends AbstractParseResultHandler<List<Object>> implements IParseResultHandler {
        /** Prints help if requested, and otherwise executes the top-level {@code Runnable} or {@code Callable} command.
         * Finally, either a list of result objects is returned, or the JVM is terminated if an exit code {@linkplain #andExit(int) was set}.
         * If the top-level command does not implement either {@code Runnable} or {@code Callable}, an {@code ExecutionException}
         * is thrown detailing the problem and capturing the offending {@code CommandLine} object.
         *
         * @param parsedCommands the {@code CommandLine} objects that resulted from successfully parsing the command line arguments
         * @param out the {@code PrintStream} to print help to if requested
         * @param ansi for printing help messages using ANSI styles and colors
         * @return an empty list if help was requested, or a list containing a single element: the result of calling the
         *      {@code Callable}, or a {@code null} element if the top-level command was a {@code Runnable}
         * @throws ParameterException if the {@link HelpCommand HelpCommand} was invoked for an unknown subcommand. Any {@code ParameterExceptions}
         *      thrown from this method are treated as if this exception was thrown during parsing and passed to the {@link IExceptionHandler}
         * @throws ExecutionException if a problem occurred while processing the parse results; use
         *      {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
         */
        public List<Object> handleParseResult(List<CommandLine> parsedCommands, PrintStream out, Ansi ansi) {
            if (printHelpIfRequested(parsedCommands, out, err(), ansi)) { return returnResultOrExit(Collections.emptyList()); }
            return returnResultOrExit(execute(parsedCommands.get(0), new ArrayList<Object>()));
        }
        /** Executes the top-level {@code Runnable} or {@code Callable} subcommand.
         * If the top-level command does not implement either {@code Runnable} or {@code Callable}, an {@code ExecutionException}
         * is thrown detailing the problem and capturing the offending {@code CommandLine} object.
         *
         * @param parseResult the {@code ParseResult} that resulted from successfully parsing the command line arguments
         * @return an empty list if help was requested, or a list containing a single element: the result of calling the
         *      {@code Callable}, or a {@code null} element if the last (sub)command was a {@code Runnable}
         * @throws ExecutionException if a problem occurred while processing the parse results; use
         *      {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
         * @since 3.0 */
        protected List<Object> handle(ParseResult parseResult) throws ExecutionException {
            return execute(parseResult.commandSpec().commandLine(), new ArrayList<Object>()); // first
        }
        @Override protected RunFirst self() { return this; }
    }
    /**
     * Command line parse result handler that prints help if requested, and otherwise executes the most specific
     * {@code Runnable} or {@code Callable} subcommand.
     * For use in the {@link #parseWithHandlers(IParseResultHandler2,  IExceptionHandler2, String...) parseWithHandler} methods.
     * <p>
     * Something like this:</p>
     * <pre>{@code
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
     *         throw new ExecutionException(last, "Parsed command (" + command + ") is not Runnable or Callable");
     *     }
     *     if (hasExitCode()) { System.exit(exitCode()); }
     *     return Arrays.asList(result);
     * }</pre>
     * <p>
     * From picocli v2.0, {@code RunLast} is used to implement the {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...) run}
     * and {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...) call} convenience methods.
     * </p>
     * @since 2.0 */
    public static class RunLast extends AbstractParseResultHandler<List<Object>> implements IParseResultHandler {
        /** Prints help if requested, and otherwise executes the most specific {@code Runnable} or {@code Callable} subcommand.
         * Finally, either a list of result objects is returned, or the JVM is terminated if an exit code {@linkplain #andExit(int) was set}.
         * If the last (sub)command does not implement either {@code Runnable} or {@code Callable}, an {@code ExecutionException}
         * is thrown detailing the problem and capturing the offending {@code CommandLine} object.
         *
         * @param parsedCommands the {@code CommandLine} objects that resulted from successfully parsing the command line arguments
         * @param out the {@code PrintStream} to print help to if requested
         * @param ansi for printing help messages using ANSI styles and colors
         * @return an empty list if help was requested, or a list containing a single element: the result of calling the
         *      {@code Callable}, or a {@code null} element if the last (sub)command was a {@code Runnable}
         * @throws ParameterException if the {@link HelpCommand HelpCommand} was invoked for an unknown subcommand. Any {@code ParameterExceptions}
         *      thrown from this method are treated as if this exception was thrown during parsing and passed to the {@link IExceptionHandler}
         * @throws ExecutionException if a problem occurred while processing the parse results; use
         *      {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
         */
        public List<Object> handleParseResult(List<CommandLine> parsedCommands, PrintStream out, Ansi ansi) {
            if (printHelpIfRequested(parsedCommands, out, err(), ansi)) { return returnResultOrExit(Collections.emptyList()); }
            return returnResultOrExit(execute(parsedCommands.get(parsedCommands.size() - 1), new ArrayList<Object>()));
        }
        /** Executes the most specific {@code Runnable} or {@code Callable} subcommand.
         * If the last (sub)command does not implement either {@code Runnable} or {@code Callable}, an {@code ExecutionException}
         * is thrown detailing the problem and capturing the offending {@code CommandLine} object.
         *
         * @param parseResult the {@code ParseResult} that resulted from successfully parsing the command line arguments
         * @return an empty list if help was requested, or a list containing a single element: the result of calling the
         *      {@code Callable}, or a {@code null} element if the last (sub)command was a {@code Runnable}
         * @throws ExecutionException if a problem occurred while processing the parse results; use
         *      {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
         * @since 3.0 */
        protected List<Object> handle(ParseResult parseResult) throws ExecutionException {
            List<CommandLine> parsedCommands = parseResult.asCommandLineList();
            return execute(parsedCommands.get(parsedCommands.size() - 1), new ArrayList<Object>());
        }
        @Override protected RunLast self() { return this; }
    }
    /**
     * Command line parse result handler that prints help if requested, and otherwise executes the top-level command and
     * all subcommands as {@code Runnable} or {@code Callable}.
     * For use in the {@link #parseWithHandlers(IParseResultHandler2,  IExceptionHandler2, String...) parseWithHandler} methods.
     * @since 2.0 */
    public static class RunAll extends AbstractParseResultHandler<List<Object>> implements IParseResultHandler {
        /** Prints help if requested, and otherwise executes the top-level command and all subcommands as {@code Runnable}
         * or {@code Callable}. Finally, either a list of result objects is returned, or the JVM is terminated if an exit
         * code {@linkplain #andExit(int) was set}. If any of the {@code CommandLine} commands does not implement either
         * {@code Runnable} or {@code Callable}, an {@code ExecutionException}
         * is thrown detailing the problem and capturing the offending {@code CommandLine} object.
         *
         * @param parsedCommands the {@code CommandLine} objects that resulted from successfully parsing the command line arguments
         * @param out the {@code PrintStream} to print help to if requested
         * @param ansi for printing help messages using ANSI styles and colors
         * @return an empty list if help was requested, or a list containing the result of executing all commands:
         *      the return values from calling the {@code Callable} commands, {@code null} elements for commands that implement {@code Runnable}
         * @throws ParameterException if the {@link HelpCommand HelpCommand} was invoked for an unknown subcommand. Any {@code ParameterExceptions}
         *      thrown from this method are treated as if this exception was thrown during parsing and passed to the {@link IExceptionHandler}
         * @throws ExecutionException if a problem occurred while processing the parse results; use
         *      {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
         */
        public List<Object> handleParseResult(List<CommandLine> parsedCommands, PrintStream out, Ansi ansi) {
            if (printHelpIfRequested(parsedCommands, out, err(), ansi)) { return returnResultOrExit(Collections.emptyList()); }
            List<Object> result = new ArrayList<Object>();
            for (CommandLine parsed : parsedCommands) {
                execute(parsed, result);
            }
            return returnResultOrExit(result);
        }
        /** Executes the top-level command and all subcommands as {@code Runnable} or {@code Callable}.
         * If any of the {@code CommandLine} commands does not implement either {@code Runnable} or {@code Callable}, an {@code ExecutionException}
         * is thrown detailing the problem and capturing the offending {@code CommandLine} object.
         *
         * @param parseResult the {@code ParseResult} that resulted from successfully parsing the command line arguments
         * @return an empty list if help was requested, or a list containing the result of executing all commands:
         *      the return values from calling the {@code Callable} commands, {@code null} elements for commands that implement {@code Runnable}
         * @throws ExecutionException if a problem occurred while processing the parse results; use
         *      {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
         * @since 3.0 */
        protected List<Object> handle(ParseResult parseResult) throws ExecutionException {
            List<Object> result = new ArrayList<Object>();
            execute(parseResult.commandSpec().commandLine(), result);
            while (parseResult.hasSubcommand()) {
                parseResult = parseResult.subcommand();
                execute(parseResult.commandSpec().commandLine(), result);
            }
            return returnResultOrExit(result);
        }
        @Override protected RunAll self() { return this; }
    }

    /** @deprecated use {@link #parseWithHandler(IParseResultHandler2,  String[])} instead
     * @since 2.0 */
    @Deprecated public List<Object> parseWithHandler(IParseResultHandler handler, PrintStream out, String... args) {
        return parseWithHandlers(handler, out, Ansi.AUTO, defaultExceptionHandler(), args);
    }
    /**
     * Returns the result of calling {@link #parseWithHandlers(IParseResultHandler2,  IExceptionHandler2, String...)} with
     * a new {@link DefaultExceptionHandler} in addition to the specified parse result handler and the specified command line arguments.
     * <p>
     * This is a convenience method intended to offer the same ease of use as the {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...) run}
     * and {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...) call} methods, but with more flexibility and better
     * support for nested subcommands.
     * </p>
     * <p>Calling this method roughly expands to:</p>
     * <pre>{@code
     * try {
     *     ParseResult parseResult = parseArgs(args);
     *     return handler.handleParseResult(parseResult);
     * } catch (ParameterException ex) {
     *     return new DefaultExceptionHandler<R>().handleParseException(ex, args);
     * }
     * }</pre>
     * <p>
     * Picocli provides some default handlers that allow you to accomplish some common tasks with very little code.
     * The following handlers are available:</p>
     * <ul>
     *   <li>{@link RunLast} handler prints help if requested, and otherwise gets the last specified command or subcommand
     * and tries to execute it as a {@code Runnable} or {@code Callable}.</li>
     *   <li>{@link RunFirst} handler prints help if requested, and otherwise executes the top-level command as a {@code Runnable} or {@code Callable}.</li>
     *   <li>{@link RunAll} handler prints help if requested, and otherwise executes all recognized commands and subcommands as {@code Runnable} or {@code Callable} tasks.</li>
     *   <li>{@link DefaultExceptionHandler} prints the error message followed by usage help</li>
     * </ul>
     * @param <R> the return type of this handler
     * @param handler the function that will handle the result of successfully parsing the command line arguments
     * @param args the command line arguments
     * @return an object resulting from handling the parse result or the exception that occurred while parsing the input
     * @throws ExecutionException if the command line arguments were parsed successfully but a problem occurred while processing the
     *      parse results; use {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
     * @see RunLast
     * @see RunAll
     * @since 3.0 */
    public <R> R parseWithHandler(IParseResultHandler2<R> handler, String[] args) {
        return parseWithHandlers(handler, new DefaultExceptionHandler<R>(), args);
    }

    /** @deprecated use {@link #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)} instead
     * @since 2.0 */
    @Deprecated public List<Object> parseWithHandlers(IParseResultHandler handler, PrintStream out, Ansi ansi, IExceptionHandler exceptionHandler, String... args) {
        try {
            List<CommandLine> result = parse(args);
            return handler.handleParseResult(result, out, ansi);
        } catch (ParameterException ex) {
            return exceptionHandler.handleException(ex, out, ansi, args);
        }
    }
    /**
     * Tries to {@linkplain #parseArgs(String...) parse} the specified command line arguments, and if successful, delegates
     * the processing of the resulting {@code ParseResult} object to the specified {@linkplain IParseResultHandler2 handler}.
     * If the command line arguments were invalid, the {@code ParameterException} thrown from the {@code parse} method
     * is caught and passed to the specified {@link IExceptionHandler2}.
     * <p>
     * This is a convenience method intended to offer the same ease of use as the {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...) run}
     * and {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...) call} methods, but with more flexibility and better
     * support for nested subcommands.
     * </p>
     * <p>Calling this method roughly expands to:</p>
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
     * Picocli provides some default handlers that allow you to accomplish some common tasks with very little code.
     * The following handlers are available:</p>
     * <ul>
     *   <li>{@link RunLast} handler prints help if requested, and otherwise gets the last specified command or subcommand
     * and tries to execute it as a {@code Runnable} or {@code Callable}.</li>
     *   <li>{@link RunFirst} handler prints help if requested, and otherwise executes the top-level command as a {@code Runnable} or {@code Callable}.</li>
     *   <li>{@link RunAll} handler prints help if requested, and otherwise executes all recognized commands and subcommands as {@code Runnable} or {@code Callable} tasks.</li>
     *   <li>{@link DefaultExceptionHandler} prints the error message followed by usage help</li>
     * </ul>
     *
     * @param handler the function that will handle the result of successfully parsing the command line arguments
     * @param exceptionHandler the function that can handle the {@code ParameterException} thrown when the command line arguments are invalid
     * @param args the command line arguments
     * @return an object resulting from handling the parse result or the exception that occurred while parsing the input
     * @throws ExecutionException if the command line arguments were parsed successfully but a problem occurred while processing the parse
     *      result {@code ParseResult} object; use {@link ExecutionException#getCommandLine()} to get the command or subcommand where processing failed
     * @param <R> the return type of the result handler and exception handler
     * @see RunLast
     * @see RunAll
     * @see DefaultExceptionHandler
     * @since 3.0 */
    public <R> R parseWithHandlers(IParseResultHandler2<R> handler, IExceptionHandler2<R> exceptionHandler, String... args) {
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
     * Equivalent to {@code new CommandLine(command).usage(out)}. See {@link #usage(PrintStream)} for details.
     * @param command the object annotated with {@link Command}, {@link Option} and {@link Parameters}
     * @param out the print stream to print the help message to
     * @throws IllegalArgumentException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     */
    public static void usage(Object command, PrintStream out) {
        toCommandLine(command, new DefaultFactory()).usage(out);
    }

    /**
     * Equivalent to {@code new CommandLine(command).usage(out, ansi)}.
     * See {@link #usage(PrintStream, Help.Ansi)} for details.
     * @param command the object annotated with {@link Command}, {@link Option} and {@link Parameters}
     * @param out the print stream to print the help message to
     * @param ansi whether the usage message should contain ANSI escape codes or not
     * @throws IllegalArgumentException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     */
    public static void usage(Object command, PrintStream out, Ansi ansi) {
        toCommandLine(command, new DefaultFactory()).usage(out, ansi);
    }

    /**
     * Equivalent to {@code new CommandLine(command).usage(out, colorScheme)}.
     * See {@link #usage(PrintStream, Help.ColorScheme)} for details.
     * @param command the object annotated with {@link Command}, {@link Option} and {@link Parameters}
     * @param out the print stream to print the help message to
     * @param colorScheme the {@code ColorScheme} defining the styles for options, parameters and commands when ANSI is enabled
     * @throws IllegalArgumentException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     */
    public static void usage(Object command, PrintStream out, ColorScheme colorScheme) {
        toCommandLine(command, new DefaultFactory()).usage(out, colorScheme);
    }

    /**
     * Delegates to {@link #usage(PrintStream, Help.Ansi)} with the {@linkplain Ansi#AUTO platform default}.
     * @param out the printStream to print to
     * @see #usage(PrintStream, Help.ColorScheme)
     */
    public void usage(PrintStream out) { usage(out, Ansi.AUTO); }
    /**
     * Delegates to {@link #usage(PrintWriter, Help.Ansi)} with the {@linkplain Ansi#AUTO platform default}.
     * @param writer the PrintWriter to print to
     * @see #usage(PrintWriter, Help.ColorScheme)
     * @since 3.0 */
    public void usage(PrintWriter writer) { usage(writer, Ansi.AUTO); }

    /**
     * Delegates to {@link #usage(PrintStream, Help.ColorScheme)} with the {@linkplain Help#defaultColorScheme(CommandLine.Help.Ansi) default color scheme}.
     * @param out the printStream to print to
     * @param ansi whether the usage message should include ANSI escape codes or not
     * @see #usage(PrintStream, Help.ColorScheme)
     */
    public void usage(PrintStream out, Ansi ansi) { usage(out, Help.defaultColorScheme(ansi)); }
    /** Similar to {@link #usage(PrintStream, Help.Ansi)} but with the specified {@code PrintWriter} instead of a {@code PrintStream}.
     * @since 3.0 */
    public void usage(PrintWriter writer, Ansi ansi) { usage(writer, Help.defaultColorScheme(ansi)); }

    /**
     * Prints a usage help message for the annotated command class to the specified {@code PrintStream}.
     * Delegates construction of the usage help message to the {@link Help} inner class and is equivalent to:
     * <pre>
     * Help help = new Help(command).addAllSubcommands(getSubcommands());
     * StringBuilder sb = new StringBuilder()
     *         .append(help.headerHeading())
     *         .append(help.header())
     *         .append(help.synopsisHeading())      //e.g. Usage:
     *         .append(help.synopsis())             //e.g. &lt;main class&gt; [OPTIONS] &lt;command&gt; [COMMAND-OPTIONS] [ARGUMENTS]
     *         .append(help.descriptionHeading())   //e.g. %nDescription:%n%n
     *         .append(help.description())          //e.g. {"Converts foos to bars.", "Use options to control conversion mode."}
     *         .append(help.parameterListHeading()) //e.g. %nPositional parameters:%n%n
     *         .append(help.parameterList())        //e.g. [FILE...] the files to convert
     *         .append(help.optionListHeading())    //e.g. %nOptions:%n%n
     *         .append(help.optionList())           //e.g. -h, --help   displays this help and exits
     *         .append(help.commandListHeading())   //e.g. %nCommands:%n%n
     *         .append(help.commandList())          //e.g.    add       adds the frup to the frooble
     *         .append(help.footerHeading())
     *         .append(help.footer());
     * out.print(sb);
     * </pre>
     * <p>Annotate your class with {@link Command} to control many aspects of the usage help message, including
     * the program name, text of section headings and section contents, and some aspects of the auto-generated sections
     * of the usage help message.
     * <p>To customize the auto-generated sections of the usage help message, like how option details are displayed,
     * instantiate a {@link Help} object and use a {@link TextTable} with more of fewer columns, a custom
     * {@linkplain Layout layout}, and/or a custom option {@linkplain Help.IOptionRenderer renderer}
     * for ultimate control over which aspects of an Option or Field are displayed where.</p>
     * @param out the {@code PrintStream} to print the usage help message to
     * @param colorScheme the {@code ColorScheme} defining the styles for options, parameters and commands when ANSI is enabled
     */
    public void usage(PrintStream out, ColorScheme colorScheme) {
        out.print(getUsageMessage(colorScheme));
    }
    /** Similar to {@link #usage(PrintStream, Help.ColorScheme)}, but with the specified {@code PrintWriter} instead of a {@code PrintStream}.
     * @since 3.0 */
    public void usage(PrintWriter writer, ColorScheme colorScheme) {
        writer.print(getUsageMessage(colorScheme));
    }
    /** Similar to {@link #usage(PrintStream)}, but returns the usage help message as a String instead of printing it to the {@code PrintStream}.
     * @since 3.2 */
    public String getUsageMessage() {
        return helpFactory().createHelp(getCommandSpec(), Help.defaultColorScheme(Ansi.AUTO)).buildUsageMessage();
    }
    /** Similar to {@link #usage(PrintStream, Help.Ansi)}, but returns the usage help message as a String instead of printing it to the {@code PrintStream}.
     * @since 3.2 */
    public String getUsageMessage(Ansi ansi) {
        return helpFactory().createHelp(getCommandSpec(), Help.defaultColorScheme(ansi)).buildUsageMessage();
    }
    /** Similar to {@link #usage(PrintStream, Help.ColorScheme)}, but returns the usage help message as a String instead of printing it to the {@code PrintStream}.
     * @since 3.2 */
    public String getUsageMessage(ColorScheme colorScheme) {
        return helpFactory().createHelp(getCommandSpec(), colorScheme).buildUsageMessage();
    }

    /**
     * Delegates to {@link #printVersionHelp(PrintStream, Help.Ansi)} with the {@linkplain Ansi#AUTO platform default}.
     * @param out the printStream to print to
     * @see #printVersionHelp(PrintStream, Help.Ansi)
     * @since 0.9.8
     */
    public void printVersionHelp(PrintStream out) { printVersionHelp(out, Ansi.AUTO); }

    /**
     * Prints version information from the {@link Command#version()} annotation to the specified {@code PrintStream}.
     * Each element of the array of version strings is printed on a separate line. Version strings may contain
     * <a href="http://picocli.info/#_usage_help_with_styles_and_colors">markup for colors and style</a>.
     * @param out the printStream to print to
     * @param ansi whether the usage message should include ANSI escape codes or not
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
     * Prints version information from the {@link Command#version()} annotation to the specified {@code PrintStream}.
     * Each element of the array of version strings is {@linkplain String#format(String, Object...) formatted} with the
     * specified parameters, and printed on a separate line. Both version strings and parameters may contain
     * <a href="http://picocli.info/#_usage_help_with_styles_and_colors">markup for colors and style</a>.
     * @param out the printStream to print to
     * @param ansi whether the usage message should include ANSI escape codes or not
     * @param params Arguments referenced by the format specifiers in the version strings
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
     * Delegates to {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)} with {@code System.out} for
     * requested usage help messages, {@code System.err} for diagnostic error messages, and {@link Ansi#AUTO}.
     * @param callable the command to call when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param args the command line arguments to parse
     * @param <C> the annotated object must implement Callable
     * @param <T> the return type of the most specific command (must implement {@code Callable})
     * @see #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.0
     */
    public static <C extends Callable<T>, T> T call(C callable, String... args) {
        return call(callable, System.out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)} with {@code System.err} for
     * diagnostic error messages and {@link Ansi#AUTO}.
     * @param callable the command to call when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param out the printStream to print the usage help message to when the user requested help
     * @param args the command line arguments to parse
     * @param <C> the annotated object must implement Callable
     * @param <T> the return type of the most specific command (must implement {@code Callable})
     * @see #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     */
    public static <C extends Callable<T>, T> T call(C callable, PrintStream out, String... args) {
        return call(callable, out, System.err, Ansi.AUTO, args);
    }
    /**
     * Delegates to {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)} with {@code System.err} for diagnostic error messages.
     * @param callable the command to call when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param out the printStream to print the usage help message to when the user requested help
     * @param ansi the ANSI style to use
     * @param args the command line arguments to parse
     * @param <C> the annotated object must implement Callable
     * @param <T> the return type of the most specific command (must implement {@code Callable})
     * @see #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     */
    public static <C extends Callable<T>, T> T call(C callable, PrintStream out, Ansi ansi, String... args) {
        return call(callable, out, System.err, ansi, args);
    }
    /**
     * Convenience method to allow command line application authors to avoid some boilerplate code in their application.
     * The annotated object needs to implement {@link Callable}. Calling this method is equivalent to:
     * <pre>{@code
     * CommandLine cmd = new CommandLine(callable);
     * List<Object> results = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
     *                                              new DefaultExceptionHandler().useErr(err).useAnsi(ansi),
     *                                              args);
     * T result = results == null || results.isEmpty() ? null : (T) results.get(0);
     * return result;
     * }</pre>
     * <p>
     * If the specified Callable command has subcommands, the {@linkplain RunLast last} subcommand specified on the
     * command line is executed.
     * Commands with subcommands may be interested in calling the {@link #parseWithHandler(IParseResultHandler2, String[]) parseWithHandler}
     * method with the {@link RunAll} handler or a custom handler.
     * </p><p>
     * Use {@link #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...) call(Class, IFactory, ...)} instead of this method
     * if you want to use a factory that performs Dependency Injection.
     * </p>
     * @param callable the command to call when {@linkplain #parse(String...) parsing} succeeds.
     * @param out the printStream to print the usage help message to when the user requested help
     * @param err the printStream to print diagnostic messages to
     * @param ansi whether the usage message should include ANSI escape codes or not
     * @param args the command line arguments to parse
     * @param <C> the annotated object must implement Callable
     * @param <T> the return type of the specified {@code Callable}
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help was requested and printed. Otherwise returns the result of calling the Callable
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.0
     */
    public static <C extends Callable<T>, T> T call(C callable, PrintStream out, PrintStream err, Ansi ansi, String... args) {
        CommandLine cmd = new CommandLine(callable);
        List<Object> results = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi), new DefaultExceptionHandler<List<Object>>().useErr(err).useAnsi(ansi), args);
        @SuppressWarnings("unchecked") T result = results == null || results.isEmpty() ? null : (T) results.get(0);
        return result;
    }
    /**
     * Delegates to {@link #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)} with {@code System.out} for
     * requested usage help messages, {@code System.err} for diagnostic error messages, and {@link Ansi#AUTO}.
     * @param callableClass class of the command to call when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param factory the factory responsible for instantiating the specified callable class and potentially inject other components
     * @param args the command line arguments to parse
     * @param <C> the annotated class must implement Callable
     * @param <T> the return type of the most specific command (must implement {@code Callable})
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified class cannot be instantiated by the factory, or does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.2
     */
    public static <C extends Callable<T>, T> T call(Class<C> callableClass, IFactory factory, String... args) {
        return call(callableClass, factory, System.out, System.err, Ansi.AUTO, args);
    }
    /**
     * Delegates to {@link #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)} with
     * {@code System.err} for diagnostic error messages, and {@link Ansi#AUTO}.
     * @param callableClass class of the command to call when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param factory the factory responsible for instantiating the specified callable class and potentially injecting other components
     * @param out the printStream to print the usage help message to when the user requested help
     * @param args the command line arguments to parse
     * @param <C> the annotated class must implement Callable
     * @param <T> the return type of the most specific command (must implement {@code Callable})
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified class cannot be instantiated by the factory, or does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.2
     */
    public static <C extends Callable<T>, T> T call(Class<C> callableClass, IFactory factory, PrintStream out, String... args) {
        return call(callableClass, factory, out, System.err, Ansi.AUTO, args);
    }
    /**
     * Delegates to {@link #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)} with
     * {@code System.err} for diagnostic error messages.
     * @param callableClass class of the command to call when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param factory the factory responsible for instantiating the specified callable class and potentially injecting other components
     * @param out the printStream to print the usage help message to when the user requested help
     * @param ansi the ANSI style to use
     * @param args the command line arguments to parse
     * @param <C> the annotated class must implement Callable
     * @param <T> the return type of the most specific command (must implement {@code Callable})
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified class cannot be instantiated by the factory, or does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help was requested and printed. Otherwise returns the result of calling the Callable
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.2
     */
    public static <C extends Callable<T>, T> T call(Class<C> callableClass, IFactory factory, PrintStream out, Ansi ansi, String... args) {
        return call(callableClass, factory, out, System.err, ansi, args);
    }
    /**
     * Convenience method to allow command line application authors to avoid some boilerplate code in their application.
     * The specified {@linkplain IFactory factory} will create an instance of the specified {@code callableClass};
     * use this method instead of {@link #call(Callable, PrintStream, PrintStream, Help.Ansi, String...) call(Callable, ...)}
     * if you want to use a factory that performs Dependency Injection.
     * The annotated class needs to implement {@link Callable}. Calling this method is equivalent to:
     * <pre>{@code
     * CommandLine cmd = new CommandLine(callableClass, factory);
     * List<Object> results = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
     *                                              new DefaultExceptionHandler().useErr(err).useAnsi(ansi),
     *                                              args);
     * T result = results == null || results.isEmpty() ? null : (T) results.get(0);
     * return result;
     * }</pre>
     * <p>
     * If the specified Callable command has subcommands, the {@linkplain RunLast last} subcommand specified on the
     * command line is executed.
     * Commands with subcommands may be interested in calling the {@link #parseWithHandler(IParseResultHandler2, String[]) parseWithHandler}
     * method with the {@link RunAll} handler or a custom handler.
     * </p>
     * @param callableClass class of the command to call when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param factory the factory responsible for instantiating the specified callable class and potentially injecting other components
     * @param out the printStream to print the usage help message to when the user requested help
     * @param err the printStream to print diagnostic messages to
     * @param ansi the ANSI style to use
     * @param args the command line arguments to parse
     * @param <C> the annotated class must implement Callable
     * @param <T> the return type of the most specific command (must implement {@code Callable})
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified class cannot be instantiated by the factory, or does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Callable throws an exception
     * @return {@code null} if an error occurred while parsing the command line options, or if help was requested and printed. Otherwise returns the result of calling the Callable
     * @see #call(Callable, PrintStream, PrintStream, Help.Ansi, String...)
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.2
     */
    public static <C extends Callable<T>, T> T call(Class<C> callableClass, IFactory factory, PrintStream out, PrintStream err, Ansi ansi, String... args) {
        CommandLine cmd = new CommandLine(callableClass, factory);
        List<Object> results = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi), new DefaultExceptionHandler<List<Object>>().useErr(err).useAnsi(ansi), args);
        @SuppressWarnings("unchecked") T result = results == null || results.isEmpty() ? null : (T) results.get(0);
        return result;
    }

    /**
     * Delegates to {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)} with {@code System.out} for
     * requested usage help messages, {@code System.err} for diagnostic error messages, and {@link Ansi#AUTO}.
     * @param runnable the command to run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param args the command line arguments to parse
     * @param <R> the annotated object must implement Runnable
     * @see #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.0
     */
    public static <R extends Runnable> void run(R runnable, String... args) {
        run(runnable, System.out, System.err, Ansi.AUTO, args);
    }

    /**
     * Delegates to {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)} with {@code System.err} for diagnostic error messages and {@link Ansi#AUTO}.
     * @param runnable the command to run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param out the printStream to print the usage help message to when the user requested help
     * @param args the command line arguments to parse
     * @param <R> the annotated object must implement Runnable
     * @see #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Runnable throws an exception
     * @see #parseWithHandler(IParseResultHandler2, String[])
     * @see RunLast
     */
    public static <R extends Runnable> void run(R runnable, PrintStream out, String... args) {
        run(runnable, out, System.err, Ansi.AUTO, args);
    }
    /**
     * Delegates to {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)} with {@code System.err} for diagnostic error messages.
     * @param runnable the command to run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param out the printStream to print the usage help message to when the user requested help
     * @param ansi whether the usage message should include ANSI escape codes or not
     * @param args the command line arguments to parse
     * @param <R> the annotated object must implement Runnable
     * @see #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     */
    public static <R extends Runnable> void run(R runnable, PrintStream out, Ansi ansi, String... args) {
        run(runnable, out, System.err, ansi, args);
    }
    /**
     * Convenience method to allow command line application authors to avoid some boilerplate code in their application.
     * The annotated object needs to implement {@link Runnable}. Calling this method is equivalent to:
     * <pre>{@code
     * CommandLine cmd = new CommandLine(runnable);
     * cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
     *                       new DefaultExceptionHandler().useErr(err).useAnsi(ansi),
     *                       args);
     * }</pre>
     * <p>
     * If the specified Runnable command has subcommands, the {@linkplain RunLast last} subcommand specified on the
     * command line is executed.
     * Commands with subcommands may be interested in calling the {@link #parseWithHandler(IParseResultHandler2, String[]) parseWithHandler}
     * method with the {@link RunAll} handler or a custom handler.
     * </p><p>
     * From picocli v2.0, this method prints usage help or version help if {@linkplain #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi) requested},
     * and any exceptions thrown by the {@code Runnable} are caught and rethrown wrapped in an {@code ExecutionException}.
     * </p><p>
     * Use {@link #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...) run(Class, IFactory, ...)} instead of this method
     * if you want to use a factory that performs Dependency Injection.
     * </p>
     * @param runnable the command to run when {@linkplain #parse(String...) parsing} succeeds.
     * @param out the printStream to print the usage help message to when the user requested help
     * @param err the printStream to print diagnostic messages to
     * @param ansi whether the usage message should include ANSI escape codes or not
     * @param args the command line arguments to parse
     * @param <R> the annotated object must implement Runnable
     * @throws InitializationException if the specified command object does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @since 3.0
     */
    public static <R extends Runnable> void run(R runnable, PrintStream out, PrintStream err, Ansi ansi, String... args) {
        CommandLine cmd = new CommandLine(runnable);
        cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi), new DefaultExceptionHandler<List<Object>>().useErr(err).useAnsi(ansi), args);
    }
    /**
     * Delegates to {@link #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)} with {@code System.out} for
     * requested usage help messages, {@code System.err} for diagnostic error messages, and {@link Ansi#AUTO}.
     * @param runnableClass class of the command to run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param factory the factory responsible for instantiating the specified Runnable class and potentially injecting other components
     * @param args the command line arguments to parse
     * @param <R> the annotated class must implement Runnable
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified class cannot be instantiated by the factory, or does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.2
     */
    public static <R extends Runnable> void run(Class<R> runnableClass, IFactory factory, String... args) {
        run(runnableClass, factory, System.out, System.err, Ansi.AUTO, args);
    }
    /**
     * Delegates to {@link #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)} with
     * {@code System.err} for diagnostic error messages, and {@link Ansi#AUTO}.
     * @param runnableClass class of the command to run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param factory the factory responsible for instantiating the specified Runnable class and potentially injecting other components
     * @param out the printStream to print the usage help message to when the user requested help
     * @param args the command line arguments to parse
     * @param <R> the annotated class must implement Runnable
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified class cannot be instantiated by the factory, or does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.2
     */
    public static <R extends Runnable> void run(Class<R> runnableClass, IFactory factory, PrintStream out, String... args) {
        run(runnableClass, factory, out, System.err, Ansi.AUTO, args);
    }
    /**
     * Delegates to {@link #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)} with
     * {@code System.err} for diagnostic error messages.
     * @param runnableClass class of the command to run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param factory the factory responsible for instantiating the specified Runnable class and potentially injecting other components
     * @param out the printStream to print the usage help message to when the user requested help
     * @param ansi whether the usage message should include ANSI escape codes or not
     * @param args the command line arguments to parse
     * @param <R> the annotated class must implement Runnable
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified class cannot be instantiated by the factory, or does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.2
     */
    public static <R extends Runnable> void run(Class<R> runnableClass, IFactory factory, PrintStream out, Ansi ansi, String... args) {
        run(runnableClass, factory, out, System.err, ansi, args);
    }
    /**
     * Convenience method to allow command line application authors to avoid some boilerplate code in their application.
     * The specified {@linkplain IFactory factory} will create an instance of the specified {@code runnableClass};
     * use this method instead of {@link #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...) run(Runnable, ...)}
     * if you want to use a factory that performs Dependency Injection.
     * The annotated class needs to implement {@link Runnable}. Calling this method is equivalent to:
     * <pre>{@code
     * CommandLine cmd = new CommandLine(runnableClass, factory);
     * cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
     *                       new DefaultExceptionHandler().useErr(err).useAnsi(ansi),
     *                       args);
     * }</pre>
     * <p>
     * If the specified Runnable command has subcommands, the {@linkplain RunLast last} subcommand specified on the
     * command line is executed.
     * Commands with subcommands may be interested in calling the {@link #parseWithHandler(IParseResultHandler2, String[]) parseWithHandler}
     * method with the {@link RunAll} handler or a custom handler.
     * </p><p>
     * This method prints usage help or version help if {@linkplain #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi) requested},
     * and any exceptions thrown by the {@code Runnable} are caught and rethrown wrapped in an {@code ExecutionException}.
     * </p>
     * @param runnableClass class of the command to run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param factory the factory responsible for instantiating the specified Runnable class and potentially injecting other components
     * @param out the printStream to print the usage help message to when the user requested help
     * @param err the printStream to print diagnostic messages to
     * @param ansi whether the usage message should include ANSI escape codes or not
     * @param args the command line arguments to parse
     * @param <R> the annotated class must implement Runnable
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified class cannot be instantiated by the factory, or does not have a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Runnable throws an exception
     * @see #run(Runnable, PrintStream, PrintStream, Help.Ansi, String...)
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @see RunLast
     * @since 3.2
     */
    public static <R extends Runnable> void run(Class<R> runnableClass, IFactory factory, PrintStream out, PrintStream err, Ansi ansi, String... args) {
        CommandLine cmd = new CommandLine(runnableClass, factory);
        cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi), new DefaultExceptionHandler<List<Object>>().useErr(err).useAnsi(ansi), args);
    }

    /**
     * Delegates to {@link #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)} with {@code System.out} for
     * requested usage help messages, {@code System.err} for diagnostic error messages, and {@link Ansi#AUTO}.
     * @param methodName the {@code @Command}-annotated method to build a {@link CommandSpec} model from,
     *                   and run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param cls the class where the {@code @Command}-annotated method is declared, or a subclass
     * @param args the command line arguments to parse
     * @see #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified method does not have a {@link Command} annotation,
     *      or if the specified class contains multiple {@code @Command}-annotated methods with the specified name
     * @throws ExecutionException if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.6
     */
    public static Object invoke(String methodName, Class<?> cls, String... args) {
        return invoke(methodName, cls, System.out, System.err, Ansi.AUTO, args);
    }
    /**
     * Delegates to {@link #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)} with the specified stream for
     * requested usage help messages, {@code System.err} for diagnostic error messages, and {@link Ansi#AUTO}.
     * @param methodName the {@code @Command}-annotated method to build a {@link CommandSpec} model from,
     *                   and run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param cls the class where the {@code @Command}-annotated method is declared, or a subclass
     * @param out the printstream to print requested help message to
     * @param args the command line arguments to parse
     * @see #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified method does not have a {@link Command} annotation,
     *      or if the specified class contains multiple {@code @Command}-annotated methods with the specified name
     * @throws ExecutionException if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.6
     */
    public static Object invoke(String methodName, Class<?> cls, PrintStream out, String... args) {
        return invoke(methodName, cls, out, System.err, Ansi.AUTO, args);
    }
    /**
     * Delegates to {@link #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)} with the specified stream for
     * requested usage help messages, {@code System.err} for diagnostic error messages, and the specified Ansi mode.
     * @param methodName the {@code @Command}-annotated method to build a {@link CommandSpec} model from,
     *                   and run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param cls the class where the {@code @Command}-annotated method is declared, or a subclass
     * @param out the printstream to print requested help message to
     * @param ansi whether the usage message should include ANSI escape codes or not
     * @param args the command line arguments to parse
     * @see #invoke(String, Class, PrintStream, PrintStream, Help.Ansi, String...)
     * @throws InitializationException if the specified method does not have a {@link Command} annotation,
     *      or if the specified class contains multiple {@code @Command}-annotated methods with the specified name
     * @throws ExecutionException if the Runnable throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.6
     */
    public static Object invoke(String methodName, Class<?> cls, PrintStream out, Ansi ansi, String... args) {
        return invoke(methodName, cls, out, System.err, ansi, args);
    }
    /**
     * Convenience method to allow command line application authors to avoid some boilerplate code in their application.
     * Constructs a {@link CommandSpec} model from the {@code @Option} and {@code @Parameters}-annotated method parameters
     * of the {@code @Command}-annotated method, parses the specified command line arguments and invokes the specified method.
     * Calling this method is equivalent to:
     * <pre>{@code
     * Method commandMethod = getCommandMethods(cls, methodName).get(0);
     * CommandLine cmd = new CommandLine(commandMethod);
     * List<Object> list = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi),
     *                                           new DefaultExceptionHandler().useErr(err).useAnsi(ansi),
     *                                           args);
     * return list == null ? null : list.get(0);
     * }</pre>
     * @param methodName the {@code @Command}-annotated method to build a {@link CommandSpec} model from,
     *                   and run when {@linkplain #parseArgs(String...) parsing} succeeds.
     * @param cls the class where the {@code @Command}-annotated method is declared, or a subclass
     * @param out the printStream to print the usage help message to when the user requested help
     * @param err the printStream to print diagnostic messages to
     * @param ansi whether the usage message should include ANSI escape codes or not
     * @param args the command line arguments to parse
     * @throws InitializationException if the specified method does not have a {@link Command} annotation,
     *      or if the specified class contains multiple {@code @Command}-annotated methods with the specified name
     * @throws ExecutionException if the method throws an exception
     * @see #parseWithHandlers(IParseResultHandler2, IExceptionHandler2, String...)
     * @since 3.6
     */
    public static Object invoke(String methodName, Class<?> cls, PrintStream out, PrintStream err, Ansi ansi, String... args) {
        List<Method> candidates = getCommandMethods(cls, methodName);
        if (candidates.size() != 1) { throw new InitializationException("Expected exactly one @Command-annotated method for " + cls.getName() + "::" + methodName + "(...), but got: " + candidates); }
        Method method = candidates.get(0);
        CommandLine cmd = new CommandLine(method);
        List<Object> list = cmd.parseWithHandlers(new RunLast().useOut(out).useAnsi(ansi), new DefaultExceptionHandler<List<Object>>().useErr(err).useAnsi(ansi), args);
        return list == null ? null : list.get(0);
    }

    /**
     * Helper to get methods of a class annotated with {@link Command @Command} via reflection, optionally filtered by method name (not {@link Command#name() @Command.name}).
     * Methods have to be either public (inherited) members or be declared by {@code cls}, that is "inherited" static or protected methods will not be picked up.
     *
     * @param cls the class to search for methods annotated with {@code @Command}
     * @param methodName if not {@code null}, return only methods whose method name (not {@link Command#name() @Command.name}) equals this string. Ignored if {@code null}.
     * @return the matching command methods, or an empty list
     * @see #invoke(String, Class, String...)
     * @since 3.6.0
     */
    public static List<Method> getCommandMethods(Class<?> cls, String methodName) {
        Set<Method> candidates = new TreeSet<Method>(new Comparator<Method>() {
            public int compare(Method o1, Method o2) { return o1.getName().compareTo(o2.getName()); }
        });
        // traverse public member methods (excludes static/non-public, includes inherited)
        candidates.addAll(Arrays.asList(Assert.notNull(cls, "class").getMethods()));
        // traverse directly declared methods (includes static/non-public, excludes inherited)
        candidates.addAll(Arrays.asList(Assert.notNull(cls, "class").getDeclaredMethods()));

        List<Method> result = new ArrayList<Method>();
        for (Method method : candidates) {
            if (method.isAnnotationPresent(Command.class)) {
                if (methodName == null || methodName.equals(method.getName())) { result.add(method); }
            }
        }
        return result;
    }

    /**
     * Registers the specified type converter for the specified class. When initializing fields annotated with
     * {@link Option}, the field's type is used as a lookup key to find the associated type converter, and this
     * type converter converts the original command line argument string value to the correct type.
     * <p>
     * Java 8 lambdas make it easy to register custom type converters:
     * </p>
     * <pre>
     * commandLine.registerConverter(java.nio.file.Path.class, s -&gt; java.nio.file.Paths.get(s));
     * commandLine.registerConverter(java.time.Duration.class, s -&gt; java.time.Duration.parse(s));</pre>
     * <p>
     * Built-in type converters are pre-registered for the following java 1.5 types:
     * </p>
     * <ul>
     *   <li>all primitive types</li>
     *   <li>all primitive wrapper types: Boolean, Byte, Character, Double, Float, Integer, Long, Short</li>
     *   <li>any enum</li>
     *   <li>java.io.File</li>
     *   <li>java.math.BigDecimal</li>
     *   <li>java.math.BigInteger</li>
     *   <li>java.net.InetAddress</li>
     *   <li>java.net.URI</li>
     *   <li>java.net.URL</li>
     *   <li>java.nio.charset.Charset</li>
     *   <li>java.sql.Time</li>
     *   <li>java.util.Date</li>
     *   <li>java.util.UUID</li>
     *   <li>java.util.regex.Pattern</li>
     *   <li>StringBuilder</li>
     *   <li>CharSequence</li>
     *   <li>String</li>
     * </ul>
     * <p>The specified converter will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment the converter is registered</em>. Subcommands added
     * later will not have this converter added automatically. To ensure a custom type converter is available to all
     * subcommands, register the type converter last, after adding subcommands.</p>
     *
     * @param cls the target class to convert parameter string values to
     * @param converter the class capable of converting string values to the specified target type
     * @param <K> the target type
     * @return this CommandLine object, to allow method chaining
     * @see #addSubcommand(String, Object)
     */
    public <K> CommandLine registerConverter(Class<K> cls, ITypeConverter<K> converter) {
        interpreter.converterRegistry.put(Assert.notNull(cls, "class"), Assert.notNull(converter, "converter"));
        for (CommandLine command : getCommandSpec().commands.values()) {
            command.registerConverter(cls, converter);
        }
        return this;
    }

    /** Returns the String that separates option names from option values when parsing command line options.
     * @return the String the parser uses to separate option names from option values
     * @see ParserSpec#separator() */
    public String getSeparator() { return getCommandSpec().parser().separator(); }

    /** Sets the String the parser uses to separate option names from option values to the specified value.
     * The separator may also be set declaratively with the {@link CommandLine.Command#separator()} annotation attribute.
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param separator the String that separates option names from option values
     * @see ParserSpec#separator(String)
     * @return this {@code CommandLine} object, to allow method chaining */
    public CommandLine setSeparator(String separator) {
        getCommandSpec().parser().separator(Assert.notNull(separator, "separator"));
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setSeparator(separator);
        }
        return this;
    }

    /** Returns the ResourceBundle of this command or {@code null} if no resource bundle is set.
     * @see Command#resourceBundle()
     * @see CommandSpec#resourceBundle()
     * @since 3.6 */
    public ResourceBundle getResourceBundle() { return getCommandSpec().resourceBundle(); }

    /** Sets the ResourceBundle containing usage help message strings.
     * <p>The specified bundle will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will not be impacted. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param bundle the ResourceBundle containing usage help message strings
     * @return this {@code CommandLine} object, to allow method chaining
     * @see Command#resourceBundle()
     * @see CommandSpec#resourceBundle(ResourceBundle)
     * @since 3.6 */
    public CommandLine setResourceBundle(ResourceBundle bundle) {
        getCommandSpec().resourceBundle(bundle);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.getCommandSpec().resourceBundle(bundle);
        }
        return this;
    }

    /** Returns the maximum width of the usage help message. The default is 80.
     * @see UsageMessageSpec#width() */
    public int getUsageHelpWidth() { return getCommandSpec().usageMessage().width(); }

    /** Sets the maximum width of the usage help message. Longer lines are wrapped.
     * <p>The specified setting will be registered with this {@code CommandLine} and the full hierarchy of its
     * subcommands and nested sub-subcommands <em>at the moment this method is called</em>. Subcommands added
     * later will have the default setting. To ensure a setting is applied to all
     * subcommands, call the setter last, after adding subcommands.</p>
     * @param width the maximum width of the usage help message
     * @see UsageMessageSpec#width(int)
     * @return this {@code CommandLine} object, to allow method chaining */
    public CommandLine setUsageHelpWidth(int width) {
        getCommandSpec().usageMessage().width(width);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setUsageHelpWidth(width);
        }
        return this;
    }

    /** Returns the command name (also called program name) displayed in the usage help synopsis.
     * @return the command name (also called program name) displayed in the usage
     * @see CommandSpec#name()
     * @since 2.0 */
    public String getCommandName() { return getCommandSpec().name(); }

    /** Sets the command name (also called program name) displayed in the usage help synopsis to the specified value.
     * Note that this method only modifies the usage help message, it does not impact parsing behaviour.
     * The command name may also be set declaratively with the {@link CommandLine.Command#name()} annotation attribute.
     * @param commandName command name (also called program name) displayed in the usage help synopsis
     * @return this {@code CommandLine} object, to allow method chaining
     * @see CommandSpec#name(String)
     * @since 2.0 */
    public CommandLine setCommandName(String commandName) {
        getCommandSpec().name(Assert.notNull(commandName, "commandName"));
        return this;
    }

    /** Returns whether arguments starting with {@code '@'} should be treated as the path to an argument file and its
     * contents should be expanded into separate arguments for each line in the specified file.
     * This property is {@code true} by default.
     * @return whether "argument files" or {@code @files} should be expanded into their content
     * @since 2.1 */
    public boolean isExpandAtFiles() { return getCommandSpec().parser().expandAtFiles(); }

    /** Sets whether arguments starting with {@code '@'} should be treated as the path to an argument file and its
     * contents should be expanded into separate arguments for each line in the specified file. ({@code true} by default.)
     * @param expandAtFiles whether "argument files" or {@code @files} should be expanded into their content
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 2.1 */
    public CommandLine setExpandAtFiles(boolean expandAtFiles) {
        getCommandSpec().parser().expandAtFiles(expandAtFiles);
        return this;
    }

    /** Returns the character that starts a single-line comment or {@code null} if all content of argument files should
     * be interpreted as arguments (without comments).
     * If specified, all characters from the comment character to the end of the line are ignored.
     * @return the character that starts a single-line comment or {@code null}. The default is {@code '#'}.
     * @since 3.5 */
    public Character getAtFileCommentChar() { return getCommandSpec().parser().atFileCommentChar(); }

    /** Sets the character that starts a single-line comment or {@code null} if all content of argument files should
     * be interpreted as arguments (without comments).
     * If specified, all characters from the comment character to the end of the line are ignored.
     * @param atFileCommentChar the character that starts a single-line comment or {@code null}. The default is {@code '#'}.
     * @return this {@code CommandLine} object, to allow method chaining
     * @since 3.5 */
    public CommandLine setAtFileCommentChar(Character atFileCommentChar) {
        getCommandSpec().parser().atFileCommentChar(atFileCommentChar);
        for (CommandLine command : getCommandSpec().subcommands().values()) {
            command.setAtFileCommentChar(atFileCommentChar);
        }
        return this;
    }
    private static boolean isBoolean(Class<?> type) { return type == Boolean.class || type == Boolean.TYPE; }
    private static CommandLine toCommandLine(Object obj, IFactory factory) { return obj instanceof CommandLine ? (CommandLine) obj : new CommandLine(obj, factory);}
    private static boolean isMultiValue(Class<?> cls) { return cls.isArray() || Collection.class.isAssignableFrom(cls) || Map.class.isAssignableFrom(cls); }

    private static class NoCompletionCandidates implements Iterable<String> {
        public Iterator<String> iterator() { return Collections.<String>emptyList().iterator(); }
    }
    /**
     * <p>
     * Annotate fields in your class with {@code @Option} and picocli will initialize these fields when matching
     * arguments are specified on the command line. In the case of command methods (annotated with {@code @Command}),
     * command options can be defined by annotating method parameters with {@code @Option}.
     * </p><p>
     * Command class example:
     * </p>
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
     *     &#064;Option(names = { "-v", "--verbose"}, description = "Verbose mode. Helpful for troubleshooting. Multiple -v options increase the verbosity.")
     *     private boolean[] verbose;
     *
     *     &#064;Option(names = { "-h", "--help", "-?", "-help"}, usageHelp = true, description = "Display this help and exit")
     *     private boolean help;
     * }
     * </pre>
     * <p>
     * A field cannot be annotated with both {@code @Parameters} and {@code @Option} or a
     * {@code ParameterException} is thrown.
     * </p>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    public @interface Option {
        /**
         * One or more option names. At least one option name is required.
         * <p>
         * Different environments have different conventions for naming options, but usually options have a prefix
         * that sets them apart from parameters.
         * Picocli supports all of the below styles. The default separator is {@code '='}, but this can be configured.
         * </p><p>
         * <b>*nix</b>
         * </p><p>
         * In Unix and Linux, options have a short (single-character) name, a long name or both.
         * Short options
         * (<a href="http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html#tag_12_02">POSIX
         * style</a> are single-character and are preceded by the {@code '-'} character, e.g., {@code `-v'}.
         * <a href="https://www.gnu.org/software/tar/manual/html_node/Long-Options.html">GNU-style</a> long
         * (or <em>mnemonic</em>) options start with two dashes in a row, e.g., {@code `--file'}.
         * </p><p>Picocli supports the POSIX convention that short options can be grouped, with the last option
         * optionally taking a parameter, which may be attached to the option name or separated by a space or
         * a {@code '='} character. The below examples are all equivalent:
         * </p><pre>
         * -xvfFILE
         * -xvf FILE
         * -xvf=FILE
         * -xv --file FILE
         * -xv --file=FILE
         * -x -v --file FILE
         * -x -v --file=FILE
         * </pre><p>
         * <b>DOS</b>
         * </p><p>
         * DOS options mostly have upper case single-character names and start with a single slash {@code '/'} character.
         * Option parameters are separated by a {@code ':'} character. Options cannot be grouped together but
         * must be specified separately. For example:
         * </p><pre>
         * DIR /S /A:D /T:C
         * </pre><p>
         * <b>PowerShell</b>
         * </p><p>
         * Windows PowerShell options generally are a word preceded by a single {@code '-'} character, e.g., {@code `-Help'}.
         * Option parameters are separated by a space or by a {@code ':'} character.
         * </p>
         * @return one or more option names
         */
        String[] names();

        /**
         * Indicates whether this option is required. By default this is false.
         * If an option is required, but a user invokes the program without specifying the required option,
         * a {@link MissingParameterException} is thrown from the {@link #parse(String...)} method.
         * @return whether this option is required
         */
        boolean required() default false;

        /**
         * Set {@code help=true} if this option should disable validation of the remaining arguments:
         * If the {@code help} option is specified, no error message is generated for missing required options.
         * <p>
         * This attribute is useful for special options like help ({@code -h} and {@code --help} on unix,
         * {@code -?} and {@code -Help} on Windows) or version ({@code -V} and {@code --version} on unix,
         * {@code -Version} on Windows).
         * </p>
         * <p>
         * Note that the {@link #parse(String...)} method will not print help documentation. It will only set
         * the value of the annotated field. It is the responsibility of the caller to inspect the annotated fields
         * and take the appropriate action.
         * </p>
         * @return whether this option disables validation of the other arguments
         * @deprecated Use {@link #usageHelp()} and {@link #versionHelp()} instead. See {@link #printHelpIfRequested(List, PrintStream, CommandLine.Help.Ansi)}
         */
        @Deprecated boolean help() default false;

        /**
         * Set {@code usageHelp=true} for the {@code --help} option that triggers display of the usage help message.
         * The <a href="http://picocli.info/#_printing_help_automatically">convenience methods</a> {@code Commandline.call},
         * {@code Commandline.run}, and {@code Commandline.parseWithHandler(s)} will automatically print usage help
         * when an option with {@code usageHelp=true} was specified on the command line.
         * <p>
         * By default, <em>all</em> options and positional parameters are included in the usage help message
         * <em>except when explicitly marked {@linkplain #hidden() hidden}.</em>
         * </p><p>
         * If this option is specified on the command line, picocli will not validate the remaining arguments (so no "missing required
         * option" errors) and the {@link CommandLine#isUsageHelpRequested()} method will return {@code true}.
         * </p><p>
         * Alternatively, consider annotating your command with {@linkplain Command#mixinStandardHelpOptions() @Command(mixinStandardHelpOptions = true)}.
         * </p>
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
         * Set {@code versionHelp=true} for the {@code --version} option that triggers display of the version information.
         * The <a href="http://picocli.info/#_printing_help_automatically">convenience methods</a> {@code Commandline.call},
         * {@code Commandline.run}, and {@code Commandline.parseWithHandler(s)} will automatically print version information
         * when an option with {@code versionHelp=true} was specified on the command line.
         * <p>
         * The version information string is obtained from the command's {@linkplain Command#version() version} annotation
         * or from the {@linkplain Command#versionProvider() version provider}.
         * </p><p>
         * If this option is specified on the command line, picocli will not validate the remaining arguments (so no "missing required
         * option" errors) and the {@link CommandLine#isUsageHelpRequested()} method will return {@code true}.
         * </p><p>
         * Alternatively, consider annotating your command with {@linkplain Command#mixinStandardHelpOptions() @Command(mixinStandardHelpOptions = true)}.
         * </p>
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
         * From picocli 3.2, the usage string may contain variables that are rendered when help is requested.
         * The string {@code ${DEFAULT-VALUE}} is replaced with the default value of the option. This is regardless of
         * the command's {@link Command#showDefaultValues() showDefaultValues} setting or the option's {@link #showDefaultValue() showDefaultValue} setting.
         * The string {@code ${COMPLETION-CANDIDATES}} is replaced with the completion candidates generated by
         * {@link #completionCandidates()} in the description for this option.
         * Also, embedded {@code %n} newline markers are converted to actual newlines.
         * </p>
         * @return the description of this option
         */
        String[] description() default {};

        /**
         * Specifies the minimum number of required parameters and the maximum number of accepted parameters.
         * If an option declares a positive arity, and the user specifies an insufficient number of parameters on the
         * command line, a {@link MissingParameterException} is thrown by the {@link #parse(String...)} method.
         * <p>
         * In many cases picocli can deduce the number of required parameters from the field's type.
         * By default, flags (boolean options) have arity zero,
         * and single-valued type fields (String, int, Integer, double, Double, File, Date, etc) have arity one.
         * Generally, fields with types that cannot hold multiple values can omit the {@code arity} attribute.
         * </p><p>
         * Fields used to capture options with arity two or higher should have a type that can hold multiple values,
         * like arrays or Collections. See {@link #type()} for strongly-typed Collection fields.
         * </p><p>
         * For example, if an option has 2 required parameters and any number of optional parameters,
         * specify {@code @Option(names = "-example", arity = "2..*")}.
         * </p>
         * <b>A note on boolean options</b>
         * <p>
         * By default picocli does not expect boolean options (also called "flags" or "switches") to have a parameter.
         * You can make a boolean option take a required parameter by annotating your field with {@code arity="1"}.
         * For example: </p>
         * <pre>&#064;Option(names = "-v", arity = "1") boolean verbose;</pre>
         * <p>
         * Because this boolean field is defined with arity 1, the user must specify either {@code <program> -v false}
         * or {@code <program> -v true}
         * on the command line, or a {@link MissingParameterException} is thrown by the {@link #parse(String...)}
         * method.
         * </p><p>
         * To make the boolean parameter possible but optional, define the field with {@code arity = "0..1"}.
         * For example: </p>
         * <pre>&#064;Option(names="-v", arity="0..1") boolean verbose;</pre>
         * <p>This will accept any of the below without throwing an exception:</p>
         * <pre>
         * -v
         * -v true
         * -v false
         * </pre>
         * @return how many arguments this option requires
         */
        String arity() default "";

        /**
         * Specify a {@code paramLabel} for the option parameter to be used in the usage help message. If omitted,
         * picocli uses the field name in fish brackets ({@code '<'} and {@code '>'}) by default. Example:
         * <pre>class Example {
         *     &#064;Option(names = {"-o", "--output"}, paramLabel="FILE", description="path of the output file")
         *     private File out;
         *     &#064;Option(names = {"-j", "--jobs"}, arity="0..1", description="Allow N jobs at once; infinite jobs with no arg.")
         *     private int maxJobs = -1;
         * }</pre>
         * <p>By default, the above gives a usage help message like the following:</p><pre>
         * Usage: &lt;main class&gt; [OPTIONS]
         * -o, --output FILE       path of the output file
         * -j, --jobs [&lt;maxJobs&gt;]  Allow N jobs at once; infinite jobs with no arg.
         * </pre>
         * @return name of the option parameter used in the usage help message
         */
        String paramLabel() default "";

        /** Returns whether usage syntax decorations around the {@linkplain #paramLabel() paramLabel} should be suppressed.
         * The default is {@code false}: by default, the paramLabel is surrounded with {@code '['} and {@code ']'} characters
         * if the value is optional and followed by ellipses ("...") when multiple values can be specified.
         * @since 3.6.0 */
        boolean hideParamSyntax() default false;

        /** <p>
         * Optionally specify a {@code type} to control exactly what Class the option parameter should be converted
         * to. This may be useful when the field type is an interface or an abstract class. For example, a field can
         * be declared to have type {@code java.lang.Number}, and annotating {@code @Option(type=Short.class)}
         * ensures that the option parameter value is converted to a {@code Short} before setting the field value.
         * </p><p>
         * For array fields whose <em>component</em> type is an interface or abstract class, specify the concrete <em>component</em> type.
         * For example, a field with type {@code Number[]} may be annotated with {@code @Option(type=Short.class)}
         * to ensure that option parameter values are converted to {@code Short} before adding an element to the array.
         * </p><p>
         * Picocli will use the {@link ITypeConverter} that is
         * {@linkplain #registerConverter(Class, ITypeConverter) registered} for the specified type to convert
         * the raw String values before modifying the field value.
         * </p><p>
         * Prior to 2.0, the {@code type} attribute was necessary for {@code Collection} and {@code Map} fields,
         * but starting from 2.0 picocli will infer the component type from the generic type's type arguments.
         * For example, for a field of type {@code Map<TimeUnit, Long>} picocli will know the option parameter
         * should be split up in key=value pairs, where the key should be converted to a {@code java.util.concurrent.TimeUnit}
         * enum value, and the value should be converted to a {@code Long}. No {@code @Option(type=...)} type attribute
         * is required for this. For generic types with wildcards, picocli will take the specified upper or lower bound
         * as the Class to convert to, unless the {@code @Option} annotation specifies an explicit {@code type} attribute.
         * </p><p>
         * If the field type is a raw collection or a raw map, and you want it to contain other values than Strings,
         * or if the generic type's type arguments are interfaces or abstract classes, you may
         * specify a {@code type} attribute to control the Class that the option parameter should be converted to.
         * @return the type(s) to convert the raw String values
         */
        Class<?>[] type() default {};

        /**
         * Optionally specify one or more {@link ITypeConverter} classes to use to convert the command line argument into
         * a strongly typed value (or key-value pair for map fields). This is useful when a particular field should
         * use a custom conversion that is different from the normal conversion for the field's type.
         * <p>For example, for a specific field you may want to use a converter that maps the constant names defined
         * in {@link java.sql.Types java.sql.Types} to the {@code int} value of these constants, but any other {@code int} fields should
         * not be affected by this and should continue to use the standard int converter that parses numeric values.</p>
         * @return the type converter(s) to use to convert String values to strongly typed values for this field
         * @see CommandLine#registerConverter(Class, ITypeConverter)
         */
        Class<? extends ITypeConverter<?>>[] converter() default {};

        /**
         * Specify a regular expression to use to split option parameter values before applying them to the field.
         * All elements resulting from the split are added to the array or Collection. Ignored for single-value fields.
         * @return a regular expression to split option parameter values or {@code ""} if the value should not be split
         * @see String#split(String)
         */
        String split() default "";

        /**
         * Set {@code hidden=true} if this option should not be included in the usage help message.
         * @return whether this option should be excluded from the usage documentation
         */
        boolean hidden() default false;

        /** Returns the default value of this option, before splitting and type conversion.
         * @return a String that (after type conversion) will be used as the value for this option if no value was specified on the command line
         * @since 3.2 */
        String defaultValue() default "__no_default_value__";

        /** Use this attribute to control for a specific option whether its default value should be shown in the usage
         * help message. If not specified, the default value is only shown when the {@link Command#showDefaultValues()}
         * is set {@code true} on the command. Use this attribute to specify whether the default value
         * for this specific option should always be shown or never be shown, regardless of the command setting.
         * <p>Note that picocli 3.2 allows {@linkplain #description() embedding default values} anywhere in the description that ignores this setting.</p>
         * @return whether this option's default value should be shown in the usage help message
         */
        Help.Visibility showDefaultValue() default Help.Visibility.ON_DEMAND;

        /** Use this attribute to specify an {@code Iterable<String>} class that generates completion candidates for this option.
         * For map fields, completion candidates should be in {@code key=value} form.
         * <p>
         * Completion candidates are used in bash completion scripts generated by the {@code picocli.AutoComplete} class.
         * Bash has special completion options to generate file names and host names, and the bash completion scripts
         * generated by {@code AutoComplete} delegate to these bash built-ins for {@code @Options} whose {@code type} is
         * {@code java.io.File}, {@code java.nio.file.Path} or {@code java.net.InetAddress}.
         * </p><p>
         * For {@code @Options} whose {@code type} is a Java {@code enum}, {@code AutoComplete} can generate completion
         * candidates from the type. For other types, use this attribute to specify completion candidates.
         * </p>
         *
         * @return a class whose instances can iterate over the completion candidates for this option
         * @see picocli.CommandLine.IFactory
         * @since 3.2 */
        Class<? extends Iterable<String>> completionCandidates() default NoCompletionCandidates.class;

        /**
         * Set {@code interactive=true} if this option will prompt the end user for a value (like a password).
         * Only supported for single-value options (not arrays, collections or maps).
         * When running on Java 6 or greater, this will use the {@link Console#readPassword()} API to get a value without echoing input to the console.
         * @return whether this option prompts the end user for a value to be entered on the command line
         * @since 3.5
         */
        boolean interactive() default false;

        /** ResourceBundle key for this option. If not specified, (and a ResourceBundle {@linkplain Command#resourceBundle() exists for this command}) an attempt
         * is made to find the option description using any of the option names (without leading hyphens) as key.
         * @see OptionSpec#description()
         * @since 3.6
         */
        String descriptionKey() default "";
    }
    /**
     * <p>
     * Fields annotated with {@code @Parameters} will be initialized with positional parameters. By specifying the
     * {@link #index()} attribute you can pick the exact position or a range of positional parameters to apply. If no
     * index is specified, the field will get all positional parameters (and so it should be an array or a collection).
     * </p><p>
     * In the case of command methods (annotated with {@code @Command}), method parameters may be annotated with {@code @Parameters},
     * but are are considered positional parameters by default, unless they are annotated with {@code @Option}.
     * </p><p>
     * Command class example:
     * </p>
     * <pre>
     * import static picocli.CommandLine.*;
     *
     * public class MyCalcParameters {
     *     &#064;Parameters(description = "Any number of input numbers")
     *     private List&lt;BigDecimal&gt; files = new ArrayList&lt;BigDecimal&gt;();
     *
     *     &#064;Option(names = { "-h", "--help" }, usageHelp = true, description = "Display this help and exit")
     *     private boolean help;
     * }
     * </pre><p>
     * A field cannot be annotated with both {@code @Parameters} and {@code @Option} or a {@code ParameterException}
     * is thrown.</p>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    public @interface Parameters {
        /** Specify an index ("0", or "1", etc.) to pick which of the command line arguments should be assigned to this
         * field. For array or Collection fields, you can also specify an index range ("0..3", or "2..*", etc.) to assign
         * a subset of the command line arguments to this field. The default is "*", meaning all command line arguments.
         * @return an index or range specifying which of the command line arguments should be assigned to this field
         */
        String index() default "";

        /** Description of the parameter(s), used when generating the usage documentation.
         * <p>
         * From picocli 3.2, the usage string may contain variables that are rendered when help is requested.
         * The string {@code ${DEFAULT-VALUE}} is replaced with the default value of the positional parameter. This is regardless of
         * the command's {@link Command#showDefaultValues() showDefaultValues} setting or the positional parameter's {@link #showDefaultValue() showDefaultValue} setting.
         * The string {@code ${COMPLETION-CANDIDATES}} is replaced with the completion candidates generated by
         * {@link #completionCandidates()} in the description for this positional parameter.
         * Also, embedded {@code %n} newline markers are converted to actual newlines.
         * </p>
         * @return the description of the parameter(s)
         */
        String[] description() default {};

        /**
         * Specifies the minimum number of required parameters and the maximum number of accepted parameters. If a
         * positive arity is declared, and the user specifies an insufficient number of parameters on the command line,
         * {@link MissingParameterException} is thrown by the {@link #parse(String...)} method.
         * <p>The default depends on the type of the parameter: booleans require no parameters, arrays and Collections
         * accept zero to any number of parameters, and any other type accepts one parameter.</p>
         * @return the range of minimum and maximum parameters accepted by this command
         */
        String arity() default "";

        /**
         * Specify a {@code paramLabel} for the parameter to be used in the usage help message. If omitted,
         * picocli uses the field name in fish brackets ({@code '<'} and {@code '>'}) by default. Example:
         * <pre>class Example {
         *     &#064;Parameters(paramLabel="FILE", description="path of the input FILE(s)")
         *     private File[] inputFiles;
         * }</pre>
         * <p>By default, the above gives a usage help message like the following:</p><pre>
         * Usage: &lt;main class&gt; [FILE...]
         * [FILE...]       path of the input FILE(s)
         * </pre>
         * @return name of the positional parameter used in the usage help message
         */
        String paramLabel() default "";

        /** Returns whether usage syntax decorations around the {@linkplain #paramLabel() paramLabel} should be suppressed.
         * The default is {@code false}: by default, the paramLabel is surrounded with {@code '['} and {@code ']'} characters
         * if the value is optional and followed by ellipses ("...") when multiple values can be specified.
         * @since 3.6.0 */
        boolean hideParamSyntax() default false;

        /**
         * <p>
         * Optionally specify a {@code type} to control exactly what Class the positional parameter should be converted
         * to. This may be useful when the field type is an interface or an abstract class. For example, a field can
         * be declared to have type {@code java.lang.Number}, and annotating {@code @Parameters(type=Short.class)}
         * ensures that the positional parameter value is converted to a {@code Short} before setting the field value.
         * </p><p>
         * For array fields whose <em>component</em> type is an interface or abstract class, specify the concrete <em>component</em> type.
         * For example, a field with type {@code Number[]} may be annotated with {@code @Parameters(type=Short.class)}
         * to ensure that positional parameter values are converted to {@code Short} before adding an element to the array.
         * </p><p>
         * Picocli will use the {@link ITypeConverter} that is
         * {@linkplain #registerConverter(Class, ITypeConverter) registered} for the specified type to convert
         * the raw String values before modifying the field value.
         * </p><p>
         * Prior to 2.0, the {@code type} attribute was necessary for {@code Collection} and {@code Map} fields,
         * but starting from 2.0 picocli will infer the component type from the generic type's type arguments.
         * For example, for a field of type {@code Map<TimeUnit, Long>} picocli will know the positional parameter
         * should be split up in key=value pairs, where the key should be converted to a {@code java.util.concurrent.TimeUnit}
         * enum value, and the value should be converted to a {@code Long}. No {@code @Parameters(type=...)} type attribute
         * is required for this. For generic types with wildcards, picocli will take the specified upper or lower bound
         * as the Class to convert to, unless the {@code @Parameters} annotation specifies an explicit {@code type} attribute.
         * </p><p>
         * If the field type is a raw collection or a raw map, and you want it to contain other values than Strings,
         * or if the generic type's type arguments are interfaces or abstract classes, you may
         * specify a {@code type} attribute to control the Class that the positional parameter should be converted to.
         * @return the type(s) to convert the raw String values
         */
        Class<?>[] type() default {};

        /**
         * Optionally specify one or more {@link ITypeConverter} classes to use to convert the command line argument into
         * a strongly typed value (or key-value pair for map fields). This is useful when a particular field should
         * use a custom conversion that is different from the normal conversion for the field's type.
         * <p>For example, for a specific field you may want to use a converter that maps the constant names defined
         * in {@link java.sql.Types java.sql.Types} to the {@code int} value of these constants, but any other {@code int} fields should
         * not be affected by this and should continue to use the standard int converter that parses numeric values.</p>
         * @return the type converter(s) to use to convert String values to strongly typed values for this field
         * @see CommandLine#registerConverter(Class, ITypeConverter)
         */
        Class<? extends ITypeConverter<?>>[] converter() default {};

        /**
         * Specify a regular expression to use to split positional parameter values before applying them to the field.
         * All elements resulting from the split are added to the array or Collection. Ignored for single-value fields.
         * @return a regular expression to split operand values or {@code ""} if the value should not be split
         * @see String#split(String)
         */
        String split() default "";

        /**
         * Set {@code hidden=true} if this parameter should not be included in the usage message.
         * @return whether this parameter should be excluded from the usage message
         */
        boolean hidden() default false;

        /** Returns the default value of this positional parameter, before splitting and type conversion.
         * @return a String that (after type conversion) will be used as the value for this positional parameter if no value was specified on the command line
         * @since 3.2 */
        String defaultValue() default "__no_default_value__";

        /** Use this attribute to control for a specific positional parameter whether its default value should be shown in the usage
         * help message. If not specified, the default value is only shown when the {@link Command#showDefaultValues()}
         * is set {@code true} on the command. Use this attribute to specify whether the default value
         * for this specific positional parameter should always be shown or never be shown, regardless of the command setting.
         * <p>Note that picocli 3.2 allows {@linkplain #description() embedding default values} anywhere in the description that ignores this setting.</p>
         * @return whether this positional parameter's default value should be shown in the usage help message
         */
        Help.Visibility showDefaultValue() default Help.Visibility.ON_DEMAND;

        /** Use this attribute to specify an {@code Iterable<String>} class that generates completion candidates for
         * this positional parameter. For map fields, completion candidates should be in {@code key=value} form.
         * <p>
         * Completion candidates are used in bash completion scripts generated by the {@code picocli.AutoComplete} class.
         * Unfortunately, {@code picocli.AutoComplete} is not very good yet at generating completions for positional parameters.
         * </p>
         *
         * @return a class whose instances can iterate over the completion candidates for this positional parameter
         * @see picocli.CommandLine.IFactory
         * @since 3.2 */
        Class<? extends Iterable<String>> completionCandidates() default NoCompletionCandidates.class;

        /**
         * Set {@code interactive=true} if this positional parameter will prompt the end user for a value (like a password).
         * Only supported for single-value positional parameters (not arrays, collections or maps).
         * When running on Java 6 or greater, this will use the {@link Console#readPassword()} API to get a value without echoing input to the console.
         * @return whether this positional parameter prompts the end user for a value to be entered on the command line
         * @since 3.5
         */
        boolean interactive() default false;

        /** ResourceBundle key for this option. If not specified, (and a ResourceBundle {@linkplain Command#resourceBundle() exists for this command}) an attempt
         * is made to find the positional parameter description using {@code paramLabel() + "[" + index() + "]"} as key.
         *
         * @see PositionalParamSpec#description()
         * @since 3.6
         */
        String descriptionKey() default "";
    }

    /**
     * <p>
     * Fields annotated with {@code @ParentCommand} will be initialized with the parent command of the current subcommand.
     * If the current command does not have a parent command, this annotation has no effect.
     * </p><p>
     * Parent commands often define options that apply to all the subcommands.
     * This annotation offers a convenient way to inject a reference to the parent command into a subcommand, so the
     * subcommand can access its parent options. For example:
     * </p><pre>
     * &#064;Command(name = "top", subcommands = Sub.class)
     * class Top implements Runnable {
     *
     *     &#064;Option(names = {"-d", "--directory"}, description = "this option applies to all subcommands")
     *     File baseDirectory;
     *
     *     public void run() { System.out.println("Hello from top"); }
     * }
     *
     * &#064;Command(name = "sub")
     * class Sub implements Runnable {
     *
     *     &#064;ParentCommand
     *     private Top parent;
     *
     *     public void run() {
     *         System.out.println("Subcommand: parent command 'directory' is " + parent.baseDirectory);
     *     }
     * }
     * </pre>
     * @since 2.2
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ParentCommand { }

    /**
     * Fields annotated with {@code @Unmatched} will be initialized with the list of unmatched command line arguments, if any.
     * If this annotation is found, picocli automatically sets {@linkplain CommandLine#setUnmatchedArgumentsAllowed(boolean) unmatchedArgumentsAllowed} to {@code true}.
     * @see CommandLine#isUnmatchedArgumentsAllowed()
     * @since 3.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Unmatched { }

    /**
     * <p>
     * Fields annotated with {@code @Mixin} are "expanded" into the current command: {@link Option @Option} and
     * {@link Parameters @Parameters} in the mixin class are added to the options and positional parameters of this command.
     * A {@link DuplicateOptionAnnotationsException} is thrown if any of the options in the mixin has the same name as
     * an option in this command.
     * </p><p>
     * The {@code Mixin} annotation provides a way to reuse common options and parameters without subclassing. For example:
     * </p><pre>
     * class HelloWorld implements Runnable {
     *
     *     // adds the --help and --version options to this command
     *     &#064;Mixin
     *     private HelpOptions = new HelpOptions();
     *
     *     &#064;Option(names = {"-u", "--userName"}, required = true, description = "The user name")
     *     String userName;
     *
     *     public void run() { System.out.println("Hello, " + userName); }
     * }
     *
     * // Common reusable help options.
     * class HelpOptions {
     *
     *     &#064;Option(names = { "-h", "--help"}, usageHelp = true, description = "Display this help and exit")
     *     private boolean help;
     *
     *     &#064;Option(names = { "-V", "--version"}, versionHelp = true, description = "Display version info and exit")
     *     private boolean versionHelp;
     * }
     * </pre>
     * @since 3.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    public @interface Mixin {
        /** Optionally specify a name that the mixin object can be retrieved with from the {@code CommandSpec}.
         * If not specified the name of the annotated field is used.
         * @return a String to register the mixin object with, or an empty String if the name of the annotated field should be used */
        String name() default "";
    }
    /**
     * Fields annotated with {@code @Spec} will be initialized with the {@code CommandSpec} for the command the field is part of. Example usage:
     * <pre>
     * class InjectSpecExample implements Runnable {
     *     &#064;Spec CommandSpec commandSpec;
     *     //...
     *     public void run() {
     *         // do something with the injected objects
     *     }
     * }
     * </pre>
     * @since 3.2
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    public @interface Spec { }
    /**
     * <p>Annotate your class with {@code @Command} when you want more control over the format of the generated help
     * message. From 3.6, methods can also be annotated with {@code @Command}, where the method parameters define the
     * command options and positional parameters.
     * </p><pre>
     * &#064;Command(name      = "Encrypt", mixinStandardHelpOptions = true,
     *        description = "Encrypt FILE(s), or standard input, to standard output or to the output file.",
     *        version     = "Encrypt version 1.0",
     *        footer      = "Copyright (c) 2017")
     * public class Encrypt {
     *     &#064;Parameters(paramLabel = "FILE", description = "Any number of input files")
     *     private List&lt;File&gt; files = new ArrayList&lt;File&gt;();
     *
     *     &#064;Option(names = { "-o", "--out" }, description = "Output file (default: print to console)")
     *     private File outputFile;
     *
     *     &#064;Option(names = { "-v", "--verbose"}, description = "Verbose mode. Helpful for troubleshooting. Multiple -v options increase the verbosity.")
     *     private boolean[] verbose;
     * }</pre>
     * <p>
     * The structure of a help message looks like this:
     * </p><ul>
     *   <li>[header]</li>
     *   <li>[synopsis]: {@code Usage: <commandName> [OPTIONS] [FILE...]}</li>
     *   <li>[description]</li>
     *   <li>[parameter list]: {@code      [FILE...]   Any number of input files}</li>
     *   <li>[option list]: {@code   -h, --help   prints this help message and exits}</li>
     *   <li>[footer]</li>
     * </ul> */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.PACKAGE, ElementType.METHOD})
    public @interface Command {
        /** Program name to show in the synopsis. If omitted, {@code "<main class>"} is used.
         * For {@linkplain #subcommands() declaratively added} subcommands, this attribute is also used
         * by the parser to recognize subcommands in the command line arguments.
         * @return the program name to show in the synopsis
         * @see CommandSpec#name()
         * @see Help#commandName() */
        String name() default "<main class>";

        /** Alternative command names by which this subcommand is recognized on the command line.
         * @return one or more alternative command names
         * @since 3.1 */
        String[] aliases() default {};

        /** A list of classes to instantiate and register as subcommands. When registering subcommands declaratively
         * like this, you don't need to call the {@link CommandLine#addSubcommand(String, Object)} method. For example, this:
         * <pre>
         * &#064;Command(subcommands = {
         *         GitStatus.class,
         *         GitCommit.class,
         *         GitBranch.class })
         * public class Git { ... }
         *
         * CommandLine commandLine = new CommandLine(new Git());
         * </pre> is equivalent to this:
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
         * @return the declaratively registered subcommands of this command, or an empty array if none
         * @see CommandLine#addSubcommand(String, Object)
         * @see HelpCommand
         * @since 0.9.8
         */
        Class<?>[] subcommands() default {};

        /** Specify whether methods annotated with {@code @Command} should be registered as subcommands of their
         * enclosing {@code @Command} class.
         * The default is {@code true}. For example:
         * <pre>
         * &#064;Command
         * public class Git {
         *     &#064;Command
         *     void status() { ... }
         * }
         *
         * CommandLine git = new CommandLine(new Git());
         * </pre> is equivalent to this:
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
         * @return whether methods annotated with {@code @Command} should be registered as subcommands
         * @see CommandLine#addSubcommand(String, Object)
         * @see CommandLine#getCommandMethods(Class, String)
         * @see CommandSpec#addMethodSubcommands()
         * @since 3.6.0 */
        boolean addMethodSubcommands() default true;

        /** String that separates options from option parameters. Default is {@code "="}. Spaces are also accepted.
         * @return the string that separates options from option parameters, used both when parsing and when generating usage help
         * @see CommandLine#setSeparator(String) */
        String separator() default "=";

        /** Version information for this command, to print to the console when the user specifies an
         * {@linkplain Option#versionHelp() option} to request version help. This is not part of the usage help message.
         *
         * @return a string or an array of strings with version information about this command (each string in the array is displayed on a separate line).
         * @since 0.9.8
         * @see CommandLine#printVersionHelp(PrintStream)
         */
        String[] version() default {};

        /** Class that can provide version information dynamically at runtime. An implementation may return version
         * information obtained from the JAR manifest, a properties file or some other source.
         * @return a Class that can provide version information dynamically at runtime
         * @since 2.2 */
        Class<? extends IVersionProvider> versionProvider() default NoVersionProvider.class;

        /**
         * Adds the standard {@code -h} and {@code --help} {@linkplain Option#usageHelp() usageHelp} options and {@code -V}
         * and {@code --version} {@linkplain Option#versionHelp() versionHelp} options to the options of this command.
         * <p>
         * Note that if no {@link #version()} or {@link #versionProvider()} is specified, the {@code --version} option will not print anything.
         * </p><p>
         * For {@linkplain #resourceBundle() internationalization}: the help option has {@code descriptionKey = "mixinStandardHelpOptions.help"},
         * and the version option has {@code descriptionKey = "mixinStandardHelpOptions.version"}.
         * </p>
         * @return whether the auto-help mixin should be added to this command
         * @since 3.0 */
        boolean mixinStandardHelpOptions() default false;

        /** Set this attribute to {@code true} if this subcommand is a help command, and required options and positional
         * parameters of the parent command should not be validated. If a subcommand marked as {@code helpCommand} is
         * specified on the command line, picocli will not validate the parent arguments (so no "missing required
         * option" errors) and the {@link CommandLine#printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi)} method will return {@code true}.
         * @return {@code true} if this subcommand is a help command and picocli should not check for missing required
         *      options and positional parameters on the parent command
         * @since 3.0 */
        boolean helpCommand() default false;

        /** Set the heading preceding the header section. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * @return the heading preceding the header section
         * @see UsageMessageSpec#headerHeading()
         * @see Help#headerHeading(Object...)  */
        String headerHeading() default "";

        /** Optional summary description of the command, shown before the synopsis.
         * @return summary description of the command
         * @see UsageMessageSpec#header()
         * @see Help#header(Object...)  */
        String[] header() default {};

        /** Set the heading preceding the synopsis text. May contain embedded
         * {@linkplain java.util.Formatter format specifiers}. The default heading is {@code "Usage: "} (without a line
         * break between the heading and the synopsis text).
         * @return the heading preceding the synopsis text
         * @see Help#synopsisHeading(Object...)  */
        String synopsisHeading() default "Usage: ";

        /** Specify {@code true} to generate an abbreviated synopsis like {@code "<main> [OPTIONS] [PARAMETERS...]"}.
         * By default, a detailed synopsis with individual option names and parameters is generated.
         * @return whether the synopsis should be abbreviated
         * @see Help#abbreviatedSynopsis()
         * @see Help#detailedSynopsis(Comparator, boolean) */
        boolean abbreviateSynopsis() default false;

        /** Specify one or more custom synopsis lines to display instead of an auto-generated synopsis.
         * @return custom synopsis text to replace the auto-generated synopsis
         * @see Help#customSynopsis(Object...) */
        String[] customSynopsis() default {};

        /** Set the heading preceding the description section. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * @return the heading preceding the description section
         * @see Help#descriptionHeading(Object...)  */
        String descriptionHeading() default "";

        /** Optional text to display between the synopsis line(s) and the list of options.
         * @return description of this command
         * @see Help#description(Object...) */
        String[] description() default {};

        /** Set the heading preceding the parameters list. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * @return the heading preceding the parameters list
         * @see Help#parameterListHeading(Object...)  */
        String parameterListHeading() default "";

        /** Set the heading preceding the options list. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * @return the heading preceding the options list
         * @see Help#optionListHeading(Object...)  */
        String optionListHeading() default "";

        /** Specify {@code false} to show Options in declaration order. The default is to sort alphabetically.
         * @return whether options should be shown in alphabetic order. */
        boolean sortOptions() default true;

        /** Prefix required options with this character in the options list. The default is no marker: the synopsis
         * indicates which options and parameters are required.
         * @return the character to show in the options list to mark required options */
        char requiredOptionMarker() default ' ';

        /** Class that can provide default values dynamically at runtime. An implementation may return default
         * value obtained from a configuration file like a properties file or some other source.
         * @return a Class that can provide default values dynamically at runtime
         * @since 3.6 */
        Class<? extends IDefaultValueProvider> defaultValueProvider() default NoDefaultProvider.class;

        /** Specify {@code true} to show default values in the description column of the options list (except for
         * boolean options). False by default.
         * <p>Note that picocli 3.2 allows {@linkplain Option#description() embedding default values} anywhere in the
         * option or positional parameter description that ignores this setting.</p>
         * @return whether the default values for options and parameters should be shown in the description column */
        boolean showDefaultValues() default false;

        /** Set the heading preceding the subcommands list. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * The default heading is {@code "Commands:%n"} (with a line break at the end).
         * @return the heading preceding the subcommands list
         * @see Help#commandListHeading(Object...)  */
        String commandListHeading() default "Commands:%n";

        /** Set the heading preceding the footer section. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * @return the heading preceding the footer section
         * @see Help#footerHeading(Object...)  */
        String footerHeading() default "";

        /** Optional text to display after the list of options.
         * @return text to display after the list of options
         * @see Help#footer(Object...) */
        String[] footer() default {};

        /**
         * Set {@code hidden=true} if this command should not be included in the list of commands in the usage help of the parent command.
         * @return whether this command should be excluded from the usage message
         * @since 3.0
         */
        boolean hidden() default false;

        /** Set the base name of the ResourceBundle to find option and positional parameters descriptions, as well as
         * usage help message sections and section headings. <p>See {@link Messages} for more details and an example.</p>
         * @return the base name of the ResourceBundle for usage help strings
         * @see ArgSpec#messages()
         * @see UsageMessageSpec#messages()
         * @see CommandSpec#resourceBundle()
         * @see CommandLine#setResourceBundle(ResourceBundle)
         * @since 3.6
         */
        String resourceBundle() default "";

        /** Set the {@link UsageMessageSpec#width(int) usage help message width}. The default is 80.
         * @since 3.7
         */
        int usageHelpWidth() default 80;
    }
    /**
     * <p>
     * When parsing command line arguments and initializing
     * fields annotated with {@link Option @Option} or {@link Parameters @Parameters},
     * String values can be converted to any type for which a {@code ITypeConverter} is registered.
     * </p><p>
     * This interface defines the contract for classes that know how to convert a String into some domain object.
     * Custom converters can be registered with the {@link #registerConverter(Class, ITypeConverter)} method.
     * </p><p>
     * Java 8 lambdas make it easy to register custom type converters:
     * </p>
     * <pre>
     * commandLine.registerConverter(java.nio.file.Path.class, s -&gt; java.nio.file.Paths.get(s));
     * commandLine.registerConverter(java.time.Duration.class, s -&gt; java.time.Duration.parse(s));</pre>
     * <p>
     * Built-in type converters are pre-registered for the following java 1.5 types:
     * </p>
     * <ul>
     *   <li>all primitive types</li>
     *   <li>all primitive wrapper types: Boolean, Byte, Character, Double, Float, Integer, Long, Short</li>
     *   <li>any enum</li>
     *   <li>java.io.File</li>
     *   <li>java.math.BigDecimal</li>
     *   <li>java.math.BigInteger</li>
     *   <li>java.net.InetAddress</li>
     *   <li>java.net.URI</li>
     *   <li>java.net.URL</li>
     *   <li>java.nio.charset.Charset</li>
     *   <li>java.sql.Time</li>
     *   <li>java.util.Date</li>
     *   <li>java.util.UUID</li>
     *   <li>java.util.regex.Pattern</li>
     *   <li>StringBuilder</li>
     *   <li>CharSequence</li>
     *   <li>String</li>
     * </ul>
     * @param <K> the type of the object that is the result of the conversion
     */
    public interface ITypeConverter<K> {
        /**
         * Converts the specified command line argument value to some domain object.
         * @param value the command line argument String value
         * @return the resulting domain object
         * @throws Exception an exception detailing what went wrong during the conversion
         */
        K convert(String value) throws Exception;
    }

    /**
     * Provides version information for a command. Commands may configure a provider with the
     * {@link Command#versionProvider()} annotation attribute.
     * @since 2.2 */
    public interface IVersionProvider {
        /**
         * Returns version information for a command.
         * @return version information (each string in the array is displayed on a separate line)
         * @throws Exception an exception detailing what went wrong when obtaining version information
         */
        String[] getVersion() throws Exception;
    }
    private static class NoVersionProvider implements IVersionProvider {
        public String[] getVersion() throws Exception { throw new UnsupportedOperationException(); }
    }

    /**
     * Provides default value for a command. Commands may configure a provider with the
     * {@link Command#defaultValueProvider()} annotation attribute.
     * @since 3.6 */
    public interface IDefaultValueProvider {

        /** Returns the default value for an option or positional parameter or {@code null}.
        * The returned value is converted to the type of the option/positional parameter
        * via the same type converter used when populating this option/positional
        * parameter from a command line argument.
        * @param argSpec the option or positional parameter, never {@code null}
        * @return the default value for the option or positional parameter, or {@code null} if
        *       this provider has no default value for the specified option or positional parameter
        * @throws Exception when there was a problem obtaining the default value
        */
        String defaultValue(ArgSpec argSpec) throws Exception;
    }
    private static class NoDefaultProvider implements IDefaultValueProvider {
        public String defaultValue(ArgSpec argSpec) { throw new UnsupportedOperationException(); }
    }

    /**
     * Factory for instantiating classes that are registered declaratively with annotation attributes, like
     * {@link Command#subcommands()}, {@link Option#converter()}, {@link Parameters#converter()} and {@link Command#versionProvider()}.
     * <p>The default factory implementation simply creates a new instance of the specified class when {@link #create(Class)} is invoked.
     * </p><p>
     * You may provide a custom implementation of this interface.
     * For example, a custom factory implementation could delegate to a dependency injection container that provides the requested instance.
     * </p>
     * @see picocli.CommandLine#CommandLine(Object, IFactory)
     * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
     * @since 2.2 */
    public interface IFactory {
        /**
         * Returns an instance of the specified class.
         * @param cls the class of the object to return
         * @param <K> the type of the object to return
         * @return the instance
         * @throws Exception an exception detailing what went wrong when creating or obtaining the instance
         */
        <K> K create(Class<K> cls) throws Exception;
    }
    /** Returns a default {@link IFactory} implementation. Package-protected for testing purposes. */
    public static IFactory defaultFactory() { return new DefaultFactory(); }
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
        private static ITypeConverter<?>[] createConverter(IFactory factory, Class<? extends ITypeConverter<?>>[] classes) {
            ITypeConverter<?>[] result = new ITypeConverter<?>[classes.length];
            for (int i = 0; i < classes.length; i++) { result[i] = create(factory, classes[i]); }
            return result;
        }
        static IVersionProvider createVersionProvider(IFactory factory, Class<? extends IVersionProvider> cls) {
            return create(factory, cls);
        }
        static IDefaultValueProvider createDefaultValueProvider(IFactory factory, Class<? extends IDefaultValueProvider> cls) {
            return create(factory, cls);
        }
        static Iterable<String> createCompletionCandidates(IFactory factory, Class<? extends Iterable<String>> cls) {
            return create(factory, cls);
        }
        static <T> T create(IFactory factory, Class<T> cls) {
            try { return factory.create(cls); }
            catch (Exception ex) { throw new InitializationException("Could not instantiate " + cls + ": " + ex, ex); }
        }
    }

    public interface IHelpFactory {
        public Help createHelp(CommandSpec commandSpec, ColorScheme colorScheme);
    }

    public static class HelpFactory implements IHelpFactory {
        public Help createHelp(CommandSpec commandSpec, ColorScheme colorScheme) {
            return new Help(commandSpec, colorScheme);
        }
    }

    /** Describes the number of parameters required and accepted by an option or a positional parameter.
     * @since 0.9.7
     */
    public static class Range implements Comparable<Range> {
        /** Required number of parameters for an option or positional parameter. */
        public final int min;
        /** Maximum accepted number of parameters for an option or positional parameter. */
        public final int max;
        public final boolean isVariable;
        private final boolean isUnspecified;
        private final String originalValue;

        /** Constructs a new Range object with the specified parameters.
         * @param min minimum number of required parameters
         * @param max maximum number of allowed parameters (or Integer.MAX_VALUE if variable)
         * @param variable {@code true} if any number or parameters is allowed, {@code false} otherwise
         * @param unspecified {@code true} if no arity was specified on the option/parameter (value is based on type)
         * @param originalValue the original value that was specified on the option or parameter
         */
        public Range(int min, int max, boolean variable, boolean unspecified, String originalValue) {
            if (min < 0 || max < 0) { throw new InitializationException("Invalid negative range (min=" + min + ", max=" + max + ")"); }
            if (min > max) { throw new InitializationException("Invalid range (min=" + min + ", max=" + max + ")"); }
            this.min = min;
            this.max = max;
            this.isVariable = variable;
            this.isUnspecified = unspecified;
            this.originalValue = originalValue;
        }
        /** Returns a new {@code Range} based on the {@link Option#arity()} annotation on the specified field,
         * or the field type's default arity if no arity was specified.
         * @param field the field whose Option annotation to inspect
         * @return a new {@code Range} based on the Option arity annotation on the specified field */
        public static Range optionArity(Field field) { return optionArity(new TypedMember(field)); }
        private static Range optionArity(TypedMember member) {
            return member.isAnnotationPresent(Option.class)
                    ? adjustForType(Range.valueOf(member.getAnnotation(Option.class).arity()), member)
                    : new Range(0, 0, false, true, "0");
        }
        /** Returns a new {@code Range} based on the {@link Parameters#arity()} annotation on the specified field,
         * or the field type's default arity if no arity was specified.
         * @param field the field whose Parameters annotation to inspect
         * @return a new {@code Range} based on the Parameters arity annotation on the specified field */
        public static Range parameterArity(Field field) { return parameterArity(new TypedMember(field)); }
        private static Range parameterArity(TypedMember member) {
            if (member.isAnnotationPresent(Parameters.class)) {
                return adjustForType(Range.valueOf(member.getAnnotation(Parameters.class).arity()), member);
            } else {
                return member.isMethodParameter()
                        ? adjustForType(Range.valueOf(""), member)
                        : new Range(0, 0, false, true, "0");
            }
        }
        /** Returns a new {@code Range} based on the {@link Parameters#index()} annotation on the specified field.
         * @param field the field whose Parameters annotation to inspect
         * @return a new {@code Range} based on the Parameters index annotation on the specified field */
        public static Range parameterIndex(Field field) { return parameterIndex(new TypedMember(field)); }
        private static Range parameterIndex(TypedMember member) {
            if (member.isAnnotationPresent(Parameters.class)) {
                Range result = Range.valueOf(member.getAnnotation(Parameters.class).index());
                if (!result.isUnspecified) { return result; }
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
        /** Returns the default arity {@code Range}: for {@link Option options} this is 0 for booleans and 1 for
         * other types, for {@link Parameters parameters} booleans have arity 0, arrays or Collections have
         * arity "0..*", and other types have arity 1.
         * @param field the field whose default arity to return
         * @return a new {@code Range} indicating the default arity of the specified field
         * @since 2.0 */
        public static Range defaultArity(Field field) { return defaultArity(new TypedMember(field)); }
        private static Range defaultArity(TypedMember member) {
            Class<?> type = member.getType();
            if (member.isAnnotationPresent(Option.class)) {
                Class<?>[] typeAttribute = ArgsReflection
                        .inferTypes(type, member.getAnnotation(Option.class).type(), member.getGenericType());
                boolean zeroArgs = isBoolean(type) || (isMultiValue(type) && isBoolean(typeAttribute[0]));
                return zeroArgs ? Range.valueOf("0").unspecified(true)
                                : Range.valueOf("1").unspecified(true);
            }
            if (isMultiValue(type)) {
                return Range.valueOf("0..1").unspecified(true);
            }
            return Range.valueOf("1").unspecified(true);// for single-valued fields (incl. boolean positional parameters)
        }
        /** Returns the default arity {@code Range} for {@link Option options}: booleans have arity 0, other types have arity 1.
         * @param type the type whose default arity to return
         * @return a new {@code Range} indicating the default arity of the specified type
         * @deprecated use {@link #defaultArity(Field)} instead */
        @Deprecated public static Range defaultArity(Class<?> type) {
            return isBoolean(type) ? Range.valueOf("0").unspecified(true) : Range.valueOf("1").unspecified(true);
        }
        private int size() { return 1 + max - min; }
        static Range parameterCapacity(TypedMember member) {
            Range arity = parameterArity(member);
            if (!member.isMultiValue()) { return arity; }
            Range index = parameterIndex(member);
            return parameterCapacity(arity, index);
        }
        private static Range parameterCapacity(Range arity, Range index) {
            if (arity.max == 0)    { return arity; }
            if (index.size() == 1) { return arity; }
            if (index.isVariable)  { return Range.valueOf(arity.min + "..*"); }
            if (arity.size() == 1) { return Range.valueOf(arity.min * index.size() + ""); }
            if (arity.isVariable)  { return Range.valueOf(arity.min * index.size() + "..*"); }
            return Range.valueOf(arity.min * index.size() + ".." + arity.max * index.size());
        }

        /** Leniently parses the specified String as an {@code Range} value and return the result. A range string can
         * be a fixed integer value or a range of the form {@code MIN_VALUE + ".." + MAX_VALUE}. If the
         * {@code MIN_VALUE} string is not numeric, the minimum is zero. If the {@code MAX_VALUE} is not numeric, the
         * range is taken to be variable and the maximum is {@code Integer.MAX_VALUE}.
         * @param range the value range string to parse
         * @return a new {@code Range} value */
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
        /** Returns a new Range object with the {@code min} value replaced by the specified value.
         * The {@code max} of the returned Range is guaranteed not to be less than the new {@code min} value.
         * @param newMin the {@code min} value of the returned Range object
         * @return a new Range object with the specified {@code min} value */
        public Range min(int newMin) { return new Range(newMin, Math.max(newMin, max), isVariable, isUnspecified, originalValue); }

        /** Returns a new Range object with the {@code max} value replaced by the specified value.
         * The {@code min} of the returned Range is guaranteed not to be greater than the new {@code max} value.
         * @param newMax the {@code max} value of the returned Range object
         * @return a new Range object with the specified {@code max} value */
        public Range max(int newMax) { return new Range(Math.min(min, newMax), newMax, isVariable, isUnspecified, originalValue); }

        /** Returns a new Range object with the {@code isUnspecified} value replaced by the specified value.
         * @param unspecified the {@code unspecified} value of the returned Range object
         * @return a new Range object with the specified {@code unspecified} value */
        public Range unspecified(boolean unspecified) { return new Range(min, max, isVariable, unspecified, originalValue); }

        /**
         * Returns {@code true} if this Range includes the specified value, {@code false} otherwise.
         * @param value the value to check
         * @return {@code true} if the specified value is not less than the minimum and not greater than the maximum of this Range
         */
        public boolean contains(int value) { return min <= value && max >= value; }

        public boolean equals(Object object) {
            if (!(object instanceof Range)) { return false; }
            Range other = (Range) object;
            return other.max == this.max && other.min == this.min && other.isVariable == this.isVariable;
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
    private static void validatePositionalParameters(List<PositionalParamSpec> positionalParametersFields) {
        int min = 0;
        for (PositionalParamSpec positional : positionalParametersFields) {
            Range index = positional.index();
            if (index.min > min) {
                throw new ParameterIndexGapException("Command definition should have a positional parameter with index=" + min +
                        ". Nearest positional parameter '" + positional.paramLabel() + "' has index=" + index.min);
            }
            min = Math.max(min, index.max);
            min = min == Integer.MAX_VALUE ? min : min + 1;
        }
    }
    @SuppressWarnings("unchecked") private static Stack<String> copy(Stack<String> stack) { return (Stack<String>) stack.clone(); }
    private static <T> Stack<T> reverse(Stack<T> stack) {
        Collections.reverse(stack);
        return stack;
    }
    private static <T> List<T> reverseList(List<T> list) {
        Collections.reverse(list);
        return list;
    }

    /** This class provides a namespace for classes and interfaces that model concepts and attributes of command line interfaces in picocli.
     * @since 3.0 */
    public static final class Model {
        private Model() {}

        /** Customizable getter for obtaining the current value of an option or positional parameter.
         * When an option or positional parameter is matched on the command line, its getter or setter is invoked to capture the value.
         * For example, an option can be bound to a field or a method, and when the option is matched on the command line, the
         * field's value is set or the method is invoked with the option parameter value.
         * @since 3.0 */
        public static interface IGetter {
            /** Returns the current value of the binding. For multi-value options and positional parameters,
             * this method returns an array, collection or map to add values to.
             * @throws PicocliException if a problem occurred while obtaining the current value
             * @throws Exception internally, picocli call sites will catch any exceptions thrown from here and rethrow them wrapped in a PicocliException */
            <T> T get() throws Exception;
        }
        /** Customizable setter for modifying the value of an option or positional parameter.
         * When an option or positional parameter is matched on the command line, its setter is invoked to capture the value.
         * For example, an option can be bound to a field or a method, and when the option is matched on the command line, the
         * field's value is set or the method is invoked with the option parameter value.
         * @since 3.0 */
        public static interface ISetter {
            /** Sets the new value of the option or positional parameter.
             *
             * @param value the new value of the option or positional parameter
             * @param <T> type of the value
             * @return the previous value of the binding (if supported by this binding)
             * @throws PicocliException if a problem occurred while setting the new value
             * @throws Exception internally, picocli call sites will catch any exceptions thrown from here and rethrow them wrapped in a PicocliException */
            <T> T set(T value) throws Exception;
        }

        /** The {@code CommandSpec} class models a command specification, including the options, positional parameters and subcommands
         * supported by the command, as well as attributes for the version help message and the usage help message of the command.
         * <p>
         * Picocli views a command line application as a hierarchy of commands: there is a top-level command (usually the Java
         * class with the {@code main} method) with optionally a set of command line options, positional parameters and subcommands.
         * Subcommands themselves can have options, positional parameters and nested sub-subcommands to any level of depth.
         * </p><p>
         * The object model has a corresponding hierarchy of {@code CommandSpec} objects, each with a set of {@link OptionSpec},
         * {@link PositionalParamSpec} and {@linkplain CommandLine subcommands} associated with it.
         * This object model is used by the picocli command line interpreter and help message generator.
         * </p><p>Picocli can construct a {@code CommandSpec} automatically from classes with {@link Command @Command}, {@link Option @Option} and
         * {@link Parameters @Parameters} annotations. Alternatively a {@code CommandSpec} can be constructed programmatically.
         * </p>
         * @since 3.0 */
        public static class CommandSpec {
            /** Constant String holding the default program name: {@code "<main class>" }. */
            public static final String DEFAULT_COMMAND_NAME = "<main class>";

            /** Constant Boolean holding the default setting for whether this is a help command: <code>{@value}</code>.*/
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
            private final ParserSpec parser = new ParserSpec();
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
    
            private CommandSpec(Object userObject) { this.userObject = userObject; }
    
            /** Creates and returns a new {@code CommandSpec} without any associated user object. */
            public static CommandSpec create() { return wrapWithoutInspection(null); }
    
            /** Creates and returns a new {@code CommandSpec} with the specified associated user object.
             * The specified user object is <em>not</em> inspected for annotations.
             * @param userObject the associated user object. May be any object, may be {@code null}.
             */
            public static CommandSpec wrapWithoutInspection(Object userObject) { return new CommandSpec(userObject); }

            /** Creates and returns a new {@code CommandSpec} initialized from the specified associated user object. The specified
             * user object must have at least one {@link Command}, {@link Option} or {@link Parameters} annotation.
             * @param userObject the user object annotated with {@link Command}, {@link Option} and/or {@link Parameters} annotations.
             * @throws InitializationException if the specified object has no picocli annotations or has invalid annotations
             */
            public static CommandSpec forAnnotatedObject(Object userObject) { return forAnnotatedObject(userObject, new DefaultFactory()); }

            /** Creates and returns a new {@code CommandSpec} initialized from the specified associated user object. The specified
             * user object must have at least one {@link Command}, {@link Option} or {@link Parameters} annotation.
             * @param userObject the user object annotated with {@link Command}, {@link Option} and/or {@link Parameters} annotations.
             * @param factory the factory used to create instances of {@linkplain Command#subcommands() subcommands}, {@linkplain Option#converter() converters}, etc., that are registered declaratively with annotation attributes
             * @throws InitializationException if the specified object has no picocli annotations or has invalid annotations
             */
            public static CommandSpec forAnnotatedObject(Object userObject, IFactory factory) { return CommandReflection.extractCommandSpec(userObject, factory, true); }

            /** Creates and returns a new {@code CommandSpec} initialized from the specified associated user object. If the specified
             * user object has no {@link Command}, {@link Option} or {@link Parameters} annotations, an empty {@code CommandSpec} is returned.
             * @param userObject the user object annotated with {@link Command}, {@link Option} and/or {@link Parameters} annotations.
             * @throws InitializationException if the specified object has invalid annotations
             */
            public static CommandSpec forAnnotatedObjectLenient(Object userObject) { return forAnnotatedObjectLenient(userObject, new DefaultFactory()); }

            /** Creates and returns a new {@code CommandSpec} initialized from the specified associated user object. If the specified
             * user object has no {@link Command}, {@link Option} or {@link Parameters} annotations, an empty {@code CommandSpec} is returned.
             * @param userObject the user object annotated with {@link Command}, {@link Option} and/or {@link Parameters} annotations.
             * @param factory the factory used to create instances of {@linkplain Command#subcommands() subcommands}, {@linkplain Option#converter() converters}, etc., that are registered declaratively with annotation attributes
             * @throws InitializationException if the specified object has invalid annotations
             */
            public static CommandSpec forAnnotatedObjectLenient(Object userObject, IFactory factory) { return CommandReflection.extractCommandSpec(userObject, factory, false); }

            /** Ensures all attributes of this {@code CommandSpec} have a valid value; throws an {@link InitializationException} if this cannot be achieved. */
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
                        if (!isBoolean(option.type())) { wrongUsageHelpAttr.add(option.longestName()); }
                    }
                    if (option.versionHelp()) {
                        versionHelpAttr.add(option.longestName());
                        if (!isBoolean(option.type())) { wrongVersionHelpAttr.add(option.longestName()); }
                    }
                }
                String wrongType = "Non-boolean options like %s should not be marked as '%s=true'. Usually a command has one %s boolean flag that triggers display of the %s. Alternatively, consider using @Command(mixinStandardHelpOptions = true) on your command instead.";
                String multiple = "Multiple options %s are marked as '%s=true'. Usually a command has only one %s option that triggers display of the %s. Alternatively, consider using @Command(mixinStandardHelpOptions = true) on your command instead.%n";
                if (!wrongUsageHelpAttr.isEmpty()) {
                    throw new InitializationException(String.format(wrongType, wrongUsageHelpAttr, "usageHelp", "--help", "usage help message"));
                }
                if (!wrongVersionHelpAttr.isEmpty()) {
                    throw new InitializationException(String.format(wrongType, wrongVersionHelpAttr, "versionHelp", "--version", "version information"));
                }
                if (usageHelpAttr.size() > 1)   { new Tracer().warn(multiple, usageHelpAttr, "usageHelp", "--help", "usage help message"); }
                if (versionHelpAttr.size() > 1) { new Tracer().warn(multiple, versionHelpAttr, "versionHelp", "--version", "version information"); }
            }
    
            /** Returns the user object associated with this command.
             * @see CommandLine#getCommand() */
            public Object userObject() { return userObject; }
    
            /** Returns the CommandLine constructed with this {@code CommandSpec} model. */
            public CommandLine commandLine() { return commandLine;}
    
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
            public ParserSpec parser() { return parser; }
            /** Initializes the parser specification for this command from the specified settings and returns this commandSpec.*/
            public CommandSpec parser(ParserSpec settings) { parser.initFrom(settings); return this; }

            /** Returns the usage help message specification for this command. */
            public UsageMessageSpec usageMessage() { return usageMessage; }
            /** Initializes the usageMessage specification for this command from the specified settings and returns this commandSpec.*/
            public CommandSpec usageMessage(UsageMessageSpec settings) { usageMessage.initFrom(settings, this); return this; }

            /** Returns the resource bundle for this command.
             * @return the resource bundle from the {@linkplain UsageMessageSpec#messages()}
             * @since 3.6 */
            public ResourceBundle resourceBundle() { return Messages.resourceBundle(usageMessage.messages()); }
            /** Initializes the resource bundle for this command: sets the {@link UsageMessageSpec#messages(Messages) UsageMessageSpec.messages} to
             * a {@link Messages Messages} object created from this command spec and the specified bundle, and then sets the
             * {@link ArgSpec#messages(Messages) ArgSpec.messages} of all options and positional parameters in this command
             * to the same {@code Messages} instance. Subcommands are not modified.
             * @param bundle the ResourceBundle to set, may be {@code null}
             * @return this commandSpec
             * @see #addSubcommand(String, CommandLine)
             * @since 3.6 */
            public CommandSpec resourceBundle(ResourceBundle bundle) {
                usageMessage().messages(new Messages(this, bundle));
                updateArgSpecMessages();
                return this;
            }
            private void updateArgSpecMessages() {
                for (OptionSpec opt : options()) { opt.messages(usageMessage().messages()); }
                for (PositionalParamSpec pos : positionalParameters()) { pos.messages(usageMessage().messages()); }
            }

            /** Returns a read-only view of the subcommand map. */
            public Map<String, CommandLine> subcommands() { return Collections.unmodifiableMap(commands); }
    
            /** Adds the specified subcommand with the specified name.
             * If the specified subcommand does not have a ResourceBundle set, it is initialized to the ResourceBundle of this command spec.
             * @param name subcommand name - when this String is encountered in the command line arguments the subcommand is invoked
             * @param subcommand describes the subcommand to envoke when the name is encountered on the command line
             * @return this {@code CommandSpec} object for method chaining */
            public CommandSpec addSubcommand(String name, CommandSpec subcommand) {
                return addSubcommand(name, new CommandLine(subcommand));
            }
    
            /** Adds the specified subcommand with the specified name.
             * If the specified subcommand does not have a ResourceBundle set, it is initialized to the ResourceBundle of this command spec.
             * @param name subcommand name - when this String is encountered in the command line arguments the subcommand is invoked
             * @param subCommandLine the subcommand to envoke when the name is encountered on the command line
             * @return this {@code CommandSpec} object for method chaining */
            public CommandSpec addSubcommand(String name, CommandLine subCommandLine) {
                CommandLine previous = commands.put(name, subCommandLine);
                if (previous != null && previous != subCommandLine) { throw new InitializationException("Another subcommand named '" + name + "' already exists for command '" + this.name() + "'"); }
                CommandSpec subSpec = subCommandLine.getCommandSpec();
                if (subSpec.name == null) { subSpec.name(name); }
                subSpec.parent(this);
                for (String alias : subSpec.aliases()) {
                    previous = commands.put(alias, subCommandLine);
                    if (previous != null && previous != subCommandLine) { throw new InitializationException("Alias '" + alias + "' for subcommand '" + name + "' is already used by another subcommand of '" + this.name() + "'"); }
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

            /** Reflects on the class of the {@linkplain #userObject() user object} and registers any command methods
             * (class methods annotated with {@code @Command}) as subcommands.
             *
             * @return this {@link CommandSpec} object for method chaining
             * @see #addMethodSubcommands(IFactory)
             * @see #addSubcommand(String, CommandLine)
             * @since 3.6.0
             */
            public CommandSpec addMethodSubcommands() { return addMethodSubcommands(new DefaultFactory()); }

            /** Reflects on the class of the {@linkplain #userObject() user object} and registers any command methods
             * (class methods annotated with {@code @Command}) as subcommands.
             * @param factory the factory used to create instances of subcommands, converters, etc., that are registered declaratively with annotation attributes
             * @return this {@link CommandSpec} object for method chaining
             * @see #addSubcommand(String, CommandLine)
             * @since 3.7.0
             */
            public CommandSpec addMethodSubcommands(IFactory factory) {
                if (userObject() instanceof Method) {
                     throw new UnsupportedOperationException("cannot discover methods of non-class: " + userObject());
                }
                for (Method method : getCommandMethods(userObject().getClass(), null)) {
                    CommandLine cmd = new CommandLine(method, factory);
                    addSubcommand(cmd.getCommandName(), cmd);
                }
                return this;
            }

            /** Returns the parent command of this subcommand, or {@code null} if this is a top-level command. */
            public CommandSpec parent() { return parent; }
    
            /** Sets the parent command of this subcommand.
             * @return this CommandSpec for method chaining */
            public CommandSpec parent(CommandSpec parent) { this.parent = parent; return this; }
    
            /** Adds the specified option spec or positional parameter spec to the list of configured arguments to expect.
             * @param arg the option spec or positional parameter spec to add
             * @return this CommandSpec for method chaining */
            public CommandSpec add(ArgSpec arg) { return arg.isOption() ? addOption((OptionSpec) arg) : addPositional((PositionalParamSpec) arg); }
    
            /** Adds the specified option spec to the list of configured arguments to expect.
             * The option's {@linkplain OptionSpec#description()} may now return Strings from this
             * CommandSpec's {@linkplain UsageMessageSpec#messages() messages}.
             * The option parameter's {@linkplain OptionSpec#defaultValueString()} may
             * now return Strings from this CommandSpec's {@link CommandSpec#defaultValueProvider()} IDefaultValueProvider}.
             * @param option the option spec to add
             * @return this CommandSpec for method chaining
             * @throws DuplicateOptionAnnotationsException if any of the names of the specified option is the same as the name of another option */
            public CommandSpec addOption(OptionSpec option) {
                args.add(option);
                options.add(option);
                for (String name : option.names()) { // cannot be null or empty
                    OptionSpec existing = optionsByNameMap.put(name, option);
                    if (existing != null && !existing.equals(option)) {
                        throw DuplicateOptionAnnotationsException.create(name, option, existing);
                    }
                    if (name.length() == 2 && name.startsWith("-")) { posixOptionsByKeyMap.put(name.charAt(1), option); }
                }
                if (option.required()) { requiredArgs.add(option); }
                option.messages(usageMessage().messages());
                option.commandSpec = this;
                return this;
            }
            /** Adds the specified positional parameter spec to the list of configured arguments to expect.
             * The positional parameter's {@linkplain PositionalParamSpec#description()} may
             * now return Strings from this CommandSpec's {@linkplain UsageMessageSpec#messages() messages}.
             * The positional parameter's {@linkplain PositionalParamSpec#defaultValueString()} may
             * now return Strings from this CommandSpec's {@link CommandSpec#defaultValueProvider()} IDefaultValueProvider}.
             * @param positional the positional parameter spec to add
             * @return this CommandSpec for method chaining */
            public CommandSpec addPositional(PositionalParamSpec positional) {
                args.add(positional);
                positionalParameters.add(positional);
                if (positional.required()) { requiredArgs.add(positional); }
                positional.messages(usageMessage().messages());
                positional.commandSpec = this;
                return this;
            }
    
            /** Adds the specified mixin {@code CommandSpec} object to the map of mixins for this command.
             * @param name the name that can be used to later retrieve the mixin
             * @param mixin the mixin whose options and positional parameters and other attributes to add to this command
             * @return this CommandSpec for method chaining */
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
                for (OptionSpec optionSpec         : mixin.options())              { addOption(optionSpec); }
                for (PositionalParamSpec paramSpec : mixin.positionalParameters()) { addPositional(paramSpec); }
                return this;
            }

            /** Adds the specified {@code UnmatchedArgsBinding} to the list of model objects to capture unmatched arguments for this command.
             * @param spec the unmatched arguments binding to capture unmatched arguments
             * @return this CommandSpec for method chaining */
            public CommandSpec addUnmatchedArgsBinding(UnmatchedArgsBinding spec) { unmatchedArgs.add(spec); parser().unmatchedArgumentsAllowed(true); return this; }
    
            /** Returns a map of the mixin names to mixin {@code CommandSpec} objects configured for this command.
             * @return an immutable map of mixins added to this command. */
            public Map<String, CommandSpec> mixins() { return Collections.unmodifiableMap(mixins); }
    
            /** Returns the list of options configured for this command.
             * @return an immutable list of options that this command recognizes. */
            public List<OptionSpec> options() { return Collections.unmodifiableList(options); }
    
            /** Returns the list of positional parameters configured for this command.
             * @return an immutable list of positional parameters that this command recognizes. */
            public List<PositionalParamSpec> positionalParameters() { return Collections.unmodifiableList(positionalParameters); }
    
            /** Returns a map of the option names to option spec objects configured for this command.
             * @return an immutable map of options that this command recognizes. */
            public Map<String, OptionSpec> optionsMap() { return Collections.unmodifiableMap(optionsByNameMap); }
    
            /** Returns a map of the short (single character) option names to option spec objects configured for this command.
             * @return an immutable map of options that this command recognizes. */
            public Map<Character, OptionSpec> posixOptionsMap() { return Collections.unmodifiableMap(posixOptionsByKeyMap); }

            /** Returns the list of required options and positional parameters configured for this command.
             * @return an immutable list of the required options and positional parameters for this command. */
            public List<ArgSpec> requiredArgs() { return Collections.unmodifiableList(requiredArgs); }

            /** Returns the list of {@link UnmatchedArgsBinding UnmatchedArgumentsBindings} configured for this command;
             * each {@code UnmatchedArgsBinding} captures the arguments that could not be matched to any options or positional parameters. */
            public List<UnmatchedArgsBinding> unmatchedArgsBindings() { return Collections.unmodifiableList(unmatchedArgs); }
    
            /** Returns name of this command. Used in the synopsis line of the help message.
             * {@link #DEFAULT_COMMAND_NAME} by default, initialized from {@link Command#name()} if defined.
             * @see #qualifiedName() */
            public String name() { return (name == null) ? DEFAULT_COMMAND_NAME : name; }

            /** Returns the alias command names of this subcommand.
             * @since 3.1 */
            public String[] aliases() { return aliases.toArray(new String[0]); }

            /** Returns the list of all options and positional parameters configured for this command.
             * @return an immutable list of all options and positional parameters for this command. */
            public List<ArgSpec> args() { return Collections.unmodifiableList(args); }
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
                    for (int i = 0; i < values.length; i++) { values[i] = args.get(i + shift).getValue(); }
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

            /** Returns the String to use as the program name in the synopsis line of the help message:
             * this command's {@link #name() name}, preceded by the qualified name of the parent command, if any, separated by a space.
             * @return {@link #DEFAULT_COMMAND_NAME} by default, initialized from {@link Command#name()} and the parent command if defined.
             * @since 3.0.1 */
            public String qualifiedName() { return qualifiedName(" "); }
            /** Returns this command's fully qualified name, which is its {@link #name() name}, preceded by the qualified name of the parent command, if this command has a parent command.
             * @return {@link #DEFAULT_COMMAND_NAME} by default, initialized from {@link Command#name()} and the parent command if any.
             * @param separator the string to put between the names of the commands in the hierarchy
             * @since 3.6 */
            public String qualifiedName(String separator) {
                String result = name();
                if (parent() != null) { result = parent().qualifiedName(separator) + separator + result; }
                return result;
            }

            /** Returns version information for this command, to print to the console when the user specifies an
             * {@linkplain OptionSpec#versionHelp() option} to request version help. This is not part of the usage help message.
             * @return the version strings generated by the {@link #versionProvider() version provider} if one is set, otherwise the {@linkplain #version(String...) version literals}*/
            public String[] version() {
                if (versionProvider != null) {
                    try {
                        return versionProvider.getVersion();
                    } catch (Exception ex) {
                        String msg = "Could not get version info from " + versionProvider + ": " + ex;
                        throw new ExecutionException(this.commandLine, msg, ex);
                    }
                }
                return version == null ? UsageMessageSpec.DEFAULT_MULTI_LINE : version;
            }
    
            /** Returns the version provider for this command, to generate the {@link #version()} strings.
             * @return the version provider or {@code null} if the version strings should be returned from the {@linkplain #version(String...) version literals}.*/
            public IVersionProvider versionProvider() { return versionProvider; }

            /** Returns whether this subcommand is a help command, and required options and positional
             * parameters of the parent command should not be validated.
             * @return {@code true} if this subcommand is a help command and picocli should not check for missing required
             *      options and positional parameters on the parent command
             * @see Command#helpCommand() */
            public boolean helpCommand() { return (isHelpCommand == null) ? DEFAULT_IS_HELP_COMMAND : isHelpCommand; }

            /** Returns {@code true} if the standard help options have been mixed in with this command, {@code false} otherwise. */
            public boolean mixinStandardHelpOptions() { return mixins.containsKey(AutoHelpMixin.KEY); }

            /** Returns a string representation of this command, used in error messages and trace messages. */
            public String toString() { return toString; }

            /** Sets the String to use as the program name in the synopsis line of the help message.
             * @return this CommandSpec for method chaining */
            public CommandSpec name(String name) { this.name = name; return this; }

            /** Sets the alternative names by which this subcommand is recognized on the command line.
             * @return this CommandSpec for method chaining
             * @since 3.1 */
            public CommandSpec aliases(String... aliases) {
                this.aliases = new LinkedHashSet<String>(Arrays.asList(aliases == null ? new String[0] : aliases));
                return this;
            }

            /** Returns the default value provider for this command.
             * @return the default value provider or {@code null}
             * @since 3.6 */
            public IDefaultValueProvider defaultValueProvider() { return defaultValueProvider; }

            /** Sets default value provider for this command.
             * @param defaultValueProvider the default value provider to use, or {@code null}.
             * @return this CommandSpec for method chaining
             * @since 3.6 */
            public CommandSpec defaultValueProvider(IDefaultValueProvider  defaultValueProvider) { this.defaultValueProvider = defaultValueProvider; return this; }

            /** Sets version information literals for this command, to print to the console when the user specifies an
             * {@linkplain OptionSpec#versionHelp() option} to request version help. Only used if no {@link #versionProvider() versionProvider} is set.
             * @return this CommandSpec for method chaining */
            public CommandSpec version(String... version) { this.version = version; return this; }
    
            /** Sets version provider for this command, to generate the {@link #version()} strings.
             * @param versionProvider the version provider to use to generate the version strings, or {@code null} if the {@linkplain #version(String...) version literals} should be used.
             * @return this CommandSpec for method chaining */
            public CommandSpec versionProvider(IVersionProvider versionProvider) { this.versionProvider = versionProvider; return this; }

            /** Sets whether this is a help command and required parameter checking should be suspended.
             * @return this CommandSpec for method chaining
             * @see Command#helpCommand() */
            public CommandSpec helpCommand(boolean newValue) {isHelpCommand = newValue; return this;}

            /** Sets whether the standard help options should be mixed in with this command.
             * @return this CommandSpec for method chaining
             * @see Command#mixinStandardHelpOptions() */
            public CommandSpec mixinStandardHelpOptions(boolean newValue) {
                if (newValue) {
                    CommandSpec mixin = CommandSpec.forAnnotatedObject(new AutoHelpMixin(), new DefaultFactory());
                    addMixin(AutoHelpMixin.KEY, mixin);
                } else {
                    CommandSpec helpMixin = mixins.remove(AutoHelpMixin.KEY);
                    if (helpMixin != null) {
                        options.removeAll(helpMixin.options);
                        for (OptionSpec option : helpMixin.options()) {
                            for (String name : option.names) {
                                optionsByNameMap.remove(name);
                                if (name.length() == 2 && name.startsWith("-")) { posixOptionsByKeyMap.remove(name.charAt(1)); }
                            }
                        }
                    }
                }
                return this;
            }

            /** Sets the string representation of this command, used in error messages and trace messages.
             * @param newValue the string representation
             * @return this CommandSpec for method chaining */
            public CommandSpec withToString(String newValue) { this.toString = newValue; return this; }
    
            void initName(String value)                 { if (initializable(name, value, DEFAULT_COMMAND_NAME))                           {name = value;} }
            void initHelpCommand(boolean value)         { if (initializable(isHelpCommand, value, DEFAULT_IS_HELP_COMMAND))               {isHelpCommand = value;} }
            void initVersion(String[] value)            { if (initializable(version, value, UsageMessageSpec.DEFAULT_MULTI_LINE))         {version = value.clone();} }
            void initVersionProvider(IVersionProvider value) { if (versionProvider == null) { versionProvider = value; } }
            void initVersionProvider(Class<? extends IVersionProvider> value, IFactory factory) {
                if (initializable(versionProvider, value, NoVersionProvider.class)) { versionProvider = (DefaultFactory.createVersionProvider(factory, value)); }
            }
            void initDefaultValueProvider(IDefaultValueProvider value) { if (defaultValueProvider == null) { defaultValueProvider = value; } }
            void initDefaultValueProvider(Class<? extends IDefaultValueProvider> value, IFactory factory) {
                if (initializable(defaultValueProvider, value, NoDefaultProvider.class)) { defaultValueProvider = (DefaultFactory.createDefaultValueProvider(factory, value)); }
            }
            void updateName(String value)               { if (isNonDefault(value, DEFAULT_COMMAND_NAME))                 {name = value;} }
            void updateHelpCommand(boolean value)       { if (isNonDefault(value, DEFAULT_IS_HELP_COMMAND))              {isHelpCommand = value;} }
            void updateVersion(String[] value)          { if (isNonDefault(value, UsageMessageSpec.DEFAULT_MULTI_LINE))  {version = value.clone();} }
            void updateVersionProvider(Class<? extends IVersionProvider> value, IFactory factory) {
                if (isNonDefault(value, NoVersionProvider.class)) { versionProvider = (DefaultFactory.createVersionProvider(factory, value)); }
            }

            /** Returns the option with the specified short name, or {@code null} if no option with that name is defined for this command. */
            public OptionSpec findOption(char shortName) { return findOption(shortName, options()); }
            /** Returns the option with the specified name, or {@code null} if no option with that name is defined for this command.
             * @param name used to search the options. May include option name prefix characters or not. */
            public OptionSpec findOption(String name) { return findOption(name, options()); }

            static OptionSpec findOption(char shortName, Iterable<OptionSpec> options) {
                for (OptionSpec option : options) {
                    for (String name : option.names()) {
                        if (name.length() == 2 && name.charAt(0) == '-' && name.charAt(1) == shortName) { return option; }
                        if (name.length() == 1 && name.charAt(0) == shortName) { return option; }
                    }
                }
                return null;
            }
            static OptionSpec findOption(String name, List<OptionSpec> options) {
                for (OptionSpec option : options) {
                    for (String prefixed : option.names()) {
                        if (prefixed.equals(name) || stripPrefix(prefixed).equals(name)) { return option; }
                    }
                }
                return null;
            }
            static String stripPrefix(String prefixed) {
                for (int i = 0; i < prefixed.length(); i++) {
                    if (Character.isJavaIdentifierPart(prefixed.charAt(i))) { return prefixed.substring(i); }
                }
                return prefixed;
            }
            List<String> findOptionNamesWithPrefix(String prefix) {
                List<String> result = new ArrayList<String>();
                for (OptionSpec option : options()) {
                    for (String name : option.names()) {
                        if (stripPrefix(name).startsWith(prefix)) { result.add(name); }
                    }
                }
                return result;
            }

            boolean resemblesOption(String arg, Tracer tracer) {
                if (parser().unmatchedOptionsArePositionalParams()) {
                    if (tracer != null && tracer.isDebug()) {tracer.debug("Parser is configured to treat all unmatched options as positional parameter%n", arg);}
                    return false;
                }
                if (options().isEmpty()) {
                    boolean result = arg.startsWith("-");
                    if (tracer != null && tracer.isDebug()) {tracer.debug("%s %s an option%n", arg, (result ? "resembles" : "doesn't resemble"));}
                    return result;
                }
                int count = 0;
                for (String optionName : optionsMap().keySet()) {
                    for (int i = 0; i < arg.length(); i++) {
                        if (optionName.length() > i && arg.charAt(i) == optionName.charAt(i)) { count++; } else { break; }
                    }
                }
                boolean result = count > 0 && count * 10 >= optionsMap().size() * 9; // at least one prefix char in common with 9 out of 10 options
                if (tracer != null && tracer.isDebug()) {tracer.debug("%s %s an option: %d matching prefix chars out of %d option names%n", arg, (result ? "resembles" : "doesn't resemble"), count, optionsMap().size());}
                return result;
            }
        }
        private static boolean initializable(Object current, Object candidate, Object defaultValue) {
            return current == null && isNonDefault(candidate, defaultValue);
        }
        private static boolean initializable(Object current, Object[] candidate, Object[] defaultValue) {
            return current == null && isNonDefault(candidate, defaultValue);
        }
        private static boolean isNonDefault(Object candidate, Object defaultValue) {
            return !Assert.notNull(defaultValue, "defaultValue").equals(candidate);
        }
        private static boolean isNonDefault(Object[] candidate, Object[] defaultValue) {
            return !Arrays.equals(Assert.notNull(defaultValue, "defaultValue"), candidate);
        }
        /** Models the usage help message specification.
         * @since 3.0 */
        public static class UsageMessageSpec {
            /** Constant holding the default usage message width: <code>{@value}</code>. */
            public  final static int DEFAULT_USAGE_WIDTH = 80;
            private final static int MINIMUM_USAGE_WIDTH = 55;

            /** Constant String holding the default synopsis heading: <code>{@value}</code>. */
            static final String DEFAULT_SYNOPSIS_HEADING = "Usage: ";

            /** Constant String holding the default command list heading: <code>{@value}</code>. */
            static final String DEFAULT_COMMAND_LIST_HEADING = "Commands:%n";

            /** Constant String holding the default string that separates options from option parameters: {@code ' '} ({@value}). */
            static final char DEFAULT_REQUIRED_OPTION_MARKER = ' ';

            /** Constant Boolean holding the default setting for whether to abbreviate the synopsis: <code>{@value}</code>.*/
            static final Boolean DEFAULT_ABBREVIATE_SYNOPSIS = Boolean.FALSE;

            /** Constant Boolean holding the default setting for whether to sort the options alphabetically: <code>{@value}</code>.*/
            static final Boolean DEFAULT_SORT_OPTIONS = Boolean.TRUE;

            /** Constant Boolean holding the default setting for whether to show default values in the usage help message: <code>{@value}</code>.*/
            static final Boolean DEFAULT_SHOW_DEFAULT_VALUES = Boolean.FALSE;

            /** Constant Boolean holding the default setting for whether this command should be listed in the usage help of the parent command: <code>{@value}</code>.*/
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
                if (userValue == null) { return defaultWidth; }
                try {
                    int width = Integer.parseInt(userValue);
                    if (width < MINIMUM_USAGE_WIDTH) {
                        new Tracer().warn("Invalid picocli.usage.width value %d. Using minimum usage width %d.%n", width, MINIMUM_USAGE_WIDTH);
                        return MINIMUM_USAGE_WIDTH;
                    }
                    return width;
                } catch (NumberFormatException ex) {
                    new Tracer().warn("Invalid picocli.usage.width value '%s'. Using usage width %d.%n", userValue, defaultWidth);
                    return defaultWidth;
                }
            }

            /** Returns the maximum usage help message width. Derived from system property {@code "picocli.usage.width"}
             * if set, otherwise returns the value set via the {@link #width(int)} method, or if not set, the {@linkplain #DEFAULT_USAGE_WIDTH default width}.
             * @return the maximum usage help message width. Never returns less than 55. */
            public int width() { return getSysPropertyWidthOrDefault(width); }

            /**
             * Sets the maximum usage help message width to the specified value. Longer values are wrapped.
             * @param newValue the new maximum usage help message width. Must be 55 or greater.
             * @return this {@code UsageMessageSpec} for method chaining
             * @throws IllegalArgumentException if the specified width is less than 55
             */
            public UsageMessageSpec width(int newValue) {
                if (newValue < MINIMUM_USAGE_WIDTH) {
                    throw new InitializationException("Invalid usage message width " + newValue + ". Minimum value is " + MINIMUM_USAGE_WIDTH);
                }
                width = newValue; return this;
            }

            private String str(String localized, String value, String defaultValue) {
                return localized != null ? localized : (value != null ? value : defaultValue);
            }
            private String[] arr(String[] localized, String[] value, String[] defaultValue) {
                return localized != null ? localized : (value != null ? value.clone() : defaultValue);
            }
            private String   resourceStr(String key) { return messages == null ? null : messages.getString(key, null); }
            private String[] resourceArr(String key) { return messages == null ? null : messages.getStringArray(key, null); }

            /** Returns the optional heading preceding the header section. Initialized from {@link Command#headerHeading()}, or null. */
            public String headerHeading() { return str(resourceStr("usage.headerHeading"), headerHeading, DEFAULT_SINGLE_VALUE); }

            /** Returns the optional header lines displayed at the top of the help message. For subcommands, the first header line is
             * displayed in the list of commands. Values are initialized from {@link Command#header()}
             * if the {@code Command} annotation is present, otherwise this is an empty array and the help message has no
             * header. Applications may programmatically set this field to create a custom help message. */
            public String[] header() { return arr(resourceArr("usage.header"), header, DEFAULT_MULTI_LINE); }

            /** Returns the optional heading preceding the synopsis. Initialized from {@link Command#synopsisHeading()}, {@code "Usage: "} by default. */
            public String synopsisHeading() { return str(resourceStr("usage.synopsisHeading"), synopsisHeading, DEFAULT_SYNOPSIS_HEADING); }

            /** Returns whether the synopsis line(s) should show an abbreviated synopsis without detailed option names. */
            public boolean abbreviateSynopsis() { return (abbreviateSynopsis == null) ? DEFAULT_ABBREVIATE_SYNOPSIS : abbreviateSynopsis; }

            /** Returns the optional custom synopsis lines to use instead of the auto-generated synopsis.
             * Initialized from {@link Command#customSynopsis()} if the {@code Command} annotation is present,
             * otherwise this is an empty array and the synopsis is generated.
             * Applications may programmatically set this field to create a custom help message. */
            public String[] customSynopsis() { return arr(resourceArr("usage.customSynopsis"), customSynopsis, DEFAULT_MULTI_LINE); }

            /** Returns the optional heading preceding the description section. Initialized from {@link Command#descriptionHeading()}, or null. */
            public String descriptionHeading() { return str(resourceStr("usage.descriptionHeading"), descriptionHeading, DEFAULT_SINGLE_VALUE); }

            /** Returns the optional text lines to use as the description of the help message, displayed between the synopsis and the
             * options list. Initialized from {@link Command#description()} if the {@code Command} annotation is present,
             * otherwise this is an empty array and the help message has no description.
             * Applications may programmatically set this field to create a custom help message. */
            public String[] description() { return arr(resourceArr("usage.description"), description, DEFAULT_MULTI_LINE); }

            /** Returns the optional heading preceding the parameter list. Initialized from {@link Command#parameterListHeading()}, or null. */
            public String parameterListHeading() { return str(resourceStr("usage.parameterListHeading"), parameterListHeading, DEFAULT_SINGLE_VALUE); }

            /** Returns the optional heading preceding the options list. Initialized from {@link Command#optionListHeading()}, or null. */
            public String optionListHeading() { return str(resourceStr("usage.optionListHeading"), optionListHeading, DEFAULT_SINGLE_VALUE); }

            /** Returns whether the options list in the usage help message should be sorted alphabetically. */
            public boolean sortOptions() { return (sortOptions == null) ? DEFAULT_SORT_OPTIONS : sortOptions; }

            /** Returns the character used to prefix required options in the options list. */
            public char requiredOptionMarker() { return (requiredOptionMarker == null) ? DEFAULT_REQUIRED_OPTION_MARKER : requiredOptionMarker; }

            /** Returns whether the options list in the usage help message should show default values for all non-boolean options. */
            public boolean showDefaultValues() { return (showDefaultValues == null) ? DEFAULT_SHOW_DEFAULT_VALUES : showDefaultValues; }

            /**
             * Returns whether this command should be hidden from the usage help message of the parent command.
             * @return {@code true} if this command should not appear in the usage help message of the parent command
             */
            public boolean hidden() { return (hidden == null) ? DEFAULT_HIDDEN : hidden; }

            /** Returns the optional heading preceding the subcommand list. Initialized from {@link Command#commandListHeading()}. {@code "Commands:%n"} by default. */
            public String commandListHeading() { return str(resourceStr("usage.commandListHeading"), commandListHeading, DEFAULT_COMMAND_LIST_HEADING); }

            /** Returns the optional heading preceding the footer section. Initialized from {@link Command#footerHeading()}, or null. */
            public String footerHeading() { return str(resourceStr("usage.footerHeading"), footerHeading, DEFAULT_SINGLE_VALUE); }

            /** Returns the optional footer text lines displayed at the bottom of the help message. Initialized from
             * {@link Command#footer()} if the {@code Command} annotation is present, otherwise this is an empty array and
             * the help message has no footer.
             * Applications may programmatically set this field to create a custom help message. */
            public String[] footer() { return arr(resourceArr("usage.footer"), footer, DEFAULT_MULTI_LINE); }

            /** Sets the heading preceding the header section. Initialized from {@link Command#headerHeading()}, or null.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec headerHeading(String headerHeading) { this.headerHeading = headerHeading; return this; }

            /** Sets the optional header lines displayed at the top of the help message. For subcommands, the first header line is
             * displayed in the list of commands.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec header(String... header) { this.header = header; return this; }

            /** Sets the optional heading preceding the synopsis.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec synopsisHeading(String newValue) {synopsisHeading = newValue; return this;}

            /** Sets whether the synopsis line(s) should show an abbreviated synopsis without detailed option names.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec abbreviateSynopsis(boolean newValue) {abbreviateSynopsis = newValue; return this;}

            /** Sets the optional custom synopsis lines to use instead of the auto-generated synopsis.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec customSynopsis(String... customSynopsis) { this.customSynopsis = customSynopsis; return this; }

            /** Sets the heading preceding the description section.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec descriptionHeading(String newValue) {descriptionHeading = newValue; return this;}

            /** Sets the optional text lines to use as the description of the help message, displayed between the synopsis and the
             * options list.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec description(String... description) { this.description = description; return this; }

            /** Sets the optional heading preceding the parameter list.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec parameterListHeading(String newValue) {parameterListHeading = newValue; return this;}

            /** Sets the heading preceding the options list.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec optionListHeading(String newValue) {optionListHeading = newValue; return this;}

            /** Sets whether the options list in the usage help message should be sorted alphabetically.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec sortOptions(boolean newValue) {sortOptions = newValue; return this;}

            /** Sets the character used to prefix required options in the options list.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec requiredOptionMarker(char newValue) {requiredOptionMarker = newValue; return this;}

            /** Sets whether the options list in the usage help message should show default values for all non-boolean options.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec showDefaultValues(boolean newValue) {showDefaultValues = newValue; return this;}

            /**
             * Set the hidden flag on this command to control whether to show or hide it in the help usage text of the parent command.
             * @param value enable or disable the hidden flag
             * @return this UsageMessageSpec for method chaining
             * @see Command#hidden() */
            public UsageMessageSpec hidden(boolean value) { hidden = value; return this; }

            /** Sets the optional heading preceding the subcommand list.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec commandListHeading(String newValue) {commandListHeading = newValue; return this;}

            /** Sets the optional heading preceding the footer section.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec footerHeading(String newValue) {footerHeading = newValue; return this;}

            /** Sets the optional footer text lines displayed at the bottom of the help message.
             * @return this UsageMessageSpec for method chaining */
            public UsageMessageSpec footer(String... footer) { this.footer = footer; return this; }
            /** Returns the Messages for this usage help message specification, or {@code null}.
             * @return the Messages object that encapsulates this {@linkplain CommandSpec#resourceBundle() command's resource bundle}
             * @since 3.6 */
            public Messages messages() { return messages; }
            /** Sets the Messages for this usageMessage specification, and returns this UsageMessageSpec.
             * @param msgs the new Messages value that encapsulates this {@linkplain CommandSpec#resourceBundle() command's resource bundle}, may be {@code null}
             * @since 3.6 */
            public UsageMessageSpec messages(Messages msgs) { messages = msgs; return this; }
            void updateFromCommand(Command cmd, CommandSpec commandSpec) {
                if (isNonDefault(cmd.synopsisHeading(), DEFAULT_SYNOPSIS_HEADING))            {synopsisHeading = cmd.synopsisHeading();}
                if (isNonDefault(cmd.commandListHeading(), DEFAULT_COMMAND_LIST_HEADING))     {commandListHeading = cmd.commandListHeading();}
                if (isNonDefault(cmd.requiredOptionMarker(), DEFAULT_REQUIRED_OPTION_MARKER)) {requiredOptionMarker = cmd.requiredOptionMarker();}
                if (isNonDefault(cmd.abbreviateSynopsis(), DEFAULT_ABBREVIATE_SYNOPSIS))      {abbreviateSynopsis = cmd.abbreviateSynopsis();}
                if (isNonDefault(cmd.sortOptions(), DEFAULT_SORT_OPTIONS))                    {sortOptions = cmd.sortOptions();}
                if (isNonDefault(cmd.showDefaultValues(), DEFAULT_SHOW_DEFAULT_VALUES))       {showDefaultValues = cmd.showDefaultValues();}
                if (isNonDefault(cmd.hidden(), DEFAULT_HIDDEN))                               {hidden = cmd.hidden();}
                if (isNonDefault(cmd.customSynopsis(), DEFAULT_MULTI_LINE))                   {customSynopsis = cmd.customSynopsis().clone();}
                if (isNonDefault(cmd.description(), DEFAULT_MULTI_LINE))                      {description = cmd.description().clone();}
                if (isNonDefault(cmd.descriptionHeading(), DEFAULT_SINGLE_VALUE))             {descriptionHeading = cmd.descriptionHeading();}
                if (isNonDefault(cmd.header(), DEFAULT_MULTI_LINE))                           {header = cmd.header().clone();}
                if (isNonDefault(cmd.headerHeading(), DEFAULT_SINGLE_VALUE))                  {headerHeading = cmd.headerHeading();}
                if (isNonDefault(cmd.footer(), DEFAULT_MULTI_LINE))                           {footer = cmd.footer().clone();}
                if (isNonDefault(cmd.footerHeading(), DEFAULT_SINGLE_VALUE))                  {footerHeading = cmd.footerHeading();}
                if (isNonDefault(cmd.parameterListHeading(), DEFAULT_SINGLE_VALUE))           {parameterListHeading = cmd.parameterListHeading();}
                if (isNonDefault(cmd.optionListHeading(), DEFAULT_SINGLE_VALUE))              {optionListHeading = cmd.optionListHeading();}
                if (isNonDefault(cmd.usageHelpWidth(), DEFAULT_USAGE_WIDTH))                  {width(cmd.usageHelpWidth());} // validate

                ResourceBundle rb = StringUtils.isBlank(cmd.resourceBundle()) ? null : ResourceBundle.getBundle(cmd.resourceBundle());
                if (rb != null) { messages(new Messages(commandSpec, rb)); } // else preserve superclass bundle
            }
            void initFromMixin(UsageMessageSpec mixin, CommandSpec commandSpec) {
                if (initializable(synopsisHeading, mixin.synopsisHeading(), DEFAULT_SYNOPSIS_HEADING))                 {synopsisHeading = mixin.synopsisHeading();}
                if (initializable(commandListHeading, mixin.commandListHeading(), DEFAULT_COMMAND_LIST_HEADING))       {commandListHeading = mixin.commandListHeading();}
                if (initializable(requiredOptionMarker, mixin.requiredOptionMarker(), DEFAULT_REQUIRED_OPTION_MARKER)) {requiredOptionMarker = mixin.requiredOptionMarker();}
                if (initializable(abbreviateSynopsis, mixin.abbreviateSynopsis(), DEFAULT_ABBREVIATE_SYNOPSIS))        {abbreviateSynopsis = mixin.abbreviateSynopsis();}
                if (initializable(sortOptions, mixin.sortOptions(), DEFAULT_SORT_OPTIONS))                             {sortOptions = mixin.sortOptions();}
                if (initializable(showDefaultValues, mixin.showDefaultValues(), DEFAULT_SHOW_DEFAULT_VALUES))          {showDefaultValues = mixin.showDefaultValues();}
                if (initializable(hidden, mixin.hidden(), DEFAULT_HIDDEN))                                             {hidden = mixin.hidden();}
                if (initializable(customSynopsis, mixin.customSynopsis(), DEFAULT_MULTI_LINE))                         {customSynopsis = mixin.customSynopsis().clone();}
                if (initializable(description, mixin.description(), DEFAULT_MULTI_LINE))                               {description = mixin.description().clone();}
                if (initializable(descriptionHeading, mixin.descriptionHeading(), DEFAULT_SINGLE_VALUE))               {descriptionHeading = mixin.descriptionHeading();}
                if (initializable(header, mixin.header(), DEFAULT_MULTI_LINE))                                         {header = mixin.header().clone();}
                if (initializable(headerHeading, mixin.headerHeading(), DEFAULT_SINGLE_VALUE))                         {headerHeading = mixin.headerHeading();}
                if (initializable(footer, mixin.footer(), DEFAULT_MULTI_LINE))                                         {footer = mixin.footer().clone();}
                if (initializable(footerHeading, mixin.footerHeading(), DEFAULT_SINGLE_VALUE))                         {footerHeading = mixin.footerHeading();}
                if (initializable(parameterListHeading, mixin.parameterListHeading(), DEFAULT_SINGLE_VALUE))           {parameterListHeading = mixin.parameterListHeading();}
                if (initializable(optionListHeading, mixin.optionListHeading(), DEFAULT_SINGLE_VALUE))                 {optionListHeading = mixin.optionListHeading();}
                if (Messages.empty(messages)) { messages(Messages.copy(commandSpec, mixin.messages())); }
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
        /** Models parser configuration specification.
         * @since 3.0 */
        public static class ParserSpec {

            /** Constant String holding the default separator between options and option parameters: <code>{@value}</code>.*/
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
            private boolean collectErrors = false;
            private boolean caseInsensitiveEnumValuesAllowed = false;
            private boolean trimQuotes = false;
            private boolean splitQuotedStrings = false;

            /** Returns the String to use as the separator between options and option parameters. {@code "="} by default,
             * initialized from {@link Command#separator()} if defined.*/
            public String separator() { return (separator == null) ? DEFAULT_SEPARATOR : separator; }

            /** @see CommandLine#isStopAtUnmatched() */
            public boolean stopAtUnmatched()                   { return stopAtUnmatched; }
            /** @see CommandLine#isStopAtPositional() */
            public boolean stopAtPositional()                  { return stopAtPositional; }
            /** @see CommandLine#getEndOfOptionsDelimiter()
             * @since 3.5 */
            public String endOfOptionsDelimiter()             { return endOfOptionsDelimiter; }
            /** @see CommandLine#isToggleBooleanFlags() */
            public boolean toggleBooleanFlags()                { return toggleBooleanFlags; }
            /** @see CommandLine#isOverwrittenOptionsAllowed() */
            public boolean overwrittenOptionsAllowed()         { return overwrittenOptionsAllowed; }
            /** @see CommandLine#isUnmatchedArgumentsAllowed() */
            public boolean unmatchedArgumentsAllowed()         { return unmatchedArgumentsAllowed; }
            /** @see CommandLine#isExpandAtFiles() */
            public boolean expandAtFiles()                     { return expandAtFiles; }
            /** @see CommandLine#getAtFileCommentChar()
             * @since 3.5 */
            public Character atFileCommentChar()               { return atFileCommentChar; }
            /** @see CommandLine#isPosixClusteredShortOptionsAllowed() */
            public boolean posixClusteredShortOptionsAllowed() { return posixClusteredShortOptionsAllowed; }
            /** @see CommandLine#isCaseInsensitiveEnumValuesAllowed()
             * @since 3.4 */
            public boolean caseInsensitiveEnumValuesAllowed()  { return caseInsensitiveEnumValuesAllowed; }
            /** @see CommandLine#isTrimQuotes()
             * @since 3.7 */
            public boolean trimQuotes()  { return trimQuotes; }
            /** @see CommandLine#isSplitQuotedStrings()
             * @since 3.7 */
            public boolean splitQuotedStrings()  { return splitQuotedStrings; }
            /** @see CommandLine#isUnmatchedOptionsArePositionalParams() */
            public boolean unmatchedOptionsArePositionalParams() { return unmatchedOptionsArePositionalParams; }
            private boolean splitFirst()                       { return limitSplit(); }
            /** Returns true if arguments should be split first before any further processing and the number of
             * parts resulting from the split is limited to the max arity of the argument. */
            public boolean limitSplit()                        { return limitSplit; }
            /** Returns true if options with attached arguments should not consume subsequent arguments and should not validate arity. */
            public boolean aritySatisfiedByAttachedOptionParam() { return aritySatisfiedByAttachedOptionParam; }
            /** Returns true if exceptions during parsing should be collected instead of thrown.
             * Multiple errors may be encountered during parsing. These can be obtained from {@link ParseResult#errors()}.
             * @since 3.2 */
            public boolean collectErrors()                     { return collectErrors; }

            /** Sets the String to use as the separator between options and option parameters.
             * @return this ParserSpec for method chaining */
            public ParserSpec separator(String separator)                                  { this.separator = separator; return this; }
            /** @see CommandLine#setStopAtUnmatched(boolean) */
            public ParserSpec stopAtUnmatched(boolean stopAtUnmatched)                     { this.stopAtUnmatched = stopAtUnmatched; return this; }
            /** @see CommandLine#setStopAtPositional(boolean) */
            public ParserSpec stopAtPositional(boolean stopAtPositional)                   { this.stopAtPositional = stopAtPositional; return this; }
            /** @see CommandLine#setEndOfOptionsDelimiter(String)
             * @since 3.5 */
            public ParserSpec endOfOptionsDelimiter(String delimiter)                      { this.endOfOptionsDelimiter = Assert.notNull(delimiter, "end-of-options delimiter"); return this; }
            /** @see CommandLine#setToggleBooleanFlags(boolean) */
            public ParserSpec toggleBooleanFlags(boolean toggleBooleanFlags)               { this.toggleBooleanFlags = toggleBooleanFlags; return this; }
            /** @see CommandLine#setOverwrittenOptionsAllowed(boolean) */
            public ParserSpec overwrittenOptionsAllowed(boolean overwrittenOptionsAllowed) { this.overwrittenOptionsAllowed = overwrittenOptionsAllowed; return this; }
            /** @see CommandLine#setUnmatchedArgumentsAllowed(boolean) */
            public ParserSpec unmatchedArgumentsAllowed(boolean unmatchedArgumentsAllowed) { this.unmatchedArgumentsAllowed = unmatchedArgumentsAllowed; return this; }
            /** @see CommandLine#setExpandAtFiles(boolean) */
            public ParserSpec expandAtFiles(boolean expandAtFiles)                         { this.expandAtFiles = expandAtFiles; return this; }
            /** @see CommandLine#setAtFileCommentChar(Character)
             * @since 3.5 */
            public ParserSpec atFileCommentChar(Character atFileCommentChar)               { this.atFileCommentChar = atFileCommentChar; return this; }
            /** @see CommandLine#setPosixClusteredShortOptionsAllowed(boolean) */
            public ParserSpec posixClusteredShortOptionsAllowed(boolean posixClusteredShortOptionsAllowed) { this.posixClusteredShortOptionsAllowed = posixClusteredShortOptionsAllowed; return this; }
            /** @see CommandLine#setCaseInsensitiveEnumValuesAllowed(boolean)
             * @since 3.4 */
            public ParserSpec caseInsensitiveEnumValuesAllowed(boolean caseInsensitiveEnumValuesAllowed) { this.caseInsensitiveEnumValuesAllowed = caseInsensitiveEnumValuesAllowed; return this; }
            /** @see CommandLine#setTrimQuotes(boolean)
             * @since 3.7 */
            public ParserSpec trimQuotes(boolean trimQuotes) { this.trimQuotes = trimQuotes; return this; }
            /** @see CommandLine#setSplitQuotedStrings(boolean)
             * @since 3.7 */
            public ParserSpec splitQuotedStrings(boolean splitQuotedStrings)  { this.splitQuotedStrings = splitQuotedStrings; return this; }
            /** @see CommandLine#setUnmatchedOptionsArePositionalParams(boolean) */
            public ParserSpec unmatchedOptionsArePositionalParams(boolean unmatchedOptionsArePositionalParams) { this.unmatchedOptionsArePositionalParams = unmatchedOptionsArePositionalParams; return this; }
            /** Sets whether exceptions during parsing should be collected instead of thrown.
             * Multiple errors may be encountered during parsing. These can be obtained from {@link ParseResult#errors()}.
             * @since 3.2 */
            public ParserSpec collectErrors(boolean collectErrors)                         { this.collectErrors = collectErrors; return this; }

            /** Returns true if options with attached arguments should not consume subsequent arguments and should not validate arity. */
            public ParserSpec aritySatisfiedByAttachedOptionParam(boolean newValue) { aritySatisfiedByAttachedOptionParam = newValue; return this; }

            /** Sets whether arguments should be {@linkplain ArgSpec#splitRegex() split} first before any further processing.
             * If true, the original argument will only be split into as many parts as allowed by max arity. */
            public ParserSpec limitSplit(boolean limitSplit)                               { this.limitSplit = limitSplit; return this; }
            void initSeparator(String value)   { if (initializable(separator, value, DEFAULT_SEPARATOR)) {separator = value;} }
            void updateSeparator(String value) { if (isNonDefault(value, DEFAULT_SEPARATOR))             {separator = value;} }
            public String toString() {
                return String.format("posixClusteredShortOptionsAllowed=%s, stopAtPositional=%s, stopAtUnmatched=%s, " +
                                "separator=%s, overwrittenOptionsAllowed=%s, unmatchedArgumentsAllowed=%s, expandAtFiles=%s, " +
                                "atFileCommentChar=%s, endOfOptionsDelimiter=%s, limitSplit=%s, aritySatisfiedByAttachedOptionParam=%s, " +
                                "toggleBooleanFlags=%s, unmatchedOptionsArePositionalParams=%s, collectErrors=%s," +
                                "caseInsensitiveEnumValuesAllowed=%s, trimQuotes=%s, splitQuotedStrings=%s",
                        posixClusteredShortOptionsAllowed, stopAtPositional, stopAtUnmatched,
                        separator, overwrittenOptionsAllowed, unmatchedArgumentsAllowed, expandAtFiles,
                        atFileCommentChar, endOfOptionsDelimiter, limitSplit, aritySatisfiedByAttachedOptionParam,
                        toggleBooleanFlags, unmatchedOptionsArePositionalParams, collectErrors,
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
        /** Models the shared attributes of {@link OptionSpec} and {@link PositionalParamSpec}.
         * @since 3.0 */
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
            private List<String> stringValues = new ArrayList<String>();
            private List<String> originalStringValues = new ArrayList<String>();
            protected String toString;
            private List<Object> typedValues = new ArrayList<Object>();
            Map<Integer, Object> typedValueAtPosition = new TreeMap<Integer, Object>();

            /** Constructs a new {@code ArgSpec}. */
            private <T extends Builder<T>> ArgSpec(Builder<T> builder) {
                description = builder.description == null ? new String[0] : builder.description;
                descriptionKey = builder.descriptionKey;
                splitRegex = builder.splitRegex == null ? "" : builder.splitRegex;
                paramLabel = StringUtils.isBlank(builder.paramLabel) ? "PARAM" : builder.paramLabel;
                hideParamSyntax = builder.hideParamSyntax;
                converters = builder.converters == null ? new ITypeConverter<?>[0] : builder.converters;
                showDefaultValue = builder.showDefaultValue == null ? Help.Visibility.ON_DEMAND : builder.showDefaultValue;
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
                        tempArity = (builder.type == null || isBoolean(builder.type)) ? Range.valueOf("0") : Range.valueOf("1");
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
                        auxiliaryTypes = new Class<?>[]{type.getComponentType()};
                    } else if (Collection.class.isAssignableFrom(type)) { // type is a collection but element type is unspecified
                        auxiliaryTypes = new Class<?>[] {String.class}; // use String elements
                    } else if (Map.class.isAssignableFrom(type)) { // type is a map but element type is unspecified
                        auxiliaryTypes = new Class<?>[] {String.class, String.class}; // use String keys and String values
                    } else {
                        auxiliaryTypes = new Class<?>[] {type};
                    }
                } else {
                    auxiliaryTypes = builder.auxiliaryTypes;
                }
                if (builder.completionCandidates == null && auxiliaryTypes[0].isEnum()) {
                    List<String> list = new ArrayList<String>();
                    for (Object c : auxiliaryTypes[0].getEnumConstants()) { list.add(c.toString()); }
                    completionCandidates = Collections.unmodifiableList(list);
                } else {
                    completionCandidates = builder.completionCandidates;
                }
                if (interactive && (arity.min != 1 || arity.max != 1)) {
                    throw new InitializationException("Interactive options and positional parameters are only supported for arity=1, not for arity=" + arity);
                }
            }

            /** Returns whether this is a required option or positional parameter.
             * @see Option#required() */
            public boolean required()      { return required; }

            /** Returns whether this option will prompt the user to enter a value on the command line.
             * @see Option#interactive() */
            public boolean interactive()   { return interactive; }

            /** Returns the description template of this option, before variables are rendered.
             * @see Option#description() */
            public String[] description()  { return description.clone(); }

            /** Returns the description key of this arg spec, used to get the description from a resource bundle.
             * @see Option#descriptionKey()
             * @see Parameters#descriptionKey()
             * @since 3.6 */
            public String descriptionKey()  { return descriptionKey; }

            /** Returns the description of this option, after variables are rendered. Used when generating the usage documentation.
             * @see Option#description()
             * @since 3.2 */
            public String[] renderedDescription()  {
                String[] desc = description();
                if (desc == null || desc.length == 0) { return desc; }
                StringBuilder candidates = new StringBuilder();
                if (completionCandidates() != null) {
                    for (String c : completionCandidates()) {
                        if (candidates.length() > 0) { candidates.append(", "); }
                        candidates.append(c);
                    }
                }
                String defaultValueString = defaultValueString();
                String[] result = new String[desc.length];
                for (int i = 0; i < desc.length; i++) {
                    result[i] = String.format(desc[i].replace(DESCRIPTION_VARIABLE_DEFAULT_VALUE, defaultValueString)
                            .replace(DESCRIPTION_VARIABLE_COMPLETION_CANDIDATES, candidates.toString()));
                }
                return result;
            }

            /** Returns how many arguments this option or positional parameter requires.
             * @see Option#arity() */
            public Range arity()           { return arity; }
    
            /** Returns the name of the option or positional parameter used in the usage help message.
             * @see Option#paramLabel() {@link Parameters#paramLabel()} */
            public String paramLabel()     { return paramLabel; }
    
            /** Returns whether usage syntax decorations around the {@linkplain #paramLabel() paramLabel} should be suppressed.
             * The default is {@code false}: by default, the paramLabel is surrounded with {@code '['} and {@code ']'} characters
             * if the value is optional and followed by ellipses ("...") when multiple values can be specified.
             * @since 3.6.0 */
            public boolean hideParamSyntax()     { return hideParamSyntax; }
    
            /** Returns auxiliary type information used when the {@link #type()} is a generic {@code Collection}, {@code Map} or an abstract class.
             * @see Option#type() */
            public Class<?>[] auxiliaryTypes() { return auxiliaryTypes.clone(); }
    
            /** Returns one or more {@link CommandLine.ITypeConverter type converters} to use to convert the command line
             * argument into a strongly typed value (or key-value pair for map fields). This is useful when a particular
             * option or positional parameter should use a custom conversion that is different from the normal conversion for the arg spec's type.
             * @see Option#converter() */
            public ITypeConverter<?>[] converters() { return converters.clone(); }
    
            /** Returns a regular expression to split option parameter values or {@code ""} if the value should not be split.
             * @see Option#split() */
            public String splitRegex()     { return splitRegex; }
    
            /** Returns whether this option should be excluded from the usage message.
             * @see Option#hidden() */
            public boolean hidden()        { return hidden; }
    
            /** Returns the type to convert the option or positional parameter to before {@linkplain #setValue(Object) setting} the value. */
            public Class<?> type()         { return type; }
    
            /** Returns the default value of this option or positional parameter, before splitting and type conversion.
             * This method returns the programmatically set value; this may differ from the default value that is actually used:
             * if this ArgSpec is part of a CommandSpec with a {@link IDefaultValueProvider}, picocli will first try to obtain
             * the default value from the default value provider, and this method is only called if the default provider is
             * {@code null} or returned a {@code null} value.
             * @return the programmatically set default value of this option/positional parameter,
             *      returning {@code null} means this option or positional parameter does not have a default
             * @see CommandSpec#defaultValueProvider()
             */
            public String defaultValue()   { return defaultValue; }
            /** Returns the initial value this option or positional parameter. If {@link #hasInitialValue()} is true,
             * the option will be reset to the initial value before parsing (regardless of whether a default value exists),
             * to clear values that would otherwise remain from parsing previous input. */
            public Object initialValue()     { return initialValue; }
            /** Determines whether the option or positional parameter will be reset to the {@link #initialValue()}
             * before parsing new input.*/
            public boolean hasInitialValue() { return hasInitialValue; }
    
            /** Returns whether this option or positional parameter's default value should be shown in the usage help. */
            public Help.Visibility showDefaultValue() { return showDefaultValue; }

            /** Returns the default value String displayed in the description. If this ArgSpec is part of a
             * CommandSpec with a {@link IDefaultValueProvider}, this method will first try to obtain
             * the default value from the default value provider; if the provider is {@code null} or if it
             * returns a {@code null} value, then next any value set to {@link ArgSpec#defaultValue()}
             * is returned, and if this is also {@code null}, finally the {@linkplain ArgSpec#initialValue() initial value} is returned.
             * @see CommandSpec#defaultValueProvider()
             * @see ArgSpec#defaultValue() */
            public String defaultValueString() {
                String fromProvider = null;
                IDefaultValueProvider defaultValueProvider = null;
                try {
                    defaultValueProvider = commandSpec.defaultValueProvider();
                    fromProvider = defaultValueProvider == null ? null : defaultValueProvider.defaultValue(this);
                } catch (Exception ex) {
                    new Tracer().info("Error getting default value for %s from %s: %s", this, defaultValueProvider, ex);
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

            /** Returns the explicitly set completion candidates for this option or positional parameter, valid enum
             * constant names, or {@code null} if this option or positional parameter does not have any completion
             * candidates and its type is not an enum.
             * @return the completion candidates for this option or positional parameter, valid enum constant names,
             * or {@code null}
             * @since 3.2 */
            public Iterable<String> completionCandidates() { return completionCandidates; }

            /** Returns the {@link IGetter} that is responsible for supplying the value of this argument. */
            public IGetter getter()        { return getter; }
            /** Returns the {@link ISetter} that is responsible for modifying the value of this argument. */
            public ISetter setter()        { return setter; }

            /** Returns the current value of this argument. Delegates to the current {@link #getter()}. */
            public <T> T getValue() throws PicocliException {
                try {
                    return getter.get();
                } catch (PicocliException ex) { throw ex;
                } catch (Exception ex) {        throw new PicocliException("Could not get value for " + this + ": " + ex, ex);
                }
            }
            /** Sets the value of this argument to the specified value and returns the previous value. Delegates to the current {@link #setter()}. */
            public <T> T setValue(T newValue) throws PicocliException {
                try {
                    return setter.set(newValue);
                } catch (PicocliException ex) { throw ex;
                } catch (Exception ex) {        throw new PicocliException("Could not set value (" + newValue + ") for " + this + ": " + ex, ex);
                }
            }
            /** Sets the value of this argument to the specified value and returns the previous value. Delegates to the current {@link #setter()}.
             * @since 3.5 */
            public <T> T setValue(T newValue, CommandLine commandLine) throws PicocliException {
                if (setter instanceof MethodBinding) { ((MethodBinding) setter).commandLine = commandLine; }
                try {
                    return setter.set(newValue);
                } catch (PicocliException ex) { throw ex;
                } catch (Exception ex) {        throw new PicocliException("Could not set value (" + newValue + ") for " + this + ": " + ex, ex);
                }
            }

            /** Returns {@code true} if this argument's {@link #type()} is an array, a {@code Collection} or a {@code Map}, {@code false} otherwise. */
            public boolean isMultiValue()     { return CommandLine.isMultiValue(type()); }
            /** Returns {@code true} if this argument is a named option, {@code false} otherwise. */
            public abstract boolean isOption();
            /** Returns {@code true} if this argument is a positional parameter, {@code false} otherwise. */
            public abstract boolean isPositional();
    
            /** Returns the untyped command line arguments matched by this option or positional parameter spec.
             * @return the matched arguments after {@linkplain #splitRegex() splitting}, but before type conversion.
             *      For map properties, {@code "key=value"} values are split into the key and the value part. */
            public List<String> stringValues() { return Collections.unmodifiableList(stringValues); }

            /** Returns the typed command line arguments matched by this option or positional parameter spec.
             * @return the matched arguments after {@linkplain #splitRegex() splitting} and type conversion.
             *      For map properties, {@code "key=value"} values are split into the key and the value part. */
            public List<Object> typedValues() { return Collections.unmodifiableList(typedValues); }

            /** Sets the {@code stringValues} to a new list instance. */
            protected void resetStringValues() { stringValues = new ArrayList<String>(); }

            /** Returns the original command line arguments matched by this option or positional parameter spec.
             * @return the matched arguments as found on the command line: empty Strings for options without value, the
             *      values have not been {@linkplain #splitRegex() split}, and for map properties values may look like {@code "key=value"}*/
            public List<String> originalStringValues() { return Collections.unmodifiableList(originalStringValues); }

            /** Sets the {@code originalStringValues} to a new list instance. */
            protected void resetOriginalStringValues() { originalStringValues = new ArrayList<String>(); }

            /** Returns whether the default for this option or positional parameter should be shown, potentially overriding the specified global setting.
             * @param usageHelpShowDefaults whether the command's UsageMessageSpec is configured to show default values. */
            public boolean internalShowDefaultValue(boolean usageHelpShowDefaults) {
                if (showDefaultValue() == Help.Visibility.ALWAYS)   { return true; }  // override global usage help setting
                if (showDefaultValue() == Help.Visibility.NEVER)    { return false; } // override global usage help setting
                if (initialValue == null && defaultValue() == null) { return false; } // no default value to show
                return usageHelpShowDefaults && !isBoolean(type());
            }
            /** Returns the Messages for this arg specification, or {@code null}.
             * @since 3.6 */
            public Messages messages() { return messages; }
            /** Sets the Messages for this ArgSpec, and returns this ArgSpec.
             * @param msgs the new Messages value, may be {@code null}
             * @see Command#resourceBundle()
             * @see OptionSpec#description()
             * @see PositionalParamSpec#description()
             * @since 3.6 */
            public ArgSpec messages(Messages msgs) { messages = msgs; return this; }

            /** Returns a string respresentation of this option or positional parameter. */
            public String toString() { return toString; }
    
            String[] splitValue(String value, ParserSpec parser, Range arity, int consumed) {
                if (splitRegex().length() == 0) { return new String[] {value}; }
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
                        case '\\': escaping = !escaping; break;
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
                        default: escaping = false; break;
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
                    new Tracer().warn("Unable to respect quotes while splitting value %s for %s (unprocessed remainder: %s)%n", value, this, quotedValues);
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
                        case '\\': escaping = !escaping; break;
                        case '\"':
                            if (!escaping) {
                                inQuote = !inQuote;
                                if (!inQuote) { result.append(quotedValues.remove()); }
                                skip = parser.trimQuotes();
                            }
                            break;
                        default: escaping = false; break;
                    }
                    if (!skip) { result.appendCodePoint(ch); }
                    skip = false;
                }
                return result.toString();
            }

            protected boolean equalsImpl(ArgSpec other) {
                if (other == this) { return true; }
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
                        && Arrays.equals(this.auxiliaryTypes, other.auxiliaryTypes)
                        ;
                return result;
            }
            protected int hashCodeImpl() {
                return 17
                        + 37 * Assert.hashCode(defaultValue)
                        + 37 * Assert.hashCode(type)
                        + 37 * Assert.hashCode(arity)
                        + 37 * Assert.hashCode(hidden)
                        + 37 * Assert.hashCode(paramLabel)
                        + 37 * Assert.hashCode(hideParamSyntax)
                        + 37 * Assert.hashCode(required)
                        + 37 * Assert.hashCode(splitRegex)
                        + 37 * Arrays.hashCode(description)
                        + 37 * Assert.hashCode(descriptionKey)
                        + 37 * Arrays.hashCode(auxiliaryTypes)
                        ;
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

                Builder() {}
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
    
                public    abstract ArgSpec build();
                protected abstract T self(); // subclasses must override to return "this"
                /** Returns whether this is a required option or positional parameter.
                 * @see Option#required() */
                public boolean required()      { return required; }
                /** Returns whether this option prompts the user to enter a value on the command line.
                 * @see Option#interactive() */
                public boolean interactive()   { return interactive; }

                /** Returns the description of this option, used when generating the usage documentation.
                 * @see Option#description() */
                public String[] description()  { return description; }

                /** Returns the description key of this arg spec, used to get the description from a resource bundle.
                 * @see Option#descriptionKey()
                 * @see Parameters#descriptionKey()
                 * @since 3.6 */
                public String descriptionKey()  { return descriptionKey; }

                /** Returns how many arguments this option or positional parameter requires.
                 * @see Option#arity() */
                public Range arity()           { return arity; }
    
                /** Returns the name of the option or positional parameter used in the usage help message.
                 * @see Option#paramLabel() {@link Parameters#paramLabel()} */
                public String paramLabel()     { return paramLabel; }

                /** Returns whether usage syntax decorations around the {@linkplain #paramLabel() paramLabel} should be suppressed.
                 * The default is {@code false}: by default, the paramLabel is surrounded with {@code '['} and {@code ']'} characters
                 * if the value is optional and followed by ellipses ("...") when multiple values can be specified.
                 * @since 3.6.0 */
                public boolean hideParamSyntax()     { return hideParamSyntax; }

                /** Returns auxiliary type information used when the {@link #type()} is a generic {@code Collection}, {@code Map} or an abstract class.
                 * @see Option#type() */
                public Class<?>[] auxiliaryTypes() { return auxiliaryTypes; }

                /** Returns one or more {@link CommandLine.ITypeConverter type converters} to use to convert the command line
                 * argument into a strongly typed value (or key-value pair for map fields). This is useful when a particular
                 * option or positional parameter should use a custom conversion that is different from the normal conversion for the arg spec's type.
                 * @see Option#converter() */
                public ITypeConverter<?>[] converters() { return converters; }

                /** Returns a regular expression to split option parameter values or {@code ""} if the value should not be split.
                 * @see Option#split() */
                public String splitRegex()     { return splitRegex; }

                /** Returns whether this option should be excluded from the usage message.
                 * @see Option#hidden() */
                public boolean hidden()        { return hidden; }

                /** Returns the type to convert the option or positional parameter to before {@linkplain #setValue(Object) setting} the value. */
                public Class<?> type()         { return type; }

                /** Returns the default value of this option or positional parameter, before splitting and type conversion.
                 * A value of {@code null} means this option or positional parameter does not have a default. */
                public String defaultValue()     { return defaultValue; }
                /** Returns the initial value this option or positional parameter. If {@link #hasInitialValue()} is true,
                 * the option will be reset to the initial value before parsing (regardless of whether a default value exists),
                 * to clear values that would otherwise remain from parsing previous input. */
                public Object initialValue()     { return initialValue; }
                /** Determines whether the option or positional parameter will be reset to the {@link #initialValue()}
                 * before parsing new input.*/
                public boolean hasInitialValue() { return hasInitialValue; }

                /** Returns whether this option or positional parameter's default value should be shown in the usage help. */
                public Help.Visibility showDefaultValue() { return showDefaultValue; }

                /** Returns the completion candidates for this option or positional parameter, or {@code null}.
                 * @since 3.2 */
                public Iterable<String> completionCandidates() { return completionCandidates; }

                /** Returns the {@link IGetter} that is responsible for supplying the value of this argument. */
                public IGetter getter()        { return getter; }
                /** Returns the {@link ISetter} that is responsible for modifying the value of this argument. */
                public ISetter setter()        { return setter; }

                public String toString() { return toString; }

                /** Sets whether this is a required option or positional parameter, and returns this builder. */
                public T required(boolean required)          { this.required = required; return self(); }

                /** Sets whether this option prompts the user to enter a value on the command line, and returns this builder. */
                public T interactive(boolean interactive)          { this.interactive = interactive; return self(); }

                /** Sets the description of this option, used when generating the usage documentation, and returns this builder.
                 * @see Option#description() */
                public T description(String... description)  { this.description = Assert.notNull(description, "description").clone(); return self(); }

                /** Sets the description key that is used to look up the description in a resource bundle, and returns this builder.
                 * @see Option#descriptionKey()
                 * @see Parameters#descriptionKey()
                 * @since 3.6 */
                public T descriptionKey(String descriptionKey) { this.descriptionKey = descriptionKey; return self(); }

                /** Sets how many arguments this option or positional parameter requires, and returns this builder. */
                public T arity(String range)                 { return arity(Range.valueOf(range)); }
    
                /** Sets how many arguments this option or positional parameter requires, and returns this builder. */
                public T arity(Range arity)                  { this.arity = Assert.notNull(arity, "arity"); return self(); }
    
                /** Sets the name of the option or positional parameter used in the usage help message, and returns this builder. */
                public T paramLabel(String paramLabel)       { this.paramLabel = Assert.notNull(paramLabel, "paramLabel"); return self(); }

                /** Sets whether usage syntax decorations around the {@linkplain #paramLabel() paramLabel} should be suppressed.
                 * The default is {@code false}: by default, the paramLabel is surrounded with {@code '['} and {@code ']'} characters
                 * if the value is optional and followed by ellipses ("...") when multiple values can be specified.
                 * @since 3.6.0 */
                public T hideParamSyntax(boolean hideParamSyntax) { this.hideParamSyntax = hideParamSyntax; return self(); }
    
                /** Sets auxiliary type information, and returns this builder.
                 * @param types  the element type(s) when the {@link #type()} is a generic {@code Collection} or a {@code Map};
                 * or the concrete type when the {@link #type()} is an abstract class. */
                public T auxiliaryTypes(Class<?>... types)   { this.auxiliaryTypes = Assert.notNull(types, "types").clone(); return self(); }
    
                /** Sets option/positional param-specific converter (or converters for Maps), and returns this builder. */
                public T converters(ITypeConverter<?>... cs) { this.converters = Assert.notNull(cs, "type converters").clone(); return self(); }
    
                /** Sets a regular expression to split option parameter values or {@code ""} if the value should not be split, and returns this builder. */
                public T splitRegex(String splitRegex)       { this.splitRegex = Assert.notNull(splitRegex, "splitRegex"); return self(); }
    
                /** Sets whether this option or positional parameter's default value should be shown in the usage help, and returns this builder. */
                public T showDefaultValue(Help.Visibility visibility) { showDefaultValue = Assert.notNull(visibility, "visibility"); return self(); }

                /** Sets the completion candidates for this option or positional parameter, and returns this builder.
                 * @since 3.2 */
                public T completionCandidates(Iterable<String> completionCandidates) { this.completionCandidates = Assert.notNull(completionCandidates, "completionCandidates"); return self(); }

                /** Sets whether this option should be excluded from the usage message, and returns this builder. */
                public T hidden(boolean hidden)              { this.hidden = hidden; return self(); }
    
                /** Sets the type to convert the option or positional parameter to before {@linkplain #setValue(Object) setting} the value, and returns this builder.
                 * @param propertyType the type of this option or parameter. For multi-value options and positional parameters this can be an array, or a (sub-type of) Collection or Map. */
                public T type(Class<?> propertyType)         { this.type = Assert.notNull(propertyType, "type"); return self(); }
    
                /** Sets the default value of this option or positional parameter to the specified value, and returns this builder.
                 * Before parsing the command line, the result of {@linkplain #splitRegex() splitting} and {@linkplain #converters() type converting}
                 * this default value is applied to the option or positional parameter. A value of {@code null} or {@code "__no_default_value__"} means no default. */
                public T defaultValue(String defaultValue)   { this.defaultValue = NO_DEFAULT_VALUE.equals(defaultValue) ? null : defaultValue; return self(); }

                /** Sets the initial value of this option or positional parameter to the specified value, and returns this builder.
                 * If {@link #hasInitialValue()} is true, the option will be reset to the initial value before parsing (regardless
                 * of whether a default value exists), to clear values that would otherwise remain from parsing previous input. */
                public T initialValue(Object initialValue)   { this.initialValue = initialValue; return self(); }

                /** Determines whether the option or positional parameter will be reset to the {@link #initialValue()}
                 * before parsing new input.*/
                public T hasInitialValue(boolean hasInitialValue)   { this.hasInitialValue = hasInitialValue; return self(); }

                /** Sets the {@link IGetter} that is responsible for getting the value of this argument, and returns this builder. */
                public T getter(IGetter getter)              { this.getter = getter; return self(); }
                /** Sets the {@link ISetter} that is responsible for modifying the value of this argument, and returns this builder. */
                public T setter(ISetter setter)              { this.setter = setter; return self(); }

                /** Sets the string respresentation of this option or positional parameter to the specified value, and returns this builder. */
                public T withToString(String toString)       { this.toString = toString; return self(); }
            }
        }
        /** The {@code OptionSpec} class models aspects of a <em>named option</em> of a {@linkplain CommandSpec command}, including whether
         * it is required or optional, the option parameters supported (or required) by the option,
         * and attributes for the usage help message describing the option.
         * <p>
         * An option has one or more names. The option is matched when the parser encounters one of the option names in the command line arguments.
         * Depending on the option's {@link #arity() arity},
         * the parser may expect it to have option parameters. The parser will call {@link #setValue(Object) setValue} on
         * the matched option for each of the option parameters encountered.
         * </p><p>
         * For multi-value options, the {@code type} may be an array, a {@code Collection} or a {@code Map}. In this case
         * the parser will get the data structure by calling {@link #getValue() getValue} and modify the contents of this data structure.
         * (In the case of arrays, the array is replaced with a new instance with additional elements.)
         * </p><p>
         * Before calling the setter, picocli converts the option parameter value from a String to the option parameter's type.
         * </p>
         * <ul>
         *   <li>If a option-specific {@link #converters() converter} is configured, this will be used for type conversion.
         *   If the option's type is a {@code Map}, the map may have different types for its keys and its values, so
         *   {@link #converters() converters} should provide two converters: one for the map keys and one for the map values.</li>
         *   <li>Otherwise, the option's {@link #type() type} is used to look up a converter in the list of
         *   {@linkplain CommandLine#registerConverter(Class, ITypeConverter) registered converters}.
         *   For multi-value options,
         *   the {@code type} may be an array, or a {@code Collection} or a {@code Map}. In that case the elements are converted
         *   based on the option's {@link #auxiliaryTypes() auxiliaryTypes}. The auxiliaryType is used to look up
         *   the converter(s) to use to convert the individual parameter values.
         *   Maps may have different types for its keys and its values, so {@link #auxiliaryTypes() auxiliaryTypes}
         *   should provide two types: one for the map keys and one for the map values.</li>
         * </ul>
         * <p>
         * {@code OptionSpec} objects are used by the picocli command line interpreter and help message generator.
         * Picocli can construct an {@code OptionSpec} automatically from fields and methods with {@link Option @Option}
         * annotations. Alternatively an {@code OptionSpec} can be constructed programmatically.
         * </p><p>
         * When an {@code OptionSpec} is created from an {@link Option @Option} -annotated field or method, it is "bound"
         * to that field or method: this field is set (or the method is invoked) when the option is matched and
         * {@link #setValue(Object) setValue} is called.
         * Programmatically constructed {@code OptionSpec} instances will remember the value passed to the
         * {@link #setValue(Object) setValue} method so it can be retrieved with the {@link #getValue() getValue} method.
         * This behaviour can be customized by installing a custom {@link IGetter} and {@link ISetter} on the {@code OptionSpec}.
         * </p>
         * @since 3.0 */
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
            public static OptionSpec.Builder builder(String[] names) { return new Builder(names); }
    
            /** Ensures all attributes of this {@code OptionSpec} have a valid value; throws an {@link InitializationException} if this cannot be achieved. */
            private OptionSpec(Builder builder) {
                super(builder);
                names = builder.names;
                help = builder.help;
                usageHelp = builder.usageHelp;
                versionHelp = builder.versionHelp;
    
                if (names == null || names.length == 0 || Arrays.asList(names).contains("")) {
                    throw new InitializationException("Invalid names: " + Arrays.toString(names));
                }
                if (toString() == null) { toString = "option " + longestName(); }

//                if (arity().max == 0 && !(isBoolean(type()) || (isMultiValue() && isBoolean(auxiliaryTypes()[0])))) {
//                    throw new InitializationException("Option " + longestName() + " is not a boolean so should not be defined with arity=" + arity());
//                }
            }
    
            /** Returns a new Builder initialized with the attributes from this {@code OptionSpec}. Calling {@code build} immediately will return a copy of this {@code OptionSpec}.
             * @return a builder that can create a copy of this spec
             */
            public Builder toBuilder()    { return new Builder(this); }
            @Override public boolean isOption()     { return true; }
            @Override public boolean isPositional() { return false; }

            public boolean internalShowDefaultValue(boolean usageMessageShowDefaults) {
                return super.internalShowDefaultValue(usageMessageShowDefaults) && !help() && !versionHelp() && !usageHelp();
            }

            /** Returns the description template of this option, before variables are {@linkplain Option#description() rendered}.
             * If a resource bundle has been {@linkplain ArgSpec#messages(Messages) set}, this method will first try to find a value in the resource bundle:
             * If the resource bundle has no entry for the {@code fully qualified commandName + "." + descriptionKey} or for the unqualified {@code descriptionKey},
             * an attempt is made to find the option description using any of the option names (without leading hyphens) as key,
             * first with the {@code fully qualified commandName + "."} prefix, then without.
             * @see CommandSpec#qualifiedName(String)
             * @see Option#description() */
            @Override public String[] description() {
                if (messages() == null) { return super.description(); }
                String[] newValue = messages().getStringArray(descriptionKey(), null);
                if (newValue != null) { return newValue; }
                for (String name : names()) {
                    newValue = messages().getStringArray(CommandSpec.stripPrefix(name), null);
                    if (newValue != null) { return newValue; }
                }
                return super.description();
            }

            /** Returns one or more option names. The returned array will contain at least one option name.
             * @see Option#names() */
            public String[] names()       { return names.clone(); }

            /** Returns the longest {@linkplain #names() option name}. */
            public String longestName() { return Help.ShortestFirst.longestFirst(names.clone())[0]; }

            /** Returns the shortest {@linkplain #names() option name}. */
            public String shortestName() { return Help.ShortestFirst.sort(names.clone())[0]; }

            /** Returns whether this option disables validation of the other arguments.
             * @see Option#help()
             * @deprecated Use {@link #usageHelp()} and {@link #versionHelp()} instead. */
            @Deprecated public boolean help() { return help; }
    
            /** Returns whether this option allows the user to request usage help.
             * @see Option#usageHelp()  */
            public boolean usageHelp()    { return usageHelp; }
    
            /** Returns whether this option allows the user to request version information.
             * @see Option#versionHelp()  */
            public boolean versionHelp()  { return versionHelp; }
            public boolean equals(Object obj) {
                if (obj == this) { return true; }
                if (!(obj instanceof OptionSpec)) { return false; }
                OptionSpec other = (OptionSpec) obj;
                boolean result = super.equalsImpl(other)
                        && help == other.help
                        && usageHelp == other.usageHelp
                        && versionHelp == other.versionHelp
                        && new HashSet<String>(Arrays.asList(names)).equals(new HashSet<String>(Arrays.asList(other.names)));
                return result;
            }
            public int hashCode() {
                return super.hashCodeImpl()
                        + 37 * Assert.hashCode(help)
                        + 37 * Assert.hashCode(usageHelp)
                        + 37 * Assert.hashCode(versionHelp)
                        + 37 * Arrays.hashCode(names);
            }
    
            /** Builder responsible for creating valid {@code OptionSpec} objects.
             * @since 3.0
             */
            public static class Builder extends ArgSpec.Builder<Builder> {
                private String[] names;
                private boolean help;
                private boolean usageHelp;
                private boolean versionHelp;
    
                private Builder(String[] names) { this.names = names.clone(); }
                private Builder(OptionSpec original) {
                    super(original);
                    names = original.names;
                    help = original.help;
                    usageHelp = original.usageHelp;
                    versionHelp = original.versionHelp;
                }
    
                /** Returns a valid {@code OptionSpec} instance. */
                @Override public OptionSpec build() { return new OptionSpec(this); }
                /** Returns this builder. */
                @Override protected Builder self() { return this; }

                /** Returns one or more option names. At least one option name is required.
                 * @see Option#names() */
                public String[] names()       { return names; }

                /** Returns whether this option disables validation of the other arguments.
                 * @see Option#help()
                 * @deprecated Use {@link #usageHelp()} and {@link #versionHelp()} instead. */
                @Deprecated public boolean help() { return help; }

                /** Returns whether this option allows the user to request usage help.
                 * @see Option#usageHelp()  */
                public boolean usageHelp()    { return usageHelp; }

                /** Returns whether this option allows the user to request version information.
                 * @see Option#versionHelp()  */
                public boolean versionHelp()  { return versionHelp; }

                /** Replaces the option names with the specified values. At least one option name is required, and returns this builder.
                 * @return this builder instance to provide a fluent interface */
                public Builder names(String... names)           { this.names = Assert.notNull(names, "names").clone(); return self(); }
    
                /** Sets whether this option disables validation of the other arguments, and returns this builder. */
                public Builder help(boolean help)               { this.help = help; return self(); }
    
                /** Sets whether this option allows the user to request usage help, and returns this builder. */
                public Builder usageHelp(boolean usageHelp)     { this.usageHelp = usageHelp; return self(); }
    
                /** Sets whether this option allows the user to request version information, and returns this builder.*/
                public Builder versionHelp(boolean versionHelp) { this.versionHelp = versionHelp; return self(); }
            }
        }
        /** The {@code PositionalParamSpec} class models aspects of a <em>positional parameter</em> of a {@linkplain CommandSpec command}, including whether
         * it is required or optional, and attributes for the usage help message describing the positional parameter.
         * <p>
         * Positional parameters have an {@link #index() index} (or a range of indices). A positional parameter is matched when the parser
         * encounters a command line argument at that index. Named options and their parameters do not change the index counter,
         * so the command line can contain a mixture of positional parameters and named options.
         * </p><p>
         * Depending on the positional parameter's {@link #arity() arity}, the parser may consume multiple command line
         * arguments starting from the current index. The parser will call {@link #setValue(Object) setValue} on
         * the {@code PositionalParamSpec} for each of the parameters encountered.
         * For multi-value positional parameters, the {@code type} may be an array, a {@code Collection} or a {@code Map}. In this case
         * the parser will get the data structure by calling {@link #getValue() getValue} and modify the contents of this data structure.
         * (In the case of arrays, the array is replaced with a new instance with additional elements.)
         * </p><p>
         * Before calling the setter, picocli converts the positional parameter value from a String to the parameter's type.
         * </p>
         * <ul>
         *   <li>If a positional parameter-specific {@link #converters() converter} is configured, this will be used for type conversion.
         *   If the positional parameter's type is a {@code Map}, the map may have different types for its keys and its values, so
         *   {@link #converters() converters} should provide two converters: one for the map keys and one for the map values.</li>
         *   <li>Otherwise, the positional parameter's {@link #type() type} is used to look up a converter in the list of
         *   {@linkplain CommandLine#registerConverter(Class, ITypeConverter) registered converters}. For multi-value positional parameters,
         *   the {@code type} may be an array, or a {@code Collection} or a {@code Map}. In that case the elements are converted
         *   based on the positional parameter's {@link #auxiliaryTypes() auxiliaryTypes}. The auxiliaryType is used to look up
         *   the converter(s) to use to convert the individual parameter values.
         *   Maps may have different types for its keys and its values, so {@link #auxiliaryTypes() auxiliaryTypes}
         *   should provide two types: one for the map keys and one for the map values.</li>
         * </ul>
         * <p>
         * {@code PositionalParamSpec} objects are used by the picocli command line interpreter and help message generator.
         * Picocli can construct a {@code PositionalParamSpec} automatically from fields and methods with {@link Parameters @Parameters}
         * annotations. Alternatively a {@code PositionalParamSpec} can be constructed programmatically.
         * </p><p>
         * When a {@code PositionalParamSpec} is created from a {@link Parameters @Parameters} -annotated field or method,
         * it is "bound" to that field or method: this field is set (or the method is invoked) when the position is matched
         * and {@link #setValue(Object) setValue} is called.
         * Programmatically constructed {@code PositionalParamSpec} instances will remember the value passed to the
         * {@link #setValue(Object) setValue} method so it can be retrieved with the {@link #getValue() getValue} method.
         * This behaviour can be customized by installing a custom {@link IGetter} and {@link ISetter} on the {@code PositionalParamSpec}.
         * </p>
         * @since 3.0 */
        public static class PositionalParamSpec extends ArgSpec {
            private Range index;
            private Range capacity;

            /** Ensures all attributes of this {@code PositionalParamSpec} have a valid value; throws an {@link InitializationException} if this cannot be achieved. */
            private PositionalParamSpec(Builder builder) {
                super(builder);
                index = builder.index == null ? Range.valueOf("*") : builder.index; 
                capacity = builder.capacity == null ? Range.parameterCapacity(arity(), index) : builder.capacity;
                if (toString == null) { toString = "positional parameter[" + index() + "]"; }
            }
            /** Returns a new Builder initialized with the attributes from this {@code PositionalParamSpec}. Calling {@code build} immediately will return a copy of this {@code PositionalParamSpec}.
             * @return a builder that can create a copy of this spec
             */
            public Builder toBuilder()    { return new Builder(this); }
            @Override public boolean isOption()     { return false; }
            @Override public boolean isPositional() { return true; }

            /** Returns the description template of this positional parameter, before variables are {@linkplain Parameters#description() rendered}.
             * If a resource bundle has been {@linkplain ArgSpec#messages(Messages) set}, this method will first try to find a value in the resource bundle:
             * If the resource bundle has no entry for the {@code fully qualified commandName + "." + descriptionKey} or for the unqualified {@code descriptionKey},
             * an attempt is made to find the positional parameter description using {@code paramLabel() + "[" + index() + "]"} as key,
             * first with the {@code fully qualified commandName + "."} prefix, then without.
             * @see Parameters#description()
             * @see CommandSpec#qualifiedName(String)
             * @since 3.6 */
            @Override public String[] description() {
                if (messages() == null) { return super.description(); }
                String[] newValue = messages().getStringArray(descriptionKey(), null);
                if (newValue != null) { return newValue; }
                newValue = messages().getStringArray(paramLabel() + "[" + index() + "]", null);
                if (newValue != null) { return newValue; }
                return super.description();
            }

            /** Returns an index or range specifying which of the command line arguments should be assigned to this positional parameter.
             * @see Parameters#index() */
            public Range index()            { return index; }
            public Range capacity()        { return capacity; }
            public static Builder builder() { return new Builder(); }

            public int hashCode() {
                return super.hashCodeImpl()
                        + 37 * Assert.hashCode(capacity)
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
                return super.equalsImpl(other)
                        && Assert.equals(this.capacity, other.capacity)
                        && Assert.equals(this.index, other.index);
            }
    
            /** Builder responsible for creating valid {@code PositionalParamSpec} objects.
             * @since 3.0
             */
            public static class Builder extends ArgSpec.Builder<Builder> {
                private Range capacity;
                private Range index;
                private Builder() {}
                private Builder(PositionalParamSpec original) {
                    super(original);
                    index = original.index;
                    capacity = original.capacity;
                }
                /** Returns a valid {@code PositionalParamSpec} instance. */
                @Override public PositionalParamSpec build() { return new PositionalParamSpec(this); }
                /** Returns this builder. */
                @Override protected Builder self()  { return this; }

                /** Returns an index or range specifying which of the command line arguments should be assigned to this positional parameter.
                 * @see Parameters#index() */
                public Range index()            { return index; }

                /** Sets the index or range specifying which of the command line arguments should be assigned to this positional parameter, and returns this builder. */
                public Builder index(String range)  { return index(Range.valueOf(range)); }
    
                /** Sets the index or range specifying which of the command line arguments should be assigned to this positional parameter, and returns this builder. */
                public Builder index(Range index)   { this.index = index; return self(); }
    
                public Builder capacity(Range capacity)   { this.capacity = capacity; return self(); }
            }
        }

        /** This class allows applications to specify a custom binding that will be invoked for unmatched arguments.
         * A binding can be created with a {@code ISetter} that consumes the unmatched arguments {@code String[]}, or with a
         * {@code IGetter} that produces a {@code Collection<String>} that the unmatched arguments can be added to.
         * @since 3.0 */
        public static class UnmatchedArgsBinding {
            private final IGetter getter;
            private final ISetter setter;

            /** Creates a {@code UnmatchedArgsBinding} for a setter that consumes {@code String[]} objects.
             * @param setter consumes the String[] array with unmatched arguments. */
            public static UnmatchedArgsBinding forStringArrayConsumer(ISetter setter) { return new UnmatchedArgsBinding(null, setter); }

            /** Creates a {@code UnmatchedArgsBinding} for a getter that produces a {@code Collection<String>} that the unmatched arguments can be added to.
             * @param getter supplies a {@code Collection<String>} that the unmatched arguments can be added to. */
            public static UnmatchedArgsBinding forStringCollectionSupplier(IGetter getter) { return new UnmatchedArgsBinding(getter, null); }

            private UnmatchedArgsBinding(IGetter getter, ISetter setter) {
                if (getter == null && setter == null) { throw new IllegalArgumentException("Getter and setter cannot both be null"); }
                this.setter = setter;
                this.getter = getter;
            }
            /** Returns the getter responsible for producing a {@code Collection} that the unmatched arguments can be added to. */
            public IGetter getter() { return getter; }
            /** Returns the setter responsible for consuming the unmatched arguments. */
            public ISetter setter() { return setter; }
            void addAll(String[] unmatched) {
                if (setter != null) {
                    try {
                        setter.set(unmatched);
                    } catch (Exception ex) {
                        throw new PicocliException(String.format("Could not invoke setter (%s) with unmatched argument array '%s': %s", setter, Arrays.toString(unmatched), ex), ex);
                    }
                }
                if (getter != null) {
                    try {
                        Collection<String> collection = getter.get();
                        Assert.notNull(collection, "getter returned null Collection");
                        collection.addAll(Arrays.asList(unmatched));
                    } catch (Exception ex) {
                        throw new PicocliException(String.format("Could not add unmatched argument array '%s' to collection returned by getter (%s): %s",
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
                    tmp = (String) Class.forName("java.lang.reflect.Parameter").getDeclaredMethod("getName").invoke(parameter);
                } catch (Exception ignored) {}
                this.name = tmp;
            }
            public Type getParameterizedType() { return method.getGenericParameterTypes()[paramIndex]; }
            public String getName() { return name; }
            public Class<?> getType() { return method.getParameterTypes()[paramIndex]; }
            public Method getDeclaringExecutable() { return method; }
            @Override public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
                for (Annotation annotation : getDeclaredAnnotations()) {
                    if (annotationClass.isAssignableFrom(annotation.getClass())) { return annotationClass.cast(annotation); }
                }
                return null;
            }
            @Override public Annotation[] getDeclaredAnnotations() { return method.getParameterAnnotations()[paramIndex]; }
            @Override public void setAccessible(boolean flag) throws SecurityException { method.setAccessible(flag); }
            @Override public boolean isAccessible() throws SecurityException { return method.isAccessible(); }
            @Override public String toString() { return method.toString() + ":" + getName(); }
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
                    throw new InitializationException("Invalid picocli annotation on interface field");
                }
                FieldBinding binding = new FieldBinding(scope, field);
                getter = binding; setter = binding;
            }
            static TypedMember createIfAnnotated(Method method, Object scope) {
                return isAnnotated(method) ? new TypedMember(method, scope) : null;
            }
            private TypedMember(Method method, Object scope) {
                accessible = Assert.notNull(method, "method");
                accessible.setAccessible(true);
                name = propertyName(method.getName());
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean isGetter = parameterTypes.length == 0 && method.getReturnType() != Void.TYPE && method.getReturnType() != Void.class;
                boolean isSetter = parameterTypes.length > 0;
                if (isSetter == isGetter) { throw new InitializationException("Invalid method, must be either getter or setter: " + method); }
                if (isGetter) {
                    hasInitialValue = true;
                    type = method.getReturnType();
                    genericType = method.getGenericReturnType();
                    if (Proxy.isProxyClass(scope.getClass())) {
                        PicocliInvocationHandler handler = (PicocliInvocationHandler) Proxy.getInvocationHandler(scope);
                        PicocliInvocationHandler.ProxyBinding binding = handler.new ProxyBinding(method);
                        getter = binding; setter = binding;
                        initializeInitialValue(method);
                    } else {
                        //throw new IllegalArgumentException("Getter method but not a proxy: " + scope + ": " + method);
                        MethodBinding binding = new MethodBinding(scope, method);
                        getter = binding; setter = binding;
                    }
                } else {
                    hasInitialValue = false;
                    type = parameterTypes[0];
                    genericType = method.getGenericParameterTypes()[0];
                    MethodBinding binding = new MethodBinding(scope, method);
                    getter = binding; setter = binding;
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
                getter = binding; setter = binding;
                hasInitialValue = initializeInitialValue(param);
            }

            private boolean initializeInitialValue(Object arg) {
                boolean initialized = true;
                try {
                    if      (type == Boolean.TYPE || type == Boolean.class) { setter.set(false); }
                    else if (type == Byte.TYPE    || type == Byte.class)    { setter.set(Byte.valueOf((byte) 0)); }
                    else if (type == Short.TYPE   || type == Short.class)   { setter.set(Short.valueOf((short) 0)); }
                    else if (type == Integer.TYPE || type == Integer.class) { setter.set(Integer.valueOf(0)); }
                    else if (type == Long.TYPE    || type == Long.class)    { setter.set(Long.valueOf(0L)); }
                    else if (type == Float.TYPE   || type == Float.class)   { setter.set(Float.valueOf(0f)); }
                    else if (type == Double.TYPE  || type == Double.class)  { setter.set(Double.valueOf(0d)); }
                    else { initialized = false; }
                } catch (Exception ex) {
                    throw new InitializationException("Could not set initial value for " + arg + ": " + ex.toString(), ex);
                }
                return initialized;
            }
            static boolean isAnnotated(AnnotatedElement e) {
                return false
                        || e.isAnnotationPresent(Option.class)
                        || e.isAnnotationPresent(Parameters.class)
                        || e.isAnnotationPresent(Unmatched.class)
                        || e.isAnnotationPresent(Mixin.class)
                        || e.isAnnotationPresent(Spec.class)
                        || e.isAnnotationPresent(ParentCommand.class);
            }
            boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) { return accessible.isAnnotationPresent(annotationClass); }
            <T extends Annotation> T getAnnotation(Class<T> annotationClass) { return accessible.getAnnotation(annotationClass); }
            String name()            { return name; }
            boolean isArgSpec()      { return isOption() || isParameter() || (isMethodParameter() && !isMixin()); }
            boolean isOption()       { return isAnnotationPresent(Option.class); }
            boolean isParameter()    { return isAnnotationPresent(Parameters.class); }
            boolean isMixin()        { return isAnnotationPresent(Mixin.class); }
            boolean isUnmatched()    { return isAnnotationPresent(Unmatched.class); }
            boolean isInjectSpec()   { return isAnnotationPresent(Spec.class); }
            boolean isMultiValue()   { return CommandLine.isMultiValue(getType()); }
            IGetter getter()         { return getter; }
            ISetter setter()         { return setter; }
            Class<?> getType()       { return type; }
            Type getGenericType()    { return genericType; }
            public String toString() { return accessible.toString(); }
            String toGenericString() { return accessible instanceof Field ? ((Field) accessible).toGenericString() : accessible instanceof Method ? ((Method) accessible).toGenericString() : ((MethodParam)accessible).toString(); }
            boolean isMethodParameter() { return accessible instanceof MethodParam; }
            String mixinName()    {
                String annotationName = getAnnotation(Mixin.class).name();
                return StringUtils.isBlank(annotationName) ? name() : annotationName;
            }

            static String propertyName(String methodName) {
                if (methodName.length() > 3 && (methodName.startsWith("get") || methodName.startsWith("set"))) { return decapitalize(methodName.substring(3)); }
                return decapitalize(methodName);
            }
            private static String decapitalize(String name) {
                if (name == null || name.length() == 0) { return name; }
                char[] chars = name.toCharArray();
                chars[0] = Character.toLowerCase(chars[0]);
                return new String(chars);
            }
        }

        /** Utility class for getting resource bundle strings.
         * Enhances the standard <a href="https://docs.oracle.com/javase/8/docs/api/java/util/ResourceBundle.html">ResourceBundle</a>
         * with support for String arrays and qualified keys: keys that may or may not be prefixed with the fully qualified command name.
         * <p>Example properties resource bundle:</p><pre>
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
         * <p>Resources for multiple commands can be specified in a single ResourceBundle. Keys and their value can be
         * shared by multiple commands (so you don't need to repeat them for every command), but keys can be prefixed with
         * {@code fully qualified command name + "."} to specify different values for different commands.
         * The most specific key wins. For example: </p>
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
         * @see Command#resourceBundle()
         * @see Option#descriptionKey()
         * @see OptionSpec#description()
         * @see PositionalParamSpec#description()
         * @see CommandSpec#qualifiedName(String)
         * @since 3.6 */
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
                if (rb == null) { return Collections.emptySet(); }
                Set<String> keys = new LinkedHashSet<String>();
                for (Enumeration<String> k = rb.getKeys(); k.hasMoreElements(); keys.add(k.nextElement()));
                return keys;
            }

            /** Returns a copy of the specified Messages object with the CommandSpec replaced by the specified one.
             * @param spec the CommandSpec of the returned Messages
             * @param original the Messages object whose ResourceBundle to reference
             * @return a Messages object with the specified CommandSpec and the ResourceBundle of the specified Messages object
             */
            public static Messages copy(CommandSpec spec, Messages original) {
                return original == null ? null : new Messages(spec, original.rb);
            }
            /** Returns {@code true} if the specified {@code Messages} is {@code null} or has a {@code null ResourceBundle}. */
            public static boolean empty(Messages messages) { return messages == null || messages.rb == null; }

            /** Returns the String value found in the resource bundle for the specified key, or the specified default value if not found.
             * @param key unqualified resource bundle key. This method will first try to find a value by qualifying the key with the command's fully qualified name,
             *             and if not found, it will try with the unqualified key.
             * @param defaultValue value to return if the resource bundle is null or empty, or if no value was found by the qualified or unqualified key
             * @return the String value found in the resource bundle for the specified key, or the specified default value
             */
            public String getString(String key, String defaultValue) {
                if (rb == null || keys.isEmpty()) { return defaultValue; }
                String cmd = spec.qualifiedName(".");
                if (keys.contains(cmd + "." + key)) { return rb.getString(cmd + "." + key); }
                if (keys.contains(key)) { return rb.getString(key); }
                return defaultValue;
            }
            /** Returns the String array value found in the resource bundle for the specified key, or the specified default value if not found.
             * Multi-line strings can be specified in the resource bundle with {@code key.0}, {@code key.1}, {@code key.2}, etc.
             * @param key unqualified resource bundle key. This method will first try to find a value by qualifying the key with the command's fully qualified name,
             *            and if not found, it will try with the unqualified key.
             * @param defaultValues value to return if the resource bundle is null or empty, or if no value was found by the qualified or unqualified key
             * @return the String array value found in the resource bundle for the specified key, or the specified default value
             */
            public String[] getStringArray(String key, String[] defaultValues) {
                if (rb == null || keys.isEmpty()) { return defaultValues; }
                String cmd = spec.qualifiedName(".");
                List<String> result = addAllWithPrefix(rb, cmd + "." + key, keys, new ArrayList<String>());
                if (!result.isEmpty()) { return result.toArray(new String[0]); }
                addAllWithPrefix(rb, key, keys, result);
                return result.isEmpty() ? defaultValues : result.toArray(new String[0]);
            }
            private static List<String> addAllWithPrefix(ResourceBundle rb, String key, Set<String> keys, List<String> result) {
                if (keys.contains(key)) { result.add(rb.getString(key)); }
                for (int i = 0; true; i++) {
                    String elementKey = key + "." + i;
                    if (keys.contains(elementKey)) {
                        result.add(rb.getString(elementKey));
                    } else {
                        return result;
                    }
                }
            }
            /** Returns the ResourceBundle of the specified Messages object or {@code null} if the specified Messages object is {@code null}. */
            public static ResourceBundle resourceBundle(Messages messages) { return messages == null ? null : messages.resourceBundle(); }
            /** Returns the ResourceBundle of this object or {@code null}. */
            public ResourceBundle resourceBundle() { return rb; }
            /** Returns the CommandSpec of this object, never {@code null}. */
            public CommandSpec commandSpec() { return spec; }
        }
        private static class CommandReflection {
            static CommandSpec extractCommandSpec(Object command, IFactory factory, boolean annotationsAreMandatory) {
                Class<?> cls = command.getClass();
                Tracer t = new Tracer();
                t.debug("Creating CommandSpec for object of class %s with factory %s%n", cls.getName(), factory.getClass().getName());
                if (command instanceof CommandSpec) { return (CommandSpec) command; }
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
                            t.debug("%s. Creating Proxy for interface %s%n", ex.getCause(), cls.getName());
                            instance = Proxy.newProxyInstance(cls.getClassLoader(), new Class<?>[]{cls}, new PicocliInvocationHandler());
                        } else {
                            throw ex;
                        }
                    }
                } else if (command instanceof Method) {
                    cls = null; // don't mix in options/positional params from outer class @Command
                }

                CommandSpec result = CommandSpec.wrapWithoutInspection(Assert.notNull(instance, "command"));

                Stack<Class<?>> hierarchy = new Stack<Class<?>>();
                while (cls != null) { hierarchy.add(cls); cls = cls.getSuperclass(); }
                boolean hasCommandAnnotation = false;
                boolean mixinStandardHelpOptions = false;
                while (!hierarchy.isEmpty()) {
                    cls = hierarchy.pop();
                    boolean thisCommandHasAnnotation = updateCommandAttributes(cls, result, factory);
                    hasCommandAnnotation |= thisCommandHasAnnotation;
                    hasCommandAnnotation |= initFromAnnotatedFields(instance, cls, result, factory);
                    if (cls.isAnnotationPresent(Command.class)) {
                        mixinStandardHelpOptions |= cls.getAnnotation(Command.class).mixinStandardHelpOptions();
                    }
                }
                result.mixinStandardHelpOptions(mixinStandardHelpOptions); //#377 Standard help options should be added last
                if (command instanceof Method) {
                    Method method = (Method) command;
                    t.debug("Using method %s as command %n", method);
                    commandClassName = method.toString();
                    hasCommandAnnotation |= updateCommandAttributes(method, result, factory);
                    result.mixinStandardHelpOptions(method.getAnnotation(Command.class).mixinStandardHelpOptions());
                    initFromMethodParameters(instance, method, result, factory);
                    // set command name to method name, unless @Command#name is set
                    result.initName(((Method)command).getName());
                }
                result.updateArgSpecMessages();

                if (annotationsAreMandatory) {validateCommandSpec(result, hasCommandAnnotation, commandClassName); }
                result.withToString(commandClassName).validate();
                return result;
            }

            private static boolean updateCommandAttributes(Class<?> cls, CommandSpec commandSpec, IFactory factory) {
                // superclass values should not overwrite values if both class and superclass have a @Command annotation
                if (!cls.isAnnotationPresent(Command.class)) { return false; }

                Command cmd = cls.getAnnotation(Command.class);
                return updateCommandAttributes(cmd, commandSpec, factory);
            }
            private static boolean updateCommandAttributes(Method method, CommandSpec commandSpec, IFactory factory) {
                Command cmd = method.getAnnotation(Command.class);
                return updateCommandAttributes(cmd, commandSpec, factory);
            }
            private static boolean updateCommandAttributes(Command cmd, CommandSpec commandSpec, IFactory factory) {
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
                        if (Help.class == sub) { throw new InitializationException(Help.class.getName() + " is not a valid subcommand. Did you mean " + HelpCommand.class.getName() + "?"); }
                        CommandLine subcommandLine = toCommandLine(factory.create(sub), factory);
                        parent.addSubcommand(subcommandName(sub), subcommandLine);
                        initParentCommand(subcommandLine.getCommandSpec().userObject(), parent.userObject());
                    }
                    catch (InitializationException ex) { throw ex; }
                    catch (NoSuchMethodException ex) { throw new InitializationException("Cannot instantiate subcommand " +
                            sub.getName() + ": the class has no constructor", ex); }
                    catch (Exception ex) {
                        throw new InitializationException("Could not instantiate and add subcommand " +
                                sub.getName() + ": " + ex, ex);
                    }
                }
                if (cmd.addMethodSubcommands() && !(parent.userObject() instanceof Method)) {
                    parent.addMethodSubcommands(factory);
                }
            }
            static void initParentCommand(Object subcommand, Object parent) {
                if (subcommand == null) { return; }
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
                    throw new InitializationException("Unable to initialize @ParentCommand field: " + ex, ex);
                }
            }
            private static String subcommandName(Class<?> sub) {
                Command subCommand = sub.getAnnotation(Command.class);
                if (subCommand == null || Help.DEFAULT_COMMAND_NAME.equals(subCommand.name())) {
                    throw new InitializationException("Subcommand " + sub.getName() +
                            " is missing the mandatory @Command annotation with a 'name' attribute");
                }
                return subCommand.name();
            }
            private static boolean initFromAnnotatedFields(Object scope, Class<?> cls, CommandSpec receiver, IFactory factory) {
                boolean result = false;
                for (Field field : cls.getDeclaredFields()) {
                    result |= initFromAnnotatedTypedMembers(TypedMember.createIfAnnotated(field, scope), receiver, factory);
                }
                for (Method method : cls.getDeclaredMethods()) {
                    result |= initFromAnnotatedTypedMembers(TypedMember.createIfAnnotated(method, scope), receiver, factory);
                }
                return result;
            }
            private static boolean initFromAnnotatedTypedMembers(TypedMember member, CommandSpec receiver, IFactory factory) {
                boolean result = false;
                if (member == null) { return result; }
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
                    if (member.isOption())         { receiver.addOption(ArgsReflection.extractOptionSpec(member, factory)); }
                    else if (member.isParameter()) { receiver.addPositional(ArgsReflection.extractPositionalParamSpec(member, factory)); }
                    else                           { receiver.addPositional(ArgsReflection.extractUnannotatedPositionalParamSpec(member, factory)); }
                }
                if (member.isInjectSpec()) {
                    validateInjectSpec(member);
                    try { member.setter().set(receiver); } catch (Exception ex) { throw new InitializationException("Could not inject spec", ex); }
                }
                return result;
            }
            private static boolean initFromMethodParameters(Object scope, Method method, CommandSpec receiver, IFactory factory) {
                boolean result = false;
                int optionCount = 0;
                for (int i = 0, count = method.getParameterTypes().length; i < count; i++) {
                    MethodParam param = new MethodParam(method, i);
                    if (param.isAnnotationPresent(Option.class) || param.isAnnotationPresent(Mixin.class)) {
                        optionCount++;
                    } else {
                        param.position = i - optionCount;
                    }
                    result |= initFromAnnotatedTypedMembers(new TypedMember(param, scope), receiver, factory);
                }
                return result;
            }
            private static void validateMixin(TypedMember member) {
                if (member.isMixin() && member.isArgSpec()) {
                    throw new DuplicateOptionAnnotationsException("A member cannot be both a @Mixin command and an @Option or @Parameters, but '" + member + "' is both.");
                }
                if (member.isMixin() && member.isUnmatched()) {
                    throw new DuplicateOptionAnnotationsException("A member cannot be both a @Mixin command and an @Unmatched but '" + member + "' is both.");
                }
            }
            private static void validateUnmatched(TypedMember member) {
                if (member.isUnmatched() && member.isArgSpec()) {
                    throw new DuplicateOptionAnnotationsException("A member cannot have both @Unmatched and @Option or @Parameters annotations, but '" + member + "' has both.");
                }
            }
            private static void validateArgSpecField(TypedMember member) {
                if (member.isOption() && member.isParameter()) {
                    throw new DuplicateOptionAnnotationsException("A member can be either @Option or @Parameters, but '" + member + "' is both.");
                }
                if (member.isMixin() && member.isArgSpec()) {
                    throw new DuplicateOptionAnnotationsException("A member cannot be both a @Mixin command and an @Option or @Parameters, but '" + member + "' is both.");
                }
                if (!(member.accessible instanceof Field)) { return; }
                Field field = (Field) member.accessible;
                if (Modifier.isFinal(field.getModifiers()) && (field.getType().isPrimitive() || String.class.isAssignableFrom(field.getType()))) {
                    throw new InitializationException("Constant (final) primitive and String fields like " + field + " cannot be used as " +
                            (member.isOption() ? "an @Option" : "a @Parameter") + ": compile-time constant inlining may hide new values written to it.");
                }
            }
            private static void validateCommandSpec(CommandSpec result, boolean hasCommandAnnotation, String commandClassName) {
                if (!hasCommandAnnotation && result.positionalParameters.isEmpty() && result.optionsByNameMap.isEmpty() && result.unmatchedArgs.isEmpty()) {
                    throw new InitializationException(commandClassName + " is not a command: it has no @Command, @Option, @Parameters or @Unmatched annotations");
                }
            }
            private static void validateInjectSpec(TypedMember member) {
                if (member.isInjectSpec() && (member.isOption() || member.isParameter())) {
                    throw new DuplicateOptionAnnotationsException("A member cannot have both @Spec and @Option or @Parameters annotations, but '" + member + "' has both.");
                }
                if (member.isInjectSpec() && member.isUnmatched()) {
                    throw new DuplicateOptionAnnotationsException("A member cannot have both @Spec and @Unmatched annotations, but '" + member + "' has both.");
                }
                if (member.isInjectSpec() && member.isMixin()) {
                    throw new DuplicateOptionAnnotationsException("A member cannot have both @Spec and @Mixin annotations, but '" + member + "' has both.");
                }
                if (member.getType() != CommandSpec.class) { throw new InitializationException("@picocli.CommandLine.Spec annotation is only supported on fields of type " + CommandSpec.class.getName()); }
            }
            private static CommandSpec buildMixinForField(TypedMember member, IFactory factory) {
                try {
                    Object userObject = member.getter().get();
                    if (userObject == null) {
                        userObject = factory.create(member.getType());
                        member.setter().set(userObject);
                    }
                    CommandSpec result = CommandSpec.forAnnotatedObject(userObject, factory);
                    return result.withToString(abbreviate("mixin from member " + member.toGenericString()));
                } catch (InitializationException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new InitializationException("Could not access or modify mixin member " + member + ": " + ex, ex);
                }
            }
            private static UnmatchedArgsBinding buildUnmatchedForField(final TypedMember member) {
                if (!(member.getType().equals(String[].class) ||
                        (List.class.isAssignableFrom(member.getType()) && member.getGenericType() instanceof ParameterizedType
                                && ((ParameterizedType) member.getGenericType()).getActualTypeArguments()[0].equals(String.class)))) {
                    throw new InitializationException("Invalid type for " + member + ": must be either String[] or List<String>");
                }
                if (member.getType().equals(String[].class)) {
                    return UnmatchedArgsBinding.forStringArrayConsumer(member.setter());
                } else {
                    return UnmatchedArgsBinding.forStringCollectionSupplier(new IGetter() {
                        @SuppressWarnings("unchecked") public <T> T get() throws Exception {
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

        /** Helper class to reflectively create OptionSpec and PositionalParamSpec objects from annotated elements.
         * Package protected for testing. CONSIDER THIS CLASS PRIVATE.  */
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
                    builder.completionCandidates(DefaultFactory.createCompletionCandidates(factory, option.completionCandidates()));
                }

                builder.arity(Range.optionArity(member));
                builder.required(option.required());
                builder.interactive(option.interactive());
                Class<?>[] elementTypes = inferTypes(member.getType(), option.type(), member.getGenericType());
                builder.auxiliaryTypes(elementTypes);
                builder.paramLabel(inferLabel(option.paramLabel(), member.name(), member.getType(), elementTypes));
                builder.hideParamSyntax(option.hideParamSyntax());
                builder.description(option.description());
                builder.descriptionKey(option.descriptionKey());
                builder.splitRegex(option.split());
                builder.hidden(option.hidden());
                builder.defaultValue(option.defaultValue());
                builder.converters(DefaultFactory.createConverter(factory, option.converter()));
                return builder.build();
            }
            static PositionalParamSpec extractPositionalParamSpec(TypedMember member, IFactory factory) {
                PositionalParamSpec.Builder builder = PositionalParamSpec.builder();
                initCommon(builder, member);
                Range arity = Range.parameterArity(member);
                builder.arity(arity);
                builder.index(Range.parameterIndex(member));
                builder.capacity(Range.parameterCapacity(member));
                builder.required(arity.min > 0);

                Parameters parameters = member.getAnnotation(Parameters.class);
                builder.interactive(parameters.interactive());
                Class<?>[] elementTypes = inferTypes(member.getType(), parameters.type(), member.getGenericType());
                builder.auxiliaryTypes(elementTypes);
                builder.paramLabel(inferLabel(parameters.paramLabel(), member.name(), member.getType(), elementTypes));
                builder.hideParamSyntax(parameters.hideParamSyntax());
                builder.description(parameters.description());
                builder.descriptionKey(parameters.descriptionKey());
                builder.splitRegex(parameters.split());
                builder.hidden(parameters.hidden());
                builder.defaultValue(parameters.defaultValue());
                builder.converters(DefaultFactory.createConverter(factory, parameters.converter()));
                builder.showDefaultValue(parameters.showDefaultValue());
                if (!NoCompletionCandidates.class.equals(parameters.completionCandidates())) {
                    builder.completionCandidates(DefaultFactory.createCompletionCandidates(factory, parameters.completionCandidates()));
                }
                return builder.build();
            }
            static PositionalParamSpec extractUnannotatedPositionalParamSpec(TypedMember member, IFactory factory) {
                PositionalParamSpec.Builder builder = PositionalParamSpec.builder();
                initCommon(builder, member);
                Range arity = Range.parameterArity(member);
                builder.arity(arity);
                builder.index(Range.parameterIndex(member));
                builder.capacity(Range.parameterCapacity(member));
                builder.required(arity.min > 0);

                builder.interactive(false);
                Class<?>[] elementTypes = inferTypes(member.getType(), new Class<?>[] {}, member.getGenericType());
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
                builder.withToString((member.accessible instanceof Field ? "field " : member.accessible instanceof Method ? "method " : member.accessible.getClass().getSimpleName() + " ") + abbreviate(member.toGenericString()));

                builder.getter(member.getter()).setter(member.setter());
                builder.hasInitialValue(member.hasInitialValue);
                try { builder.initialValue(member.getter().get()); } catch (Exception ex) { builder.initialValue(null); }
            }
            static String abbreviate(String text) {
                return text.replace("private ", "")
                        .replace("protected ", "")
                        .replace("public ", "")
                        .replace("java.lang.", "");
            }
            private static String inferLabel(String label, String fieldName, Class<?> fieldType, Class<?>[] types) {
                if (!StringUtils.isBlank(label)) { return label.trim(); }
                String name = fieldName;
                if (Map.class.isAssignableFrom(fieldType)) { // #195 better param labels for map fields
                    Class<?>[] paramTypes = types;
                    if (paramTypes.length < 2 || paramTypes[0] == null || paramTypes[1] == null) {
                        name = "String=String";
                    } else { name = paramTypes[0].getSimpleName() + "=" + paramTypes[1].getSimpleName(); }
                }
                return "<" + name + ">";
            }
            private static Class<?>[] inferTypes(Class<?> propertyType, Class<?>[] annotationTypes, Type genericType) {
                if (annotationTypes.length > 0) { return annotationTypes; }
                if (propertyType.isArray()) { return new Class<?>[] { propertyType.getComponentType() }; }
                if (CommandLine.isMultiValue(propertyType)) {
                    if (genericType instanceof ParameterizedType) {// e.g. Map<Long, ? extends Number>
                        ParameterizedType parameterizedType = (ParameterizedType) genericType;
                        Type[] paramTypes = parameterizedType.getActualTypeArguments(); // e.g. ? extends Number
                        Class<?>[] result = new Class<?>[paramTypes.length];
                        for (int i = 0; i < paramTypes.length; i++) {
                            if (paramTypes[i] instanceof Class) { result[i] = (Class<?>) paramTypes[i]; continue; } // e.g. Long
                            if (paramTypes[i] instanceof WildcardType) { // e.g. ? extends Number
                                WildcardType wildcardType = (WildcardType) paramTypes[i];
                                Type[] lower = wildcardType.getLowerBounds(); // e.g. []
                                if (lower.length > 0 && lower[0] instanceof Class) { result[i] = (Class<?>) lower[0]; continue; }
                                Type[] upper = wildcardType.getUpperBounds(); // e.g. Number
                                if (upper.length > 0 && upper[0] instanceof Class) { result[i] = (Class<?>) upper[0]; continue; }
                            }
                            Arrays.fill(result, String.class); return result; // too convoluted generic type, giving up
                        }
                        return result; // we inferred all types from ParameterizedType
                    }
                    return new Class<?>[] {String.class, String.class}; // field is multi-value but not ParameterizedType
                }
                return new Class<?>[] {propertyType}; // not a multi-value field
            }
        }
        private static class FieldBinding implements IGetter, ISetter {
            private final Object scope;
            private final Field field;
            FieldBinding(Object scope, Field field) { this.scope = scope; this.field = field; }
            public <T> T get() throws PicocliException {
                try {
                    @SuppressWarnings("unchecked") T result = (T) field.get(scope);
                    return result;
                } catch (Exception ex) {
                    throw new PicocliException("Could not get value for field " + field, ex);
                }
            }
            public <T> T set(T value) throws PicocliException {
                try {
                    @SuppressWarnings("unchecked") T result = (T) field.get(scope);
                    field.set(scope, value);
                    return result;
                } catch (Exception ex) {
                    throw new PicocliException("Could not set value for field " + field + " to " + value, ex);
                }
            }
        }
        static class MethodBinding implements IGetter, ISetter {
            private final Object scope;
            private final Method method;
            private Object currentValue;
            CommandLine commandLine;
            MethodBinding(Object scope, Method method) { this.scope = scope; this.method = method; }
            @SuppressWarnings("unchecked") public <T> T get() { return (T) currentValue; }
            public <T> T set(T value) throws PicocliException {
                try {
                    @SuppressWarnings("unchecked") T result = (T) currentValue;
                    method.invoke(scope, value);
                    currentValue = value;
                    return result;
                } catch (InvocationTargetException ex) {
                    if (ex.getCause() instanceof PicocliException) { throw (PicocliException) ex.getCause(); }
                    throw new ParameterException(commandLine, "Could not invoke " + method + " with " + value, ex.getCause());
                } catch (Exception ex) {
                    throw new ParameterException(commandLine, "Could not invoke " + method + " with " + value, ex);
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
                ProxyBinding(Method method) { this.method = Assert.notNull(method, "method"); }
                @SuppressWarnings("unchecked") public <T> T get() { return (T) map.get(method.getName()); }
                public <T> T set(T value) {
                    T result = get();
                    map.put(method.getName(), value);
                    return result;
                }
            }
        }
        private static class ObjectBinding implements IGetter, ISetter {
            private Object value;
            @SuppressWarnings("unchecked") public <T> T get() { return (T) value; }
            public <T> T set(T value) {
                @SuppressWarnings("unchecked") T result = value;
                this.value = value;
                return result;
            }
        }
    }

    /** Encapsulates the result of parsing an array of command line arguments.
     * @since 3.0 */
    public static class ParseResult {
        /** Creates and returns a new {@code ParseResult.Builder} for the specified command spec. */
        public static Builder builder(CommandSpec commandSpec) { return new Builder(commandSpec); }
        /** Builds immutable {@code ParseResult} instances. */
        public static class Builder {
            private final CommandSpec commandSpec;
            private final Set<OptionSpec> options = new LinkedHashSet<OptionSpec>();
            private final Set<PositionalParamSpec> positionals = new LinkedHashSet<PositionalParamSpec>();
            private final List<String> unmatched = new ArrayList<String>();
            private final List<String> originalArgList = new ArrayList<String>();
            private final List<List<PositionalParamSpec>> positionalParams = new ArrayList<List<PositionalParamSpec>>();
            private ParseResult subcommand;
            private boolean usageHelpRequested;
            private boolean versionHelpRequested;
            boolean isInitializingDefaultValues;
            private List<Exception> errors = new ArrayList<Exception>(1);
            private List<Object> nowProcessing;

            private Builder(CommandSpec spec) { commandSpec = Assert.notNull(spec, "commandSpec"); }
            /** Creates and returns a new {@code ParseResult} instance for this builder's configuration. */
            public ParseResult build() { return new ParseResult(this); }

            private void nowProcessing(ArgSpec spec, Object value) {
                if (nowProcessing != null && !isInitializingDefaultValues) {
                    nowProcessing.add(spec.isPositional() ? spec : value);
                }
            }

            /** Adds the specified {@code OptionSpec} or {@code PositionalParamSpec} to the list of options and parameters
             * that were matched on the command line.
             * @param arg the matched {@code OptionSpec} or {@code PositionalParamSpec}
             * @param position the command line position at which the  {@code PositionalParamSpec} was matched. Ignored for {@code OptionSpec}s.
             * @return this builder for method chaining */
            public Builder add(ArgSpec arg, int position) {
                if (arg.isOption()) {
                    addOption((OptionSpec) arg);
                } else {
                    addPositionalParam((PositionalParamSpec) arg, position);
                }
                return this;
            }
            /** Adds the specified {@code OptionSpec} to the list of options that were matched on the command line. */
            public Builder addOption(OptionSpec option) { if (!isInitializingDefaultValues) {options.add(option);} return this; }
            /** Adds the specified {@code PositionalParamSpec} to the list of parameters that were matched on the command line.
             * @param positionalParam the matched {@code PositionalParamSpec}
             * @param position the command line position at which the  {@code PositionalParamSpec} was matched.
             * @return this builder for method chaining */
            public Builder addPositionalParam(PositionalParamSpec positionalParam, int position) {
                if (isInitializingDefaultValues) { return this; }
                positionals.add(positionalParam);
                while (positionalParams.size() <= position) { positionalParams.add(new ArrayList<PositionalParamSpec>()); }
                positionalParams.get(position).add(positionalParam);
                return this;
            }
            /** Adds the specified command line argument to the list of unmatched command line arguments. */
            public Builder addUnmatched(String arg) { unmatched.add(arg); return this; }
            /** Adds all elements of the specified command line arguments stack to the list of unmatched command line arguments. */
            public Builder addUnmatched(Stack<String> args) { while (!args.isEmpty()) { addUnmatched(args.pop()); } return this; }
            /** Sets the specified {@code ParseResult} for a subcommand that was matched on the command line. */
            public Builder subcommand(ParseResult subcommand) { this.subcommand = subcommand; return this; }
            /** Sets the specified command line arguments that were parsed. */
            public Builder originalArgs(String[] originalArgs) { originalArgList.addAll(Arrays.asList(originalArgs)); return this;}

            void addStringValue        (ArgSpec argSpec, String value) { if (!isInitializingDefaultValues) { argSpec.stringValues.add(value);} }
            void addOriginalStringValue(ArgSpec argSpec, String value) { if (!isInitializingDefaultValues) { argSpec.originalStringValues.add(value); } }
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
        final List<Object> tentativeMatch;

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
            matchedPositionalParams = new ArrayList<List<PositionalParamSpec>>(builder.positionalParams);
            errors = new ArrayList<Exception>(builder.errors);
            usageHelpRequested = builder.usageHelpRequested;
            versionHelpRequested = builder.versionHelpRequested;
            tentativeMatch = builder.nowProcessing;
        }
        /** Returns the option with the specified short name, or {@code null} if no option with that name was matched
         * on the command line.
         * <p>Use {@link OptionSpec#getValue() getValue} on the returned {@code OptionSpec} to get the matched value (or values),
         * converted to the type of the option. Alternatively, use {@link OptionSpec#stringValues() stringValues}
         * to get the matched String values after they were {@linkplain OptionSpec#splitRegex() split} into parts, or
         * {@link OptionSpec#originalStringValues() originalStringValues} to get the original String values that were
         * matched on the command line, before any processing.
         * </p><p>To get the {@linkplain OptionSpec#defaultValue() default value} of an option that was
         * {@linkplain #hasMatchedOption(char) <em>not</em> matched} on the command line, use
         * {@code parseResult.commandSpec().findOption(shortName).getValue()}. </p>
         * @see CommandSpec#findOption(char)  */
        public OptionSpec matchedOption(char shortName) { return CommandSpec.findOption(shortName, matchedOptions); }

        /** Returns the option with the specified name, or {@code null} if no option with that name was matched on the command line.
         * <p>Use {@link OptionSpec#getValue() getValue} on the returned {@code OptionSpec} to get the matched value (or values),
         * converted to the type of the option. Alternatively, use {@link OptionSpec#stringValues() stringValues}
         * to get the matched String values after they were {@linkplain OptionSpec#splitRegex() split} into parts, or
         * {@link OptionSpec#originalStringValues() originalStringValues} to get the original String values that were
         * matched on the command line, before any processing.
         * </p><p>To get the {@linkplain OptionSpec#defaultValue() default value} of an option that was
         * {@linkplain #hasMatchedOption(String) <em>not</em> matched} on the command line, use
         * {@code parseResult.commandSpec().findOption(String).getValue()}. </p>
         * @see CommandSpec#findOption(String)
         * @param name used to search the matched options. May be an alias of the option name that was actually specified on the command line.
         *      The specified name may include option name prefix characters or not. */
        public OptionSpec matchedOption(String name) { return CommandSpec.findOption(name, matchedOptions); }

        /** Returns the first {@code PositionalParamSpec} that matched an argument at the specified position, or {@code null} if no positional parameters were matched at that position. */
        public PositionalParamSpec matchedPositional(int position) {
            if (matchedPositionalParams.size() <= position || matchedPositionalParams.get(position).isEmpty()) { return null; }
            return matchedPositionalParams.get(position).get(0);
        }

        /** Returns all {@code PositionalParamSpec} objects that matched an argument at the specified position, or an empty list if no positional parameters were matched at that position. */
        public List<PositionalParamSpec> matchedPositionals(int position) {
            if (matchedPositionalParams.size() <= position) { return Collections.emptyList(); }
            return matchedPositionalParams.get(position) == null ? Collections.<PositionalParamSpec>emptyList() : matchedPositionalParams.get(position);
        }
        /** Returns the {@code CommandSpec} for the matched command. */
        public CommandSpec commandSpec()                    { return commandSpec; }

        /** Returns whether an option whose aliases include the specified short name was matched on the command line.
         * @param shortName used to search the matched options. May be an alias of the option name that was actually specified on the command line. */
        public boolean hasMatchedOption(char shortName)     { return matchedOption(shortName) != null; }
        /** Returns whether an option whose aliases include the specified name was matched on the command line.
         * @param name used to search the matched options. May be an alias of the option name that was actually specified on the command line.
         *      The specified name may include option name prefix characters or not. */
        public boolean hasMatchedOption(String name)        { return matchedOption(name) != null; }
        /** Returns whether the specified option was matched on the command line. */
        public boolean hasMatchedOption(OptionSpec option)  { return matchedOptions.contains(option); }

        /** Returns whether a positional parameter was matched at the specified position. */
        public boolean hasMatchedPositional(int position)   { return matchedPositional(position) != null; }
        /** Returns whether the specified positional parameter was matched on the command line. */
        public boolean hasMatchedPositional(PositionalParamSpec positional) { return matchedUniquePositionals.contains(positional); }

        /** Returns a list of matched options, in the order they were found on the command line. */
        public List<OptionSpec> matchedOptions()            { return Collections.unmodifiableList(matchedOptions); }

        /** Returns a list of matched positional parameters. */
        public List<PositionalParamSpec> matchedPositionals() { return Collections.unmodifiableList(matchedUniquePositionals); }

        /** Returns a list of command line arguments that did not match any options or positional parameters. */
        public List<String> unmatched()                     { return Collections.unmodifiableList(unmatched); }

        /** Returns the command line arguments that were parsed. */
        public List<String> originalArgs()                  { return Collections.unmodifiableList(originalArgs); }

        /** If {@link ParserSpec#collectErrors} is {@code true}, returns the list of exceptions that were encountered during parsing, otherwise, returns an empty list.
         * @since 3.2 */
        public List<Exception> errors()                     { return Collections.unmodifiableList(errors); }

        /** Returns the command line argument value of the option with the specified name, converted to the {@linkplain OptionSpec#type() type} of the option, or the specified default value if no option with the specified name was matched. */
        public <T> T matchedOptionValue(char shortName, T defaultValue)    { return matchedOptionValue(matchedOption(shortName), defaultValue); }
        /** Returns the command line argument value of the option with the specified name, converted to the {@linkplain OptionSpec#type() type} of the option, or the specified default value if no option with the specified name was matched. */
        public <T> T matchedOptionValue(String name, T defaultValue)       { return matchedOptionValue(matchedOption(name), defaultValue); }
        /** Returns the command line argument value of the specified option, converted to the {@linkplain OptionSpec#type() type} of the option, or the specified default value if the specified option is {@code null}. */
        @SuppressWarnings("unchecked")
        private <T> T matchedOptionValue(OptionSpec option, T defaultValue) { return option == null ? defaultValue : (T) option.getValue(); }

        /** Returns the command line argument value of the positional parameter at the specified position, converted to the {@linkplain PositionalParamSpec#type() type} of the positional parameter, or the specified default value if no positional parameter was matched at that position. */
        public <T> T matchedPositionalValue(int position, T defaultValue)  { return matchedPositionalValue(matchedPositional(position), defaultValue); }
        /** Returns the command line argument value of the specified positional parameter, converted to the {@linkplain PositionalParamSpec#type() type} of the positional parameter, or the specified default value if the specified positional parameter is {@code null}. */
        @SuppressWarnings("unchecked")
        private <T> T matchedPositionalValue(PositionalParamSpec positional, T defaultValue) { return positional == null ? defaultValue : (T) positional.getValue(); }

        /** Returns {@code true} if a subcommand was matched on the command line, {@code false} otherwise. */
        public boolean hasSubcommand()          { return subcommand != null; }

        /** Returns the {@code ParseResult} for the subcommand of this command that was matched on the command line, or {@code null} if no subcommand was matched. */
        public ParseResult subcommand()         { return subcommand; }

        /** Returns {@code true} if one of the options that was matched on the command line is a {@link OptionSpec#usageHelp() usageHelp} option. */
        public boolean isUsageHelpRequested()   { return usageHelpRequested; }

        /** Returns {@code true} if one of the options that was matched on the command line is a {@link OptionSpec#versionHelp() versionHelp} option. */
        public boolean isVersionHelpRequested() { return versionHelpRequested; }

        /** Returns this {@code ParseResult} as a list of {@code CommandLine} objects, one for each matched command/subcommand.
         * For backwards compatibility with pre-3.0 methods. */
        public List<CommandLine> asCommandLineList() {
            List<CommandLine> result = new ArrayList<CommandLine>();
            ParseResult pr = this;
            while (pr != null) { result.add(pr.commandSpec().commandLine()); pr = pr.hasSubcommand() ? pr.subcommand() : null; }
            return result;
        }
    }
    private enum LookBehind { SEPARATE, ATTACHED, ATTACHED_WITH_SEPARATOR;
        public boolean isAttached() { return this != LookBehind.SEPARATE; }
    }
    /**
     * Helper class responsible for processing command line arguments.
     */
    private class Interpreter {
        private final Map<Class<?>, ITypeConverter<?>> converterRegistry = new HashMap<Class<?>, ITypeConverter<?>>();
        private boolean isHelpRequested;
        private int position;
        private boolean endOfOptions;
        private ParseResult.Builder parseResult;

        Interpreter() { registerBuiltInConverters(); }

        private void registerBuiltInConverters() {
            converterRegistry.put(Object.class,        new BuiltIn.StringConverter());
            converterRegistry.put(String.class,        new BuiltIn.StringConverter());
            converterRegistry.put(StringBuilder.class, new BuiltIn.StringBuilderConverter());
            converterRegistry.put(CharSequence.class,  new BuiltIn.CharSequenceConverter());
            converterRegistry.put(Byte.class,          new BuiltIn.ByteConverter());
            converterRegistry.put(Byte.TYPE,           new BuiltIn.ByteConverter());
            converterRegistry.put(Boolean.class,       new BuiltIn.BooleanConverter());
            converterRegistry.put(Boolean.TYPE,        new BuiltIn.BooleanConverter());
            converterRegistry.put(Character.class,     new BuiltIn.CharacterConverter());
            converterRegistry.put(Character.TYPE,      new BuiltIn.CharacterConverter());
            converterRegistry.put(Short.class,         new BuiltIn.ShortConverter());
            converterRegistry.put(Short.TYPE,          new BuiltIn.ShortConverter());
            converterRegistry.put(Integer.class,       new BuiltIn.IntegerConverter());
            converterRegistry.put(Integer.TYPE,        new BuiltIn.IntegerConverter());
            converterRegistry.put(Long.class,          new BuiltIn.LongConverter());
            converterRegistry.put(Long.TYPE,           new BuiltIn.LongConverter());
            converterRegistry.put(Float.class,         new BuiltIn.FloatConverter());
            converterRegistry.put(Float.TYPE,          new BuiltIn.FloatConverter());
            converterRegistry.put(Double.class,        new BuiltIn.DoubleConverter());
            converterRegistry.put(Double.TYPE,         new BuiltIn.DoubleConverter());
            converterRegistry.put(File.class,          new BuiltIn.FileConverter());
            converterRegistry.put(URI.class,           new BuiltIn.URIConverter());
            converterRegistry.put(URL.class,           new BuiltIn.URLConverter());
            converterRegistry.put(Date.class,          new BuiltIn.ISO8601DateConverter());
            converterRegistry.put(BigDecimal.class,    new BuiltIn.BigDecimalConverter());
            converterRegistry.put(BigInteger.class,    new BuiltIn.BigIntegerConverter());
            converterRegistry.put(Charset.class,       new BuiltIn.CharsetConverter());
            converterRegistry.put(InetAddress.class,   new BuiltIn.InetAddressConverter());
            converterRegistry.put(Pattern.class,       new BuiltIn.PatternConverter());
            converterRegistry.put(UUID.class,          new BuiltIn.UUIDConverter());
            converterRegistry.put(Currency.class,      new BuiltIn.CurrencyConverter());
            converterRegistry.put(TimeZone.class,      new BuiltIn.TimeZoneConverter());
            converterRegistry.put(ByteOrder.class,     new BuiltIn.ByteOrderConverter());
            converterRegistry.put(Class.class,         new BuiltIn.ClassConverter());
            converterRegistry.put(NetworkInterface.class, new BuiltIn.NetworkInterfaceConverter());

            BuiltIn.ISO8601TimeConverter.registerIfAvailable(converterRegistry, tracer);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.sql.Connection", "java.sql.DriverManager","getConnection", String.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.sql.Driver", "java.sql.DriverManager","getDriver", String.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.sql.Timestamp", "java.sql.Timestamp","valueOf", String.class);

            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.Duration", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.Instant", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.LocalDate", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.LocalDateTime", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.LocalTime", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.MonthDay", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.OffsetDateTime", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.OffsetTime", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.Period", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.Year", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.YearMonth", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.ZonedDateTime", "parse", CharSequence.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.ZoneId", "of", String.class);
            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.ZoneOffset", "of", String.class);

            BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.nio.file.Path", "java.nio.file.Paths", "get", String.class, String[].class);
        }
        private ParserSpec config() { return commandSpec.parser(); }
        /**
         * Entry point into parsing command line arguments.
         * @param args the command line arguments
         * @return a list with all commands and subcommands initialized by this method
         * @throws ParameterException if the specified command line arguments are invalid
         */
        List<CommandLine> parse(String... args) {
            Assert.notNull(args, "argument array");
            if (tracer.isInfo()) {tracer.info("Parsing %d command line args %s%n", args.length, Arrays.toString(args));}
            if (tracer.isDebug()){tracer.debug("Parser configuration: %s%n", config());}
            List<String> expanded = new ArrayList<String>();
            for (String arg : args) { addOrExpand(arg, expanded, new LinkedHashSet<String>()); }
            Stack<String> arguments = new Stack<String>();
            arguments.addAll(reverseList(expanded));
            List<CommandLine> result = new ArrayList<CommandLine>();
            parse(result, arguments, args, new ArrayList<Object>());
            return result;
        }

        private void addOrExpand(String arg, List<String> arguments, Set<String> visited) {
            if (config().expandAtFiles() && !arg.equals("@") && arg.startsWith("@")) {
                arg = arg.substring(1);
                if (arg.startsWith("@")) {
                    if (tracer.isInfo()) { tracer.info("Not expanding @-escaped argument %s (trimmed leading '@' char)%n", arg); }
                } else {
                    if (tracer.isInfo()) { tracer.info("Expanding argument file @%s%n", arg); }
                    expandArgumentFile(arg, arguments, visited);
                    return;
                }
            }
            arguments.add(arg);
        }
        private void expandArgumentFile(String fileName, List<String> arguments, Set<String> visited) {
            File file = new File(fileName);
            if (!file.canRead()) {
                if (tracer.isInfo()) {tracer.info("File %s does not exist or cannot be read; treating argument literally%n", fileName);}
                arguments.add("@" + fileName);
            } else if (visited.contains(file.getAbsolutePath())) {
                if (tracer.isInfo()) {tracer.info("Already visited file %s; ignoring...%n", file.getAbsolutePath());}
            } else {
                expandValidArgumentFile(fileName, file, arguments, visited);
            }
        }
        private void expandValidArgumentFile(String fileName, File file, List<String> arguments, Set<String> visited) {
            visited.add(file.getAbsolutePath());
            List<String> result = new ArrayList<String>();
            LineNumberReader reader = null;
            try {
                reader = new LineNumberReader(new FileReader(file));
                StreamTokenizer tok = new StreamTokenizer(reader);
                tok.resetSyntax();
                tok.wordChars(' ', 255);
                tok.whitespaceChars(0, ' ');
                tok.quoteChar('"');
                tok.quoteChar('\'');
                if (commandSpec.parser().atFileCommentChar() != null) {
                    tok.commentChar(commandSpec.parser().atFileCommentChar());
                }
                while (tok.nextToken() != StreamTokenizer.TT_EOF) {
                    addOrExpand(tok.sval, result, visited);
                }
            } catch (Exception ex) {
                throw new InitializationException("Could not read argument file @" + fileName, ex);
            } finally {
                if (reader != null) { try {reader.close();} catch (Exception ignored) {} }
            }
            if (tracer.isInfo()) {tracer.info("Expanded file @%s to arguments %s%n", fileName, result);}
            arguments.addAll(result);
        }

        private void clear() {
            position = 0;
            endOfOptions = false;
            isHelpRequested = false;
            parseResult = ParseResult.builder(getCommandSpec());
            for (OptionSpec option : getCommandSpec().options())                           { clear(option); }
            for (PositionalParamSpec positional : getCommandSpec().positionalParameters()) { clear(positional); }
        }
        private void clear(ArgSpec argSpec) {
            argSpec.resetStringValues();
            argSpec.resetOriginalStringValues();
            argSpec.typedValues.clear();
            argSpec.typedValueAtPosition.clear();
            if (argSpec.hasInitialValue()) {
                try {
                    argSpec.setter().set(argSpec.initialValue());
                    tracer.debug("Set initial value for %s of type %s to %s.%n", argSpec, argSpec.type(), String.valueOf(argSpec.initialValue()));
                } catch (Exception ex) {
                    tracer.warn("Could not set initial value for %s of type %s to %s: %s%n", argSpec, argSpec.type(), String.valueOf(argSpec.initialValue()), ex);
                }
            } else {
                tracer.debug("Initial value not available for %s%n", argSpec);
            }
        }
        private void maybeThrow(PicocliException ex) throws PicocliException {
            if (commandSpec.parser().collectErrors) {
                parseResult.addError(ex);
            } else {
                throw ex;
            }
        }

        private void parse(List<CommandLine> parsedCommands, Stack<String> argumentStack, String[] originalArgs, List<Object> nowProcessing) {
            clear(); // first reset any state in case this CommandLine instance is being reused
            if (tracer.isDebug()) {tracer.debug("Initializing %s: %d options, %d positional parameters, %d required, %d subcommands.%n",
                    commandSpec.toString(), new HashSet<ArgSpec>(commandSpec.optionsMap().values()).size(),
                    commandSpec.positionalParameters().size(), commandSpec.requiredArgs().size(), commandSpec
                            .subcommands().size());}
            parsedCommands.add(CommandLine.this);
            List<ArgSpec> required = new ArrayList<ArgSpec>(commandSpec.requiredArgs());
            Set<ArgSpec> initialized = new HashSet<ArgSpec>();
            Collections.sort(required, new PositionalParametersSorter());
            boolean continueOnError = commandSpec.parser().collectErrors;
            do {
                int stackSize = argumentStack.size();
                try {
                    applyDefaultValues(required);
                    processArguments(parsedCommands, argumentStack, required, initialized, originalArgs, nowProcessing);
                } catch (ParameterException ex) {
                    maybeThrow(ex);
                } catch (Exception ex) {
                    int offendingArgIndex = originalArgs.length - argumentStack.size() - 1;
                    String arg = offendingArgIndex >= 0 && offendingArgIndex < originalArgs.length ? originalArgs[offendingArgIndex] : "?";
                    maybeThrow(ParameterException.create(CommandLine.this, ex, arg, offendingArgIndex, originalArgs));
                }
                if (continueOnError && stackSize == argumentStack.size() && stackSize > 0) {
                    parseResult.unmatched.add(argumentStack.pop());
                }
            } while (!argumentStack.isEmpty() && continueOnError);
            if (!isAnyHelpRequested() && !required.isEmpty()) {
                for (ArgSpec missing : required) {
                    if (missing.isOption()) {
                        maybeThrow(MissingParameterException.create(CommandLine.this, required, config().separator()));
                    } else {
                        assertNoMissingParameters(missing, missing.arity(), argumentStack);
                    }
                }
            }
            if (!parseResult.unmatched.isEmpty()) {
                String[] unmatched = parseResult.unmatched.toArray(new String[0]);
                for (UnmatchedArgsBinding unmatchedArgsBinding : getCommandSpec().unmatchedArgsBindings()) {
                    unmatchedArgsBinding.addAll(unmatched.clone());
                }
                if (!isUnmatchedArgumentsAllowed()) { maybeThrow(new UnmatchedArgumentException(CommandLine.this, Collections.unmodifiableList(parseResult.unmatched))); }
                if (tracer.isInfo()) { tracer.info("Unmatched arguments: %s%n", parseResult.unmatched); }
            }
        }

        private void applyDefaultValues(List<ArgSpec> required) throws Exception {
            parseResult.isInitializingDefaultValues = true;
            for (OptionSpec option              : commandSpec.options())              { applyDefault(commandSpec.defaultValueProvider(), option,     required); }
            for (PositionalParamSpec positional : commandSpec.positionalParameters()) { applyDefault(commandSpec.defaultValueProvider(), positional, required); }
            parseResult.isInitializingDefaultValues = false;
        }

        private void applyDefault(IDefaultValueProvider defaultValueProvider,
            ArgSpec arg, List<ArgSpec> required) throws Exception {

            // Default value provider return value is only used if provider exists and if value
            // is not null otherwise the original default or initial value are used
            String fromProvider = defaultValueProvider == null ? null : defaultValueProvider.defaultValue(arg);
            String defaultValue = fromProvider == null ? arg.defaultValue() : fromProvider;

            if (defaultValue == null) { return; }
            if (tracer.isDebug()) {tracer.debug("Applying defaultValue (%s) to %s%n", defaultValue, arg);}
            Range arity = arg.arity().min(Math.max(1, arg.arity().min));

            applyOption(arg, LookBehind.SEPARATE, arity, stack(defaultValue), new HashSet<ArgSpec>(), arg.toString);
            required.remove(arg);
        }

        private Stack<String> stack(String value) {Stack<String> result = new Stack<String>(); result.push(value); return result;}

        private void processArguments(List<CommandLine> parsedCommands,
                                      Stack<String> args,
                                      Collection<ArgSpec> required,
                                      Set<ArgSpec> initialized,
                                      String[] originalArgs,
                                      List<Object> nowProcessing) throws Exception {
            // arg must be one of:
            // 1. the "--" double dash separating options from positional arguments
            // 1. a stand-alone flag, like "-v" or "--verbose": no value required, must map to boolean or Boolean field
            // 2. a short option followed by an argument, like "-f file" or "-ffile": may map to any type of field
            // 3. a long option followed by an argument, like "-file out.txt" or "-file=out.txt"
            // 3. one or more remaining arguments without any associated options. Must be the last in the list.
            // 4. a combination of stand-alone options, like "-vxr". Equivalent to "-v -x -r", "-v true -x true -r true"
            // 5. a combination of stand-alone options and one option with an argument, like "-vxrffile"

            parseResult.originalArgs(originalArgs);
            parseResult.nowProcessing = nowProcessing;
            String separator = config().separator();
            while (!args.isEmpty()) {
                if (endOfOptions) {
                    processRemainderAsPositionalParameters(required, initialized, args);
                    return;
                }
                String arg = args.pop();
                if (tracer.isDebug()) {tracer.debug("Processing argument '%s'. Remainder=%s%n", arg, reverse(copy(args)));}

                // Double-dash separates options from positional arguments.
                // If found, then interpret the remaining args as positional parameters.
                if (commandSpec.parser.endOfOptionsDelimiter().equals(arg)) {
                    tracer.info("Found end-of-options delimiter '--'. Treating remainder as positional parameters.%n");
                    endOfOptions = true;
                    processRemainderAsPositionalParameters(required, initialized, args);
                    return; // we are done
                }

                // if we find another command, we are done with the current command
                if (commandSpec.subcommands().containsKey(arg)) {
                    CommandLine subcommand = commandSpec.subcommands().get(arg);
                    nowProcessing.add(subcommand.commandSpec);
                    updateHelpRequested(subcommand.commandSpec);
                    if (!isAnyHelpRequested() && !required.isEmpty()) { // ensure current command portion is valid
                        throw MissingParameterException.create(CommandLine.this, required, separator);
                    }
                    if (tracer.isDebug()) {tracer.debug("Found subcommand '%s' (%s)%n", arg, subcommand.commandSpec.toString());}
                    subcommand.interpreter.parse(parsedCommands, args, originalArgs, nowProcessing);
                    parseResult.subcommand(subcommand.interpreter.parseResult.build());
                    return; // remainder done by the command
                }

                // First try to interpret the argument as a single option (as opposed to a compact group of options).
                // A single option may be without option parameters, like "-v" or "--verbose" (a boolean value),
                // or an option may have one or more option parameters.
                // A parameter may be attached to the option.
                boolean paramAttachedToOption = false;
                int separatorIndex = arg.indexOf(separator);
                if (separatorIndex > 0) {
                    String key = arg.substring(0, separatorIndex);
                    // be greedy. Consume the whole arg as an option if possible.
                    if (commandSpec.optionsMap().containsKey(key) && !commandSpec.optionsMap().containsKey(arg)) {
                        paramAttachedToOption = true;
                        String optionParam = arg.substring(separatorIndex + separator.length());
                        args.push(optionParam);
                        arg = key;
                        if (tracer.isDebug()) {tracer.debug("Separated '%s' option from '%s' option parameter%n", key, optionParam);}
                    } else {
                        if (tracer.isDebug()) {tracer.debug("'%s' contains separator '%s' but '%s' is not a known option%n", arg, separator, key);}
                    }
                } else {
                    if (tracer.isDebug()) {tracer.debug("'%s' cannot be separated into <option>%s<option-parameter>%n", arg, separator);}
                }
                if (isStandaloneOption(arg)) {
                    processStandaloneOption(required, initialized, arg, args, paramAttachedToOption);
                }
                // Compact (single-letter) options can be grouped with other options or with an argument.
                // only single-letter options can be combined with other options or with an argument
                else if (config().posixClusteredShortOptionsAllowed() && arg.length() > 2 && arg.startsWith("-")) {
                    if (tracer.isDebug()) {tracer.debug("Trying to process '%s' as clustered short options%n", arg, args);}
                    processClusteredShortOptions(required, initialized, arg, args);
                }
                // The argument could not be interpreted as an option: process it as a positional argument
                else {
                    args.push(arg);
                    if (tracer.isDebug()) {tracer.debug("Could not find option '%s', deciding whether to treat as unmatched option or positional parameter...%n", arg);}
                    if (commandSpec.resemblesOption(arg, tracer)) { handleUnmatchedArgument(args); continue; } // #149
                    if (tracer.isDebug()) {tracer.debug("No option named '%s' found. Processing remainder as positional parameters%n", arg);}
                    processPositionalParameter(required, initialized, args);
                }
            }
        }

        private boolean isStandaloneOption(String arg) {
            return commandSpec.optionsMap().containsKey(arg);
        }
        private void handleUnmatchedArgument(Stack<String> args) throws Exception {
            if (!args.isEmpty()) { handleUnmatchedArgument(args.pop()); }
            if (config().stopAtUnmatched()) {
                // addAll would give args in reverse order
                while (!args.isEmpty()) { handleUnmatchedArgument(args.pop()); }
            }
        }
        private void handleUnmatchedArgument(String arg) {
            parseResult.unmatched.add(arg);
        }

        private void processRemainderAsPositionalParameters(Collection<ArgSpec> required, Set<ArgSpec> initialized, Stack<String> args) throws Exception {
            while (!args.empty()) {
                processPositionalParameter(required, initialized, args);
            }
        }
        private void processPositionalParameter(Collection<ArgSpec> required, Set<ArgSpec> initialized, Stack<String> args) throws Exception {
            if (tracer.isDebug()) {tracer.debug("Processing next arg as a positional parameter at index=%d. Remainder=%s%n", position, reverse(copy(args)));}
            if (config().stopAtPositional()) {
                if (!endOfOptions && tracer.isDebug()) {tracer.debug("Parser was configured with stopAtPositional=true, treating remaining arguments as positional parameters.%n");}
                endOfOptions = true;
            }
            int argsConsumed = 0;
            int interactiveConsumed = 0;
            int originalNowProcessingSize = parseResult.nowProcessing.size();
            for (PositionalParamSpec positionalParam : commandSpec.positionalParameters()) {
                Range indexRange = positionalParam.index();
                if (!indexRange.contains(position) || positionalParam.typedValueAtPosition.get(position) != null) {
                    continue;
                }
                Stack<String> argsCopy = copy(args);
                Range arity = positionalParam.arity();
                if (tracer.isDebug()) {tracer.debug("Position %d is in index range %s. Trying to assign args to %s, arity=%s%n", position, indexRange, positionalParam, arity);}
                if (!assertNoMissingParameters(positionalParam, arity, argsCopy)) { break; } // #389 collectErrors parsing
                int originalSize = argsCopy.size();
                int actuallyConsumed = applyOption(positionalParam, LookBehind.SEPARATE, arity, argsCopy, initialized, "args[" + indexRange + "] at position " + position);
                int count = originalSize - argsCopy.size();
                if (count > 0 || actuallyConsumed > 0) {
                    required.remove(positionalParam);
                    if (positionalParam.interactive()) { interactiveConsumed++; }
                }
                argsConsumed = Math.max(argsConsumed, count);
                while (parseResult.nowProcessing.size() > originalNowProcessingSize + count) {
                    parseResult.nowProcessing.remove(parseResult.nowProcessing.size() - 1);
                }
            }
            // remove processed args from the stack
            for (int i = 0; i < argsConsumed; i++) { args.pop(); }
            position += argsConsumed + interactiveConsumed;
            if (tracer.isDebug()) {tracer.debug("Consumed %d arguments and %d interactive values, moving position to index %d.%n", argsConsumed, interactiveConsumed, position);}
            if (argsConsumed == 0 && interactiveConsumed == 0 && !args.isEmpty()) {
                handleUnmatchedArgument(args);
            }
        }

        private void processStandaloneOption(Collection<ArgSpec> required,
                                             Set<ArgSpec> initialized,
                                             String arg,
                                             Stack<String> args,
                                             boolean paramAttachedToKey) throws Exception {
            ArgSpec argSpec = commandSpec.optionsMap().get(arg);
            required.remove(argSpec);
            Range arity = argSpec.arity();
            if (paramAttachedToKey) {
                arity = arity.min(Math.max(1, arity.min)); // if key=value, minimum arity is at least 1
            }
            LookBehind lookBehind = paramAttachedToKey ? LookBehind.ATTACHED_WITH_SEPARATOR : LookBehind.SEPARATE;
            if (tracer.isDebug()) {tracer.debug("Found option named '%s': %s, arity=%s%n", arg, argSpec, arity);}
            parseResult.nowProcessing.add(argSpec);
            applyOption(argSpec, lookBehind, arity, args, initialized, "option " + arg);
        }

        private void processClusteredShortOptions(Collection<ArgSpec> required,
                                                  Set<ArgSpec> initialized,
                                                  String arg,
                                                  Stack<String> args) throws Exception {
            String prefix = arg.substring(0, 1);
            String cluster = arg.substring(1);
            boolean paramAttachedToOption = true;
            boolean first = true;
            do {
                if (cluster.length() > 0 && commandSpec.posixOptionsMap().containsKey(cluster.charAt(0))) {
                    ArgSpec argSpec = commandSpec.posixOptionsMap().get(cluster.charAt(0));
                    Range arity = argSpec.arity();
                    String argDescription = "option " + prefix + cluster.charAt(0);
                    if (tracer.isDebug()) {tracer.debug("Found option '%s%s' in %s: %s, arity=%s%n", prefix, cluster.charAt(0), arg,
                            argSpec, arity);}
                    required.remove(argSpec);
                    cluster = cluster.length() > 0 ? cluster.substring(1) : "";
                    paramAttachedToOption = cluster.length() > 0;
                    LookBehind lookBehind = paramAttachedToOption ? LookBehind.ATTACHED : LookBehind.SEPARATE;
                    if (cluster.startsWith(config().separator())) {// attached with separator, like -f=FILE or -v=true
                        lookBehind = LookBehind.ATTACHED_WITH_SEPARATOR;
                        cluster = cluster.substring(config().separator().length());
                        arity = arity.min(Math.max(1, arity.min)); // if key=value, minimum arity is at least 1
                    }
                    if (arity.min > 0 && !StringUtils.isBlank(cluster)) {
                        if (tracer.isDebug()) {tracer.debug("Trying to process '%s' as option parameter%n", cluster);}
                    }
                    // arity may be >= 1, or
                    // arity <= 0 && !cluster.startsWith(separator)
                    // e.g., boolean @Option("-v", arity=0, varargs=true); arg "-rvTRUE", remainder cluster="TRUE"
                    if (!StringUtils.isBlank(cluster)) {
                        args.push(cluster); // interpret remainder as option parameter (CAUTION: may be empty string!)
                    }
                    if (first) {
                        parseResult.nowProcessing.add(argSpec);
                        first = false;
                    } else {
                        parseResult.nowProcessing.set(parseResult.nowProcessing.size() - 1, argSpec); // replace
                    }
                    int argCount = args.size();
                    int consumed = applyOption(argSpec, lookBehind, arity, args, initialized, argDescription);
                    // if cluster was consumed as a parameter or if this field was the last in the cluster we're done; otherwise continue do-while loop
                    if (StringUtils.isBlank(cluster) || args.isEmpty() || args.size() < argCount) {
                        return;
                    }
                    cluster = args.pop();
                } else { // cluster is empty || cluster.charAt(0) is not a short option key
                    if (cluster.length() == 0) { // we finished parsing a group of short options like -rxv
                        return; // return normally and parse the next arg
                    }
                    // We get here when the remainder of the cluster group is neither an option,
                    // nor a parameter that the last option could consume.
                    if (arg.endsWith(cluster)) {
                        args.push(paramAttachedToOption ? prefix + cluster : cluster);
                        if (args.peek().equals(arg)) { // #149 be consistent between unmatched short and long options
                            if (tracer.isDebug()) {tracer.debug("Could not match any short options in %s, deciding whether to treat as unmatched option or positional parameter...%n", arg);}
                            if (commandSpec.resemblesOption(arg, tracer)) { handleUnmatchedArgument(args); return; } // #149
                            processPositionalParameter(required, initialized, args);
                            return;
                        }
                        // remainder was part of a clustered group that could not be completely parsed
                        if (tracer.isDebug()) {tracer.debug("No option found for %s in %s%n", cluster, arg);}
                        handleUnmatchedArgument(args);
                    } else {
                        args.push(cluster);
                        if (tracer.isDebug()) {tracer.debug("%s is not an option parameter for %s%n", cluster, arg);}
                        processPositionalParameter(required, initialized, args);
                    }
                    return;
                }
            } while (true);
        }

        private int applyOption(ArgSpec argSpec,
                                LookBehind lookBehind,
                                Range arity,
                                Stack<String> args,
                                Set<ArgSpec> initialized,
                                String argDescription) throws Exception {
            updateHelpRequested(argSpec);
            boolean consumeOnlyOne = commandSpec.parser().aritySatisfiedByAttachedOptionParam() && lookBehind.isAttached();
            Stack<String> workingStack = args;
            if (consumeOnlyOne) {
                workingStack = args.isEmpty() ? args : stack(args.pop());
            } else {
                if (!assertNoMissingParameters(argSpec, arity, args)) { return 0; } // #389 collectErrors parsing
            }

            if (argSpec.interactive()) {
                String name = argSpec.isOption() ? ((OptionSpec) argSpec).longestName() : "position " + position;
                String prompt = String.format("Enter value for %s (%s): ", name, Utils.safeGet(argSpec.renderedDescription(), 0));
                if (tracer.isDebug()) {tracer.debug("Reading value for %s from console...%n", name);}
                char[] value = readPassword(prompt, false);
                if (tracer.isDebug()) {tracer.debug("User entered '%s' for %s.%n", value, name);}
                workingStack.push(new String(value));
            }

            int result;
            if (argSpec.type().isArray()) {
                result = applyValuesToArrayField(argSpec, lookBehind, arity, workingStack, initialized, argDescription);
            } else if (Collection.class.isAssignableFrom(argSpec.type())) {
                result = applyValuesToCollectionField(argSpec, lookBehind, arity, workingStack, initialized, argDescription);
            } else if (Map.class.isAssignableFrom(argSpec.type())) {
                result = applyValuesToMapField(argSpec, lookBehind, arity, workingStack, initialized, argDescription);
            } else {
                result = applyValueToSingleValuedField(argSpec, lookBehind, arity, workingStack, initialized, argDescription);
            }
            if (workingStack != args && !workingStack.isEmpty()) {
                args.push(workingStack.pop());
                if (!workingStack.isEmpty()) {throw new IllegalStateException("Working stack should be empty but was " + new ArrayList<String>(workingStack));}
            }
            return result;
        }

        private int applyValueToSingleValuedField(ArgSpec argSpec,
                                                  LookBehind lookBehind,
                                                  Range derivedArity,
                                                  Stack<String> args,
                                                  Set<ArgSpec> initialized,
                                                  String argDescription) throws Exception {
            boolean noMoreValues = args.isEmpty();
            String value = args.isEmpty() ? null : trim(args.pop()); // unquote the value
            Range arity = argSpec.arity().isUnspecified ? derivedArity : argSpec.arity(); // #509
            if (arity.max == 0 && !arity.isUnspecified && lookBehind == LookBehind.ATTACHED_WITH_SEPARATOR) { // #509
                throw new MaxValuesExceededException(CommandLine.this, optionDescription("", argSpec, 0) +
                        " should be specified without '" + value + "' parameter");
            }
            int result = arity.min; // the number or args we need to consume

            Class<?> cls = argSpec.auxiliaryTypes()[0]; // field may be interface/abstract type, use annotation to get concrete type
            if (arity.min <= 0) { // value may be optional

                // special logic for booleans: BooleanConverter accepts only "true" or "false".
                if (cls == Boolean.class || cls == Boolean.TYPE) {

                    // boolean option with arity = 0..1 or 0..*: value MAY be a param
                    if (arity.max > 0 && "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                        result = 1;            // if it is a varargs we only consume 1 argument if it is a boolean value
                        if (!lookBehind.isAttached()) { parseResult.nowProcessing(argSpec, value); }
                    } else if (lookBehind != LookBehind.ATTACHED_WITH_SEPARATOR) { // if attached, try converting the value to boolean (and fail if invalid value)
                        // it's okay to ignore value if not attached to option
                        if (value != null) {
                            args.push(value); // we don't consume the value
                        }
                        if (commandSpec.parser().toggleBooleanFlags()) {
                            Boolean currentValue = (Boolean) argSpec.getValue();
                            value = String.valueOf(currentValue == null || !currentValue); // #147 toggle existing boolean value
                        } else {
                            value = "true";
                        }
                    }
                } else { // non-boolean option with optional value #325
                    if (isOption(value)) {
                        args.push(value); // we don't consume the value
                        value = "";
                    } else if (value == null) {
                        value = "";
                    } else {
                        if (!lookBehind.isAttached()) { parseResult.nowProcessing(argSpec, value); }
                    }
                }
            } else {
                if (!lookBehind.isAttached()) { parseResult.nowProcessing(argSpec, value); }
            }
            if (noMoreValues && value == null) {
                return 0;
            }
            ITypeConverter<?> converter = getTypeConverter(cls, argSpec, 0);
            Object newValue = tryConvert(argSpec, -1, converter, value, cls);
            Object oldValue = argSpec.getValue();
            String traceMessage = "Setting %s to '%3$s' (was '%2$s') for %4$s%n";
            if (initialized != null) {
                if (initialized.contains(argSpec)) {
                    if (!isOverwrittenOptionsAllowed()) {
                        throw new OverwrittenOptionException(CommandLine.this, argSpec, optionDescription("", argSpec, 0) +  " should be specified only once");
                    }
                    traceMessage = "Overwriting %s value '%s' with '%s' for %s%n";
                }
                initialized.add(argSpec);
            }
            if (tracer.isInfo()) { tracer.info(traceMessage, argSpec.toString(), String.valueOf(oldValue), String.valueOf(newValue), argDescription); }
            argSpec.setValue(newValue, commandSpec.commandLine());
            parseResult.addOriginalStringValue(argSpec, value);// #279 track empty string value if no command line argument was consumed
            parseResult.addStringValue(argSpec, value);
            parseResult.addTypedValues(argSpec, position, newValue);
            parseResult.add(argSpec, position);
            return result;
        }
        private int applyValuesToMapField(ArgSpec argSpec,
                                          LookBehind lookBehind,
                                          Range arity,
                                          Stack<String> args,
                                          Set<ArgSpec> initialized,
                                          String argDescription) throws Exception {
            Class<?>[] classes = argSpec.auxiliaryTypes();
            if (classes.length < 2) { throw new ParameterException(CommandLine.this, argSpec.toString() + " needs two types (one for the map key, one for the value) but only has " + classes.length + " types configured.",argSpec, null); }
            ITypeConverter<?> keyConverter   = getTypeConverter(classes[0], argSpec, 0);
            ITypeConverter<?> valueConverter = getTypeConverter(classes[1], argSpec, 1);
            @SuppressWarnings("unchecked") Map<Object, Object> map = (Map<Object, Object>) argSpec.getValue();
            if (map == null || (!map.isEmpty() && !initialized.contains(argSpec))) {
                map = createMap(argSpec.type()); // map class
                argSpec.setValue(map, commandSpec.commandLine());
            }
            initialized.add(argSpec);
            int originalSize = map.size();
            consumeMapArguments(argSpec, lookBehind, arity, args, classes, keyConverter, valueConverter, map, argDescription);
            parseResult.add(argSpec, position);
            argSpec.setValue(map, commandSpec.commandLine());
            return map.size() - originalSize;
        }

        private void consumeMapArguments(ArgSpec argSpec,
                                         LookBehind lookBehind,
                                         Range arity,
                                         Stack<String> args,
                                         Class<?>[] classes,
                                         ITypeConverter<?> keyConverter,
                                         ITypeConverter<?> valueConverter,
                                         Map<Object, Object> result,
                                         String argDescription) throws Exception {

            // don't modify Interpreter.position: same position may be consumed by multiple ArgSpec objects
            int currentPosition = position;

            // first do the arity.min mandatory parameters
            int initialSize = argSpec.stringValues().size();
            int consumed = consumedCountMap(0, initialSize, argSpec);
            for (int i = 0; consumed < arity.min && !args.isEmpty(); i++) {
                Map<Object, Object> typedValuesAtPosition = new LinkedHashMap<Object, Object>();
                parseResult.addTypedValues(argSpec, currentPosition++, typedValuesAtPosition);
                assertNoMissingMandatoryParameter(argSpec, args, i, arity);
                consumeOneMapArgument(argSpec, lookBehind, arity, consumed, args.pop(), classes, keyConverter, valueConverter, typedValuesAtPosition, i, argDescription);
                result.putAll(typedValuesAtPosition);
                consumed = consumedCountMap(i + 1, initialSize, argSpec);
                lookBehind = LookBehind.SEPARATE;
            }
            // now process the varargs if any
            for (int i = consumed; consumed < arity.max && !args.isEmpty(); i++) {
                if (!varargCanConsumeNextValue(argSpec, args.peek())) { break; }

                Map<Object, Object> typedValuesAtPosition = new LinkedHashMap<Object, Object>();
                parseResult.addTypedValues(argSpec, currentPosition++, typedValuesAtPosition);
                if (!canConsumeOneMapArgument(argSpec, arity, consumed, args.peek(), classes, keyConverter, valueConverter, argDescription)) {
                    break; // leave empty map at argSpec.typedValueAtPosition[currentPosition] so we won't try to consume that position again
                }
                consumeOneMapArgument(argSpec, lookBehind, arity, consumed, args.pop(), classes, keyConverter, valueConverter, typedValuesAtPosition, i, argDescription);
                result.putAll(typedValuesAtPosition);
                consumed = consumedCountMap(i + 1, initialSize, argSpec);
                lookBehind = LookBehind.SEPARATE;
            }
        }

        private void consumeOneMapArgument(ArgSpec argSpec,
                                           LookBehind lookBehind,
                                           Range arity, int consumed,
                                           String arg,
                                           Class<?>[] classes,
                                           ITypeConverter<?> keyConverter, ITypeConverter<?> valueConverter,
                                           Map<Object, Object> result,
                                           int index,
                                           String argDescription) {
            if (!lookBehind.isAttached()) { parseResult.nowProcessing(argSpec, arg); }
            String raw = trim(arg);
            String[] values = argSpec.splitValue(raw, commandSpec.parser(), arity, consumed);
            for (String value : values) {
                String[] keyValue = splitKeyValue(argSpec, value);
                Object mapKey =   tryConvert(argSpec, index, keyConverter,   keyValue[0], classes[0]);
                Object mapValue = tryConvert(argSpec, index, valueConverter, keyValue[1], classes[1]);
                result.put(mapKey, mapValue);
                if (tracer.isInfo()) { tracer.info("Putting [%s : %s] in %s<%s, %s> %s for %s%n", String.valueOf(mapKey), String.valueOf(mapValue),
                        result.getClass().getSimpleName(), classes[0].getSimpleName(), classes[1].getSimpleName(), argSpec.toString(), argDescription); }
                parseResult.addStringValue(argSpec, keyValue[0]);
                parseResult.addStringValue(argSpec, keyValue[1]);
            }
            parseResult.addOriginalStringValue(argSpec, raw);
        }

        private boolean canConsumeOneMapArgument(ArgSpec argSpec, Range arity, int consumed,
                                                 String raw, Class<?>[] classes,
                                                 ITypeConverter<?> keyConverter, ITypeConverter<?> valueConverter,
                                                 String argDescription) {
            String[] values = argSpec.splitValue(raw, commandSpec.parser(), arity, consumed);
            try {
                for (String value : values) {
                    String[] keyValue = splitKeyValue(argSpec, value);
                    tryConvert(argSpec, -1, keyConverter, keyValue[0], classes[0]);
                    tryConvert(argSpec, -1, valueConverter, keyValue[1], classes[1]);
                }
                return true;
            } catch (PicocliException ex) {
                tracer.debug("$s cannot be assigned to %s: type conversion fails: %s.%n", raw, argDescription, ex.getMessage());
                return false;
            }
        }

        private String[] splitKeyValue(ArgSpec argSpec, String value) {
            String[] keyValue = value.split("=", 2);
            if (keyValue.length < 2) {
                String splitRegex = argSpec.splitRegex();
                if (splitRegex.length() == 0) {
                    throw new ParameterException(CommandLine.this, "Value for option " + optionDescription("",
                            argSpec, 0) + " should be in KEY=VALUE format but was " + value, argSpec, value);
                } else {
                    throw new ParameterException(CommandLine.this, "Value for option " + optionDescription("",
                            argSpec, 0) + " should be in KEY=VALUE[" + splitRegex + "KEY=VALUE]... format but was " + value, argSpec, value);
                }
            }
            return keyValue;
        }

        private void assertNoMissingMandatoryParameter(ArgSpec argSpec, Stack<String> args, int i, Range arity) {
            if (!varargCanConsumeNextValue(argSpec, args.peek())) {
                String desc = arity.min > 1 ? (i + 1) + " (of " + arity.min + " mandatory parameters) " : "";
                throw new MissingParameterException(CommandLine.this, argSpec, "Expected parameter " + desc + "for " + optionDescription("", argSpec, -1) + " but found '" + args.peek() + "'");
            }
        }
        private int applyValuesToArrayField(ArgSpec argSpec,
                                            LookBehind lookBehind,
                                            Range arity,
                                            Stack<String> args,
                                            Set<ArgSpec> initialized,
                                            String argDescription) throws Exception {
            Object existing = argSpec.getValue();
            int length = existing == null ? 0 : Array.getLength(existing);
            Class<?> type = argSpec.auxiliaryTypes()[0];
            List<Object> converted = consumeArguments(argSpec, lookBehind, arity, args, type, argDescription);
            List<Object> newValues = new ArrayList<Object>();
            if (initialized.contains(argSpec)) { // existing values are default values if initialized does NOT contain argsSpec
                for (int i = 0; i < length; i++) {
                    newValues.add(Array.get(existing, i)); // keep non-default values
                }
            }
            initialized.add(argSpec);
            for (Object obj : converted) {
                if (obj instanceof Collection<?>) {
                    newValues.addAll((Collection<?>) obj);
                } else {
                    newValues.add(obj);
                }
            }
            Object array = Array.newInstance(type, newValues.size());
            for (int i = 0; i < newValues.size(); i++) {
                Array.set(array, i, newValues.get(i));
            }
            argSpec.setValue(array, commandSpec.commandLine());
            parseResult.add(argSpec, position);
            return converted.size(); // return how many args were consumed
        }

        @SuppressWarnings("unchecked")
        private int applyValuesToCollectionField(ArgSpec argSpec,
                                                 LookBehind lookBehind,
                                                 Range arity,
                                                 Stack<String> args,
                                                 Set<ArgSpec> initialized,
                                                 String argDescription) throws Exception {
            Collection<Object> collection = (Collection<Object>) argSpec.getValue();
            Class<?> type = argSpec.auxiliaryTypes()[0];
            List<Object> converted = consumeArguments(argSpec, lookBehind, arity, args, type, argDescription);
            if (collection == null || (!collection.isEmpty() && !initialized.contains(argSpec))) {
                collection = createCollection(argSpec.type()); // collection type
                argSpec.setValue(collection, commandSpec.commandLine());
            }
            initialized.add(argSpec);
            for (Object element : converted) {
                if (element instanceof Collection<?>) {
                    collection.addAll((Collection<?>) element);
                } else {
                    collection.add(element);
                }
            }
            parseResult.add(argSpec, position);
            argSpec.setValue(collection, commandSpec.commandLine());
            return converted.size();
        }

        private List<Object> consumeArguments(ArgSpec argSpec,
                                              LookBehind lookBehind,
                                              Range arity,
                                              Stack<String> args,
                                              Class<?> type,
                                              String argDescription) throws Exception {
            List<Object> result = new ArrayList<Object>();

            // don't modify Interpreter.position: same position may be consumed by multiple ArgSpec objects
            int currentPosition = position;

            // first do the arity.min mandatory parameters
            int initialSize = argSpec.stringValues().size();
            int consumed = consumedCount(0, initialSize, argSpec);
            for (int i = 0; consumed < arity.min && !args.isEmpty(); i++) {
                List<Object> typedValuesAtPosition = new ArrayList<Object>();
                parseResult.addTypedValues(argSpec, currentPosition++, typedValuesAtPosition);
                assertNoMissingMandatoryParameter(argSpec, args, i, arity);
                consumeOneArgument(argSpec, lookBehind, arity, consumed, args.pop(), type, typedValuesAtPosition, i, argDescription);
                result.addAll(typedValuesAtPosition);
                consumed = consumedCount(i + 1, initialSize, argSpec);
                lookBehind = LookBehind.SEPARATE;
            }
            // now process the varargs if any
            for (int i = consumed; consumed < arity.max && !args.isEmpty(); i++) {
                if (!varargCanConsumeNextValue(argSpec, args.peek())) { break; }

                List<Object> typedValuesAtPosition = new ArrayList<Object>();
                parseResult.addTypedValues(argSpec, currentPosition++, typedValuesAtPosition);
                if (!canConsumeOneArgument(argSpec, arity, consumed, args.peek(), type, argDescription)) {
                    break; // leave empty list at argSpec.typedValueAtPosition[currentPosition] so we won't try to consume that position again
                }
                consumeOneArgument(argSpec, lookBehind, arity, consumed, args.pop(), type, typedValuesAtPosition, i, argDescription);
                result.addAll(typedValuesAtPosition);
                consumed = consumedCount(i + 1, initialSize, argSpec);
                lookBehind = LookBehind.SEPARATE;
            }
            if (result.isEmpty() && arity.min == 0 && arity.max <= 1 && isBoolean(type)) {
                return Arrays.asList((Object) Boolean.TRUE);
            }
            return result;
        }

        private int consumedCount(int i, int initialSize, ArgSpec arg) {
            return commandSpec.parser().splitFirst() ? arg.stringValues().size() - initialSize : i;
        }

        private int consumedCountMap(int i, int initialSize, ArgSpec arg) {
            return commandSpec.parser().splitFirst() ? (arg.stringValues().size() - initialSize) / 2 : i;
        }

        private int consumeOneArgument(ArgSpec argSpec,
                                       LookBehind lookBehind,
                                       Range arity,
                                       int consumed,
                                       String arg,
                                       Class<?> type,
                                       List<Object> result,
                                       int index,
                                       String argDescription) {
            if (!lookBehind.isAttached()) { parseResult.nowProcessing(argSpec, arg); }
            String raw = trim(arg);
            String[] values = argSpec.splitValue(raw, commandSpec.parser(), arity, consumed);
            ITypeConverter<?> converter = getTypeConverter(type, argSpec, 0);
            for (int j = 0; j < values.length; j++) {
                result.add(tryConvert(argSpec, index, converter, values[j], type));
                if (tracer.isInfo()) {
                    tracer.info("Adding [%s] to %s for %s%n", String.valueOf(result.get(result.size() - 1)), argSpec.toString(), argDescription);
                }
                parseResult.addStringValue(argSpec, values[j]);
            }
            parseResult.addOriginalStringValue(argSpec, raw);
            return ++index;
        }
        private boolean canConsumeOneArgument(ArgSpec argSpec, Range arity, int consumed, String arg, Class<?> type, String argDescription) {
            ITypeConverter<?> converter = getTypeConverter(type, argSpec, 0);
            try {
                String[] values = argSpec.splitValue(trim(arg), commandSpec.parser(), arity, consumed);
//                if (!argSpec.acceptsValues(values.length, commandSpec.parser())) {
//                    tracer.debug("$s would split into %s values but %s cannot accept that many values.%n", arg, values.length, argDescription);
//                    return false;
//                }
                for (String value : values) {
                    tryConvert(argSpec, -1, converter, value, type);
                }
                return true;
            } catch (PicocliException ex) {
                tracer.debug("$s cannot be assigned to %s: type conversion fails: %s.%n", arg, argDescription, ex.getMessage());
                return false;
            }
        }

        /** Returns whether the next argument can be assigned to a vararg option/positional parameter.
         * <p>
         * Usually, we stop if we encounter '--', a command, or another option.
         * However, if end-of-options has been reached, positional parameters may consume all remaining arguments. </p>*/
        private boolean varargCanConsumeNextValue(ArgSpec argSpec, String nextValue) {
            if (endOfOptions && argSpec.isPositional()) { return true; }
            boolean isCommand = commandSpec.subcommands().containsKey(nextValue);
            return !isCommand && !isOption(nextValue);
        }

        /**
         * Called when parsing varargs parameters for a multi-value option.
         * When an option is encountered, the remainder should not be interpreted as vararg elements.
         * @param arg the string to determine whether it is an option or not
         * @return true if it is an option, false otherwise
         */
        private boolean isOption(String arg) {
            if (arg == null)      { return false; }
            if ("--".equals(arg)) { return true; }

            // not just arg prefix: we may be in the middle of parsing -xrvfFILE
            if (commandSpec.optionsMap().containsKey(arg)) { // -v or -f or --file (not attached to param or other option)
                return true;
            }
            int separatorIndex = arg.indexOf(config().separator());
            if (separatorIndex > 0) { // -f=FILE or --file==FILE (attached to param via separator)
                if (commandSpec.optionsMap().containsKey(arg.substring(0, separatorIndex))) {
                    return true;
                }
            }
            return (arg.length() > 2 && arg.startsWith("-") && commandSpec.posixOptionsMap().containsKey(arg.charAt(1)));
        }
        private Object tryConvert(ArgSpec argSpec, int index, ITypeConverter<?> converter, String value, Class<?> type)
                throws ParameterException {
            try {
                return converter.convert(value);
            } catch (TypeConversionException ex) {
                String msg = String.format("Invalid value for %s: %s", optionDescription("", argSpec, index), ex.getMessage());
                throw new ParameterException(CommandLine.this, msg, argSpec, value);
            } catch (Exception other) {
                String desc = optionDescription("", argSpec, index);
                String msg = String.format("Invalid value for %s: cannot convert '%s' to %s (%s)", desc, value, type.getSimpleName(), other);
                throw new ParameterException(CommandLine.this, msg, other, argSpec, value);
            }
        }

        private String optionDescription(String prefix, ArgSpec argSpec, int index) {
            String desc = "";
            if (argSpec.isOption()) {
                desc = prefix + "option '" + ((OptionSpec) argSpec).longestName() + "'";
                if (index >= 0) {
                    if (argSpec.arity().max > 1) {
                        desc += " at index " + index;
                    }
                    desc += " (" + argSpec.paramLabel() + ")";
                }
            } else {
                desc = prefix + "positional parameter at index " + ((PositionalParamSpec) argSpec).index() + " (" + argSpec.paramLabel() + ")";
            }
            return desc;
        }

        private boolean isAnyHelpRequested() { return isHelpRequested || parseResult.versionHelpRequested || parseResult.usageHelpRequested; }

        private void updateHelpRequested(CommandSpec command) {
            isHelpRequested |= command.helpCommand();
        }
        private void updateHelpRequested(ArgSpec argSpec) {
            if (argSpec.isOption()) {
                OptionSpec option = (OptionSpec) argSpec;
                isHelpRequested                  |= is(argSpec, "help", option.help());
                parseResult.versionHelpRequested |= is(argSpec, "versionHelp", option.versionHelp());
                parseResult.usageHelpRequested   |= is(argSpec, "usageHelp", option.usageHelp());
            }
        }
        private boolean is(ArgSpec p, String attribute, boolean value) {
            if (value) { if (tracer.isInfo()) {tracer.info("%s has '%s' annotation: not validating required fields%n", p.toString(), attribute); }}
            return value;
        }
        @SuppressWarnings("unchecked")
        private Collection<Object> createCollection(Class<?> collectionClass) throws Exception {
            if (collectionClass.isInterface()) {
                if (List.class.isAssignableFrom(collectionClass)) {
                    return new ArrayList<Object>();
                } else if (SortedSet.class.isAssignableFrom(collectionClass)) {
                    return new TreeSet<Object>();
                } else if (Set.class.isAssignableFrom(collectionClass)) {
                    return new LinkedHashSet<Object>();
                } else if (Queue.class.isAssignableFrom(collectionClass)) {
                    return new LinkedList<Object>(); // ArrayDeque is only available since 1.6
                }
                return new ArrayList<Object>();
            }
            // custom Collection implementation class must have default constructor
            return (Collection<Object>) collectionClass.newInstance();
        }
        @SuppressWarnings("unchecked") private Map<Object, Object> createMap(Class<?> mapClass) throws Exception {
            try { // if it is an implementation class, instantiate it
                return (Map<Object, Object>) mapClass.getDeclaredConstructor().newInstance();
            } catch (Exception ignored) {}
            return new LinkedHashMap<Object, Object>();
        }
        private ITypeConverter<?> getTypeConverter(final Class<?> type, ArgSpec argSpec, int index) {
            if (argSpec.converters().length > index) { return argSpec.converters()[index]; }
            if (converterRegistry.containsKey(type)) { return converterRegistry.get(type); }
            if (type.isEnum()) {
                return new ITypeConverter<Object>() {
                    @SuppressWarnings("unchecked")
                    public Object convert(String value) throws Exception {
                        if (commandSpec.parser().caseInsensitiveEnumValuesAllowed()) {
                            String upper = value.toUpperCase();
                            for (Object enumConstant : type.getEnumConstants()) {
                                if (upper.equals(String.valueOf(enumConstant).toUpperCase())) { return enumConstant; }
                            }
                        }
                        try { return Enum.valueOf((Class<Enum>) type, value); }
                        catch (Exception ex) { throw new TypeConversionException(
                                String.format("expected one of %s but was '%s'", Arrays.asList(type.getEnumConstants()), value)); }
                    }
                };
            }
            throw new MissingTypeConverterException(CommandLine.this, "No TypeConverter registered for " + type.getName() + " of " + argSpec);
        }

        private boolean assertNoMissingParameters(ArgSpec argSpec, Range arity, Stack<String> args) {
            if (argSpec.interactive()) { return true; }
            int available = args.size();
            if (available > 0 && commandSpec.parser().splitFirst() && argSpec.splitRegex().length() > 0) {
                available += argSpec.splitValue(args.peek(), commandSpec.parser(), arity, 0).length - 1;
            }
            if (arity.min > available) {
                if (arity.min == 1) {
                    if (argSpec.isOption()) {
                        maybeThrow(new MissingParameterException(CommandLine.this, argSpec, "Missing required parameter for " +
                                optionDescription("", argSpec, 0)));
                        return false;
                    }
                    Range indexRange = ((PositionalParamSpec) argSpec).index();
                    String sep = "";
                    String names = ": ";
                    int count = 0;
                    List<PositionalParamSpec> positionalParameters = commandSpec.positionalParameters();
                    for (int i = indexRange.min; i < positionalParameters.size(); i++) {
                        if (positionalParameters.get(i).arity().min > 0) {
                            names += sep + positionalParameters.get(i).paramLabel();
                            sep = ", ";
                            count++;
                        }
                    }
                    String msg = "Missing required parameter";
                    Range paramArity = argSpec.arity();
                    if (count > 1 || arity.min - available > 1) {
                        msg += "s";
                    }
                    maybeThrow(new MissingParameterException(CommandLine.this, argSpec, msg + names));
                } else if (args.isEmpty()) {
                    maybeThrow(new MissingParameterException(CommandLine.this, argSpec, optionDescription("", argSpec, 0) +
                            " requires at least " + arity.min + " values, but none were specified."));
                } else {
                    maybeThrow(new MissingParameterException(CommandLine.this, argSpec, optionDescription("", argSpec, 0) +
                            " requires at least " + arity.min + " values, but only " + available + " were specified: " + reverse(args)));
                }
                return false;
            }
            return true;
        }
        private String trim(String value) {
            return unquote(value);
        }

        private String unquote(String value) {
            if (!commandSpec.parser().trimQuotes()) { return value; }
            return value == null
                    ? null
                    : (value.length() > 1 && value.startsWith("\"") && value.endsWith("\""))
                        ? value.substring(1, value.length() - 1)
                        : value;
        }

        char[] readPassword(String prompt, boolean echoInput) {
            try {
                Object console = System.class.getDeclaredMethod("console").invoke(null);
                Method method;
                if (echoInput) {
                    method = console.getClass().getDeclaredMethod("readLine", String.class, Object[].class);
                    String line = (String) method.invoke(console, prompt, new Object[0]);
                    return line.toCharArray();
                } else {
                    method = console.getClass().getDeclaredMethod("readPassword", String.class, Object[].class);
                    return (char[]) method.invoke(console, prompt, new Object[0]);
                }
            } catch (Exception e) {
                System.out.print(prompt);
                InputStreamReader isr = new InputStreamReader(System.in);
                BufferedReader in = new BufferedReader(isr);
                try {
                    return in.readLine().toCharArray();
                } catch (IOException ex2) {
                    throw new IllegalStateException(ex2);
                }
            }
        }
    }
    private static class PositionalParametersSorter implements Comparator<ArgSpec> {
        private static final Range OPTION_INDEX = new Range(0, 0, false, true, "0");
        public int compare(ArgSpec p1, ArgSpec p2) {
            int result = index(p1).compareTo(index(p2));
            return (result == 0) ? p1.arity().compareTo(p2.arity()) : result;
        }
        private Range index(ArgSpec arg) { return arg.isOption() ? OPTION_INDEX : ((PositionalParamSpec) arg).index(); }
    }
    /**
     * Inner class to group the built-in {@link ITypeConverter} implementations.
     */
    private static class BuiltIn {
        static class StringConverter implements ITypeConverter<String> {
            public String convert(String value) { return value; }
        }
        static class StringBuilderConverter implements ITypeConverter<StringBuilder> {
            public StringBuilder convert(String value) { return new StringBuilder(value); }
        }
        static class CharSequenceConverter implements ITypeConverter<CharSequence> {
            public String convert(String value) { return value; }
        }
        /** Converts {@code "true"} or {@code "false"} to a {@code Boolean}. Other values result in a ParameterException.*/
        static class BooleanConverter implements ITypeConverter<Boolean> {
            public Boolean convert(String value) {
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    return Boolean.parseBoolean(value);
                } else {
                    throw new TypeConversionException("'" + value + "' is not a boolean");
                }
            }
        }
        static class CharacterConverter implements ITypeConverter<Character> {
            public Character convert(String value) {
                if (value.length() > 1) {
                    throw new TypeConversionException("'" + value + "' is not a single character");
                }
                return value.charAt(0);
            }
        }
        private static TypeConversionException fail(String value, Class<?> c) { return fail(value, c, "'%s' is not a %s"); }
        private static TypeConversionException fail(String value, Class<?> c, String template) {
            return new TypeConversionException(String.format(template, value, c.getSimpleName()));
        }
        /** Converts text to a {@code Byte} by delegating to {@link Byte#valueOf(String)}.*/
        static class ByteConverter implements ITypeConverter<Byte> {
            public Byte convert(String value) { try {return Byte.valueOf(value);} catch (Exception ex) {throw fail(value, Byte.TYPE);} }
        }
        /** Converts text to a {@code Short} by delegating to {@link Short#valueOf(String)}.*/
        static class ShortConverter implements ITypeConverter<Short> {
            public Short convert(String value) { try {return Short.valueOf(value);} catch (Exception ex) {throw fail(value, Short.TYPE);}  }
        }
        /** Converts text to an {@code Integer} by delegating to {@link Integer#valueOf(String)}.*/
        static class IntegerConverter implements ITypeConverter<Integer> {
            public Integer convert(String value) { try {return Integer.valueOf(value);} catch (Exception ex) {throw fail(value, Integer.TYPE, "'%s' is not an %s");}  }
        }
        /** Converts text to a {@code Long} by delegating to {@link Long#valueOf(String)}.*/
        static class LongConverter implements ITypeConverter<Long> {
            public Long convert(String value) { try {return Long.valueOf(value);} catch (Exception ex) {throw fail(value, Long.TYPE);}  }
        }
        static class FloatConverter implements ITypeConverter<Float> {
            public Float convert(String value) { try {return Float.valueOf(value);} catch (Exception ex) {throw fail(value, Float.TYPE);}  }
        }
        static class DoubleConverter implements ITypeConverter<Double> {
            public Double convert(String value) { try {return Double.valueOf(value);} catch (Exception ex) {throw fail(value, Double.TYPE);}  }
        }
        static class FileConverter implements ITypeConverter<File> {
            public File convert(String value) { return new File(value); }
        }
        static class URLConverter implements ITypeConverter<URL> {
            public URL convert(String value) throws MalformedURLException { return new URL(value); }
        }
        static class URIConverter implements ITypeConverter<URI> {
            public URI convert(String value) throws URISyntaxException { return new URI(value); }
        }
        /** Converts text in {@code yyyy-mm-dd} format to a {@code java.util.Date}. ParameterException on failure. */
        static class ISO8601DateConverter implements ITypeConverter<Date> {
            public Date convert(String value) {
                try {
                    return new SimpleDateFormat("yyyy-MM-dd").parse(value);
                } catch (ParseException e) {
                    throw new TypeConversionException("'" + value + "' is not a yyyy-MM-dd date");
                }
            }
        }
        /** Converts text in any of the following formats to a {@code java.sql.Time}: {@code HH:mm}, {@code HH:mm:ss},
         * {@code HH:mm:ss.SSS}, {@code HH:mm:ss,SSS}. Other formats result in a ParameterException. */
        static class ISO8601TimeConverter implements ITypeConverter<Object> {
            // Implementation note: use reflection so that picocli only requires the java.base module in Java 9.
            private static final String FQCN = "java.sql.Time";
            public Object convert(String value) {
                try {
                    if (value.length() <= 5) {
                        return createTime(new SimpleDateFormat("HH:mm").parse(value).getTime());
                    } else if (value.length() <= 8) {
                        return createTime(new SimpleDateFormat("HH:mm:ss").parse(value).getTime());
                    } else if (value.length() <= 12) {
                        try {
                            return createTime(new SimpleDateFormat("HH:mm:ss.SSS").parse(value).getTime());
                        } catch (ParseException e2) {
                            return createTime(new SimpleDateFormat("HH:mm:ss,SSS").parse(value).getTime());
                        }
                    }
                } catch (ParseException ignored) {
                    // ignored because we throw a ParameterException below
                }
                throw new TypeConversionException("'" + value + "' is not a HH:mm[:ss[.SSS]] time");
            }

            private Object createTime(long epochMillis) {
                try {
                    Class<?> timeClass = Class.forName(FQCN);
                    Constructor<?> constructor = timeClass.getDeclaredConstructor(long.class);
                    return constructor.newInstance(epochMillis);
                } catch (Exception e) {
                    throw new TypeConversionException("Unable to create new java.sql.Time with long value " + epochMillis + ": " + e.getMessage());
                }
            }

            public static void registerIfAvailable(Map<Class<?>, ITypeConverter<?>> registry, Tracer tracer) {
                if (excluded(FQCN, tracer)) { return; }
                try {
                    registry.put(Class.forName(FQCN), new ISO8601TimeConverter());
                } catch (Exception e) {
                    if (!traced.contains(FQCN)) {
                        tracer.debug("Could not register converter for %s: %s%n", FQCN, e.toString());
                    }
                    traced.add(FQCN);
                }
            }
        }
        static class BigDecimalConverter implements ITypeConverter<BigDecimal> {
            public BigDecimal convert(String value) { return new BigDecimal(value); }
        }
        static class BigIntegerConverter implements ITypeConverter<BigInteger> {
            public BigInteger convert(String value) { return new BigInteger(value); }
        }
        static class CharsetConverter implements ITypeConverter<Charset> {
            public Charset convert(String s) { return Charset.forName(s); }
        }
        /** Converts text to a {@code InetAddress} by delegating to {@link InetAddress#getByName(String)}. */
        static class InetAddressConverter implements ITypeConverter<InetAddress> {
            public InetAddress convert(String s) throws Exception { return InetAddress.getByName(s); }
        }
        static class PatternConverter implements ITypeConverter<Pattern> {
            public Pattern convert(String s) { return Pattern.compile(s); }
        }
        static class UUIDConverter implements ITypeConverter<UUID> {
            public UUID convert(String s) throws Exception { return UUID.fromString(s); }
        }
        static class CurrencyConverter implements ITypeConverter<Currency> {
            public Currency convert(String s) throws Exception { return Currency.getInstance(s); }
        }
        static class TimeZoneConverter implements ITypeConverter<TimeZone> {
            public TimeZone convert(String s) throws Exception { return TimeZone.getTimeZone(s); }
        }
        static class ByteOrderConverter implements ITypeConverter<ByteOrder> {
            public ByteOrder convert(String s) throws Exception {
                if (s.equalsIgnoreCase(ByteOrder.BIG_ENDIAN.toString())) { return ByteOrder.BIG_ENDIAN; }
                if (s.equalsIgnoreCase(ByteOrder.LITTLE_ENDIAN.toString())) { return ByteOrder.LITTLE_ENDIAN; }
                throw new TypeConversionException("'" + s + "' is not a valid ByteOrder");
            }
        }
        static class ClassConverter implements ITypeConverter<Class<?>> {
            public Class<?> convert(String s) throws Exception { return Class.forName(s); }
        }
        static class NetworkInterfaceConverter implements ITypeConverter<NetworkInterface> {
            public NetworkInterface convert(String s) throws Exception {
                try {
                    InetAddress addr = new InetAddressConverter().convert(s);
                    return NetworkInterface.getByInetAddress(addr);
                } catch (Exception ex) {
                    try { return NetworkInterface.getByName(s);
                    } catch (Exception ex2) {
                        throw new TypeConversionException("'" + s + "' is not an InetAddress or NetworkInterface name");
                    }
                }
            }
        }
        static void registerIfAvailable(Map<Class<?>, ITypeConverter<?>> registry, Tracer tracer, String fqcn, String factoryMethodName, Class<?>... paramTypes) {
            registerIfAvailable(registry, tracer, fqcn, fqcn, factoryMethodName, paramTypes);
        }
        static void registerIfAvailable(Map<Class<?>, ITypeConverter<?>> registry, Tracer tracer, String fqcn, String factoryClass, String factoryMethodName, Class<?>... paramTypes) {
            if (excluded(fqcn, tracer)) { return; }
            try {
                Class<?> cls = Class.forName(fqcn);
                Class<?> factory = Class.forName(factoryClass);
                Method method = factory.getDeclaredMethod(factoryMethodName, paramTypes);
                registry.put(cls, new ReflectionConverter(method, paramTypes));
            } catch (Exception e) {
                if (!traced.contains(fqcn)) {
                    tracer.debug("Could not register converter for %s: %s%n", fqcn, e.toString());
                }
                traced.add(fqcn);
            }
        }
        static boolean excluded(String fqcn, Tracer tracer) {
            String[] excludes = System.getProperty("picocli.converters.excludes", "").split(",");
            for (String regex : excludes) {
                if (fqcn.matches(regex)) {
                    tracer.debug("BuiltIn type converter for %s is not loaded: (picocli.converters.excludes=%s)%n", fqcn, System.getProperty("picocli.converters.excludes"));
                    return true;
                }
            }
            return false;
        }
        static Set<String> traced = new HashSet<String>();
        static class ReflectionConverter implements ITypeConverter<Object> {
            private final Method method;
            private Class<?>[] paramTypes;

            public ReflectionConverter(Method method, Class<?>... paramTypes) {
                this.method = Assert.notNull(method, "method");
                this.paramTypes = Assert.notNull(paramTypes, "paramTypes");
            }

            public Object convert(String s) {
                try {
                    if (paramTypes.length > 1) {
                        return method.invoke(null, s, new String[0]);
                    } else {
                        return method.invoke(null, s);
                    }
                } catch (InvocationTargetException e) {
                    throw new TypeConversionException(String.format("cannot convert '%s' to %s (%s)", s, method.getReturnType(), e.getTargetException()));
                } catch (Exception e) {
                    throw new TypeConversionException(String.format("cannot convert '%s' to %s (%s)", s, method.getReturnType(), e));
                }
            }
        }
        private BuiltIn() {} // private constructor: never instantiate
    }

    static class AutoHelpMixin {
        private static final String KEY = "mixinStandardHelpOptions";

        @Option(names = {"-h", "--help"}, usageHelp = true, descriptionKey = "mixinStandardHelpOptions.help",
                description = "Show this help message and exit.")
        private boolean helpRequested;

        @Option(names = {"-V", "--version"}, versionHelp = true, descriptionKey = "mixinStandardHelpOptions.version",
                description = "Print version information and exit.")
        private boolean versionRequested;
    }

    /** Help command that can be installed as a subcommand on all application commands. When invoked with a subcommand
     * argument, it prints usage help for the specified subcommand. For example:<pre>
     *
     * // print help for subcommand
     * command help subcommand
     * </pre><p>
     * When invoked without additional parameters, it prints usage help for the parent command. For example:
     * </p><pre>
     *
     * // print help for command
     * command help
     * </pre>
     * For {@linkplain Messages internationalization}: this command has a {@code --help} option with {@code descriptionKey = "helpCommand.help"},
     * and a {@code COMMAND} positional parameter with {@code descriptionKey = "helpCommand.command"}.
     * @since 3.0
     */
    @Command(name = "help", header = "Displays help information about the specified command",
            synopsisHeading = "%nUsage: ", helpCommand = true,
            description = {"%nWhen no COMMAND is given, the usage help for the main command is displayed.",
                    "If a COMMAND is specified, the help for that command is shown.%n"})
    public static final class HelpCommand implements IHelpCommandInitializable, Runnable {

        @Option(names = {"-h", "--help"}, usageHelp = true, descriptionKey = "helpCommand.help",
                description = "Show usage help for the help command and exit.")
        private boolean helpRequested;

        @Parameters(paramLabel = "COMMAND", descriptionKey = "helpCommand.command",
                    description = "The COMMAND to display the usage help message for.")
        private String[] commands = new String[0];

        private CommandLine self;
        private PrintStream out;
        private PrintStream err;
        private Ansi ansi;

        /** Invokes {@link #usage(PrintStream, Help.Ansi) usage} for the specified command, or for the parent command. */
        public void run() {
            CommandLine parent = self == null ? null : self.getParent();
            if (parent == null) { return; }
            if (commands.length > 0) {
                CommandLine subcommand = parent.getSubcommands().get(commands[0]);
                if (subcommand != null) {
                    subcommand.usage(out, ansi);
                } else {
                    throw new ParameterException(parent, "Unknown subcommand '" + commands[0] + "'.", null, commands[0]);
                }
            } else {
                parent.usage(out, ansi);
            }
        }
        /** {@inheritDoc} */
        public void init(CommandLine helpCommandLine, Ansi ansi, PrintStream out, PrintStream err) {
            this.self = Assert.notNull(helpCommandLine, "helpCommandLine");
            this.ansi = Assert.notNull(ansi, "ansi");
            this.out  = Assert.notNull(out, "out");
            this.err  = Assert.notNull(err, "err");
        }
    }

    /** Help commands that provide usage help for other commands can implement this interface to be initialized with the information they need.
     * <p>The {@link #printHelpIfRequested(List, PrintStream, PrintStream, Help.Ansi) CommandLine::printHelpIfRequested} method calls the
     * {@link #init(CommandLine, picocli.CommandLine.Help.Ansi, PrintStream, PrintStream) init} method on commands marked as {@link Command#helpCommand() helpCommand}
     * before the help command's {@code run} or {@code call} method is called.</p>
     * <p><b>Implementation note:</b></p><p>
     * If an error occurs in the {@code run} or {@code call} method while processing the help request, it is recommended custom Help
     * commands throw a {@link ParameterException ParameterException} with a reference to the parent command. The {@link DefaultExceptionHandler DefaultExceptionHandler} will print
     * the error message and the usage for the parent command, and will terminate with the exit code of the exception handler if one was set.
     * </p>
     * @since 3.0 */
    public static interface IHelpCommandInitializable {
        /** Initializes this object with the information needed to implement a help command that provides usage help for other commands.
         * @param helpCommandLine the {@code CommandLine} object associated with this help command. Implementors can use
         *                        this to walk the command hierarchy and get access to the help command's parent and sibling commands.
         * @param ansi whether to use Ansi colors or not
         * @param out the stream to print the usage help message to
         * @param err the error stream to print any diagnostic messages to, in addition to the output from the exception handler
         */
        void init(CommandLine helpCommandLine, Ansi ansi, PrintStream out, PrintStream err);
    }

    private enum TraceLevel { OFF, WARN, INFO, DEBUG;
        public boolean isEnabled(TraceLevel other) { return ordinal() >= other.ordinal(); }
        private void print(Tracer tracer, String msg, Object... params) {
            if (tracer.level.isEnabled(this)) { tracer.stream.printf(prefix(msg), params); }
        }
        private String prefix(String msg) { return "[picocli " + this + "] " + msg; }
        static TraceLevel lookup(String key) { return key == null ? WARN : StringUtils.isBlank(key) || "true".equalsIgnoreCase(key) ? INFO : valueOf(key); }
    }
    private static class Tracer {
        TraceLevel level = TraceLevel.lookup(System.getProperty("picocli.trace"));
        PrintStream stream = System.err;
        void warn (String msg, Object... params) { TraceLevel.WARN.print(this, msg, params); }
        void info (String msg, Object... params) { TraceLevel.INFO.print(this, msg, params); }
        void debug(String msg, Object... params) { TraceLevel.DEBUG.print(this, msg, params); }
        boolean isWarn()  { return level.isEnabled(TraceLevel.WARN); }
        boolean isInfo()  { return level.isEnabled(TraceLevel.INFO); }
        boolean isDebug() { return level.isEnabled(TraceLevel.DEBUG); }
    }
    /**
     * Uses cosine similarity to find matches from a candidate set for a specified input.
     * Based on code from http://www.nearinfinity.com/blogs/seth_schroeder/groovy_cosine_similarity_in_grails.html
     *
     * @author Burt Beckwith
     */
    private static class CosineSimilarity {
        static List<String> mostSimilar(String pattern, Iterable<String> candidates) { return mostSimilar(pattern, candidates, 0); }
        static List<String> mostSimilar(String pattern, Iterable<String> candidates, double threshold) {
            pattern = pattern.toLowerCase();
            SortedMap<Double, String> sorted = new TreeMap<Double, String>();
            for (String candidate : candidates) {
                double score = similarity(pattern, candidate.toLowerCase(), 2);
                if (score > threshold) { sorted.put(score, candidate); }
            }
            return reverseList(new ArrayList<String>(sorted.values()));
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
            for (String key : m1.keySet()) { result += m1.get(key) * (m2.containsKey(key) ? m2.get(key) : 0); }
            return result;
        }
    }
    /** Base class of all exceptions thrown by {@code picocli.CommandLine}.
     * <h2>Class Diagram of the Picocli Exceptions</h2>
     * <p>
     * <img src="doc-files/class-diagram-exceptions.png" alt="Class Diagram of the Picocli Exceptions">
     * </p>
     * @since 2.0 */
    public static class PicocliException extends RuntimeException {
        private static final long serialVersionUID = -2574128880125050818L;
        public PicocliException(String msg) { super(msg); }
        public PicocliException(String msg, Throwable t) { super(msg, t); }
    }
    /** Exception indicating a problem during {@code CommandLine} initialization.
     * @since 2.0 */
    public static class InitializationException extends PicocliException {
        private static final long serialVersionUID = 8423014001666638895L;
        public InitializationException(String msg) { super(msg); }
        public InitializationException(String msg, Exception ex) { super(msg, ex); }
    }
    /** Exception indicating a problem while invoking a command or subcommand.
     * @since 2.0 */
    public static class ExecutionException extends PicocliException {
        private static final long serialVersionUID = 7764539594267007998L;
        private final CommandLine commandLine;
        public ExecutionException(CommandLine commandLine, String msg) {
            super(msg);
            this.commandLine = Assert.notNull(commandLine, "commandLine");
        }
        public ExecutionException(CommandLine commandLine, String msg, Exception ex) {
            super(msg, ex);
            this.commandLine = Assert.notNull(commandLine, "commandLine");
        }
        /** Returns the {@code CommandLine} object for the (sub)command that could not be invoked.
         * @return the {@code CommandLine} object for the (sub)command where invocation failed.
         */
        public CommandLine getCommandLine() { return commandLine; }
    }

    /** Exception thrown by {@link ITypeConverter} implementations to indicate a String could not be converted. */
    public static class TypeConversionException extends PicocliException {
        private static final long serialVersionUID = 4251973913816346114L;
        public TypeConversionException(String msg) { super(msg); }
    }
    /** Exception indicating something went wrong while parsing command line options. */
    public static class ParameterException extends PicocliException {
        private static final long serialVersionUID = 1477112829129763139L;
        private final CommandLine commandLine;
        private ArgSpec argSpec = null;
        private String value = null;

        /** Constructs a new ParameterException with the specified CommandLine and error message.
         * @param commandLine the command or subcommand whose input was invalid
         * @param msg describes the problem
         * @since 2.0 */
        public ParameterException(CommandLine commandLine, String msg) {
            super(msg);
            this.commandLine = Assert.notNull(commandLine, "commandLine");
        }

        /** Constructs a new ParameterException with the specified CommandLine and error message.
         * @param commandLine the command or subcommand whose input was invalid
         * @param msg describes the problem
         * @param t the throwable that caused this ParameterException
         * @since 2.0 */
        public ParameterException(CommandLine commandLine, String msg, Throwable t) {
            super(msg, t);
            this.commandLine = Assert.notNull(commandLine, "commandLine");
        }

        /** Constructs a new ParameterException with the specified CommandLine and error message.
         * @param commandLine the command or subcommand whose input was invalid
         * @param msg describes the problem
         * @param t the throwable that caused this ParameterException
         * @param argSpec the argSpec that caused this ParameterException
         * @param value the value that caused this ParameterException
         * @since 3.2 */
        public ParameterException(CommandLine commandLine, String msg, Throwable t, ArgSpec argSpec, String value) {
            super(msg, t);
            this.commandLine = Assert.notNull(commandLine, "commandLine");
            if (argSpec == null && value == null) { throw new IllegalArgumentException("ArgSpec and value cannot both be null"); }
            this.argSpec = argSpec;
            this.value = value;
        }

        /** Constructs a new ParameterException with the specified CommandLine and error message.
         * @param commandLine the command or subcommand whose input was invalid
         * @param msg describes the problem
         * @param argSpec the argSpec that caused this ParameterException
         * @param value the value that caused this ParameterException
         * @since 3.2 */
        public ParameterException(CommandLine commandLine, String msg, ArgSpec argSpec, String value) {
            super(msg);
            this.commandLine = Assert.notNull(commandLine, "commandLine");
            if (argSpec == null && value == null) { throw new IllegalArgumentException("ArgSpec and value cannot both be null"); }
            this.argSpec = argSpec;
            this.value = value;
        }


        /** Returns the {@code CommandLine} object for the (sub)command whose input could not be parsed.
         * @return the {@code CommandLine} object for the (sub)command where parsing failed.
         * @since 2.0
         */
        public CommandLine getCommandLine() { return commandLine; }

        /** Returns the {@code ArgSpec} object for the (sub)command whose input could not be parsed.
         * @return the {@code ArgSpec} object for the (sub)command where parsing failed.
         * @since 3.2
         */
        public ArgSpec getArgSpec() { return argSpec; }

        /** Returns the {@code String} value for the (sub)command whose input could not be parsed.
         * @return the {@code String} value for the (sub)command where parsing failed.
         * @since 3.2
         */
        public String getValue() { return value; }

        private static ParameterException create(CommandLine cmd, Exception ex, String arg, int i, String[] args) {
            String msg = ex.getClass().getSimpleName() + ": " + ex.getLocalizedMessage()
                    + " while processing argument at or before arg[" + i + "] '" + arg + "' in " + Arrays.toString(args) + ": " + ex.toString();
            return new ParameterException(cmd, msg, ex, null, arg);
        }
    }
    /**
     * Exception indicating that a required parameter was not specified.
     */
    public static class MissingParameterException extends ParameterException {
        private static final long serialVersionUID = 5075678535706338753L;
        private final List<ArgSpec> missing;
        public MissingParameterException(CommandLine commandLine, ArgSpec missing, String msg) { this(commandLine, Arrays.asList(missing), msg); }
        public MissingParameterException(CommandLine commandLine, Collection<ArgSpec> missing, String msg) {
            super(commandLine, msg);
            this.missing = Collections.unmodifiableList(new ArrayList<ArgSpec>(missing));
        }
        public List<ArgSpec> getMissing() { return missing; }
        private static MissingParameterException create(CommandLine cmd, Collection<ArgSpec> missing, String separator) {
            if (missing.size() == 1) {
                return new MissingParameterException(cmd, missing, "Missing required option '"
                        + describe(missing.iterator().next(), separator) + "'");
            }
            List<String> names = new ArrayList<String>(missing.size());
            for (ArgSpec argSpec : missing) {
                names.add(describe(argSpec, separator));
            }
            return new MissingParameterException(cmd, missing, "Missing required options " + names.toString());
        }
        private static String describe(ArgSpec argSpec, String separator) {
            String prefix = (argSpec.isOption())
                ? ((OptionSpec) argSpec).longestName() + separator
                : "params[" + ((PositionalParamSpec) argSpec).index() + "]" + separator;
            return prefix + argSpec.paramLabel();
        }
    }

    /**
     * Exception indicating that multiple fields have been annotated with the same Option name.
     */
    public static class DuplicateOptionAnnotationsException extends InitializationException {
        private static final long serialVersionUID = -3355128012575075641L;
        public DuplicateOptionAnnotationsException(String msg) { super(msg); }

        private static DuplicateOptionAnnotationsException create(String name, ArgSpec argSpec1, ArgSpec argSpec2) {
            return new DuplicateOptionAnnotationsException("Option name '" + name + "' is used by both " +
                    argSpec1.toString() + " and " + argSpec2.toString());
        }
    }
    /** Exception indicating that there was a gap in the indices of the fields annotated with {@link Parameters}. */
    public static class ParameterIndexGapException extends InitializationException {
        private static final long serialVersionUID = -1520981133257618319L;
        public ParameterIndexGapException(String msg) { super(msg); }
    }
    /** Exception indicating that a command line argument could not be mapped to any of the fields annotated with
     * {@link Option} or {@link Parameters}. */
    public static class UnmatchedArgumentException extends ParameterException {
        private static final long serialVersionUID = -8700426380701452440L;
        private List<String> unmatched;
        public UnmatchedArgumentException(CommandLine commandLine, String msg) { super(commandLine, msg); }
        public UnmatchedArgumentException(CommandLine commandLine, Stack<String> args) { this(commandLine, new ArrayList<String>(reverse(args))); }
        public UnmatchedArgumentException(CommandLine commandLine, List<String> args) {
            this(commandLine, describe(args, commandLine) + (args.size() == 1 ? ": " : "s: ") + str(args));
            unmatched = args;
        }
        /** Returns {@code true} and prints suggested solutions to the specified stream if such solutions exist, otherwise returns {@code false}.
         * @since 3.3.0 */
        public static boolean printSuggestions(ParameterException ex, PrintStream out) {
            return ex instanceof UnmatchedArgumentException && ((UnmatchedArgumentException) ex).printSuggestions(out);
        }
        /** Returns the unmatched command line arguments.
         * @since 3.3.0 */
        public List<String> getUnmatched() { return Collections.unmodifiableList(unmatched); }
        /** Returns {@code true} if the first unmatched command line arguments resembles an option, {@code false} otherwise.
         * @since 3.3.0 */
        public boolean isUnknownOption() { return isUnknownOption(unmatched, getCommandLine()); }
        /** Returns {@code true} and prints suggested solutions to the specified stream if such solutions exist, otherwise returns {@code false}.
         * @since 3.3.0 */
        public boolean printSuggestions(PrintStream out) {
            List<String> suggestions = getSuggestions();
            if (!suggestions.isEmpty()) {
                out.println(isUnknownOption()
                        ? "Possible solutions: " + str(suggestions)
                        : "Did you mean: " + str(suggestions).replace(", ", " or ") + "?");
            }
            return !suggestions.isEmpty();
        }
        /** Returns suggested solutions if such solutions exist, otherwise returns an empty list.
         * @since 3.3.0 */
        public List<String> getSuggestions() {
            if (unmatched == null || unmatched.isEmpty()) { return Collections.emptyList(); }
            String arg = unmatched.get(0);
            String stripped = CommandSpec.stripPrefix(arg);
            CommandSpec spec = getCommandLine().getCommandSpec();
            if (spec.resemblesOption(arg, null)) {
                return spec.findOptionNamesWithPrefix(stripped.substring(0, Math.min(2, stripped.length())));
            } else if (!spec.subcommands().isEmpty()) {
                List<String> mostSimilar = CosineSimilarity.mostSimilar(arg, spec.subcommands().keySet());
                return mostSimilar.subList(0, Math.min(3, mostSimilar.size()));
            }
            return Collections.emptyList();
        }
        private static boolean isUnknownOption(List<String> unmatch, CommandLine cmd) {
            return unmatch != null && !unmatch.isEmpty() && cmd.getCommandSpec().resemblesOption(unmatch.get(0), null);
        }
        private static String describe(List<String> unmatch, CommandLine cmd) {
            return isUnknownOption(unmatch, cmd) ? "Unknown option" : "Unmatched argument";
        }
        static String str(List<String> list) {
            String s = list.toString();
            return s.substring(0, s.length() - 1).substring(1);
        }
    }
    /** Exception indicating that more values were specified for an option or parameter than its {@link Option#arity() arity} allows. */
    public static class MaxValuesExceededException extends ParameterException {
        private static final long serialVersionUID = 6536145439570100641L;
        public MaxValuesExceededException(CommandLine commandLine, String msg) { super(commandLine, msg); }
    }
    /** Exception indicating that an option for a single-value option field has been specified multiple times on the command line. */
    public static class OverwrittenOptionException extends ParameterException {
        private static final long serialVersionUID = 1338029208271055776L;
        private final ArgSpec overwrittenArg;
        public OverwrittenOptionException(CommandLine commandLine, ArgSpec overwritten, String msg) {
        	super(commandLine, msg);
        	overwrittenArg = overwritten;
        }
        /** Returns the {@link ArgSpec} for the option which was being overwritten.
         * @since 3.8 */
        public ArgSpec getOverwritten() { return overwrittenArg; }
    }
    /**
     * Exception indicating that an annotated field had a type for which no {@link ITypeConverter} was
     * {@linkplain #registerConverter(Class, ITypeConverter) registered}.
     */
    public static class MissingTypeConverterException extends ParameterException {
        private static final long serialVersionUID = -6050931703233083760L;
        public MissingTypeConverterException(CommandLine commandLine, String msg) { super(commandLine, msg); }
    }
}
