package picocli.model;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import picocli.CommandLine;
import picocli.annots.Command;
import picocli.annots.Mixin;
import picocli.annots.Option;
import picocli.annots.ParentCommand;
import picocli.excepts.DuplicateOptionAnnotationsException;
import picocli.excepts.InitializationException;
import picocli.help.Help;
import picocli.help.HelpCommand;
import picocli.util.Assert;
import picocli.util.Tracer;

public class CommandReflection {
    static CommandSpec extractCommandSpec(Object command, IFactory factory,
            boolean annotationsAreMandatory) {
        Class<?> cls = command.getClass();
        Tracer t = new Tracer();
        t.debug("Creating CommandSpec for object of class %s with factory %s%n", cls.getName(),
                factory.getClass().getName());
        if (command instanceof CommandSpec) {
            return (CommandSpec) command;
        }
        Object instance = command;
        String commandClassName = cls.getName();
        if (command instanceof Class) {
            cls = (Class<?>) command;
            commandClassName = cls.getName();
            try {
                t.debug("Getting a %s instance from the factory%n", cls.getName());
                instance = Factory.create(factory, cls);
                cls = instance.getClass();
                commandClassName = cls.getName();
                t.debug("Factory returned a %s instance%n", commandClassName);
            } catch (InitializationException ex) {
                if (cls.isInterface()) {
                    t.debug("%s. Creating Proxy for interface %s%n", ex.getCause(), cls.getName());
                    instance = Proxy.newProxyInstance(cls.getClassLoader(), new Class<?>[] { cls },
                            new PicocliInvocationHandler());
                } else {
                    throw ex;
                }
            }
        } else if (command instanceof Method) {
            cls = null; // don't mix in options/positional params from outer class @Command
        }

        CommandSpec result = CommandSpec.wrapWithoutInspection(Assert.notNull(instance, "command"));

        Stack<Class<?>> hierarchy = new Stack<Class<?>>();
        while (cls != null) {
            hierarchy.add(cls);
            cls = cls.getSuperclass();
        }
        boolean hasCommandAnnotation = false;
        boolean mixinStandardHelpOptions = false;
        while (!hierarchy.isEmpty()) {
            cls = hierarchy.pop();
            boolean thisCommandHasAnnotation = updateCommandAttributes(cls, result, factory);
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
                    throw new InitializationException(
                            Help.class.getName() + " is not a valid subcommand. Did you mean "
                                    + HelpCommand.class.getName() + "?");
                }
                CommandLine subcommandLine = CommandLine.toCommandLine(factory.create(sub),
                        factory);
                parent.addSubcommand(subcommandName(sub), subcommandLine);
                initParentCommand(subcommandLine.getCommandSpec().userObject(),
                        parent.userObject());
            } catch (InitializationException ex) {
                throw ex;
            } catch (NoSuchMethodException ex) {
                throw new InitializationException("Cannot instantiate subcommand " + sub.getName()
                        + ": the class has no constructor", ex);
            } catch (Exception ex) {
                throw new InitializationException(
                        "Could not instantiate and add subcommand " + sub.getName() + ": " + ex,
                        ex);
            }
        }
        if (cmd.addMethodSubcommands() && !(parent.userObject() instanceof Method)) {
            parent.addMethodSubcommands(factory);
        }
    }

    //TODO:internal scope
    public static void initParentCommand(Object subcommand, Object parent) {
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
            throw new InitializationException("Unable to initialize @ParentCommand field: " + ex,
                    ex);
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

    private static boolean initFromAnnotatedFields(Object scope, Class<?> cls, CommandSpec receiver,
            IFactory factory) {
        boolean result = false;
        for (Field field : cls.getDeclaredFields()) {
            result |= initFromAnnotatedTypedMembers(TypedMember.createIfAnnotated(field, scope),
                    receiver, factory);
        }
        for (Method method : cls.getDeclaredMethods()) {
            result |= initFromAnnotatedTypedMembers(TypedMember.createIfAnnotated(method, scope),
                    receiver, factory);
        }
        return result;
    }

    private static boolean initFromAnnotatedTypedMembers(TypedMember member, CommandSpec receiver,
            IFactory factory) {
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
            Messages msg = receiver.usageMessage().messages();
            if (member.isOption()) {
                receiver.addOption(ArgsReflection.extractOptionSpec(member, factory));
            } else if (member.isParameter()) {
                receiver.addPositional(ArgsReflection.extractPositionalParamSpec(member, factory));
            } else {
                receiver.addPositional(
                        ArgsReflection.extractUnannotatedPositionalParamSpec(member, factory));
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
            if (param.isAnnotationPresent(Option.class) || param.isAnnotationPresent(Mixin.class)) {
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
                    "A member cannot be both a @Mixin command and an @Unmatched but '" + member
                            + "' is both.");
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
                    "A member can be either @Option or @Parameters, but '" + member + "' is both.");
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
            throw new InitializationException("Constant (final) primitive and String fields like "
                    + field + " cannot be used as "
                    + (member.isOption() ? "an @Option" : "a @Parameter")
                    + ": compile-time constant inlining may hide new values written to it.");
        }
    }

    private static void validateCommandSpec(CommandSpec result, boolean hasCommandAnnotation,
            String commandClassName) {
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
                    "A member cannot have both @Spec and @Unmatched annotations, but '" + member
                            + "' has both.");
        }
        if (member.isInjectSpec() && member.isMixin()) {
            throw new DuplicateOptionAnnotationsException(
                    "A member cannot have both @Spec and @Mixin annotations, but '" + member
                            + "' has both.");
        }
        if (member.getType() != CommandSpec.class) {
            throw new InitializationException(
                    "@Spec annotation is only supported on fields of type "
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
                    ArgsReflection.abbreviate("mixin from member " + member.toGenericString()));
        } catch (InitializationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InitializationException(
                    "Could not access or modify mixin member " + member + ": " + ex, ex);
        }
    }

    private static UnmatchedArgsBinding buildUnmatchedForField(final TypedMember member) {
        if (!(member.getType().equals(String[].class)
                || (List.class.isAssignableFrom(member.getType())
                        && member.getGenericType() instanceof ParameterizedType
                        && ((ParameterizedType) member.getGenericType()).getActualTypeArguments()[0]
                                .equals(String.class)))) {
            throw new InitializationException(
                    "Invalid type for " + member + ": must be either String[] or List<String>");
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
