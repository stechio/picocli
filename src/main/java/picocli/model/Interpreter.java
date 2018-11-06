package picocli.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.StreamTokenizer;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import picocli.CommandLine;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.PositionalParametersSorter;
import picocli.excepts.InitializationException;
import picocli.excepts.MaxValuesExceededException;
import picocli.excepts.MissingParameterException;
import picocli.excepts.MissingTypeConverterException;
import picocli.excepts.OverwrittenOptionException;
import picocli.excepts.ParameterException;
import picocli.excepts.PicocliException;
import picocli.excepts.TypeConversionException;
import picocli.excepts.UnmatchedArgumentException;
import picocli.util.Assert;
import picocli.util.ClassUtilsExt;
import picocli.util.CollectionUtilsExt;
import picocli.util.Tracer;
import picocli.util.Utils;

/**
 * Helper class responsible for processing command line arguments.
 */
public class Interpreter {
    enum LookBehind {
        SEPARATE, ATTACHED, ATTACHED_WITH_SEPARATOR;
        public boolean isAttached() {
            return this != LookBehind.SEPARATE;
        }
    }
    
    /**
     * Inner class to group the built-in {@link ITypeConverter} implementations.
     */
    private static class BuiltIn {
        static class StringConverter implements ITypeConverter<String> {
            public String convert(String value) {
                return value;
            }
        }

        static class StringBuilderConverter implements ITypeConverter<StringBuilder> {
            public StringBuilder convert(String value) {
                return new StringBuilder(value);
            }
        }

        static class CharSequenceConverter implements ITypeConverter<CharSequence> {
            public String convert(String value) {
                return value;
            }
        }

        /**
         * Converts {@code "true"} or {@code "false"} to a {@code Boolean}. Other values result in a
         * ParameterException.
         */
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

        private static TypeConversionException fail(String value, Class<?> c) {
            return fail(value, c, "'%s' is not a %s");
        }

        private static TypeConversionException fail(String value, Class<?> c, String template) {
            return new TypeConversionException(String.format(template, value, c.getSimpleName()));
        }

        /** Converts text to a {@code Byte} by delegating to {@link Byte#valueOf(String)}. */
        static class ByteConverter implements ITypeConverter<Byte> {
            public Byte convert(String value) {
                try {
                    return Byte.valueOf(value);
                } catch (Exception ex) {
                    throw fail(value, Byte.TYPE);
                }
            }
        }

        /** Converts text to a {@code Short} by delegating to {@link Short#valueOf(String)}. */
        static class ShortConverter implements ITypeConverter<Short> {
            public Short convert(String value) {
                try {
                    return Short.valueOf(value);
                } catch (Exception ex) {
                    throw fail(value, Short.TYPE);
                }
            }
        }

        /** Converts text to an {@code Integer} by delegating to {@link Integer#valueOf(String)}. */
        static class IntegerConverter implements ITypeConverter<Integer> {
            public Integer convert(String value) {
                try {
                    return Integer.valueOf(value);
                } catch (Exception ex) {
                    throw fail(value, Integer.TYPE, "'%s' is not an %s");
                }
            }
        }

        /** Converts text to a {@code Long} by delegating to {@link Long#valueOf(String)}. */
        static class LongConverter implements ITypeConverter<Long> {
            public Long convert(String value) {
                try {
                    return Long.valueOf(value);
                } catch (Exception ex) {
                    throw fail(value, Long.TYPE);
                }
            }
        }

        static class FloatConverter implements ITypeConverter<Float> {
            public Float convert(String value) {
                try {
                    return Float.valueOf(value);
                } catch (Exception ex) {
                    throw fail(value, Float.TYPE);
                }
            }
        }

        static class DoubleConverter implements ITypeConverter<Double> {
            public Double convert(String value) {
                try {
                    return Double.valueOf(value);
                } catch (Exception ex) {
                    throw fail(value, Double.TYPE);
                }
            }
        }

        static class FileConverter implements ITypeConverter<File> {
            public File convert(String value) {
                return new File(value);
            }
        }

        static class URLConverter implements ITypeConverter<URL> {
            public URL convert(String value) throws MalformedURLException {
                return new URL(value);
            }
        }

        static class URIConverter implements ITypeConverter<URI> {
            public URI convert(String value) throws URISyntaxException {
                return new URI(value);
            }
        }

        /**
         * Converts text in {@code yyyy-mm-dd} format to a {@code java.util.Date}.
         * ParameterException on failure.
         */
        static class ISO8601DateConverter implements ITypeConverter<Date> {
            public Date convert(String value) {
                try {
                    return new SimpleDateFormat("yyyy-MM-dd").parse(value);
                } catch (ParseException e) {
                    throw new TypeConversionException("'" + value + "' is not a yyyy-MM-dd date");
                }
            }
        }

