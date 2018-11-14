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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import picocli.annots.Command;
import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.excepts.ExecutionException;
import picocli.excepts.InitializationException;
import picocli.excepts.OverwrittenOptionException;
import picocli.excepts.ParameterException;
import picocli.excepts.UnmatchedArgumentException;
import picocli.handlers.DefaultExceptionHandler;
import picocli.handlers.IExceptionHandler;
import picocli.handlers.IExceptionHandler2;
import picocli.handlers.IParseResultHandler;
import picocli.handlers.IParseResultHandler2;
import picocli.handlers.RunAll;
import picocli.handlers.RunFirst;
import picocli.handlers.RunLast;
import picocli.help.Ansi;
import picocli.help.ColorScheme;
import picocli.help.Help;
import picocli.help.HelpFactory;
import picocli.help.IHelpCommandInitializable;
import picocli.help.IHelpFactory;
import picocli.help.Layout;
import picocli.help.Text;
import picocli.help.TextTable;
import picocli.model.ArgSpec;
import picocli.model.CommandReflection;
import picocli.model.CommandSpec;
import picocli.model.Factory;
import picocli.model.IDefaultValueProvider;
import picocli.model.IFactory;
import picocli.model.ITypeConverter;
import picocli.model.Interpreter;
import picocli.model.ParseResult;
import picocli.model.ParserSpec;
import picocli.model.UsageMessageSpec;
import picocli.util.Assert;
import picocli.util.Tracer;

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

    /**
     * Crack a command line.
     *
     * <p>
     * This method belongs to the implementation of <a href=
     * "https://github.com/apache/commons-exec/commit/360c83b02a34e98c9f406a4a3f4618e3e9327c45">org.apache.commons.exec.CommandLine</a>
     * </p>
     *
     * @param toProcess
     *            the command line to process
     * @return the command line broken into strings. An empty or null toProcess parameter results in
     *         a zero sized array
     */
    private static String[] translateCommandline(final String toProcess) {
        if (toProcess == null || toProcess.length() == 0) {
            // no command? no string
            return new String[0];
        }

        // parse with a simple finite state machine

        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        int state = normal;
        final StringTokenizer tok = new StringTokenizer(toProcess, "\"\' ", true);
        final ArrayList<String> list = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;

        while (tok.hasMoreTokens()) {
            final String nextTok = tok.nextToken();
            switch (state) {
                case inQuote:
                    if ("\'".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                case inDoubleQuote:
                    if ("\"".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                default:
                    if ("\'".equals(nextTok)) {
                        state = inQuote;
                    } else if ("\"".equals(nextTok)) {
                        state = inDoubleQuote;
                    } else if (" ".equals(nextTok)) {
                        if (lastTokenHasBeenQuoted || current.length() != 0) {
                            list.add(current.toString());
                            current = new StringBuilder();
                        }
                    } else {
                        current.append(nextTok);
                    }
                    lastTokenHasBeenQuoted = false;
                    break;
            }
        }

        if (lastTokenHasBeenQuoted || current.length() != 0) {
            list.add(current.toString());
        }

        if (state == inQuote || state == inDoubleQuote) {
            throw new IllegalArgumentException("Unbalanced quotes in " + toProcess);
        }

        final String[] args = new String[list.size()];
        return list.toArray(args);
    }

    final Tracer tracer = new Tracer();
    final CommandSpec commandSpec;
    //TODO:private scope
    public final Interpreter interpreter;
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
        this(command, new Factory());
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
        interpreter = new Interpreter(this, tracer);
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
        subcommandLine.getCommandSpec().aliases().addAll(Arrays.asList(aliases));
        getCommandSpec().addSubcommand(name, subcommandLine);
        CommandReflection.initParentCommand(subcommandLine.getCommandSpec().userObject(),
                getCommandSpec().userObject());
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
        CommandLine cli = toCommandLine(command, new Factory());
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
        CommandLine cli = toCommandLine(spec, new Factory());
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
     *            The command line arguments to parse.
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
     * @param args
     *            The raw command line arguments to parse.
     * @see #parse(String[])
     */
    public List<CommandLine> parseString(String args) {
        return parse(translateCommandline(args));
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
     *            The command line arguments to parse.
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

    /**
     * @param args
     *            The raw command line arguments to parse.
     * @see #parseArgs(String[])
     */
    public ParseResult parseArgsString(String args) {
        return parseArgs(translateCommandline(args));
    }

    public ParseResult getParseResult() {
        return interpreter.parseResult == null ? null : interpreter.parseResult.build();
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

    //TODO:package scope
    public static List<Object> execute(CommandLine parsed, List<Object> executionResult) {
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
        toCommandLine(command, new Factory()).usage(out);
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
        toCommandLine(command, new Factory()).usage(out, ansi);
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
        toCommandLine(command, new Factory()).usage(out, colorScheme);
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
     * value. The separator may also be set declaratively with the {@link Command#separator()}
     * annotation attribute.
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

    //TODO:private scope
    public static CommandLine toCommandLine(Object obj, IFactory factory) {
        return obj instanceof CommandLine ? (CommandLine) obj : new CommandLine(obj, factory);
    }

    /**
     * Returns a default {@link IFactory} implementation. Package-protected for testing purposes.
     */
    public static IFactory defaultFactory() {
        return new Factory();
    }
}
