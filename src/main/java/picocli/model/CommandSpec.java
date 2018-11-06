package picocli.model;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import picocli.CommandLine;
import picocli.CommandLine.Factory;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.NoDefaultProvider;
import picocli.CommandLine.NoVersionProvider;
import picocli.CommandLine.PositionalParametersSorter;
import picocli.Tracer;
import picocli.annots.Command;
import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.excepts.DuplicateOptionAnnotationsException;
import picocli.excepts.ExecutionException;
import picocli.excepts.InitializationException;
import picocli.excepts.ParameterIndexGapException;
import picocli.help.AutoHelpMixin;
import picocli.util.ClassUtilsExt;

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
public class CommandSpec {
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
    
    /** Constant String holding the default program name: {@code "<main class>" }. */
    public static final String DEFAULT_COMMAND_NAME = "<main class>";

    /**
     * Constant Boolean holding the default setting for whether this is a help command:
     * <code>{@value}</code>.
     */
    static final Boolean DEFAULT_IS_HELP_COMMAND = Boolean.FALSE;

    //TODO:private scope
    public final Map<String, CommandLine> commands = new LinkedHashMap<String, CommandLine>();
    //TODO:private scope
    final Map<String, OptionSpec> optionsByNameMap = new LinkedHashMap<String, OptionSpec>();
    private final Map<Character, OptionSpec> posixOptionsByKeyMap = new LinkedHashMap<Character, OptionSpec>();
    private final Map<String, CommandSpec> mixins = new LinkedHashMap<String, CommandSpec>();
    private final List<ArgSpec> requiredArgs = new ArrayList<ArgSpec>();
    private final List<ArgSpec> args = new ArrayList<ArgSpec>();
    private final List<OptionSpec> options = new ArrayList<OptionSpec>();
    //TODO:private scope
    final List<PositionalParamSpec> positionalParameters = new ArrayList<PositionalParamSpec>();
    final List<UnmatchedArgsBinding> unmatchedArgs = new ArrayList<UnmatchedArgsBinding>();
    final ParserSpec parser = new ParserSpec();
    private final UsageMessageSpec usageMessage = new UsageMessageSpec();

    //TODO:internal scope
    public final Object userObject;
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
        return forAnnotatedObject(userObject, new Factory());
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
        return forAnnotatedObjectLenient(userObject, new Factory());
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
    //TODO:internal scope
    public void validate() {
        Collections.sort(positionalParameters, new PositionalParametersSorter());
        validatePositionalParameters(positionalParameters);
        List<String> wrongUsageHelpAttr = new ArrayList<String>();
        List<String> wrongVersionHelpAttr = new ArrayList<String>();
        List<String> usageHelpAttr = new ArrayList<String>();
        List<String> versionHelpAttr = new ArrayList<String>();
        for (OptionSpec option : options()) {
            if (option.usageHelp()) {
                usageHelpAttr.add(option.longestName());
                if (!ClassUtilsExt.isBoolean(option.type())) {
                    wrongUsageHelpAttr.add(option.longestName());
                }
            }
            if (option.versionHelp()) {
                versionHelpAttr.add(option.longestName());
                if (!ClassUtilsExt.isBoolean(option.type())) {
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
    //TODO:internal scope
    public CommandSpec commandLine(CommandLine commandLine) {
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

    void updateArgSpecMessages() {
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
        return addMethodSubcommands(new Factory());
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
        for (Method method : CommandLine.getCommandMethods(userObject().getClass(), null)) {
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
    //TODO: keep in mind that defensive copy was removed (check ALL the references to ensure they don't alter this one). 
    public Set<String> aliases() {
        return aliases;
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

    //TODO:internal scope
    public Object[] argValues() {
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
                    new Factory());
            addMixin(AutoHelpMixin.KEY, mixin);
        } else {
            CommandSpec helpMixin = mixins.remove(AutoHelpMixin.KEY);
            if (helpMixin != null) {
                options.removeAll(helpMixin.options);
                for (OptionSpec option : helpMixin.options()) {
                    for (String name : option.names()) {
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
        if (Model.initializable(name, value, DEFAULT_COMMAND_NAME)) {
            name = value;
        }
    }

    void initHelpCommand(boolean value) {
        if (Model.initializable(isHelpCommand, value, DEFAULT_IS_HELP_COMMAND)) {
            isHelpCommand = value;
        }
    }

    void initVersion(String[] value) {
        if (Model.initializable(version, value, UsageMessageSpec.DEFAULT_MULTI_LINE)) {
            version = value.clone();
        }
    }

    void initVersionProvider(IVersionProvider value) {
        if (versionProvider == null) {
            versionProvider = value;
        }
    }

    void initVersionProvider(Class<? extends IVersionProvider> value, IFactory factory) {
        if (Model.initializable(versionProvider, value, NoVersionProvider.class)) {
            versionProvider = (Model.createVersionProvider(factory, value));
        }
    }

    void initDefaultValueProvider(IDefaultValueProvider value) {
        if (defaultValueProvider == null) {
            defaultValueProvider = value;
        }
    }

    void initDefaultValueProvider(Class<? extends IDefaultValueProvider> value,
            IFactory factory) {
        if (Model.initializable(defaultValueProvider, value, NoDefaultProvider.class)) {
            defaultValueProvider = (Model.createDefaultValueProvider(factory,
                    value));
        }
    }

    void updateName(String value) {
        if (Model.isNonDefault(value, DEFAULT_COMMAND_NAME)) {
            name = value;
        }
    }

    void updateHelpCommand(boolean value) {
        if (Model.isNonDefault(value, DEFAULT_IS_HELP_COMMAND)) {
            isHelpCommand = value;
        }
    }

    void updateVersion(String[] value) {
        if (Model.isNonDefault(value, UsageMessageSpec.DEFAULT_MULTI_LINE)) {
            version = value.clone();
        }
    }

    void updateVersionProvider(Class<? extends IVersionProvider> value, IFactory factory) {
        if (Model.isNonDefault(value, NoVersionProvider.class)) {
            versionProvider = (Model.createVersionProvider(factory, value));
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