        /**
         * Converts text in any of the following formats to a {@code java.sql.Time}: {@code HH:mm},
         * {@code HH:mm:ss}, {@code HH:mm:ss.SSS}, {@code HH:mm:ss,SSS}. Other formats result in a
         * ParameterException.
         */
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
                            return createTime(
                                    new SimpleDateFormat("HH:mm:ss.SSS").parse(value).getTime());
                        } catch (ParseException e2) {
                            return createTime(
                                    new SimpleDateFormat("HH:mm:ss,SSS").parse(value).getTime());
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
                    throw new TypeConversionException(
                            "Unable to create new java.sql.Time with long value " + epochMillis
                                    + ": " + e.getMessage());
                }
            }

            public static void registerIfAvailable(Map<Class<?>, ITypeConverter<?>> registry,
                    Tracer tracer) {
                if (excluded(FQCN, tracer)) {
                    return;
                }
                try {
                    registry.put(Class.forName(FQCN), new ISO8601TimeConverter());
                } catch (Exception e) {
                    if (!traced.contains(FQCN)) {
                        tracer.debug("Could not register converter for %s: %s%n", FQCN,
                                e.toString());
                    }
                    traced.add(FQCN);
                }
            }
        }

        static class BigDecimalConverter implements ITypeConverter<BigDecimal> {
            public BigDecimal convert(String value) {
                return new BigDecimal(value);
            }
        }

        static class BigIntegerConverter implements ITypeConverter<BigInteger> {
            public BigInteger convert(String value) {
                return new BigInteger(value);
            }
        }

        static class CharsetConverter implements ITypeConverter<Charset> {
            public Charset convert(String s) {
                return Charset.forName(s);
            }
        }

        /**
         * Converts text to a {@code InetAddress} by delegating to
         * {@link InetAddress#getByName(String)}.
         */
        static class InetAddressConverter implements ITypeConverter<InetAddress> {
            public InetAddress convert(String s) throws Exception {
                return InetAddress.getByName(s);
            }
        }

        static class PatternConverter implements ITypeConverter<Pattern> {
            public Pattern convert(String s) {
                return Pattern.compile(s);
            }
        }

        static class UUIDConverter implements ITypeConverter<UUID> {
            public UUID convert(String s) throws Exception {
                return UUID.fromString(s);
            }
        }

        static class CurrencyConverter implements ITypeConverter<Currency> {
            public Currency convert(String s) throws Exception {
                return Currency.getInstance(s);
            }
        }

        static class TimeZoneConverter implements ITypeConverter<TimeZone> {
            public TimeZone convert(String s) throws Exception {
                return TimeZone.getTimeZone(s);
            }
        }

        static class ByteOrderConverter implements ITypeConverter<ByteOrder> {
            public ByteOrder convert(String s) throws Exception {
                if (s.equalsIgnoreCase(ByteOrder.BIG_ENDIAN.toString())) {
                    return ByteOrder.BIG_ENDIAN;
                }
                if (s.equalsIgnoreCase(ByteOrder.LITTLE_ENDIAN.toString())) {
                    return ByteOrder.LITTLE_ENDIAN;
                }
                throw new TypeConversionException("'" + s + "' is not a valid ByteOrder");
            }
        }

        static class ClassConverter implements ITypeConverter<Class<?>> {
            public Class<?> convert(String s) throws Exception {
                return Class.forName(s);
            }
        }

        static class NetworkInterfaceConverter implements ITypeConverter<NetworkInterface> {
            public NetworkInterface convert(String s) throws Exception {
                try {
                    InetAddress addr = new InetAddressConverter().convert(s);
                    return NetworkInterface.getByInetAddress(addr);
                } catch (Exception ex) {
                    try {
                        return NetworkInterface.getByName(s);
                    } catch (Exception ex2) {
                        throw new TypeConversionException(
                                "'" + s + "' is not an InetAddress or NetworkInterface name");
                    }
                }
            }
        }

        static void registerIfAvailable(Map<Class<?>, ITypeConverter<?>> registry, Tracer tracer,
                String fqcn, String factoryMethodName, Class<?>... paramTypes) {
            registerIfAvailable(registry, tracer, fqcn, fqcn, factoryMethodName, paramTypes);
        }

        static void registerIfAvailable(Map<Class<?>, ITypeConverter<?>> registry, Tracer tracer,
                String fqcn, String factoryClass, String factoryMethodName,
                Class<?>... paramTypes) {
            if (excluded(fqcn, tracer)) {
                return;
            }
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
                    tracer.debug(
                            "BuiltIn type converter for %s is not loaded: (picocli.converters.excludes=%s)%n",
                            fqcn, System.getProperty("picocli.converters.excludes"));
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
                    throw new TypeConversionException(
                            String.format("cannot convert '%s' to %s (%s)", s,
                                    method.getReturnType(), e.getTargetException()));
                } catch (Exception e) {
                    throw new TypeConversionException(String.format(
                            "cannot convert '%s' to %s (%s)", s, method.getReturnType(), e));
                }
            }
        }

        private BuiltIn() {
        } // private constructor: never instantiate
    }

    private CommandLine commandLine;
    //TODO:private scope
    public final Map<Class<?>, ITypeConverter<?>> converterRegistry = new HashMap<Class<?>, ITypeConverter<?>>();
    private boolean isHelpRequested;
    private int position;
    private boolean endOfOptions;
    //TODO:private scope
    public ParseResult.Builder parseResult;
    Tracer tracer;

    public Interpreter(CommandLine commandLine, Tracer tracer) {
        this.commandLine = commandLine;
        this.tracer = tracer;
        registerBuiltInConverters();
    }

    private void registerBuiltInConverters() {
        converterRegistry.put(Object.class, new BuiltIn.StringConverter());
        converterRegistry.put(String.class, new BuiltIn.StringConverter());
        converterRegistry.put(StringBuilder.class, new BuiltIn.StringBuilderConverter());
        converterRegistry.put(CharSequence.class, new BuiltIn.CharSequenceConverter());
        converterRegistry.put(Byte.class, new BuiltIn.ByteConverter());
        converterRegistry.put(Byte.TYPE, new BuiltIn.ByteConverter());
        converterRegistry.put(Boolean.class, new BuiltIn.BooleanConverter());
        converterRegistry.put(Boolean.TYPE, new BuiltIn.BooleanConverter());
        converterRegistry.put(Character.class, new BuiltIn.CharacterConverter());
        converterRegistry.put(Character.TYPE, new BuiltIn.CharacterConverter());
        converterRegistry.put(Short.class, new BuiltIn.ShortConverter());
        converterRegistry.put(Short.TYPE, new BuiltIn.ShortConverter());
        converterRegistry.put(Integer.class, new BuiltIn.IntegerConverter());
        converterRegistry.put(Integer.TYPE, new BuiltIn.IntegerConverter());
        converterRegistry.put(Long.class, new BuiltIn.LongConverter());
        converterRegistry.put(Long.TYPE, new BuiltIn.LongConverter());
        converterRegistry.put(Float.class, new BuiltIn.FloatConverter());
        converterRegistry.put(Float.TYPE, new BuiltIn.FloatConverter());
        converterRegistry.put(Double.class, new BuiltIn.DoubleConverter());
        converterRegistry.put(Double.TYPE, new BuiltIn.DoubleConverter());
        converterRegistry.put(File.class, new BuiltIn.FileConverter());
        converterRegistry.put(URI.class, new BuiltIn.URIConverter());
        converterRegistry.put(URL.class, new BuiltIn.URLConverter());
        converterRegistry.put(Date.class, new BuiltIn.ISO8601DateConverter());
        converterRegistry.put(BigDecimal.class, new BuiltIn.BigDecimalConverter());
        converterRegistry.put(BigInteger.class, new BuiltIn.BigIntegerConverter());
        converterRegistry.put(Charset.class, new BuiltIn.CharsetConverter());
        converterRegistry.put(InetAddress.class, new BuiltIn.InetAddressConverter());
        converterRegistry.put(Pattern.class, new BuiltIn.PatternConverter());
        converterRegistry.put(UUID.class, new BuiltIn.UUIDConverter());
        converterRegistry.put(Currency.class, new BuiltIn.CurrencyConverter());
        converterRegistry.put(TimeZone.class, new BuiltIn.TimeZoneConverter());
        converterRegistry.put(ByteOrder.class, new BuiltIn.ByteOrderConverter());
        converterRegistry.put(Class.class, new BuiltIn.ClassConverter());
        converterRegistry.put(NetworkInterface.class, new BuiltIn.NetworkInterfaceConverter());

        BuiltIn.ISO8601TimeConverter.registerIfAvailable(converterRegistry, tracer);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.sql.Connection",
                "java.sql.DriverManager", "getConnection", String.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.sql.Driver",
                "java.sql.DriverManager", "getDriver", String.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.sql.Timestamp",
                "java.sql.Timestamp", "valueOf", String.class);

        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.Duration",
                "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.Instant",
                "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.LocalDate",
                "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer,
                "java.time.LocalDateTime", "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.LocalTime",
                "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.MonthDay",
                "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer,
                "java.time.OffsetDateTime", "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.OffsetTime",
                "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.Period",
                "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.Year",
                "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.YearMonth",
                "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer,
                "java.time.ZonedDateTime", "parse", CharSequence.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.ZoneId", "of",
                String.class);
        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.time.ZoneOffset",
                "of", String.class);

        BuiltIn.registerIfAvailable(converterRegistry, tracer, "java.nio.file.Path",
                "java.nio.file.Paths", "get", String.class, String[].class);
    }

    private ParserSpec config() {
        return commandLine.getCommandSpec().parser();
    }

    /**
     * Entry point into parsing command line arguments.
     * 
     * @param args
     *            the command line arguments
     * @return a list with all commands and subcommands initialized by this method
     * @throws ParameterException
     *             if the specified command line arguments are invalid
     */
    public List<CommandLine> parse(String... args) {
        Assert.notNull(args, "argument array");
        if (tracer.isInfo()) {
            tracer.info("Parsing %d command line args %s%n", args.length,
                    Arrays.toString(args));
        }
        if (tracer.isDebug()) {
            tracer.debug("Parser configuration: %s%n", config());
        }
        List<String> expanded = new ArrayList<String>();
        for (String arg : args) {
            addOrExpand(arg, expanded, new LinkedHashSet<String>());
        }
        Stack<String> arguments = new Stack<String>();
        arguments.addAll(CollectionUtilsExt.reverse(expanded));
        List<CommandLine> result = new ArrayList<CommandLine>();
        parse(result, arguments, args, new ArrayList<Object>());
        return result;
    }

    private void addOrExpand(String arg, List<String> arguments, Set<String> visited) {
        if (config().expandAtFiles() && !arg.equals("@") && arg.startsWith("@")) {
            arg = arg.substring(1);
            if (arg.startsWith("@")) {
                if (tracer.isInfo()) {
                    tracer.info(
                            "Not expanding @-escaped argument %s (trimmed leading '@' char)%n",
                            arg);
                }
            } else {
                if (tracer.isInfo()) {
                    tracer.info("Expanding argument file @%s%n", arg);
                }
                expandArgumentFile(arg, arguments, visited);
                return;
            }
        }
        arguments.add(arg);
    }

    private void expandArgumentFile(String fileName, List<String> arguments, Set<String> visited) {
        File file = new File(fileName);
        if (!file.canRead()) {
            if (tracer.isInfo()) {
                tracer.info(
                        "File %s does not exist or cannot be read; treating argument literally%n",
                        fileName);
            }
            arguments.add("@" + fileName);
        } else if (visited.contains(file.getAbsolutePath())) {
            if (tracer.isInfo()) {
                tracer.info("Already visited file %s; ignoring...%n",
                        file.getAbsolutePath());
            }
        } else {
            expandValidArgumentFile(fileName, file, arguments, visited);
        }
    }

    private void expandValidArgumentFile(String fileName, File file, List<String> arguments,
            Set<String> visited) {
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
            if (commandLine.getCommandSpec().parser().atFileCommentChar() != null) {
                tok.commentChar(commandLine.getCommandSpec().parser().atFileCommentChar());
            }
            while (tok.nextToken() != StreamTokenizer.TT_EOF) {
                addOrExpand(tok.sval, result, visited);
            }
        } catch (Exception ex) {
            throw new InitializationException("Could not read argument file @" + fileName, ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
        if (tracer.isInfo()) {
            tracer.info("Expanded file @%s to arguments %s%n", fileName, result);
        }
        arguments.addAll(result);
    }

    private void clear() {
        position = 0;
        endOfOptions = false;
        isHelpRequested = false;
        parseResult = ParseResult.builder(commandLine.getCommandSpec());
        for (OptionSpec option : commandLine.getCommandSpec().options()) {
            clear(option);
        }
        for (PositionalParamSpec positional : commandLine.getCommandSpec()
                .positionalParameters()) {
            clear(positional);
        }
    }

    private void clear(ArgSpec argSpec) {
        argSpec.resetStringValues();
        argSpec.resetOriginalStringValues();
        argSpec.typedValues.clear();
        argSpec.typedValueAtPosition.clear();
        if (argSpec.hasInitialValue()) {
            try {
                argSpec.setter().set(argSpec.initialValue());
                tracer.debug("Set initial value for %s of type %s to %s.%n", argSpec,
                        argSpec.type(), String.valueOf(argSpec.initialValue()));
            } catch (Exception ex) {
                tracer.warn("Could not set initial value for %s of type %s to %s: %s%n",
                        argSpec, argSpec.type(), String.valueOf(argSpec.initialValue()), ex);
            }
        } else {
            tracer.debug("Initial value not available for %s%n", argSpec);
        }
    }

    private void maybeThrow(PicocliException ex) throws PicocliException {
        if (commandLine.getCommandSpec().parser().collectErrors) {
            parseResult.addError(ex);
        } else {
            throw ex;
        }
    }

    private void parse(List<CommandLine> parsedCommands, Stack<String> argumentStack,
            String[] originalArgs, List<Object> nowProcessing) {
        clear(); // first reset any state in case this CommandLine instance is being reused
        if (tracer.isDebug()) {
            tracer.debug(
                    "Initializing %s: %d options, %d positional parameters, %d required, %d subcommands.%n",
                    commandLine.getCommandSpec().toString(),
                    new HashSet<ArgSpec>(commandLine.getCommandSpec().optionsMap().values())
                            .size(),
                    commandLine.getCommandSpec().positionalParameters().size(),
                    commandLine.getCommandSpec().requiredArgs().size(),
                    commandLine.getCommandSpec().subcommands().size());
        }
        parsedCommands.add(commandLine);
        List<ArgSpec> required = new ArrayList<ArgSpec>(
                commandLine.getCommandSpec().requiredArgs());
        Set<ArgSpec> initialized = new HashSet<ArgSpec>();
        Collections.sort(required, new PositionalParametersSorter());
        boolean continueOnError = commandLine.getCommandSpec().parser().collectErrors;
        do {
            int stackSize = argumentStack.size();
            try {
                applyDefaultValues(required);
                processArguments(parsedCommands, argumentStack, required, initialized, originalArgs,
                        nowProcessing);
            } catch (ParameterException ex) {
                maybeThrow(ex);
            } catch (Exception ex) {
                int offendingArgIndex = originalArgs.length - argumentStack.size() - 1;
                String arg = offendingArgIndex >= 0 && offendingArgIndex < originalArgs.length
                        ? originalArgs[offendingArgIndex]
                        : "?";
                maybeThrow(ParameterException.create(commandLine, ex, arg, offendingArgIndex,
                        originalArgs));
            }
            if (continueOnError && stackSize == argumentStack.size() && stackSize > 0) {
                parseResult.unmatched.add(argumentStack.pop());
            }
        } while (!argumentStack.isEmpty() && continueOnError);
        if (!isAnyHelpRequested() && !required.isEmpty()) {
            for (ArgSpec missing : required) {
                if (missing.isOption()) {
                    maybeThrow(MissingParameterException.create(commandLine, required,
                            config().separator()));
                } else {
                    assertNoMissingParameters(missing, missing.arity(), argumentStack);
                }
            }
        }
        if (!parseResult.unmatched.isEmpty()) {
            String[] unmatched = parseResult.unmatched.toArray(new String[0]);
            for (UnmatchedArgsBinding unmatchedArgsBinding : commandLine.getCommandSpec()
                    .unmatchedArgsBindings()) {
                unmatchedArgsBinding.addAll(unmatched.clone());
            }
            if (!commandLine.isUnmatchedArgumentsAllowed()) {
                maybeThrow(new UnmatchedArgumentException(commandLine,
                        Collections.unmodifiableList(parseResult.unmatched)));
            }
            if (tracer.isInfo()) {
                tracer.info("Unmatched arguments: %s%n", parseResult.unmatched);
            }
        }
    }

    private void applyDefaultValues(List<ArgSpec> required) throws Exception {
        parseResult.isInitializingDefaultValues = true;
        for (OptionSpec option : commandLine.getCommandSpec().options()) {
            applyDefault(commandLine.getCommandSpec().defaultValueProvider(), option, required);
        }
        for (PositionalParamSpec positional : commandLine.getCommandSpec()
                .positionalParameters()) {
            applyDefault(commandLine.getCommandSpec().defaultValueProvider(), positional, required);
        }
        parseResult.isInitializingDefaultValues = false;
    }

    private void applyDefault(IDefaultValueProvider defaultValueProvider, ArgSpec arg,
            List<ArgSpec> required) throws Exception {

        // Default value provider return value is only used if provider exists and if value
        // is not null otherwise the original default or initial value are used
        String fromProvider = defaultValueProvider == null ? null
                : defaultValueProvider.defaultValue(arg);
        String defaultValue = fromProvider == null ? arg.defaultValue() : fromProvider;

        if (defaultValue == null) {
            return;
        }
        if (tracer.isDebug()) {
            tracer.debug("Applying defaultValue (%s) to %s%n", defaultValue, arg);
        }
        Range arity = arg.arity().min(Math.max(1, arg.arity().min));

        applyOption(arg, LookBehind.SEPARATE, arity, stack(defaultValue),
                new HashSet<ArgSpec>(), arg.toString);
        required.remove(arg);
    }

    private Stack<String> stack(String value) {
        Stack<String> result = new Stack<String>();
        result.push(value);
        return result;
    }

    private void processArguments(List<CommandLine> parsedCommands, Stack<String> args,
            Collection<ArgSpec> required, Set<ArgSpec> initialized,
            String[] originalArgs, List<Object> nowProcessing) throws Exception {
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
            if (tracer.isDebug()) {
                tracer.debug("Processing argument '%s'. Remainder=%s%n", arg,
                        CollectionUtilsExt.reverse(ObjectUtils.clone(args)));
            }

            // Double-dash separates options from positional arguments.
            // If found, then interpret the remaining args as positional parameters.
            if (commandLine.getCommandSpec().parser.endOfOptionsDelimiter().equals(arg)) {
                tracer.info(
                        "Found end-of-options delimiter '--'. Treating remainder as positional parameters.%n");
                endOfOptions = true;
                processRemainderAsPositionalParameters(required, initialized, args);
                return; // we are done
            }

            // if we find another command, we are done with the current command
            if (commandLine.getCommandSpec().subcommands().containsKey(arg)) {
                CommandLine subcommand = commandLine.getCommandSpec().subcommands().get(arg);
                nowProcessing.add(subcommand.getCommandSpec());
                updateHelpRequested(subcommand.getCommandSpec());
                if (!isAnyHelpRequested() && !required.isEmpty()) { // ensure current command portion is valid
                    throw MissingParameterException.create(commandLine, required, separator);
                }
                if (tracer.isDebug()) {
                    tracer.debug("Found subcommand '%s' (%s)%n", arg,
                            subcommand.getCommandSpec().toString());
                }
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
                if (commandLine.getCommandSpec().optionsMap().containsKey(key)
                        && !commandLine.getCommandSpec().optionsMap().containsKey(arg)) {
                    paramAttachedToOption = true;
                    String optionParam = arg.substring(separatorIndex + separator.length());
                    args.push(optionParam);
                    arg = key;
                    if (tracer.isDebug()) {
                        tracer.debug(
                                "Separated '%s' option from '%s' option parameter%n", key,
                                optionParam);
                    }
                } else {
                    if (tracer.isDebug()) {
                        tracer.debug(
                                "'%s' contains separator '%s' but '%s' is not a known option%n",
                                arg, separator, key);
                    }
                }
            } else {
                if (tracer.isDebug()) {
                    tracer.debug(
                            "'%s' cannot be separated into <option>%s<option-parameter>%n", arg,
                            separator);
                }
            }
            if (isStandaloneOption(arg)) {
                processStandaloneOption(required, initialized, arg, args, paramAttachedToOption);
            }
            // Compact (single-letter) options can be grouped with other options or with an argument.
            // only single-letter options can be combined with other options or with an argument
            else if (config().posixClusteredShortOptionsAllowed() && arg.length() > 2
                    && arg.startsWith("-")) {
                if (tracer.isDebug()) {
                    tracer.debug("Trying to process '%s' as clustered short options%n",
                            arg, args);
                }
                processClusteredShortOptions(required, initialized, arg, args);
            }
            // The argument could not be interpreted as an option: process it as a positional argument
            else {
                args.push(arg);
                if (tracer.isDebug()) {
                    tracer.debug(
                            "Could not find option '%s', deciding whether to treat as unmatched option or positional parameter...%n",
                            arg);
                }
                if (commandLine.getCommandSpec().resemblesOption(arg, tracer)) {
                    handleUnmatchedArgument(args);
                    continue;
                } // #149
                if (tracer.isDebug()) {
                    tracer.debug(
                            "No option named '%s' found. Processing remainder as positional parameters%n",
                            arg);
                }
                processPositionalParameter(required, initialized, args);
            }
        }
    }

    private boolean isStandaloneOption(String arg) {
        return commandLine.getCommandSpec().optionsMap().containsKey(arg);
    }

    private void handleUnmatchedArgument(Stack<String> args) throws Exception {
        if (!args.isEmpty()) {
            handleUnmatchedArgument(args.pop());
        }
        if (config().stopAtUnmatched()) {
            // addAll would give args in reverse order
            while (!args.isEmpty()) {
                handleUnmatchedArgument(args.pop());
            }
        }
    }

    private void handleUnmatchedArgument(String arg) {
        parseResult.unmatched.add(arg);
    }

    private void processRemainderAsPositionalParameters(Collection<ArgSpec> required,
            Set<ArgSpec> initialized, Stack<String> args) throws Exception {
        while (!args.empty()) {
            processPositionalParameter(required, initialized, args);
        }
    }

    private void processPositionalParameter(Collection<ArgSpec> required,
            Set<ArgSpec> initialized, Stack<String> args) throws Exception {
        if (tracer.isDebug()) {
            tracer.debug(
                    "Processing next arg as a positional parameter at index=%d. Remainder=%s%n",
                    position, CollectionUtilsExt.reverse(ObjectUtils.clone(args)));
        }
        if (config().stopAtPositional()) {
            if (!endOfOptions && tracer.isDebug()) {
                tracer.debug(
                        "Parser was configured with stopAtPositional=true, treating remaining arguments as positional parameters.%n");
            }
            endOfOptions = true;
        }
        int argsConsumed = 0;
        int interactiveConsumed = 0;
        int originalNowProcessingSize = parseResult.nowProcessing.size();
        for (PositionalParamSpec positionalParam : commandLine.getCommandSpec()
                .positionalParameters()) {
            Range indexRange = positionalParam.index();
            if (!indexRange.contains(position)
                    || positionalParam.typedValueAtPosition.get(position) != null) {
                continue;
            }
            Stack<String> argsCopy = ObjectUtils.clone(args);
            Range arity = positionalParam.arity();
            if (tracer.isDebug()) {
                tracer.debug(
                        "Position %d is in index range %s. Trying to assign args to %s, arity=%s%n",
                        position, indexRange, positionalParam, arity);
            }
            if (!assertNoMissingParameters(positionalParam, arity, argsCopy)) {
                break;
            } // #389 collectErrors parsing
            int originalSize = argsCopy.size();
            int actuallyConsumed = applyOption(positionalParam, LookBehind.SEPARATE, arity,
                    argsCopy, initialized, "args[" + indexRange + "] at position " + position);
            int count = originalSize - argsCopy.size();
            if (count > 0 || actuallyConsumed > 0) {
                required.remove(positionalParam);
                if (positionalParam.interactive()) {
                    interactiveConsumed++;
                }
            }
            argsConsumed = Math.max(argsConsumed, count);
            while (parseResult.nowProcessing.size() > originalNowProcessingSize + count) {
                parseResult.nowProcessing.remove(parseResult.nowProcessing.size() - 1);
            }
        }
        // remove processed args from the stack
        for (int i = 0; i < argsConsumed; i++) {
            args.pop();
        }
        position += argsConsumed + interactiveConsumed;
        if (tracer.isDebug()) {
            tracer.debug(
                    "Consumed %d arguments and %d interactive values, moving position to index %d.%n",
                    argsConsumed, interactiveConsumed, position);
        }
        if (argsConsumed == 0 && interactiveConsumed == 0 && !args.isEmpty()) {
            handleUnmatchedArgument(args);
        }
    }

    private void processStandaloneOption(Collection<ArgSpec> required,
            Set<ArgSpec> initialized, String arg, Stack<String> args,
            boolean paramAttachedToKey) throws Exception {
        ArgSpec argSpec = commandLine.getCommandSpec().optionsMap().get(arg);
        required.remove(argSpec);
        Range arity = argSpec.arity();
        if (paramAttachedToKey) {
            arity = arity.min(Math.max(1, arity.min)); // if key=value, minimum arity is at least 1
        }
        LookBehind lookBehind = paramAttachedToKey ? LookBehind.ATTACHED_WITH_SEPARATOR
                : LookBehind.SEPARATE;
        if (tracer.isDebug()) {
            tracer.debug("Found option named '%s': %s, arity=%s%n", arg, argSpec,
                    arity);
        }
        parseResult.nowProcessing.add(argSpec);
        applyOption(argSpec, lookBehind, arity, args, initialized, "option " + arg);
    }

    private void processClusteredShortOptions(Collection<ArgSpec> required,
            Set<ArgSpec> initialized, String arg, Stack<String> args) throws Exception {
        String prefix = arg.substring(0, 1);
        String cluster = arg.substring(1);
        boolean paramAttachedToOption = true;
        boolean first = true;
        do {
            if (cluster.length() > 0
                    && commandLine.getCommandSpec().posixOptionsMap().containsKey(cluster.charAt(0))) {
                ArgSpec argSpec = commandLine.getCommandSpec().posixOptionsMap()
                        .get(cluster.charAt(0));
                Range arity = argSpec.arity();
                String argDescription = "option " + prefix + cluster.charAt(0);
                if (tracer.isDebug()) {
                    tracer.debug("Found option '%s%s' in %s: %s, arity=%s%n", prefix,
                            cluster.charAt(0), arg, argSpec, arity);
                }
                required.remove(argSpec);
                cluster = cluster.length() > 0 ? cluster.substring(1) : "";
                paramAttachedToOption = cluster.length() > 0;
                LookBehind lookBehind = paramAttachedToOption ? LookBehind.ATTACHED
                        : LookBehind.SEPARATE;
                if (cluster.startsWith(config().separator())) {// attached with separator, like -f=FILE or -v=true
                    lookBehind = LookBehind.ATTACHED_WITH_SEPARATOR;
                    cluster = cluster.substring(config().separator().length());
                    arity = arity.min(Math.max(1, arity.min)); // if key=value, minimum arity is at least 1
                }
                if (arity.min > 0 && !StringUtils.isBlank(cluster)) {
                    if (tracer.isDebug()) {
                        tracer.debug("Trying to process '%s' as option parameter%n",
                                cluster);
                    }
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
                int consumed = applyOption(argSpec, lookBehind, arity, args, initialized,
                        argDescription);
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
                        if (tracer.isDebug()) {
                            tracer.debug(
                                    "Could not match any short options in %s, deciding whether to treat as unmatched option or positional parameter...%n",
                                    arg);
                        }
                        if (commandLine.getCommandSpec().resemblesOption(arg, tracer)) {
                            handleUnmatchedArgument(args);
                            return;
                        } // #149
                        processPositionalParameter(required, initialized, args);
                        return;
                    }
                    // remainder was part of a clustered group that could not be completely parsed
                    if (tracer.isDebug()) {
                        tracer.debug("No option found for %s in %s%n", cluster, arg);
                    }
                    handleUnmatchedArgument(args);
                } else {
                    args.push(cluster);
                    if (tracer.isDebug()) {
                        tracer.debug("%s is not an option parameter for %s%n", cluster,
                                arg);
                    }
                    processPositionalParameter(required, initialized, args);
                }
                return;
            }
        } while (true);
    }

    private int applyOption(ArgSpec argSpec, LookBehind lookBehind, Range arity,
            Stack<String> args, Set<ArgSpec> initialized, String argDescription)
            throws Exception {
        updateHelpRequested(argSpec);
        boolean consumeOnlyOne = commandLine.getCommandSpec().parser()
                .aritySatisfiedByAttachedOptionParam() && lookBehind.isAttached();
        Stack<String> workingStack = args;
        if (consumeOnlyOne) {
            workingStack = args.isEmpty() ? args : stack(args.pop());
        } else {
            if (!assertNoMissingParameters(argSpec, arity, args)) {
                return 0;
            } // #389 collectErrors parsing
        }

        if (argSpec.interactive()) {
            String name = argSpec.isOption() ? ((OptionSpec) argSpec).longestName()
                    : "position " + position;
            String prompt = String.format("Enter value for %s (%s): ", name,
                    Utils.safeGet(argSpec.renderedDescription(), 0));
            if (tracer.isDebug()) {
                tracer.debug("Reading value for %s from console...%n", name);
            }
            char[] value = readPassword(prompt, false);
            if (tracer.isDebug()) {
                tracer.debug("User entered '%s' for %s.%n", value, name);
            }
            workingStack.push(new String(value));
        }

        int result;
        if (argSpec.type().isArray()) {
            result = applyValuesToArrayField(argSpec, lookBehind, arity, workingStack, initialized,
                    argDescription);
        } else if (Collection.class.isAssignableFrom(argSpec.type())) {
            result = applyValuesToCollectionField(argSpec, lookBehind, arity, workingStack,
                    initialized, argDescription);
        } else if (Map.class.isAssignableFrom(argSpec.type())) {
            result = applyValuesToMapField(argSpec, lookBehind, arity, workingStack, initialized,
                    argDescription);
        } else {
            result = applyValueToSingleValuedField(argSpec, lookBehind, arity, workingStack,
                    initialized, argDescription);
        }
        if (workingStack != args && !workingStack.isEmpty()) {
            args.push(workingStack.pop());
            if (!workingStack.isEmpty()) {
                throw new IllegalStateException("Working stack should be empty but was "
                        + new ArrayList<String>(workingStack));
            }
        }
        return result;
    }

    private int applyValueToSingleValuedField(ArgSpec argSpec, LookBehind lookBehind,
            Range derivedArity, Stack<String> args, Set<ArgSpec> initialized,
            String argDescription) throws Exception {
        boolean noMoreValues = args.isEmpty();
        String value = args.isEmpty() ? null : trim(args.pop()); // unquote the value
        Range arity = argSpec.arity().isUnspecified ? derivedArity : argSpec.arity(); // #509
        if (arity.max == 0 && !arity.isUnspecified
                && lookBehind == LookBehind.ATTACHED_WITH_SEPARATOR) { // #509
            throw new MaxValuesExceededException(commandLine, optionDescription("", argSpec, 0)
                    + " should be specified without '" + value + "' parameter");
        }
        int result = arity.min; // the number or args we need to consume

        Class<?> cls = argSpec.auxiliaryTypes()[0]; // field may be interface/abstract type, use annotation to get concrete type
        if (arity.min <= 0) { // value may be optional

            // special logic for booleans: BooleanConverter accepts only "true" or "false".
            if (cls == Boolean.class || cls == Boolean.TYPE) {

                // boolean option with arity = 0..1 or 0..*: value MAY be a param
                if (arity.max > 0 && "true".equalsIgnoreCase(value)
                        || "false".equalsIgnoreCase(value)) {
                    result = 1; // if it is a varargs we only consume 1 argument if it is a boolean value
                    if (!lookBehind.isAttached()) {
                        parseResult.nowProcessing(argSpec, value);
                    }
                } else if (lookBehind != LookBehind.ATTACHED_WITH_SEPARATOR) { // if attached, try converting the value to boolean (and fail if invalid value)
                    // it's okay to ignore value if not attached to option
                    if (value != null) {
                        args.push(value); // we don't consume the value
                    }
                    if (commandLine.getCommandSpec().parser().toggleBooleanFlags()) {
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
                    if (!lookBehind.isAttached()) {
                        parseResult.nowProcessing(argSpec, value);
                    }
                }
            }
        } else {
            if (!lookBehind.isAttached()) {
                parseResult.nowProcessing(argSpec, value);
            }
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
                if (!commandLine.isOverwrittenOptionsAllowed()) {
                    throw new OverwrittenOptionException(commandLine, argSpec,
                            optionDescription("", argSpec, 0) + " should be specified only once");
                }
                traceMessage = "Overwriting %s value '%s' with '%s' for %s%n";
            }
            initialized.add(argSpec);
        }
        if (tracer.isInfo()) {
            tracer.info(traceMessage, argSpec.toString(), String.valueOf(oldValue),
                    String.valueOf(newValue), argDescription);
        }
        argSpec.setValue(newValue, commandLine.getCommandSpec().commandLine());
        parseResult.addOriginalStringValue(argSpec, value);// #279 track empty string value if no command line argument was consumed
        parseResult.addStringValue(argSpec, value);
        parseResult.addTypedValues(argSpec, position, newValue);
        parseResult.add(argSpec, position);
        return result;
    }

    private int applyValuesToMapField(ArgSpec argSpec, LookBehind lookBehind, Range arity,
            Stack<String> args, Set<ArgSpec> initialized, String argDescription)
            throws Exception {
        Class<?>[] classes = argSpec.auxiliaryTypes();
        if (classes.length < 2) {
            throw new ParameterException(commandLine, argSpec.toString()
                    + " needs two types (one for the map key, one for the value) but only has "
                    + classes.length + " types configured.", argSpec, null);
        }
        ITypeConverter<?> keyConverter = getTypeConverter(classes[0], argSpec, 0);
        ITypeConverter<?> valueConverter = getTypeConverter(classes[1], argSpec, 1);
        @SuppressWarnings("unchecked")
        Map<Object, Object> map = (Map<Object, Object>) argSpec.getValue();
        if (map == null || (!map.isEmpty() && !initialized.contains(argSpec))) {
            map = createMap(argSpec.type()); // map class
            argSpec.setValue(map, commandLine.getCommandSpec().commandLine());
        }
        initialized.add(argSpec);
        int originalSize = map.size();
        consumeMapArguments(argSpec, lookBehind, arity, args, classes, keyConverter, valueConverter,
                map, argDescription);
        parseResult.add(argSpec, position);
        argSpec.setValue(map, commandLine.getCommandSpec().commandLine());
        return map.size() - originalSize;
    }

    private void consumeMapArguments(ArgSpec argSpec, LookBehind lookBehind, Range arity,
            Stack<String> args, Class<?>[] classes, ITypeConverter<?> keyConverter,
            ITypeConverter<?> valueConverter, Map<Object, Object> result, String argDescription)
            throws Exception {

        // don't modify Interpreter.position: same position may be consumed by multiple ArgSpec objects
        int currentPosition = position;

        // first do the arity.min mandatory parameters
        int initialSize = argSpec.stringValues().size();
        int consumed = consumedCountMap(0, initialSize, argSpec);
        for (int i = 0; consumed < arity.min && !args.isEmpty(); i++) {
            Map<Object, Object> typedValuesAtPosition = new LinkedHashMap<Object, Object>();
            parseResult.addTypedValues(argSpec, currentPosition++, typedValuesAtPosition);
            assertNoMissingMandatoryParameter(argSpec, args, i, arity);
            consumeOneMapArgument(argSpec, lookBehind, arity, consumed, args.pop(), classes,
                    keyConverter, valueConverter, typedValuesAtPosition, i, argDescription);
            result.putAll(typedValuesAtPosition);
            consumed = consumedCountMap(i + 1, initialSize, argSpec);
            lookBehind = LookBehind.SEPARATE;
        }
        // now process the varargs if any
        for (int i = consumed; consumed < arity.max && !args.isEmpty(); i++) {
            if (!varargCanConsumeNextValue(argSpec, args.peek())) {
                break;
            }

            Map<Object, Object> typedValuesAtPosition = new LinkedHashMap<Object, Object>();
            parseResult.addTypedValues(argSpec, currentPosition++, typedValuesAtPosition);
            if (!canConsumeOneMapArgument(argSpec, arity, consumed, args.peek(), classes,
                    keyConverter, valueConverter, argDescription)) {
                break; // leave empty map at argSpec.typedValueAtPosition[currentPosition] so we won't try to consume that position again
            }
            consumeOneMapArgument(argSpec, lookBehind, arity, consumed, args.pop(), classes,
                    keyConverter, valueConverter, typedValuesAtPosition, i, argDescription);
            result.putAll(typedValuesAtPosition);
            consumed = consumedCountMap(i + 1, initialSize, argSpec);
            lookBehind = LookBehind.SEPARATE;
        }
    }

    private void consumeOneMapArgument(ArgSpec argSpec, LookBehind lookBehind, Range arity,
            int consumed, String arg, Class<?>[] classes, ITypeConverter<?> keyConverter,
            ITypeConverter<?> valueConverter, Map<Object, Object> result, int index,
            String argDescription) {
        if (!lookBehind.isAttached()) {
            parseResult.nowProcessing(argSpec, arg);
        }
        String raw = trim(arg);
        String[] values = argSpec.splitValue(raw, commandLine.getCommandSpec().parser(), arity,
                consumed);
        for (String value : values) {
            String[] keyValue = splitKeyValue(argSpec, value);
            Object mapKey = tryConvert(argSpec, index, keyConverter, keyValue[0], classes[0]);
            Object mapValue = tryConvert(argSpec, index, valueConverter, keyValue[1], classes[1]);
            result.put(mapKey, mapValue);
            if (tracer.isInfo()) {
                tracer.info("Putting [%s : %s] in %s<%s, %s> %s for %s%n",
                        String.valueOf(mapKey), String.valueOf(mapValue),
                        result.getClass().getSimpleName(), classes[0].getSimpleName(),
                        classes[1].getSimpleName(), argSpec.toString(), argDescription);
            }
            parseResult.addStringValue(argSpec, keyValue[0]);
            parseResult.addStringValue(argSpec, keyValue[1]);
        }
        parseResult.addOriginalStringValue(argSpec, raw);
    }

    private boolean canConsumeOneMapArgument(ArgSpec argSpec, Range arity, int consumed,
            String raw, Class<?>[] classes, ITypeConverter<?> keyConverter,
            ITypeConverter<?> valueConverter, String argDescription) {
        String[] values = argSpec.splitValue(raw, commandLine.getCommandSpec().parser(), arity,
                consumed);
        try {
            for (String value : values) {
                String[] keyValue = splitKeyValue(argSpec, value);
                tryConvert(argSpec, -1, keyConverter, keyValue[0], classes[0]);
                tryConvert(argSpec, -1, valueConverter, keyValue[1], classes[1]);
            }
            return true;
        } catch (PicocliException ex) {
            tracer.debug("$s cannot be assigned to %s: type conversion fails: %s.%n",
                    raw, argDescription, ex.getMessage());
            return false;
        }
    }

    private String[] splitKeyValue(ArgSpec argSpec, String value) {
        String[] keyValue = value.split("=", 2);
        if (keyValue.length < 2) {
            String splitRegex = argSpec.splitRegex();
            if (splitRegex.length() == 0) {
                throw new ParameterException(commandLine,
                        "Value for option " + optionDescription("", argSpec, 0)
                                + " should be in KEY=VALUE format but was " + value,
                        argSpec, value);
            } else {
                throw new ParameterException(commandLine,
                        "Value for option " + optionDescription("", argSpec, 0)
                                + " should be in KEY=VALUE[" + splitRegex
                                + "KEY=VALUE]... format but was " + value,
                        argSpec, value);
            }
        }
        return keyValue;
    }

    private void assertNoMissingMandatoryParameter(ArgSpec argSpec, Stack<String> args, int i,
            Range arity) {
        if (!varargCanConsumeNextValue(argSpec, args.peek())) {
            String desc = arity.min > 1 ? (i + 1) + " (of " + arity.min + " mandatory parameters) "
                    : "";
            throw new MissingParameterException(commandLine, argSpec,
                    "Expected parameter " + desc + "for " + optionDescription("", argSpec, -1)
                            + " but found '" + args.peek() + "'");
        }
    }

    private int applyValuesToArrayField(ArgSpec argSpec, LookBehind lookBehind, Range arity,
            Stack<String> args, Set<ArgSpec> initialized, String argDescription)
            throws Exception {
        Object existing = argSpec.getValue();
        int length = existing == null ? 0 : Array.getLength(existing);
        Class<?> type = argSpec.auxiliaryTypes()[0];
        List<Object> converted = consumeArguments(argSpec, lookBehind, arity, args, type,
                argDescription);
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
        argSpec.setValue(array, commandLine.getCommandSpec().commandLine());
        parseResult.add(argSpec, position);
        return converted.size(); // return how many args were consumed
    }

    @SuppressWarnings("unchecked")
    private int applyValuesToCollectionField(ArgSpec argSpec, LookBehind lookBehind,
            Range arity, Stack<String> args, Set<ArgSpec> initialized, String argDescription)
            throws Exception {
        Collection<Object> collection = (Collection<Object>) argSpec.getValue();
        Class<?> type = argSpec.auxiliaryTypes()[0];
        List<Object> converted = consumeArguments(argSpec, lookBehind, arity, args, type,
                argDescription);
        if (collection == null || (!collection.isEmpty() && !initialized.contains(argSpec))) {
            collection = createCollection(argSpec.type()); // collection type
            argSpec.setValue(collection, commandLine.getCommandSpec().commandLine());
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
        argSpec.setValue(collection, commandLine.getCommandSpec().commandLine());
        return converted.size();
    }

    private List<Object> consumeArguments(ArgSpec argSpec, LookBehind lookBehind, Range arity,
            Stack<String> args, Class<?> type, String argDescription) throws Exception {
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
            consumeOneArgument(argSpec, lookBehind, arity, consumed, args.pop(), type,
                    typedValuesAtPosition, i, argDescription);
            result.addAll(typedValuesAtPosition);
            consumed = consumedCount(i + 1, initialSize, argSpec);
            lookBehind = LookBehind.SEPARATE;
        }
        // now process the varargs if any
        for (int i = consumed; consumed < arity.max && !args.isEmpty(); i++) {
            if (!varargCanConsumeNextValue(argSpec, args.peek())) {
                break;
            }

            List<Object> typedValuesAtPosition = new ArrayList<Object>();
            parseResult.addTypedValues(argSpec, currentPosition++, typedValuesAtPosition);
            if (!canConsumeOneArgument(argSpec, arity, consumed, args.peek(), type,
                    argDescription)) {
                break; // leave empty list at argSpec.typedValueAtPosition[currentPosition] so we won't try to consume that position again
            }
            consumeOneArgument(argSpec, lookBehind, arity, consumed, args.pop(), type,
                    typedValuesAtPosition, i, argDescription);
            result.addAll(typedValuesAtPosition);
            consumed = consumedCount(i + 1, initialSize, argSpec);
            lookBehind = LookBehind.SEPARATE;
        }
        if (result.isEmpty() && arity.min == 0 && arity.max <= 1 && ClassUtilsExt.isBoolean(type)) {
            return Arrays.asList((Object) Boolean.TRUE);
        }
        return result;
    }

    private int consumedCount(int i, int initialSize, ArgSpec arg) {
        return commandLine.getCommandSpec().parser().splitFirst()
                ? arg.stringValues().size() - initialSize
                : i;
    }

    private int consumedCountMap(int i, int initialSize, ArgSpec arg) {
        return commandLine.getCommandSpec().parser().splitFirst()
                ? (arg.stringValues().size() - initialSize) / 2
                : i;
    }

    private int consumeOneArgument(ArgSpec argSpec, LookBehind lookBehind, Range arity,
            int consumed, String arg, Class<?> type, List<Object> result, int index,
            String argDescription) {
        if (!lookBehind.isAttached()) {
            parseResult.nowProcessing(argSpec, arg);
        }
        String raw = trim(arg);
        String[] values = argSpec.splitValue(raw, commandLine.getCommandSpec().parser(), arity,
                consumed);
        ITypeConverter<?> converter = getTypeConverter(type, argSpec, 0);
        for (int j = 0; j < values.length; j++) {
            result.add(tryConvert(argSpec, index, converter, values[j], type));
            if (tracer.isInfo()) {
                tracer.info("Adding [%s] to %s for %s%n",
                        String.valueOf(result.get(result.size() - 1)), argSpec.toString(),
                        argDescription);
            }
            parseResult.addStringValue(argSpec, values[j]);
        }
        parseResult.addOriginalStringValue(argSpec, raw);
        return ++index;
    }

    private boolean canConsumeOneArgument(ArgSpec argSpec, Range arity, int consumed,
            String arg, Class<?> type, String argDescription) {
        ITypeConverter<?> converter = getTypeConverter(type, argSpec, 0);
        try {
            String[] values = argSpec.splitValue(trim(arg), commandLine.getCommandSpec().parser(), arity,
                    consumed);
            //                if (!argSpec.acceptsValues(values.length, commandSpec.parser())) {
            //                    tracer.debug("$s would split into %s values but %s cannot accept that many values.%n", arg, values.length, argDescription);
            //                    return false;
            //                }
            for (String value : values) {
                tryConvert(argSpec, -1, converter, value, type);
            }
            return true;
        } catch (PicocliException ex) {
            tracer.debug("$s cannot be assigned to %s: type conversion fails: %s.%n",
                    arg, argDescription, ex.getMessage());
            return false;
        }
    }

    /**
     * Returns whether the next argument can be assigned to a vararg option/positional parameter.
     * <p>
     * Usually, we stop if we encounter '--', a command, or another option. However, if
     * end-of-options has been reached, positional parameters may consume all remaining arguments.
     * </p>
     */
    private boolean varargCanConsumeNextValue(ArgSpec argSpec, String nextValue) {
        if (endOfOptions && argSpec.isPositional()) {
            return true;
        }
        boolean isCommand = commandLine.getCommandSpec().subcommands().containsKey(nextValue);
        return !isCommand && !isOption(nextValue);
    }

    /**
     * Called when parsing varargs parameters for a multi-value option. When an option is
     * encountered, the remainder should not be interpreted as vararg elements.
     * 
     * @param arg
     *            the string to determine whether it is an option or not
     * @return true if it is an option, false otherwise
     */
    private boolean isOption(String arg) {
        if (arg == null) {
            return false;
        }
        if ("--".equals(arg)) {
            return true;
        }

        // not just arg prefix: we may be in the middle of parsing -xrvfFILE
        if (commandLine.getCommandSpec().optionsMap().containsKey(arg)) { // -v or -f or --file (not attached to param or other option)
            return true;
        }
        int separatorIndex = arg.indexOf(config().separator());
        if (separatorIndex > 0) { // -f=FILE or --file==FILE (attached to param via separator)
            if (commandLine.getCommandSpec().optionsMap()
                    .containsKey(arg.substring(0, separatorIndex))) {
                return true;
            }
        }
        return (arg.length() > 2 && arg.startsWith("-")
                && commandLine.getCommandSpec().posixOptionsMap().containsKey(arg.charAt(1)));
    }

    private Object tryConvert(ArgSpec argSpec, int index, ITypeConverter<?> converter,
            String value, Class<?> type) throws ParameterException {
        try {
            return converter.convert(value);
        } catch (TypeConversionException ex) {
            String msg = String.format("Invalid value for %s: %s",
                    optionDescription("", argSpec, index), ex.getMessage());
            throw new ParameterException(commandLine, msg, argSpec, value);
        } catch (Exception other) {
            String desc = optionDescription("", argSpec, index);
            String msg = String.format("Invalid value for %s: cannot convert '%s' to %s (%s)", desc,
                    value, type.getSimpleName(), other);
            throw new ParameterException(commandLine, msg, other, argSpec, value);
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
            desc = prefix + "positional parameter at index "
                    + ((PositionalParamSpec) argSpec).index() + " (" + argSpec.paramLabel()
                    + ")";
        }
        return desc;
    }

    private boolean isAnyHelpRequested() {
        return isHelpRequested || parseResult.versionHelpRequested
                || parseResult.usageHelpRequested;
    }

    private void updateHelpRequested(CommandSpec command) {
        isHelpRequested |= command.helpCommand();
    }

    private void updateHelpRequested(ArgSpec argSpec) {
        if (argSpec.isOption()) {
            OptionSpec option = (OptionSpec) argSpec;
            isHelpRequested |= is(argSpec, "help", option.help());
            parseResult.versionHelpRequested |= is(argSpec, "versionHelp", option.versionHelp());
            parseResult.usageHelpRequested |= is(argSpec, "usageHelp", option.usageHelp());
        }
    }

    private boolean is(ArgSpec p, String attribute, boolean value) {
        if (value) {
            if (tracer.isInfo()) {
                tracer.info("%s has '%s' annotation: not validating required fields%n",
                        p.toString(), attribute);
            }
        }
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

    @SuppressWarnings("unchecked")
    private Map<Object, Object> createMap(Class<?> mapClass) throws Exception {
        try { // if it is an implementation class, instantiate it
            return (Map<Object, Object>) mapClass.getDeclaredConstructor().newInstance();
        } catch (Exception ignored) {
        }
        return new LinkedHashMap<Object, Object>();
    }

    private ITypeConverter<?> getTypeConverter(final Class<?> type, ArgSpec argSpec,
            int index) {
        if (argSpec.converters().length > index) {
            return argSpec.converters()[index];
        }
        if (converterRegistry.containsKey(type)) {
            return converterRegistry.get(type);
        }
        if (type.isEnum()) {
            return new ITypeConverter<Object>() {
                @SuppressWarnings("unchecked")
                public Object convert(String value) throws Exception {
                    if (commandLine.getCommandSpec().parser().caseInsensitiveEnumValuesAllowed()) {
                        String upper = value.toUpperCase();
                        for (Object enumConstant : type.getEnumConstants()) {
                            if (upper.equals(String.valueOf(enumConstant).toUpperCase())) {
                                return enumConstant;
                            }
                        }
                    }
                    try {
                        return Enum.valueOf((Class<Enum>) type, value);
                    } catch (Exception ex) {
                        throw new TypeConversionException(
                                String.format("expected one of %s but was '%s'",
                                        Arrays.asList(type.getEnumConstants()), value));
                    }
                }
            };
        }
        throw new MissingTypeConverterException(commandLine,
                "No TypeConverter registered for " + type.getName() + " of " + argSpec);
    }

    private boolean assertNoMissingParameters(ArgSpec argSpec, Range arity,
            Stack<String> args) {
        if (argSpec.interactive()) {
            return true;
        }
        int available = args.size();
        if (available > 0 && commandLine.getCommandSpec().parser().splitFirst()
                && argSpec.splitRegex().length() > 0) {
            available += argSpec.splitValue(args.peek(), commandLine.getCommandSpec().parser(), arity,
                    0).length - 1;
        }
        if (arity.min > available) {
            if (arity.min == 1) {
                if (argSpec.isOption()) {
                    maybeThrow(new MissingParameterException(commandLine, argSpec,
                            "Missing required parameter for " + optionDescription("", argSpec, 0)));
                    return false;
                }
                Range indexRange = ((PositionalParamSpec) argSpec).index();
                String sep = "";
                String names = ": ";
                int count = 0;
                List<PositionalParamSpec> positionalParameters = commandLine.getCommandSpec()
                        .positionalParameters();
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
                maybeThrow(new MissingParameterException(commandLine, argSpec, msg + names));
            } else if (args.isEmpty()) {
                maybeThrow(new MissingParameterException(commandLine, argSpec,
                        optionDescription("", argSpec, 0) + " requires at least " + arity.min
                                + " values, but none were specified."));
            } else {
                maybeThrow(new MissingParameterException(commandLine, argSpec,
                        optionDescription("", argSpec, 0) + " requires at least " + arity.min
                                + " values, but only " + available + " were specified: "
                                + CollectionUtilsExt.reverse(ObjectUtils.clone(args))));
            }
            return false;
        }
        return true;
    }

    private String trim(String value) {
        return unquote(value);
    }

    private String unquote(String value) {
        if (!commandLine.getCommandSpec().parser().trimQuotes()) {
            return value;
        }
        return value == null ? null
                : (value.length() > 1 && value.startsWith("\"") && value.endsWith("\""))
                        ? value.substring(1, value.length() - 1)
                        : value;
    }

    char[] readPassword(String prompt, boolean echoInput) {
        try {
            Object console = System.class.getDeclaredMethod("console").invoke(null);
            Method method;
            if (echoInput) {
                method = console.getClass().getDeclaredMethod("readLine", String.class,
                        Object[].class);
                String line = (String) method.invoke(console, prompt, new Object[0]);
                return line.toCharArray();
            } else {
                method = console.getClass().getDeclaredMethod("readPassword", String.class,
                        Object[].class);
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