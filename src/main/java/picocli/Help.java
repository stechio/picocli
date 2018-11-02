package picocli;

import static java.util.Locale.ENGLISH;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import picocli.CommandLine.Assert;
import picocli.CommandLine.Command;
import picocli.CommandLine.DefaultFactory;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.ParserSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Model.UsageMessageSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Range;
import picocli.Help.Ansi.Style;
import picocli.Help.Ansi.Text;
import picocli.Help.Column.Overflow;
import picocli.util.ListMap;
import picocli.util.Utils;

/**
 * A collection of methods and inner classes that provide fine-grained control over the contents and
 * layout of the usage help message to display to end users when help is requested or invalid input
 * values were specified.
 * <h2>Class Diagram of the CommandLine.Help API</h2>
 * <p>
 * <img src="doc-files/class-diagram-help-api.png" alt="Class Diagram of the CommandLine.Help API">
 * </p>
 * <h2>Layered API</h2>
 * <p>
 * The {@link Command} annotation and the {@link UsageMessageSpec} programmatic API equivalent
 * provide the easiest way to configure the usage help message. See the
 * <a href="https://remkop.github.io/picocli/index.html#_usage_help">Manual</a> for details.
 * </p>
 * <p>
 * This Help class provides high-level functions to create sections of the usage help message and
 * headings for these sections. Instead of calling the
 * {@link CommandLine#usage(PrintStream, Help.ColorScheme)} method, application authors may want to
 * create a custom usage help message by reorganizing sections in a different order and/or adding
 * custom sections.
 * </p>
 * <p>
 * Finally, the Help class contains inner classes and interfaces that can be used to create custom
 * help messages.
 * </p>
 * <h3>IOptionRenderer and IParameterRenderer</h3>
 * <p>
 * Renders a field annotated with {@link Option} or {@link Parameters} to an array of {@link Text}
 * values. By default, these values are
 * </p>
 * <ul>
 * <li>mandatory marker character (if the option/parameter is {@link Option#required()
 * required})</li>
 * <li>short option name (empty for parameters)</li>
 * <li>comma or empty (empty for parameters)</li>
 * <li>long option names (the parameter {@link IParamLabelRenderer label} for parameters)</li>
 * <li>description</li>
 * </ul>
 * <p>
 * Other components rely on this ordering.
 * </p>
 * <h3>Layout</h3>
 * <p>
 * Delegates to the renderers to create {@link Text} values for the annotated fields, and uses a
 * {@link TextTable} to display these values in tabular format. Layout is responsible for deciding
 * which values to display where in the table. By default, Layout shows one option or parameter per
 * table row.
 * </p>
 * <h3>TextTable</h3>
 * <p>
 * Responsible for spacing out {@link Text} values according to the {@link Column} definitions the
 * table was created with. Columns have a width, indentation, and an overflow policy that decides
 * what to do if a value is longer than the column's width.
 * </p>
 * <h3>Text</h3>
 * <p>
 * Encapsulates rich text with styles and colors in a way that other components like
 * {@link TextTable} are unaware of the embedded ANSI escape codes.
 * </p>
 */
public class Help {
    /**
     * Provides methods and inner classes to support using ANSI escape codes in usage help messages.
     */
    public enum Ansi {
        /**
         * Only emit ANSI escape codes if the platform supports it and system property
         * {@code "picocli.ansi"} is not set to any value other than {@code "true"} (case
         * insensitive).
         */
        AUTO,
        /** Forced ON: always emit ANSI escape code regardless of the platform. */
        ON,
        /** Forced OFF: never emit ANSI escape code regardless of the platform. */
        OFF;
        /** Defines the interface for an ANSI escape sequence. */
        public interface IStyle {

            /** The Control Sequence Introducer (CSI) escape sequence {@value}. */
            String CSI = "\u001B[";

            /**
             * Returns the ANSI escape code for turning this style off.
             * 
             * @return the ANSI escape code for turning this style off
             */
            String off();

            /**
             * Returns the ANSI escape code for turning this style on.
             * 
             * @return the ANSI escape code for turning this style on
             */
            String on();
        }

        /**
         * A set of pre-defined ANSI escape code styles and colors, and a set of convenience methods
         * for parsing text with embedded markup style names, as well as convenience methods for
         * converting styles to strings with embedded escape codes.
         */
        public enum Style implements Ansi.IStyle {
            reset(0, 0), bold(1, 21), faint(2, 22), italic(3, 23), underline(4, 24), blink(5,
                    25), reverse(7, 27), fg_black(30, 39), fg_red(31, 39), fg_green(32,
                            39), fg_yellow(33, 39), fg_blue(34, 39), fg_magenta(35, 39), fg_cyan(36,
                                    39), fg_white(37, 39), bg_black(40, 49), bg_red(41,
                                            49), bg_green(42, 49), bg_yellow(43, 49), bg_blue(44,
                                                    49), bg_magenta(45,
                                                            49), bg_cyan(46, 49), bg_white(47, 49),;
            /**
             * Parses the specified style markup and returns the associated style. The markup may be
             * one of the Style enum value names, or it may be one of the Style enum value names
             * when {@code "bg_"} is prepended, or it may be one of the indexed colors in the 256
             * color palette.
             * 
             * @param str
             *            the case-insensitive style markup to convert, e.g. {@code "blue"} or
             *            {@code "bg_blue"}, or {@code "46"} (indexed color) or {@code "0;5;0"} (RGB
             *            components of an indexed color)
             * @return the IStyle for the specified converter
             */
            public static Ansi.IStyle bg(String str) {
                try {
                    return Style.valueOf(str.toLowerCase(ENGLISH));
                } catch (Exception ignored) {
                }
                try {
                    return Style.valueOf("bg_" + str.toLowerCase(ENGLISH));
                } catch (Exception ignored) {
                }
                return new Palette256Color(false, str);
            }

            /**
             * Parses the specified style markup and returns the associated style. The markup may be
             * one of the Style enum value names, or it may be one of the Style enum value names
             * when {@code "fg_"} is prepended, or it may be one of the indexed colors in the 256
             * color palette.
             * 
             * @param str
             *            the case-insensitive style markup to convert, e.g. {@code "blue"} or
             *            {@code "fg_blue"}, or {@code "46"} (indexed color) or {@code "0;5;0"} (RGB
             *            components of an indexed color)
             * @return the IStyle for the specified converter
             */
            public static Ansi.IStyle fg(String str) {
                try {
                    return Style.valueOf(str.toLowerCase(ENGLISH));
                } catch (Exception ignored) {
                }
                try {
                    return Style.valueOf("fg_" + str.toLowerCase(ENGLISH));
                } catch (Exception ignored) {
                }
                return new Palette256Color(true, str);
            }

            /**
             * Returns the concatenated ANSI escape codes for turning all specified styles off.
             * 
             * @param styles
             *            the styles to generate ANSI escape codes for
             * @return the concatenated ANSI escape codes for turning all specified styles off
             */
            public static String off(Ansi.IStyle... styles) {
                StringBuilder result = new StringBuilder();
                for (Ansi.IStyle style : styles) {
                    result.append(style.off());
                }
                return result.toString();
            }

            /**
             * Returns the concatenated ANSI escape codes for turning all specified styles on.
             * 
             * @param styles
             *            the styles to generate ANSI escape codes for
             * @return the concatenated ANSI escape codes for turning all specified styles on
             */
            public static String on(Ansi.IStyle... styles) {
                StringBuilder result = new StringBuilder();
                for (Ansi.IStyle style : styles) {
                    result.append(style.on());
                }
                return result.toString();
            }

            /**
             * Parses the specified comma-separated sequence of style descriptors and returns the
             * associated styles. For each markup, strings starting with {@code "bg("} are delegated
             * to {@link #bg(String)}, others are delegated to {@link #bg(String)}.
             * 
             * @param commaSeparatedCodes
             *            one or more descriptors, e.g. {@code "bg(blue),underline,red"}
             * @return an array with all styles for the specified descriptors
             */
            public static Ansi.IStyle[] parse(String commaSeparatedCodes) {
                String[] codes = commaSeparatedCodes.split(",");
                Ansi.IStyle[] styles = new Ansi.IStyle[codes.length];
                for (int i = 0; i < codes.length; ++i) {
                    if (codes[i].toLowerCase(ENGLISH).startsWith("fg(")) {
                        int end = codes[i].indexOf(')');
                        styles[i] = Style
                                .fg(codes[i].substring(3, end < 0 ? codes[i].length() : end));
                    } else if (codes[i].toLowerCase(ENGLISH).startsWith("bg(")) {
                        int end = codes[i].indexOf(')');
                        styles[i] = Style
                                .bg(codes[i].substring(3, end < 0 ? codes[i].length() : end));
                    } else {
                        styles[i] = Style.fg(codes[i]);
                    }
                }
                return styles;
            }

            private final int startCode;

            private final int endCode;

            Style(int startCode, int endCode) {
                this.startCode = startCode;
                this.endCode = endCode;
            }

            @Override
            public String off() {
                return CSI + endCode + "m";
            }

            @Override
            public String on() {
                return CSI + startCode + "m";
            }
        }

        /**
         * Encapsulates rich text with styles and colors. Text objects may be constructed with
         * Strings containing markup like {@code @|bg(red),white,underline some text|@}, and this
         * class converts the markup to ANSI escape codes.
         * <p>
         * Internally keeps both an enriched and a plain text representation to allow layout
         * components to calculate text width while remaining unaware of the embedded ANSI escape
         * codes.
         * </p>
         */
        public class Text implements Cloneable {
            private final int maxLength;
            private int from;
            private int length;
            private StringBuilder plain = new StringBuilder();
            private List<Ansi.StyledSection> sections = new ArrayList<>();

            /**
             * Constructs a Text with the specified max length (for use in a TextTable Column).
             * 
             * @param maxLength
             *            max length of this text
             */
            public Text(int maxLength) {
                this.maxLength = maxLength;
            }

            /**
             * Constructs a Text with the specified String, which may contain markup like
             * {@code @|bg(red),white,underline some text|@}.
             * 
             * @param input
             *            the string with markup to parse
             */
            public Text(String input) {
                maxLength = -1;
                plain.setLength(0);
                int i = 0;

                while (true) {
                    int j = input.indexOf("@|", i);
                    if (j == -1) {
                        if (i == 0) {
                            plain.append(input);
                            length = plain.length();
                            return;
                        }
                        plain.append(input.substring(i, input.length()));
                        length = plain.length();
                        return;
                    }
                    plain.append(input.substring(i, j));
                    int k = input.indexOf("|@", j);
                    if (k == -1) {
                        plain.append(input);
                        length = plain.length();
                        return;
                    }

                    j += 2;
                    String spec = input.substring(j, k);
                    String[] items = spec.split(" ", 2);
                    if (items.length == 1) {
                        plain.append(input);
                        length = plain.length();
                        return;
                    }

                    Ansi.IStyle[] styles = Style.parse(items[0]);
                    addStyledSection(plain.length(), items[1].length(), Style.on(styles),
                            Style.off(reverse(styles)) + Style.reset.off());
                    plain.append(items[1]);
                    i = k + 2;
                }
            }

            /** @deprecated use {@link #concat(String)} instead */
            @Deprecated
            public Text append(String string) {
                return concat(string);
            }

            /** @deprecated use {@link #concat(Text)} instead */
            @Deprecated
            public Text append(Text text) {
                return concat(text);
            }

            @Override
            public Object clone() {
                try {
                    return super.clone();
                } catch (CloneNotSupportedException e) {
                    throw new IllegalStateException(e);
                }
            }

            /**
             * Returns a copy of this {@code Text} instance with the specified text concatenated to
             * the end. Does not modify this instance!
             * 
             * @param string
             *            the text to concatenate to the end of this Text
             * @return a new Text instance
             * @since 3.0
             */
            public Text concat(String string) {
                return concat(new Text(string));
            }

            /**
             * Returns a copy of this {@code Text} instance with the specified text concatenated to
             * the end. Does not modify this instance!
             * 
             * @param other
             *            the text to concatenate to the end of this Text
             * @return a new Text instance
             * @since 3.0
             */
            public Text concat(Text other) {
                Text result = (Text) clone();
                result.plain = new StringBuilder(plain.toString().substring(from, from + length));
                result.from = 0;
                result.sections = new ArrayList<>();
                for (Ansi.StyledSection section : sections) {
                    result.sections.add(section.withStartIndex(section.startIndex - from));
                }
                result.plain.append(
                        other.plain.toString().substring(other.from, other.from + other.length));
                for (Ansi.StyledSection section : other.sections) {
                    int index = result.length + section.startIndex - other.from;
                    result.sections.add(section.withStartIndex(index));
                }
                result.length = result.plain.length();
                return result;
            }

            @Override
            public boolean equals(Object obj) {
                return toString().equals(String.valueOf(obj));
            }

            /**
             * Copies the specified substring of this Text into the specified destination,
             * preserving the markup.
             * 
             * @param from
             *            start of the substring
             * @param length
             *            length of the substring
             * @param destination
             *            destination Text to modify
             * @param offset
             *            indentation (padding)
             */
            public void getStyledChars(int from, int length, Text destination, int offset) {
                if (destination.length < offset) {
                    for (int i = destination.length; i < offset; i++) {
                        destination.plain.append(' ');
                    }
                    destination.length = offset;
                }
                for (Ansi.StyledSection section : sections) {
                    destination.sections.add(
                            section.withStartIndex(section.startIndex - from + destination.length));
                }
                destination.plain.append(plain.toString().substring(from, from + length));
                destination.length = destination.plain.length();
            }

            @Override
            public int hashCode() {
                return toString().hashCode();
            }

            /**
             * Returns the plain text without any formatting.
             * 
             * @return the plain text without any formatting
             */
            public String plainString() {
                return plain.toString().substring(from, from + length);
            }

            public Text[] splitLines() {
                List<Text> result = new ArrayList<>();
                boolean trailingEmptyString = plain.length() == 0;
                int start = 0, end = 0;
                for (int i = 0; i < plain.length(); i++, end = i) {
                    char c = plain.charAt(i);
                    boolean eol = c == '\n';
                    eol |= (c == '\r' && i + 1 < plain.length() && plain.charAt(i + 1) == '\n'
                            && ++i > 0); // \r\n
                    eol |= c == '\r';
                    if (eol) {
                        result.add(this.substring(start, end));
                        trailingEmptyString = i == plain.length() - 1;
                        start = i + 1;
                    }
                }
                if (start < plain.length() || trailingEmptyString) {
                    result.add(this.substring(start, plain.length()));
                }
                return result.toArray(new Text[result.size()]);
            }

            /**
             * Returns a new {@code Text} instance that is a substring of this Text. Does not modify
             * this instance!
             * 
             * @param start
             *            index in the plain text where to start the substring
             * @return a new Text instance that is a substring of this Text
             */
            public Text substring(int start) {
                return substring(start, length);
            }

            /**
             * Returns a new {@code Text} instance that is a substring of this Text. Does not modify
             * this instance!
             * 
             * @param start
             *            index in the plain text where to start the substring
             * @param end
             *            index in the plain text where to end the substring
             * @return a new Text instance that is a substring of this Text
             */
            public Text substring(int start, int end) {
                Text result = (Text) clone();
                result.from = from + start;
                result.length = end - start;
                return result;
            }

            /**
             * Returns a String representation of the text with ANSI escape codes embedded, unless
             * ANSI is {@linkplain Ansi#enabled()} not enabled}, in which case the plain text is
             * returned.
             * 
             * @return a String representation of the text with ANSI escape codes embedded (if
             *         enabled)
             */
            @Override
            public String toString() {
                if (!Ansi.this.enabled())
                    return plain.toString().substring(from, from + length);
                if (length == 0)
                    return "";
                StringBuilder sb = new StringBuilder(plain.length() + 20 * sections.size());
                Ansi.StyledSection current = null;
                int end = Math.min(from + length, plain.length());
                for (int i = from; i < end; i++) {
                    Ansi.StyledSection section = findSectionContaining(i);
                    if (section != current) {
                        if (current != null) {
                            sb.append(current.endStyles);
                        }
                        if (section != null) {
                            sb.append(section.startStyles);
                        }
                        current = section;
                    }
                    sb.append(plain.charAt(i));
                }
                if (current != null) {
                    sb.append(current.endStyles);
                }
                return sb.toString();
            }

            private void addStyledSection(int start, int length, String startStyle,
                    String endStyle) {
                sections.add(new StyledSection(start, length, startStyle, endStyle));
            }

            private Ansi.StyledSection findSectionContaining(int index) {
                for (Ansi.StyledSection section : sections) {
                    if (index >= section.startIndex && index < section.startIndex + section.length)
                        return section;
                }
                return null;
            }
        }

        /**
         * Defines a palette map of 216 colors: 6 * 6 * 6 cube (216 colors): 16 + 36 * r + 6 * g + b
         * (0 &lt;= r, g, b &lt;= 5).
         */
        static class Palette256Color implements Ansi.IStyle {
            private final int fgbg;
            private final int color;

            Palette256Color(boolean foreground, String color) {
                this.fgbg = foreground ? 38 : 48;
                String[] rgb = color.split(";");
                if (rgb.length == 3) {
                    this.color = 16 + 36 * Integer.decode(rgb[0]) + 6 * Integer.decode(rgb[1])
                            + Integer.decode(rgb[2]);
                } else {
                    this.color = Integer.decode(color);
                }
            }

            @Override
            public String off() {
                return CSI + (fgbg + 1) + "m";
            }

            @Override
            public String on() {
                return String.format(CSI + "%d;5;%dm", fgbg, color);
            }
        }

        private static class StyledSection {
            int startIndex, length;
            String startStyles, endStyles;

            StyledSection(int start, int len, String style1, String style2) {
                startIndex = start;
                length = len;
                startStyles = style1;
                endStyles = style2;
            }

            Ansi.StyledSection withStartIndex(int newStart) {
                return new StyledSection(newStart, length, startStyles, endStyles);
            }
        }

        static Text EMPTY_TEXT = OFF.new Text(0);

        static final boolean isWindows = System.getProperty("os.name").startsWith("Windows");

        static final boolean isXterm = System.getenv("TERM") != null
                && System.getenv("TERM").startsWith("xterm");

        static final boolean hasOsType = System.getenv("OSTYPE") != null; // null on Windows unless on Cygwin or MSYS

        static final boolean ISATTY = calcTTY();

        /**
         * Returns Ansi.ON if the specified {@code enabled} flag is true, Ansi.OFF otherwise.
         * 
         * @since 3.4
         */
        public static Help.Ansi valueOf(boolean enabled) {
            return enabled ? ON : OFF;
        }

        // http://stackoverflow.com/questions/1403772/how-can-i-check-if-a-java-programs-input-output-streams-are-connected-to-a-term
        static final boolean calcTTY() {
            if (isWindows && (isXterm || hasOsType))
                return true;
            try {
                return System.class.getDeclaredMethod("console").invoke(null) != null;
            } catch (Throwable reflectionFailed) {
                return true;
            }
        }

        private static boolean ansiPossible() {
            return (ISATTY && (!isWindows || isXterm || hasOsType)) || isJansiConsoleInstalled();
        }

        private static boolean isJansiConsoleInstalled() {
            try {
                Class<?> ansiConsole = Class.forName("org.fusesource.jansi.AnsiConsole");
                Field out = ansiConsole.getField("out");
                return out.get(null) == System.out;
            } catch (Exception reflectionFailed) {
                return false;
            }
        }

        private static <T> T[] reverse(T[] all) {
            for (int i = 0; i < all.length / 2; i++) {
                T temp = all[i];
                all[i] = all[all.length - i - 1];
                all[all.length - i - 1] = temp;
            }
            return all;
        }

        /**
         * Returns a new Text object where all the specified styles are applied to the full length
         * of the specified plain text.
         * 
         * @param plainText
         *            the string to apply all styles to. Must not contain markup!
         * @param styles
         *            the styles to apply to the full plain text
         * @return a new Text object
         */
        public Text apply(String plainText, List<Ansi.IStyle> styles) {
            if (plainText.length() == 0)
                return new Text(0);
            Text result = new Text(plainText.length());
            Ansi.IStyle[] all = styles.toArray(new Ansi.IStyle[styles.size()]);
            result.sections.add(new StyledSection(0, plainText.length(), Style.on(all),
                    Style.off(reverse(all)) + Style.reset.off()));
            result.plain.append(plainText);
            result.length = result.plain.length();
            return result;
        }

        /**
         * Returns {@code true} if ANSI escape codes should be emitted, {@code false} otherwise.
         * 
         * @return ON: {@code true}, OFF: {@code false}, AUTO: if system property
         *         {@code "picocli.ansi"} is defined then return its boolean value, otherwise return
         *         whether the platform supports ANSI escape codes
         */
        public boolean enabled() {
            if (this == ON)
                return true;
            if (this == OFF)
                return false;
            return (System.getProperty("picocli.ansi") == null ? ansiPossible()
                    : Boolean.getBoolean("picocli.ansi"));
        }

        /**
         * Returns a String where any markup like {@code @|bg(red),white,underline some text|@} is
         * converted to ANSI escape codes if this Ansi is ON, or suppressed if this Ansi is OFF.
         * <p>
         * Equivalent to {@code this.new Text(stringWithMarkup).toString()}.
         * 
         * @since 3.4
         */
        public String string(String stringWithMarkup) {
            return this.new Text(stringWithMarkup).toString();
        }

        /**
         * Returns a new Text object for this Ansi mode, encapsulating the specified string which
         * may contain markup like {@code @|bg(red),white,underline some text|@}.
         * <p>
         * Calling {@code toString()} on the returned Text will either include ANSI escape codes (if
         * this Ansi mode is ON), or suppress ANSI escape codes (if this Ansi mode is OFF).
         * <p>
         * Equivalent to {@code this.new Text(stringWithMarkup)}.
         * 
         * @since 3.4
         */
        public Text text(String stringWithMarkup) {
            return this.new Text(stringWithMarkup);
        }
    }

    public static abstract class ArgumentListRenderer<T extends ArgSpec>
            implements IHelpElementRenderer<List<T>> {
        @Override
        public String render(Help help, List<T> arguments) {
            Comparator<T> comparator = getComparator(help.commandSpec());
            if (comparator != null) {
                Collections.sort(arguments = new ArrayList<>(arguments), comparator);
            }
            Layout layout = getLayout(help);
            IParamLabelRenderer labelRenderer = help.parameterLabelRenderer();
            populate(layout, arguments, labelRenderer);
            return layout.toString();
        }

        protected abstract Comparator<T> getComparator(CommandSpec commandSpec);

        protected Layout getLayout(Help help) {
            return help.createLayout(getNameColumnWidth(help));
        }

        protected int getNameColumnWidth(Help help) {
            int max = 0;
            IOptionRenderer optionRenderer = new DefaultOptionRenderer(false, " ");
            for (OptionSpec option : help.commandSpec().options()) {
                Text[][] values = optionRenderer.render(option, help.parameterLabelRenderer(),
                        help.colorScheme());
                int len = values[0][3].length;
                if (len < Help.defaultOptionsColumnWidth - 3) {
                    max = Math.max(max, len);
                }
            }
            IParameterRenderer paramRenderer = new DefaultParameterRenderer(false, " ");
            for (PositionalParamSpec positional : help.commandSpec().positionalParameters()) {
                Text[][] values = paramRenderer.render(positional, help.parameterLabelRenderer(),
                        help.colorScheme());
                int len = values[0][3].length;
                if (len < Help.defaultOptionsColumnWidth - 3) {
                    max = Math.max(max, len);
                }
            }
            return max + 3;
        }

        protected abstract void populate(Help.Layout layout, List<T> arguments,
                Help.IParamLabelRenderer labelRenderer);
    }

    /**
     * All usage help message are generated with a color scheme that assigns certain styles and
     * colors to common parts of a usage message: the command name, options, positional parameters
     * and option parameters. Users may customize these styles by creating Help with a custom color
     * scheme.
     * <p>
     * Note that these options and styles may not be rendered if ANSI escape codes are not
     * {@linkplain Ansi#enabled() enabled}.
     * </p>
     * 
     * @see Help#defaultColorScheme(Ansi)
     */
    public static class ColorScheme {
        public final List<Ansi.IStyle> commandStyles = new ArrayList<>();
        public final List<Ansi.IStyle> optionStyles = new ArrayList<>();
        public final List<Ansi.IStyle> parameterStyles = new ArrayList<>();
        public final List<Ansi.IStyle> optionParamStyles = new ArrayList<>();
        final Help.Ansi ansi;

        /** Constructs a new empty ColorScheme with {@link Help.Ansi#AUTO}. */
        public ColorScheme() {
            this(Ansi.AUTO);
        }

        /**
         * Constructs a new empty ColorScheme with the specified Ansi enabled mode.
         * 
         * @see Help#defaultColorScheme(Ansi)
         * @param ansi
         *            whether to emit ANSI escape codes or not
         */
        public ColorScheme(Help.Ansi ansi) {
            this.ansi = Assert.notNull(ansi, "ansi");
        }

        public Help.Ansi ansi() {
            return ansi;
        }

        /**
         * Replaces colors and styles in this scheme with ones specified in system properties, and
         * returns this scheme. Supported property names:
         * <ul>
         * <li>{@code picocli.color.commands}</li>
         * <li>{@code picocli.color.options}</li>
         * <li>{@code picocli.color.parameters}</li>
         * <li>{@code picocli.color.optionParams}</li>
         * </ul>
         * <p>
         * Property values can be anything that {@link Help.Ansi.Style#parse(String)} can handle.
         * </p>
         * 
         * @return this ColorScheme
         */
        public Help.ColorScheme applySystemProperties() {
            replace(commandStyles, System.getProperty("picocli.color.commands"));
            replace(optionStyles, System.getProperty("picocli.color.options"));
            replace(parameterStyles, System.getProperty("picocli.color.parameters"));
            replace(optionParamStyles, System.getProperty("picocli.color.optionParams"));
            return this;
        }

        /**
         * Adds the specified styles to the registered styles for commands in this color scheme and
         * returns this color scheme.
         * 
         * @param styles
         *            the styles to add to the registered styles for commands in this color scheme
         * @return this color scheme to enable method chaining for a more fluent API
         */
        public Help.ColorScheme commands(Ansi.IStyle... styles) {
            return addAll(commandStyles, styles);
        }

        /**
         * Returns a Text with all command styles applied to the specified command string.
         * 
         * @param command
         *            the command string to apply the registered command styles to
         * @return a Text with all command styles applied to the specified command string
         */
        public Ansi.Text commandText(String command) {
            return ansi().apply(command, commandStyles);
        }

        /**
         * Adds the specified styles to the registered styles for option parameters in this color
         * scheme and returns this color scheme.
         * 
         * @param styles
         *            the styles to add to the registered styles for option parameters in this color
         *            scheme
         * @return this color scheme to enable method chaining for a more fluent API
         */
        public Help.ColorScheme optionParams(Ansi.IStyle... styles) {
            return addAll(optionParamStyles, styles);
        }

        /**
         * Returns a Text with all optionParam styles applied to the specified optionParam string.
         * 
         * @param optionParam
         *            the option parameter string to apply the registered option parameter styles to
         * @return a Text with all option parameter styles applied to the specified option parameter
         *         string
         */
        public Ansi.Text optionParamText(String optionParam) {
            return ansi().apply(optionParam, optionParamStyles);
        }

        /**
         * Adds the specified styles to the registered styles for options in this color scheme and
         * returns this color scheme.
         * 
         * @param styles
         *            the styles to add to registered the styles for options in this color scheme
         * @return this color scheme to enable method chaining for a more fluent API
         */
        public Help.ColorScheme options(Ansi.IStyle... styles) {
            return addAll(optionStyles, styles);
        }

        /**
         * Returns a Text with all option styles applied to the specified option string.
         * 
         * @param option
         *            the option string to apply the registered option styles to
         * @return a Text with all option styles applied to the specified option string
         */
        public Ansi.Text optionText(String option) {
            return ansi().apply(option, optionStyles);
        }

        /**
         * Adds the specified styles to the registered styles for positional parameters in this
         * color scheme and returns this color scheme.
         * 
         * @param styles
         *            the styles to add to registered the styles for parameters in this color scheme
         * @return this color scheme to enable method chaining for a more fluent API
         */
        public Help.ColorScheme parameters(Ansi.IStyle... styles) {
            return addAll(parameterStyles, styles);
        }

        /**
         * Returns a Text with all parameter styles applied to the specified parameter string.
         * 
         * @param parameter
         *            the parameter string to apply the registered parameter styles to
         * @return a Text with all parameter styles applied to the specified parameter string
         */
        public Ansi.Text parameterText(String parameter) {
            return ansi().apply(parameter, parameterStyles);
        }

        private Help.ColorScheme addAll(List<Ansi.IStyle> styles, Ansi.IStyle... add) {
            styles.addAll(Arrays.asList(add));
            return this;
        }

        private void replace(List<Ansi.IStyle> styles, String property) {
            if (property != null) {
                styles.clear();
                addAll(styles, Style.parse(property));
            }
        }
    }

    /**
     * Columns define the width, indent (leading number of spaces in a column before the value) and
     * {@linkplain Overflow Overflow} policy of a column in a {@linkplain TextTable TextTable}.
     */
    public static class Column {

        /**
         * Policy for handling text that is longer than the column width: span multiple columns,
         * wrap to the next row, or simply truncate the portion that doesn't fit.
         */
        public enum Overflow {
            TRUNCATE, SPAN, WRAP
        }

        /** Column width in characters */
        public final int width;

        /**
         * Indent (number of empty spaces at the start of the column preceding the text value)
         */
        public final int indent;

        /** Policy that determines how to handle values larger than the column width. */
        public final Column.Overflow overflow;

        public Column(int width, int indent, Column.Overflow overflow) {
            this.width = width;
            this.indent = indent;
            this.overflow = Assert.notNull(overflow, "overflow");
        }
    }

    public static class CommandListRenderer implements IHelpElementRenderer<Map<String, Help>> {
        @Override
        public String render(Help help, Map<String, Help> value) {
            if (value.isEmpty())
                return "";

            int commandLength = maxLength(value.keySet());
            Help.TextTable textTable = Help.TextTable.forColumns(help.colorScheme().ansi(),
                    new Help.Column(commandLength + 2, 2, Help.Column.Overflow.SPAN),
                    new Help.Column(help.commandSpec().usageMessage().width() - (commandLength + 2),
                            2, Help.Column.Overflow.WRAP));
            for (Map.Entry<String, Help> entry : value.entrySet()) {
                Help helpObj = entry.getValue();
                CommandSpec command = helpObj.commandSpec();
                String header = command.usageMessage().header() != null
                        && command.usageMessage().header().length > 0
                                ? command.usageMessage().header()[0]
                                : (command.usageMessage().description() != null
                                        && command.usageMessage().description().length > 0
                                                ? command.usageMessage().description()[0]
                                                : "");
                Text[] lines = help.colorScheme().ansi().text(header).splitLines();
                textTable.addRowValues(renderCommandNames(helpObj), lines[0]);
                for (int i = 1; i < lines.length; i++) {
                    textTable.addRowValues(Ansi.EMPTY_TEXT, lines[i]);
                }
            }
            return textTable.toString();
        }

        protected Text renderCommandNames(Help help) {
            Text result = help.colorScheme.commandText(help.aliases.get(0));
            for (int i = 1; i < help.aliases.size(); i++) {
                result = result.concat(", ")
                        .concat(help.colorScheme.commandText(help.aliases.get(i)));
            }
            return result;
        }
    }

    public interface IHelpElementRenderer<T> {
        String render(Help help, T value);
    }

    /**
     * When customizing online help for {@link OptionSpec Option} details, a custom
     * {@code IOptionRenderer} can be used to create textual representation of an Option in a
     * tabular format: one or more rows, each containing one or more columns. The {@link Layout
     * Layout} is responsible for placing these text values in the {@link TextTable TextTable}.
     */
    public interface IOptionRenderer {
        /**
         * Returns a text representation of the specified option and its parameter(s) if any.
         * 
         * @param option
         *            the command line option to show online usage help for
         * @param parameterLabelRenderer
         *            responsible for rendering option parameters to text
         * @param scheme
         *            color scheme for applying ansi color styles to options and option parameters
         * @return a 2-dimensional array of text values: one or more rows, each containing one or
         *         more columns
         * @since 3.0
         */
        Text[][] render(OptionSpec option, Help.IParamLabelRenderer parameterLabelRenderer,
                Help.ColorScheme scheme);
    }

    /**
     * When customizing online help for {@linkplain PositionalParamSpec positional parameters}
     * details, a custom {@code IParameterRenderer} can be used to create textual representation of
     * a Parameters field in a tabular format: one or more rows, each containing one or more
     * columns. The {@link Layout Layout} is responsible for placing these text values in the
     * {@link TextTable TextTable}.
     */
    public interface IParameterRenderer {
        /**
         * Returns a text representation of the specified positional parameter.
         * 
         * @param param
         *            the positional parameter to show online usage help for
         * @param parameterLabelRenderer
         *            responsible for rendering parameter labels to text
         * @param scheme
         *            color scheme for applying ansi color styles to positional parameters
         * @return a 2-dimensional array of text values: one or more rows, each containing one or
         *         more columns
         * @since 3.0
         */
        Text[][] render(PositionalParamSpec param, Help.IParamLabelRenderer parameterLabelRenderer,
                Help.ColorScheme scheme);
    }

    /**
     * When customizing online usage help for an option parameter or a positional parameter, a
     * custom {@code IParamLabelRenderer} can be used to render the parameter name or label to a
     * String.
     */
    public interface IParamLabelRenderer {

        /**
         * Returns a text rendering of the option parameter or positional parameter; returns an
         * empty string {@code ""} if the option is a boolean and does not take a parameter.
         * 
         * @param argSpec
         *            the named or positional parameter with a parameter label
         * @param ansi
         *            determines whether ANSI escape codes should be emitted or not
         * @param styles
         *            the styles to apply to the parameter label
         * @return a text rendering of the Option parameter or positional parameter
         * @since 3.0
         */
        Text renderParameterLabel(ArgSpec argSpec, Help.Ansi ansi, List<Ansi.IStyle> styles);

        /**
         * Returns the separator between option name and param label.
         * 
         * @return the separator between option name and param label
         */
        String separator();
    }

    /**
     * Use a Layout to format usage help text for options and parameters in tabular format.
     * <p>
     * Delegates to the renderers to create {@link Text} values for the annotated fields, and uses a
     * {@link TextTable} to display these values in tabular format. Layout is responsible for
     * deciding which values to display where in the table. By default, Layout shows one option or
     * parameter per table row.
     * </p>
     * <p>
     * Customize by overriding the {@link #layout(CommandLine.Model.ArgSpec, Help.Ansi.Text[][])}
     * method.
     * </p>
     * 
     * @see IOptionRenderer rendering options to text
     * @see IParameterRenderer rendering parameters to text
     * @see TextTable showing values in a tabular format
     */
    public static class Layout {
        protected final Help.ColorScheme colorScheme;
        protected final Help.TextTable table;
        protected Help.IOptionRenderer optionRenderer;
        protected Help.IParameterRenderer parameterRenderer;

        /**
         * Constructs a Layout with the specified color scheme, the specified TextTable, the
         * {@linkplain Help#createDefaultOptionRenderer() default option renderer}, and the
         * {@linkplain Help#createDefaultParameterRenderer() default parameter renderer}.
         * 
         * @param colorScheme
         *            the color scheme to use for common, auto-generated parts of the usage help
         *            message
         * @param textTable
         *            the TextTable to lay out parts of the usage help message in tabular format
         */
        public Layout(Help.ColorScheme colorScheme, Help.TextTable textTable) {
            this(colorScheme, textTable, new DefaultOptionRenderer(false, " "),
                    new DefaultParameterRenderer(false, " "));
        }

        /**
         * Constructs a Layout with the specified color scheme, the specified TextTable, the
         * specified option renderer and the specified parameter renderer.
         * 
         * @param colorScheme
         *            the color scheme to use for common, auto-generated parts of the usage help
         *            message
         * @param optionRenderer
         *            the object responsible for rendering Options to Text
         * @param parameterRenderer
         *            the object responsible for rendering Parameters to Text
         * @param textTable
         *            the TextTable to lay out parts of the usage help message in tabular format
         */
        public Layout(Help.ColorScheme colorScheme, Help.TextTable textTable,
                Help.IOptionRenderer optionRenderer, Help.IParameterRenderer parameterRenderer) {
            this.colorScheme = Assert.notNull(colorScheme, "colorScheme");
            this.table = Assert.notNull(textTable, "textTable");
            this.optionRenderer = Assert.notNull(optionRenderer, "optionRenderer");
            this.parameterRenderer = Assert.notNull(parameterRenderer, "parameterRenderer");
        }

        /**
         * Constructs a Layout with the specified color scheme, a new default TextTable, the
         * {@linkplain Help#createDefaultOptionRenderer() default option renderer}, and the
         * {@linkplain Help#createDefaultParameterRenderer() default parameter renderer}.
         * 
         * @param colorScheme
         *            the color scheme to use for common, auto-generated parts of the usage help
         *            message
         */
        public Layout(Help.ColorScheme colorScheme, int tableWidth) {
            this(colorScheme, TextTable.forDefaultColumns(colorScheme.ansi(), tableWidth));
        }

        /**
         * Delegates to the {@link #optionRenderer option renderer} of this layout to obtain text
         * values for the specified {@link OptionSpec}, and then calls the
         * {@link #layout(CommandLine.Model.ArgSpec, Help.Ansi.Text[][])} method to write these text
         * values into the correct cells in the TextTable.
         * 
         * @param option
         *            the option argument
         * @param paramLabelRenderer
         *            knows how to render option parameters
         * @since 3.0
         */
        public void addOption(OptionSpec option, Help.IParamLabelRenderer paramLabelRenderer) {
            Text[][] values = optionRenderer.render(option, paramLabelRenderer, colorScheme);
            layout(option, values);
        }

        /**
         * Calls {@link #addOption(CommandLine.Model.OptionSpec, Help.IParamLabelRenderer)} for all
         * non-hidden Options in the list.
         * 
         * @param options
         *            options to add usage descriptions for
         * @param paramLabelRenderer
         *            object that knows how to render option parameters
         * @since 3.0
         */
        public void addOptions(List<OptionSpec> options,
                Help.IParamLabelRenderer paramLabelRenderer) {
            for (OptionSpec option : options) {
                if (!option.hidden()) {
                    addOption(option, paramLabelRenderer);
                }
            }
        }

        /**
         * Delegates to the {@link #parameterRenderer parameter renderer} of this layout to obtain
         * text values for the specified {@linkplain PositionalParamSpec positional parameter}, and
         * then calls {@link #layout(CommandLine.Model.ArgSpec, Help.Ansi.Text[][])} to write these
         * text values into the correct cells in the TextTable.
         * 
         * @param param
         *            the positional parameter
         * @param paramLabelRenderer
         *            knows how to render option parameters
         * @since 3.0
         */
        public void addPositionalParameter(PositionalParamSpec param,
                Help.IParamLabelRenderer paramLabelRenderer) {
            Text[][] values = parameterRenderer.render(param, paramLabelRenderer, colorScheme);
            layout(param, values);
        }

        /**
         * Calls
         * {@link #addPositionalParameter(CommandLine.Model.PositionalParamSpec, Help.IParamLabelRenderer)}
         * for all non-hidden Parameters in the list.
         * 
         * @param params
         *            positional parameters to add usage descriptions for
         * @param paramLabelRenderer
         *            knows how to render option parameters
         * @since 3.0
         */
        public void addPositionalParameters(List<PositionalParamSpec> params,
                Help.IParamLabelRenderer paramLabelRenderer) {
            for (PositionalParamSpec param : params) {
                if (!param.hidden()) {
                    addPositionalParameter(param, paramLabelRenderer);
                }
            }
        }

        /**
         * Copies the specified text values into the correct cells in the {@link TextTable}. This
         * implementation delegates to {@link TextTable#addRowValues(Help.Ansi.Text...)} for each
         * row of values.
         * <p>
         * Subclasses may override.
         * </p>
         * 
         * @param argSpec
         *            the Option or Parameters
         * @param cellValues
         *            the text values representing the Option/Parameters, to be displayed in tabular
         *            form
         * @since 3.0
         */
        public void layout(ArgSpec argSpec, Text[][] cellValues) {
            for (Text[] oneRow : cellValues) {
                table.addRowValues(oneRow);
            }
        }

        /**
         * Returns the section of the usage help message accumulated in the TextTable owned by this
         * layout.
         */
        @Override
        public String toString() {
            return table.toString();
        }
    }

    public static class OptionListRenderer extends ArgumentListRenderer<OptionSpec> {
        @Override
        protected Comparator<OptionSpec> getComparator(CommandSpec commandSpec) {
            return commandSpec.usageMessage().sortOptions() ? createShortOptionNameComparator()
                    : null;
        }

        @Override
        protected void populate(Help.Layout layout, List<OptionSpec> arguments,
                Help.IParamLabelRenderer labelRenderer) {
            layout.addOptions(arguments, labelRenderer);
        }
    }

    public static class ParameterListRenderer extends ArgumentListRenderer<PositionalParamSpec> {
        @Override
        protected Comparator<PositionalParamSpec> getComparator(CommandSpec commandSpec) {
            return null;
        }

        @Override
        protected void populate(Help.Layout layout, List<PositionalParamSpec> arguments,
                Help.IParamLabelRenderer labelRenderer) {
            layout.addPositionalParameters(arguments, labelRenderer);
        }
    }

    public static class Section<T> {
        public Function<Help, T> body;
        public Function<Help, IHelpElementRenderer<T>> bodyRenderer;
        public Function<Help, String> heading;
        public final String name;

        public Section(String name, Function<Help, String> heading, Function<Help, T> body,
                Function<Help, IHelpElementRenderer<T>> bodyRenderer) {
            this.name = name;
            this.heading = heading;
            this.body = body;
            this.bodyRenderer = bodyRenderer;
        }

        public String render(Help help) {
            return Utils.isEmpty(body.apply(help)) ? "" : renderHeading(help) + renderBody(help);
        }

        public String renderBody(Help help) {
            return bodyRenderer.apply(help).render(help, body.apply(help));
        }

        public String renderHeading(Help help) {
            return help.headingRenderer().render(help, heading.apply(help));
        }

        private String name() {
            return name;
        }
    }

    public static class SectionHeadingRenderer extends SimpleSectionMemberRenderer<String> {
        private String after;
        private String before;

        @Override
        public String render(Help help, String value) {
            if (StringUtils.isEmpty(value))
                return StringUtils.EMPTY;

            if (before != null || after != null) {
                value = before + value + after;
            }
            String result = doRender(help, value);
            result = result.endsWith(System.getProperty("line.separator"))
                    ? result.substring(0,
                            result.length() - System.getProperty("line.separator").length())
                    : result;
            return result + new String(spaces(countTrailingSpaces(value)));
        }

        public SectionHeadingRenderer withAfter(String after) {
            this.after = after;
            return this;
        }

        public SectionHeadingRenderer withBefore(String before) {
            this.before = before;
            return this;
        }
    }

    public static class Sections extends ListMap<String, Section<?>> {
        private static final long serialVersionUID = 1L;

        public Sections() {
            super(Section::name);
        }
    }

    public static class SimpleSectionBodyRenderer extends SimpleSectionMemberRenderer<String[]> {
        @Override
        public String render(Help help, String[] value) {
            return doRender(help, value);
        }
    }

    public static abstract class SimpleSectionMemberRenderer<T> implements IHelpElementRenderer<T> {
        protected String doRender(Help help, String... values) {
            if (Utils.isEmpty(values))
                return StringUtils.EMPTY;

            Help.TextTable table = getTable(help);
            for (String value : values) {
                // TODO: params?
                //                Text[] lines = ansi().new Text(format(value, params)).splitLines();
                Text[] lines = help.colorScheme().ansi().new Text(format(value)).splitLines();
                for (Text line : lines) {
                    table.addRowValues(line);
                }
            }
            return table.toString();
        }

        protected Help.TextTable getTable(Help help) {
            Help.TextTable table = TextTable.forColumnWidths(help.colorScheme().ansi(),
                    help.commandSpec().usageMessage().width());
            table.indentWrappedLines = 0;
            return table;
        }
    }

    public static class SynopsisRenderer implements IHelpElementRenderer<UsageMessageSpec> {
        @Override
        public String render(Help help, UsageMessageSpec usageMessage) {
            if (Utils.isNotEmpty(usageMessage.customSynopsis()))
                return help.simpleSectionBodyRenderer().render(help, usageMessage.customSynopsis());
            else
                return usageMessage.abbreviateSynopsis() ? renderAbbreviated(help)
                        : renderDetailed(help, createShortOptionArityAndNameComparator(),
                                help.commandSpec().commandLine() == null || help.commandSpec()
                                        .commandLine().isPosixClusteredShortOptionsAllowed());
        }

        public String renderAbbreviated(Help help) {
            Text text = help.ansi().new Text(0);
            text = renderOptions(help, help.commandSpec().options(), text, false);
            text = renderParameters(help, help.commandSpec().positionalParameters(), text, false);
            text = renderSubcommands(help, help.commandSpec().subcommands(), text, false);
            return help.colorScheme().commandText(help.commandSpec().qualifiedName()).concat(text)
                    .concat(System.getProperty("line.separator")).toString();
        }

        public String renderDetailed(Help help, Comparator<OptionSpec> optionSort,
                boolean clusterBooleanOptions) {
            Text text = help.ansi().new Text(0);
            List<OptionSpec> options = new ArrayList<>(help.commandSpec().options());
            if (optionSort != null) {
                Collections.sort(options, optionSort);
            }
            if (clusterBooleanOptions) {
                text = renderClusteredOptions(help, options, text);
            }
            text = renderOptions(help, options, text, true);
            text = renderParameters(help, help.commandSpec().positionalParameters(), text, true);
            text = renderSubcommands(help, help.commandSpec().subcommands(), text, true);
            return renderDetailedValues(help, help.commandSpec().qualifiedName(), text);
        }

        protected Text renderClusteredOptions(Help help, List<OptionSpec> options, Text text) {
            List<OptionSpec> booleanOptions = new ArrayList<>();
            StringBuilder clusteredRequired = new StringBuilder("-");
            StringBuilder clusteredOptional = new StringBuilder("-");
            for (OptionSpec option : options) {
                if (option.hidden()) {
                    continue;
                }
                if (option.type() == boolean.class || option.type() == Boolean.class) {
                    String shortestName = option.shortestName();
                    if (shortestName.length() == 2 && shortestName.startsWith("-")) {
                        booleanOptions.add(option);
                        if (option.required()) {
                            clusteredRequired.append(shortestName.substring(1));
                        } else {
                            clusteredOptional.append(shortestName.substring(1));
                        }
                    }
                }
            }
            options.removeAll(booleanOptions);
            if (clusteredRequired.length() > 1) { // initial length was 1
                text = text.concat(" ")
                        .concat(help.colorScheme().optionText(clusteredRequired.toString()));
            }
            if (clusteredOptional.length() > 1) { // initial length was 1
                text = text.concat(" [")
                        .concat(help.colorScheme().optionText(clusteredOptional.toString()))
                        .concat("]");
            }
            return text;
        }

        protected String renderDetailedValues(Help help, String commandName, Text optionText) {
            // Fix for #142: first line of synopsis overshoots max. characters
            int synopsisHeadingLength = synopsisHeadingLength(help);
            int firstColumnLength = commandName.length() + synopsisHeadingLength;

            // synopsis heading ("Usage: ") may be on the same line, so adjust column width
            Help.TextTable textTable = TextTable.forColumnWidths(help.ansi(), firstColumnLength,
                    help.commandSpec().usageMessage().width() - firstColumnLength);
            textTable.indentWrappedLines = 1; // don't worry about first line: options (2nd column) always start with a space

            // right-adjust the command name by length of synopsis heading
            Text PADDING = Ansi.OFF.new Text(stringOf('X', synopsisHeadingLength));
            textTable.addRowValues(PADDING.concat(help.colorScheme().commandText(commandName)),
                    optionText);
            return textTable.toString().substring(synopsisHeadingLength); // cut off leading synopsis heading spaces
        }

        protected Text renderOption(Help help, OptionSpec option, Text text) {
            if (!option.hidden()) {
                Text name = help.colorScheme().optionText(option.shortestName());
                Text param = help.parameterLabelRenderer().renderParameterLabel(option,
                        help.colorScheme().ansi(), help.colorScheme().optionParamStyles);
                if (option.required()) { // e.g., -x=VAL
                    text = text.concat(" ").concat(name).concat(param).concat("");
                    if (option.isMultiValue()) { // e.g., -x=VAL [-x=VAL]...
                        text = text.concat(" [").concat(name).concat(param).concat("]...");
                    }
                } else {
                    text = text.concat(" [").concat(name).concat(param).concat("]");
                    if (option.isMultiValue()) { // add ellipsis to show option is repeatable
                        text = text.concat("...");
                    }
                }
            }
            return text;
        }

        protected Text renderOptions(Help help, List<OptionSpec> options, Text text,
                boolean detailed) {
            if (!options.isEmpty()) {
                if (detailed) {
                    for (OptionSpec option : options) {
                        text = renderOption(help, option, text);
                    }
                } else {
                    text = text.concat(" [OPTIONS]");
                }
            }
            return text;
        }

        protected Text renderParameter(Help help, PositionalParamSpec parameter, Text text) {
            if (!parameter.hidden()) {
                text = text.concat(" ");
                Text label = help.parameterLabelRenderer().renderParameterLabel(parameter,
                        help.colorScheme().ansi(), help.colorScheme().parameterStyles);
                text = text.concat(label);
            }
            return text;
        }

        protected Text renderParameters(Help help, List<PositionalParamSpec> parameters, Text text,
                boolean detailed) {
            for (PositionalParamSpec parameter : parameters) {
                text = renderParameter(help, parameter, text);
            }
            return text;
        }

        protected Text renderSubcommands(Help help, Map<String, CommandLine> subcommands, Text text,
                boolean detailed) {
            if (!subcommands.isEmpty()) {
                text = text.concat(" [").concat("COMMAND").concat("]");
            }
            return text;
        }

        protected int synopsisHeadingLength(Help help) {
            String[] lines = help.commandSpec().usageMessage().synopsisHeading()
                    .split("\\r?\\n|\\r|%n", -1);
            return lines[lines.length - 1].length();
        }
    }

    /**
     * <p>
     * Responsible for spacing out {@link Text} values according to the {@link Column} definitions
     * the table was created with. Columns have a width, indentation, and an overflow policy that
     * decides what to do if a value is longer than the column's width.
     * </p>
     */
    public static class TextTable {
        /**
         * Helper class to index positions in a {@code Help.TextTable}.
         * 
         * @since 2.0
         */
        public static class Cell {
            /** Table column index (zero based). */
            public final int column;
            /** Table row index (zero based). */
            public final int row;

            /**
             * Constructs a new Cell with the specified coordinates in the table.
             * 
             * @param column
             *            the zero-based table column
             * @param row
             *            the zero-based table row
             */
            public Cell(int column, int row) {
                this.column = column;
                this.row = row;
            }
        }

        /**
         * Constructs a {@code TextTable} with the specified columns.
         * 
         * @param ansi
         *            whether to emit ANSI escape codes or not
         * @param columns
         *            columns to construct this TextTable with
         */
        public static Help.TextTable forColumns(Help.Ansi ansi, Help.Column... columns) {
            return new TextTable(ansi, columns);
        }

        /**
         * Constructs a new TextTable with columns with the specified width, all SPANning multiple
         * columns on overflow except the last column which WRAPS to the next row.
         * 
         * @param ansi
         *            whether to emit ANSI escape codes or not
         * @param columnWidths
         *            the width of each table column (all columns have zero indent)
         */
        public static Help.TextTable forColumnWidths(Help.Ansi ansi, int... columnWidths) {
            Help.Column[] columns = new Help.Column[columnWidths.length];
            for (int i = 0; i < columnWidths.length; i++) {
                columns[i] = new Column(columnWidths[i], 0,
                        i == columnWidths.length - 1 ? Overflow.WRAP : Overflow.SPAN);
            }
            return new TextTable(ansi, columns);
        }

        /**
         * Constructs a TextTable with five columns as follows:
         * <ol>
         * <li>required option/parameter marker (width: 2, indent: 0, TRUNCATE on overflow)</li>
         * <li>short option name (width: 2, indent: 0, TRUNCATE on overflow)</li>
         * <li>comma separator (width: 1, indent: 0, TRUNCATE on overflow)</li>
         * <li>long option name(s) (width: 24, indent: 1, SPAN multiple columns on overflow)</li>
         * <li>description line(s) (width: 51, indent: 1, WRAP to next row on overflow)</li>
         * </ol>
         * 
         * @param ansi
         *            whether to emit ANSI escape codes or not
         * @param usageHelpWidth
         *            the total width of the columns combined
         */
        public static Help.TextTable forDefaultColumns(Help.Ansi ansi, int usageHelpWidth) {
            return forDefaultColumns(ansi, defaultOptionsColumnWidth, usageHelpWidth);
        }

        /**
         * Constructs a TextTable with five columns as follows:
         * <ol>
         * <li>required option/parameter marker (width: 2, indent: 0, TRUNCATE on overflow)</li>
         * <li>short option name (width: 2, indent: 0, TRUNCATE on overflow)</li>
         * <li>comma separator (width: 1, indent: 0, TRUNCATE on overflow)</li>
         * <li>long option name(s) (width: 24, indent: 1, SPAN multiple columns on overflow)</li>
         * <li>description line(s) (width: 51, indent: 1, WRAP to next row on overflow)</li>
         * </ol>
         * 
         * @param ansi
         *            whether to emit ANSI escape codes or not
         * @param longOptionsColumnWidth
         *            the width of the long options column
         * @param usageHelpWidth
         *            the total width of the columns combined
         */
        public static Help.TextTable forDefaultColumns(Help.Ansi ansi, int longOptionsColumnWidth,
                int usageHelpWidth) {
            // "* -c, --create                Creates a ...."
            return forColumns(ansi, new Column(2, 0, Overflow.TRUNCATE), // "*"
                    new Column(2, 0, Overflow.TRUNCATE), // "-c"
                    new Column(1, 0, Overflow.TRUNCATE), // ","
                    new Column(longOptionsColumnWidth, 1, Overflow.SPAN), // " --create"
                    new Column(usageHelpWidth - longOptionsColumnWidth, 1, Overflow.WRAP)); // " Creates a ..."
        }

        private static int copy(Text value, Text destination, int offset) {
            int length = Math.min(value.length, destination.maxLength - offset);
            value.getStyledChars(value.from, length, destination, offset);
            return length;
        }

        private static int length(Text str) {
            return str.length; // TODO count some characters as double length
        }

        /** The column definitions of this table. */
        private final Help.Column[] columns;

        /** The {@code char[]} slots of the {@code TextTable} to copy text values into. */
        protected final List<Text> columnValues = new ArrayList<>();

        /** By default, indent wrapped lines by 2 spaces. */
        public int indentWrappedLines = 2;

        private final Help.Ansi ansi;

        private final int tableWidth;

        protected TextTable(Help.Ansi ansi, Help.Column[] columns) {
            this.ansi = Assert.notNull(ansi, "ansi");
            this.columns = Assert.notNull(columns, "columns").clone();
            if (columns.length == 0)
                throw new IllegalArgumentException("At least one column is required");
            int totalWidth = 0;
            for (Help.Column col : columns) {
                totalWidth += col.width;
            }
            tableWidth = totalWidth;
        }

        /**
         * Adds the required {@code char[]} slots for a new row to the {@link #columnValues} field.
         */
        public void addEmptyRow() {
            for (int i = 0; i < columns.length; i++) {
                columnValues.add(ansi.new Text(columns[i].width));
            }
        }

        /**
         * Delegates to {@link #addRowValues(Help.Ansi.Text...)}.
         * 
         * @param values
         *            the text values to display in each column of the current row
         */
        public void addRowValues(String... values) {
            Text[] array = new Text[values.length];
            for (int i = 0; i < array.length; i++) {
                array[i] = values[i] == null ? Ansi.EMPTY_TEXT : ansi.new Text(values[i]);
            }
            addRowValues(array);
        }

        /**
         * Adds a new {@linkplain TextTable#addEmptyRow() empty row}, then calls
         * {@link TextTable#putValue(int, int, Help.Ansi.Text) putValue} for each of the specified
         * values, adding more empty rows if the return value indicates that the value spanned
         * multiple columns or was wrapped to multiple rows.
         * 
         * @param values
         *            the values to write into a new row in this TextTable
         * @throws IllegalArgumentException
         *             if the number of values exceeds the number of Columns in this table
         */
        public void addRowValues(Text... values) {
            if (values.length > columns.length)
                throw new IllegalArgumentException(
                        values.length + " values don't fit in " + columns.length + " columns");
            addEmptyRow();
            for (int col = 0; col < values.length; col++) {
                int row = rowCount() - 1;// write to last row: previous value may have wrapped to next row
                TextTable.Cell cell = putValue(row, col, values[col]);

                // add row if a value spanned/wrapped and there are still remaining values
                if ((cell.row != row || cell.column != col) && col != values.length - 1) {
                    addEmptyRow();
                }
            }
        }

        /**
         * Returns the {@code Text} slot at the specified row and column to write a text value into.
         * 
         * @param row
         *            the row of the cell whose Text to return
         * @param col
         *            the column of the cell whose Text to return
         * @return the Text object at the specified row and column
         * @deprecated use {@link #textAt(int, int)} instead
         */
        @Deprecated
        public Text cellAt(int row, int col) {
            return textAt(row, col);
        }

        /** The column definitions of this table. */
        public Help.Column[] columns() {
            return columns.clone();
        }

        /**
         * Writes the specified value into the cell at the specified row and column and returns the
         * last row and column written to. Depending on the Column's {@link Column#overflow
         * Overflow} policy, the value may span multiple columns or wrap to multiple rows when
         * larger than the column width.
         * 
         * @param row
         *            the target row in the table
         * @param col
         *            the target column in the table to write to
         * @param value
         *            the value to write
         * @return a Cell indicating the position in the table that was last written to (since 2.0)
         * @throws IllegalArgumentException
         *             if the specified row exceeds the table's {@linkplain TextTable#rowCount() row
         *             count}
         * @since 2.0 (previous versions returned a {@code java.awt.Point} object)
         */
        public TextTable.Cell putValue(int row, int col, Text value) {
            if (row > rowCount() - 1)
                throw new IllegalArgumentException(
                        "Cannot write to row " + row + ": rowCount=" + rowCount());
            if (value == null || value.plain.length() == 0)
                return new Cell(col, row);
            Help.Column column = columns[col];
            int indent = column.indent;
            switch (column.overflow) {
                case TRUNCATE:
                    copy(value, textAt(row, col), indent);
                    return new Cell(col, row);
                case SPAN:
                    int startColumn = col;
                    do {
                        boolean lastColumn = col == columns.length - 1;
                        int charsWritten = lastColumn
                                ? copy(BreakIterator.getLineInstance(), value, textAt(row, col),
                                        indent)
                                : copy(value, textAt(row, col), indent);
                        value = value.substring(charsWritten);
                        indent = 0;
                        if (value.length > 0) { // value did not fit in column
                            ++col; // write remainder of value in next column
                        }
                        if (value.length > 0 && col >= columns.length) { // we filled up all columns on this row
                            addEmptyRow();
                            row++;
                            col = startColumn;
                            indent = column.indent + indentWrappedLines;
                        }
                    } while (value.length > 0);
                    return new Cell(col, row);
                case WRAP:
                    BreakIterator lineBreakIterator = BreakIterator.getLineInstance();
                    do {
                        int charsWritten = copy(lineBreakIterator, value, textAt(row, col), indent);
                        value = value.substring(charsWritten);
                        indent = column.indent + indentWrappedLines;
                        if (value.length > 0) { // value did not fit in column
                            ++row; // write remainder of value in next row
                            addEmptyRow();
                        }
                    } while (value.length > 0);
                    return new Cell(col, row);
            }
            throw new IllegalStateException(column.overflow.toString());
        }

        /**
         * Returns the current number of rows of this {@code TextTable}.
         * 
         * @return the current number of rows in this TextTable
         */
        public int rowCount() {
            return columnValues.size() / columns.length;
        }

        /**
         * Returns the {@code Text} slot at the specified row and column to write a text value into.
         * 
         * @param row
         *            the row of the cell whose Text to return
         * @param col
         *            the column of the cell whose Text to return
         * @return the Text object at the specified row and column
         * @since 2.0
         */
        public Text textAt(int row, int col) {
            return columnValues.get(col + (row * columns.length));
        }

        @Override
        public String toString() {
            return toString(new StringBuilder()).toString();
        }

        /**
         * Copies the text representation that we built up from the options into the specified
         * StringBuilder.
         * 
         * @param text
         *            the StringBuilder to write into
         * @return the specified StringBuilder object (to allow method chaining and a more fluid
         *         API)
         */
        public StringBuilder toString(StringBuilder text) {
            int columnCount = this.columns.length;
            StringBuilder row = new StringBuilder(tableWidth);
            for (int i = 0; i < columnValues.size(); i++) {
                Text column = columnValues.get(i);
                row.append(column.toString());
                row.append(new String(spaces(columns[i % columnCount].width - column.length)));
                if (i % columnCount == columnCount - 1) {
                    int lastChar = row.length() - 1;
                    while (lastChar >= 0 && row.charAt(lastChar) == ' ') {
                        lastChar--;
                    } // rtrim
                    row.setLength(lastChar + 1);
                    text.append(row.toString()).append(System.getProperty("line.separator"));
                    row.setLength(0);
                }
            }
            return text;
        }

        private int copy(BreakIterator line, Text text, Text columnValue, int offset) {
            // Deceive the BreakIterator to ensure no line breaks after '-' character
            line.setText(text.plainString().replace("-", "\u00ff"));
            int done = 0;
            for (int start = line.first(), end = line
                    .next(); end != BreakIterator.DONE; start = end, end = line.next()) {
                Text word = text.substring(start, end); //.replace("\u00ff", "-"); // not needed
                if (columnValue.maxLength >= offset + done + length(word)) {
                    done += copy(word, columnValue, offset + done); // TODO messages length
                } else {
                    break;
                }
            }
            if (done == 0 && length(text) > columnValue.maxLength) {
                // The value is a single word that is too big to be written to the column. Write as much as we can.
                done = copy(text, columnValue, offset);
            }
            return done;
        }
    }

    /** Controls the visibility of certain aspects of the usage help message. */
    public enum Visibility {
        ALWAYS, NEVER, ON_DEMAND
    }

    /**
     * The DefaultOptionRenderer converts {@link OptionSpec Options} to five columns of text to
     * match the default {@linkplain TextTable TextTable} column layout. The first row of values
     * looks like this:
     * <ol>
     * <li>the required option marker (if the option is required)</li>
     * <li>2-character short option name (or empty string if no short option exists)</li>
     * <li>comma separator (only if both short option and long option exist, empty string
     * otherwise)</li>
     * <li>comma-separated string with long option name(s)</li>
     * <li>first element of the {@link OptionSpec#description()} array</li>
     * </ol>
     * <p>
     * Following this, there will be one row for each of the remaining elements of the
     * {@link OptionSpec#description()} array, and these rows look like {@code {"", "", "",
     * option.description()[i]}}.
     * </p>
     */
    static class DefaultOptionRenderer implements Help.IOptionRenderer {
        private String requiredMarker = " ";
        private boolean showDefaultValues;
        private String sep;

        public DefaultOptionRenderer(boolean showDefaultValues, String requiredMarker) {
            this.showDefaultValues = showDefaultValues;
            this.requiredMarker = Assert.notNull(requiredMarker, "requiredMarker");
        }

        @Override
        public Text[][] render(OptionSpec option, Help.IParamLabelRenderer paramLabelRenderer,
                Help.ColorScheme scheme) {
            String[] names = ShortestFirst.sort(option.names());
            int shortOptionCount = names[0].length() == 2 ? 1 : 0;
            String shortOption = shortOptionCount > 0 ? names[0] : "";
            sep = shortOptionCount > 0 && names.length > 1 ? "," : "";

            String longOption = join(names, shortOptionCount, names.length - shortOptionCount,
                    ", ");
            Text longOptionText = createLongOptionText(option, paramLabelRenderer, scheme,
                    longOption);

            String requiredOption = option.required() ? requiredMarker : "";
            return renderDescriptionLines(option, scheme, requiredOption, shortOption,
                    longOptionText);
        }

        private Text createLongOptionText(OptionSpec option, Help.IParamLabelRenderer renderer,
                Help.ColorScheme scheme, String longOption) {
            Text paramLabelText = renderer.renderParameterLabel(option, scheme.ansi(),
                    scheme.optionParamStyles);

            // if no long option, fill in the space between the short option name and the param label value
            if (paramLabelText.length > 0 && longOption.length() == 0) {
                sep = renderer.separator();
                // #181 paramLabelText may be =LABEL or [=LABEL...]
                int sepStart = paramLabelText.plainString().indexOf(sep);
                Text prefix = paramLabelText.substring(0, sepStart);
                paramLabelText = prefix.concat(paramLabelText.substring(sepStart + sep.length()));
            }
            Text longOptionText = scheme.optionText(longOption);
            longOptionText = longOptionText.concat(paramLabelText);
            return longOptionText;
        }

        private Text[][] renderDescriptionLines(OptionSpec option, Help.ColorScheme scheme,
                String requiredOption, String shortOption, Text longOptionText) {
            Text EMPTY = Ansi.EMPTY_TEXT;
            boolean[] showDefault = { option.internalShowDefaultValue(showDefaultValues) };
            List<Text[]> result = new ArrayList<>();
            String[] description = option.renderedDescription();
            Text[] descriptionFirstLines = createDescriptionFirstLines(scheme, option, description,
                    showDefault);
            result.add(new Text[] { scheme.optionText(requiredOption),
                    scheme.optionText(shortOption), scheme.ansi().new Text(sep), longOptionText,
                    descriptionFirstLines[0] });
            for (int i = 1; i < descriptionFirstLines.length; i++) {
                result.add(new Text[] { EMPTY, EMPTY, EMPTY, EMPTY, descriptionFirstLines[i] });
            }
            for (int i = 1; i < description.length; i++) {
                Text[] descriptionNextLines = scheme.ansi().new Text(description[i]).splitLines();
                for (Text line : descriptionNextLines) {
                    result.add(new Text[] { EMPTY, EMPTY, EMPTY, EMPTY, line });
                }
            }
            if (showDefault[0]) {
                addTrailingDefaultLine(result, option, scheme);
            }
            return result.toArray(new Text[result.size()][]);
        }
    }

    /**
     * The DefaultParameterRenderer converts {@linkplain PositionalParamSpec positional parameters}
     * to five columns of text to match the default {@linkplain TextTable TextTable} column layout.
     * The first row of values looks like this:
     * <ol>
     * <li>the required option marker (if the parameter's arity is to have at least one value)</li>
     * <li>empty string</li>
     * <li>empty string</li>
     * <li>parameter(s) label as rendered by the {@link IParamLabelRenderer}</li>
     * <li>first element of the {@link PositionalParamSpec#description()} array</li>
     * </ol>
     * <p>
     * Following this, there will be one row for each of the remaining elements of the
     * {@link PositionalParamSpec#description()} array, and these rows look like {@code {"", "", "",
     * param.description()[i]}}.
     * </p>
     */
    static class DefaultParameterRenderer implements Help.IParameterRenderer {
        private String requiredMarker = " ";
        private boolean showDefaultValues;

        public DefaultParameterRenderer(boolean showDefaultValues, String requiredMarker) {
            this.showDefaultValues = showDefaultValues;
            this.requiredMarker = Assert.notNull(requiredMarker, "requiredMarker");
        }

        @Override
        public Text[][] render(PositionalParamSpec param,
                Help.IParamLabelRenderer paramLabelRenderer, Help.ColorScheme scheme) {
            Text label = paramLabelRenderer.renderParameterLabel(param, scheme.ansi(),
                    scheme.parameterStyles);
            Text requiredParameter = scheme
                    .parameterText(param.arity().min > 0 ? requiredMarker : "");

            Text EMPTY = Ansi.EMPTY_TEXT;
            boolean[] showDefault = { param.internalShowDefaultValue(showDefaultValues) };
            List<Text[]> result = new ArrayList<>();
            String[] description = param.renderedDescription();
            Text[] descriptionFirstLines = createDescriptionFirstLines(scheme, param, description,
                    showDefault);
            result.add(new Text[] { requiredParameter, EMPTY, EMPTY, label,
                    descriptionFirstLines[0] });
            for (int i = 1; i < descriptionFirstLines.length; i++) {
                result.add(new Text[] { EMPTY, EMPTY, EMPTY, EMPTY, descriptionFirstLines[i] });
            }
            for (int i = 1; i < description.length; i++) {
                Text[] descriptionNextLines = scheme.ansi().new Text(description[i]).splitLines();
                for (Text line : descriptionNextLines) {
                    result.add(new Text[] { EMPTY, EMPTY, EMPTY, EMPTY, line });
                }
            }
            if (showDefault[0]) {
                addTrailingDefaultLine(result, param, scheme);
            }
            return result.toArray(new Text[result.size()][]);
        }
    }

    /**
     * DefaultParamLabelRenderer separates option parameters from their {@linkplain OptionSpec
     * option names} with a {@linkplain ParserSpec#separator() separator} string, and, unless
     * {@link ArgSpec#hideParamSyntax()} is true, surrounds optional values with {@code '['} and
     * {@code ']'} characters and uses ellipses ("...") to indicate that any number of values is
     * allowed for options or parameters with variable arity.
     */
    static class DefaultParamLabelRenderer implements Help.IParamLabelRenderer {
        private final CommandSpec commandSpec;

        /** Constructs a new DefaultParamLabelRenderer with the specified separator string. */
        public DefaultParamLabelRenderer(CommandSpec commandSpec) {
            this.commandSpec = Assert.notNull(commandSpec, "commandSpec");
        }

        @Override
        public Text renderParameterLabel(ArgSpec argSpec, Help.Ansi ansi,
                List<Ansi.IStyle> styles) {
            Range capacity = argSpec.isOption() ? argSpec.arity()
                    : ((PositionalParamSpec) argSpec).capacity();
            if (capacity.max == 0)
                return ansi.new Text("");
            if (argSpec.hideParamSyntax())
                return ansi.apply((argSpec.isOption() ? separator() : "") + argSpec.paramLabel(),
                        styles);

            Text paramName = ansi.apply(argSpec.paramLabel(), styles);
            String split = argSpec.splitRegex();
            String mandatorySep = StringUtils.isBlank(split) ? " " : split;
            String optionalSep = StringUtils.isBlank(split) ? " [" : "[" + split;

            boolean unlimitedSplit = !StringUtils.isBlank(split)
                    && !commandSpec.parser().limitSplit();
            boolean limitedSplit = !StringUtils.isBlank(split) && commandSpec.parser().limitSplit();
            Text repeating = paramName;
            int paramCount = 1;
            if (unlimitedSplit) {
                repeating = paramName.concat("[" + split).concat(paramName).concat("...]");
                paramCount++;
                mandatorySep = " ";
                optionalSep = " [";
            }
            Text result = repeating;

            int done = 1;
            for (; done < capacity.min; done++) {
                result = result.concat(mandatorySep).concat(repeating); // " PARAM" or ",PARAM"
                paramCount += paramCount;
            }
            if (!capacity.isVariable) {
                for (int i = done; i < capacity.max; i++) {
                    result = result.concat(optionalSep).concat(paramName); // " [PARAM" or "[,PARAM"
                    paramCount++;
                }
                for (int i = done; i < capacity.max; i++) {
                    result = result.concat("]");
                }
            }
            // show an extra trailing "[,PARAM]" if split and either max=* or splitting is not restricted to max
            boolean effectivelyVariable = capacity.isVariable || (limitedSplit && paramCount == 1);
            if (limitedSplit && effectivelyVariable && paramCount == 1) {
                result = result.concat(optionalSep).concat(repeating).concat("]"); // PARAM[,PARAM]...
            }
            if (effectivelyVariable) {
                if (!argSpec.arity().isVariable && argSpec.arity().min > 1) {
                    result = ansi.new Text("(").concat(result).concat(")"); // repeating group
                }
                result = result.concat("..."); // PARAM...
            }
            String optionSeparator = argSpec.isOption() ? separator() : "";
            if (capacity.min == 0) { // optional
                String sep2 = StringUtils.isBlank(optionSeparator) ? optionSeparator + "["
                        : "[" + optionSeparator;
                result = ansi.new Text(sep2).concat(result).concat("]");
            } else {
                result = ansi.new Text(optionSeparator).concat(result);
            }
            return result;
        }

        @Override
        public String separator() {
            return commandSpec.parser().separator();
        }
    }

    /**
     * The MinimalOptionRenderer converts {@link OptionSpec Options} to a single row with two
     * columns of text: an option name and a description. If multiple names or description lines
     * exist, the first value is used.
     */
    static class MinimalOptionRenderer implements Help.IOptionRenderer {
        @Override
        public Text[][] render(OptionSpec option, Help.IParamLabelRenderer parameterLabelRenderer,
                Help.ColorScheme scheme) {
            Text optionText = scheme.optionText(option.names()[0]);
            Text paramLabelText = parameterLabelRenderer.renderParameterLabel(option, scheme.ansi(),
                    scheme.optionParamStyles);
            optionText = optionText.concat(paramLabelText);
            return new Text[][] { { optionText, scheme.ansi().new Text(
                    option.description().length == 0 ? "" : option.description()[0]) } };
        }
    }

    /**
     * The MinimalParameterRenderer converts {@linkplain PositionalParamSpec positional parameters}
     * to a single row with two columns of text: the parameters label and a description. If multiple
     * description lines exist, the first value is used.
     */
    static class MinimalParameterRenderer implements Help.IParameterRenderer {
        @Override
        public Text[][] render(PositionalParamSpec param,
                Help.IParamLabelRenderer parameterLabelRenderer, Help.ColorScheme scheme) {
            return new Text[][] { {
                    parameterLabelRenderer.renderParameterLabel(param, scheme.ansi(),
                            scheme.parameterStyles),
                    scheme.ansi().new Text(
                            param.description().length == 0 ? "" : param.description()[0]) } };
        }
    }

    /** Sorts short strings before longer strings. */
    public static class ShortestFirst implements Comparator<String> {
        /** Sorts the specified array of Strings longest-first and returns it. */
        public static String[] longestFirst(String[] names) {
            Arrays.sort(names, Collections.reverseOrder(new ShortestFirst()));
            return names;
        }

        /** Sorts the specified array of Strings shortest-first and returns it. */
        public static String[] sort(String[] names) {
            Arrays.sort(names, new ShortestFirst());
            return names;
        }

        @Override
        public int compare(String o1, String o2) {
            return o1.length() - o2.length();
        }
    }

    /**
     * Sorts {@code OptionSpec} instances by their max arity first, then their min arity, then
     * delegates to super class.
     */
    static class SortByOptionArityAndNameAlphabetically
            extends Help.SortByShortestOptionNameAlphabetically {
        @Override
        public int compare(OptionSpec o1, OptionSpec o2) {
            Range arity1 = o1.arity();
            Range arity2 = o2.arity();
            int result = arity1.max - arity2.max;
            if (result == 0) {
                result = arity1.min - arity2.min;
            }
            if (result == 0) { // arity is same
                if (o1.isMultiValue() && !o2.isMultiValue()) {
                    result = 1;
                } // f1 > f2
                if (!o1.isMultiValue() && o2.isMultiValue()) {
                    result = -1;
                } // f1 < f2
            }
            return result == 0 ? super.compare(o1, o2) : result;
        }
    }

    /**
     * Sorts {@code OptionSpec} instances by their name in case-insensitive alphabetic order. If an
     * option has multiple names, the shortest name is used for the sorting. Help options follow
     * non-help options.
     */
    static class SortByShortestOptionNameAlphabetically implements Comparator<OptionSpec> {
        @Override
        public int compare(OptionSpec o1, OptionSpec o2) {
            if (o1 == null)
                return 1;
            else if (o2 == null)
                return -1;
            String[] names1 = ShortestFirst.sort(o1.names());
            String[] names2 = ShortestFirst.sort(o2.names());
            int result = names1[0].toUpperCase().compareTo(names2[0].toUpperCase()); // case insensitive sort
            result = result == 0 ? -names1[0].compareTo(names2[0]) : result; // lower case before upper case
            return o1.help() == o2.help() ? result : o2.help() ? -1 : 1; // help options come last
        }
    }

    /**
     * Constant String holding the default program name, value defined in
     * {@link CommandSpec#DEFAULT_COMMAND_NAME}.
     */
    protected static final String DEFAULT_COMMAND_NAME = CommandSpec.DEFAULT_COMMAND_NAME;

    /**
     * Constant String holding the default string that separates options from option parameters,
     * value defined in {@link ParserSpec#DEFAULT_SEPARATOR}.
     */
    protected static final String DEFAULT_SEPARATOR = ParserSpec.DEFAULT_SEPARATOR;

    private final static int defaultOptionsColumnWidth = 24;

    /**
     * Returns a new minimal OptionRenderer which converts {@link OptionSpec Options} to a single
     * row with two columns of text: an option name and a description. If multiple names or
     * descriptions exist, the first value is used.
     * 
     * @return a new minimal OptionRenderer
     */
    public static Help.IOptionRenderer createMinimalOptionRenderer() {
        return new MinimalOptionRenderer();
    }

    /**
     * Returns a new minimal ParameterRenderer which converts {@linkplain PositionalParamSpec
     * positional parameters} to a single row with two columns of text: an option name and a
     * description. If multiple descriptions exist, the first value is used.
     * 
     * @return a new minimal ParameterRenderer
     */
    public static Help.IParameterRenderer createMinimalParameterRenderer() {
        return new MinimalParameterRenderer();
    }

    /**
     * Returns a value renderer that returns the {@code paramLabel} if defined or the field name
     * otherwise.
     * 
     * @return a new minimal ParamLabelRenderer
     */
    public static Help.IParamLabelRenderer createMinimalParamLabelRenderer() {
        return new IParamLabelRenderer() {
            @Override
            public Text renderParameterLabel(ArgSpec argSpec, Help.Ansi ansi,
                    List<Ansi.IStyle> styles) {
                return ansi.apply(argSpec.paramLabel(), styles);
            }

            @Override
            public String separator() {
                return "";
            }
        };
    }

    /**
     * Sorts {@link OptionSpec OptionSpecs} by their option {@linkplain Range#max max arity} first,
     * by {@linkplain Range#min min arity} next, and by
     * {@linkplain #createShortOptionNameComparator() option name} last.
     * 
     * @return a comparator that sorts OptionSpecs by arity first, then their option name
     */
    public static Comparator<OptionSpec> createShortOptionArityAndNameComparator() {
        return new SortByOptionArityAndNameAlphabetically();
    }

    /**
     * Sorts {@link OptionSpec OptionSpecs} by their option name in case-insensitive alphabetic
     * order. If an option has multiple names, the shortest name is used for the sorting. Help
     * options follow non-help options.
     * 
     * @return a comparator that sorts OptionSpecs by their option name in case-insensitive
     *         alphabetic order
     */
    public static Comparator<OptionSpec> createShortOptionNameComparator() {
        return new SortByShortestOptionNameAlphabetically();
    }

    /**
     * Creates and returns a new {@link ColorScheme} initialized with picocli default values:
     * commands are bold, options and parameters use a yellow foreground, and option parameters use
     * italic.
     * 
     * @param ansi
     *            whether the usage help message should contain ANSI escape codes or not
     * @return a new default color scheme
     */
    public static Help.ColorScheme defaultColorScheme(Help.Ansi ansi) {
        return new ColorScheme(ansi).commands(Style.bold).options(Style.fg_yellow)
                .parameters(Style.fg_yellow).optionParams(Style.italic);
    }

    /**
     * Formats each of the specified values and appends it to the specified StringBuilder.
     * 
     * @param ansi
     *            whether the result should contain ANSI escape codes or not
     * @param usageHelpWidth
     *            the width of the usage help message
     * @param values
     *            the values to format and append to the StringBuilder
     * @param sb
     *            the StringBuilder to collect the formatted strings
     * @param params
     *            the parameters to pass to the format method when formatting each value
     * @return the specified StringBuilder
     */
    public static StringBuilder join(Help.Ansi ansi, int usageHelpWidth, String[] values,
            StringBuilder sb, Object... params) {
        if (values != null) {
            Help.TextTable table = TextTable.forColumnWidths(ansi, usageHelpWidth);
            table.indentWrappedLines = 0;
            for (String summaryLine : values) {
                Text[] lines = ansi.new Text(format(summaryLine, params)).splitLines();
                for (Text line : lines) {
                    table.addRowValues(line);
                }
            }
            table.toString(sb);
        }
        return sb;
    }

    /**
     * Sorts short strings before longer strings.
     * 
     * @return a comparators that sorts short strings before longer strings
     */
    public static Comparator<String> shortestFirst() {
        return new ShortestFirst();
    }

    private static void addTrailingDefaultLine(List<Text[]> result, ArgSpec arg,
            Help.ColorScheme scheme) {
        Text EMPTY = Ansi.EMPTY_TEXT;
        result.add(new Text[] { EMPTY, EMPTY, EMPTY, EMPTY,
                scheme.ansi().new Text("  Default: " + arg.defaultValueString()) });
    }

    private static int countTrailingSpaces(String str) {
        if (str == null)
            return 0;
        int trailingSpaces = 0;
        for (int i = str.length() - 1; i >= 0 && str.charAt(i) == ' '; i--) {
            trailingSpaces++;
        }
        return trailingSpaces;
    }

    private static Text[] createDescriptionFirstLines(Help.ColorScheme scheme, ArgSpec arg,
            String[] description, boolean[] showDefault) {
        Text[] result = scheme.ansi().new Text(CommandLine.str(description, 0)).splitLines();
        if (result.length == 0 || (result.length == 1 && result[0].plain.length() == 0)) {
            if (showDefault[0]) {
                result = new Text[] {
                        scheme.ansi().new Text("  Default: " + arg.defaultValueString()) };
                showDefault[0] = false; // don't show the default value twice
            } else {
                result = new Text[] { Ansi.EMPTY_TEXT };
            }
        }
        return result;
    }

    private static String format(String formatString, Object... params) {
        return formatString == null ? "" : String.format(formatString, params);
    }

    private static String join(String[] names, int offset, int length, String separator) {
        if (names == null)
            return "";
        StringBuilder result = new StringBuilder();
        for (int i = offset; i < offset + length; i++) {
            result.append((i > offset) ? separator : "").append(names[i]);
        }
        return result.toString();
    }

    private static int maxLength(Collection<String> any) {
        List<String> strings = new ArrayList<>(any);
        Collections.sort(strings, Collections.reverseOrder(Help.shortestFirst()));
        return strings.get(0).length();
    }

    private static char[] spaces(int length) {
        char[] result = new char[length];
        Arrays.fill(result, ' ');
        return result;
    }

    private static String stringOf(char chr, int length) {
        char[] buff = new char[length];
        Arrays.fill(buff, chr);
        return new String(buff);
    }

    protected final CommandSpec commandSpec;

    protected final Help.ColorScheme colorScheme;

    protected final Map<String, Help> commands = new LinkedHashMap<>();

    protected List<String> aliases = Collections.emptyList();

    protected Sections sections;

    private CommandListRenderer commandListRenderer;

    private SectionHeadingRenderer headingRenderer;

    private ArgumentListRenderer<OptionSpec> optionListRenderer;

    private Help.IParamLabelRenderer parameterLabelRenderer;

    private ArgumentListRenderer<PositionalParamSpec> parameterListRenderer;

    private SimpleSectionBodyRenderer simpleSectionBodyRenderer;

    private SynopsisRenderer synopsisRenderer;

    /**
     * Constructs a new {@code Help} instance with the specified color scheme, initialized from
     * annotatations on the specified class and superclasses.
     * 
     * @param commandSpec
     *            the command model to create usage help for
     * @param colorScheme
     *            the color scheme to use
     */
    public Help(CommandSpec commandSpec, Help.ColorScheme colorScheme) {
        this.commandSpec = Assert.notNull(commandSpec, "commandSpec");
        this.aliases = new ArrayList<>(Arrays.asList(commandSpec.aliases()));
        this.aliases.add(0, commandSpec.name());
        this.colorScheme = Assert.notNull(colorScheme, "colorScheme").applySystemProperties();
        parameterLabelRenderer = createDefaultParamLabelRenderer(); // uses help separator
        this.addAllSubcommands(commandSpec.subcommands());
    }

    /**
     * Constructs a new {@code Help} instance with a default color scheme, initialized from
     * annotatations on the specified class and superclasses.
     * 
     * @param command
     *            the annotated object to create usage help for
     */
    public Help(Object command) {
        this(command, Ansi.AUTO);
    }

    /**
     * Constructs a new {@code Help} instance with a default color scheme, initialized from
     * annotatations on the specified class and superclasses.
     * 
     * @param command
     *            the annotated object to create usage help for
     * @param ansi
     *            whether to emit ANSI escape codes or not
     */
    public Help(Object command, Help.Ansi ansi) {
        this(command, defaultColorScheme(ansi));
    }

    /**
     * Constructs a new {@code Help} instance with the specified color scheme, initialized from
     * annotatations on the specified class and superclasses.
     * 
     * @param command
     *            the annotated object to create usage help for
     * @param colorScheme
     *            the color scheme to use
     * @deprecated use
     *             {@link picocli.Help#Help(picocli.CommandLine.Model.CommandSpec, picocli.Help.ColorScheme)}
     */
    @Deprecated
    public Help(Object command, Help.ColorScheme colorScheme) {
        this(CommandSpec.forAnnotatedObject(command, new DefaultFactory()), colorScheme);
    }

    /**
     * Registers all specified subcommands with this Help.
     * 
     * @param commands
     *            maps the command names to the associated CommandLine object
     * @return this Help instance (for method chaining)
     * @see CommandLine#getSubcommands()
     */
    public Help addAllSubcommands(Map<String, CommandLine> commands) {
        if (commands != null) {
            // first collect aliases
            Map<CommandLine, List<String>> done = new IdentityHashMap<>();
            for (CommandLine cmd : commands.values()) {
                if (!done.containsKey(cmd)) {
                    done.put(cmd, new ArrayList<>(Arrays.asList(cmd.getCommandSpec().aliases())));
                }
            }
            // then loop over all names that the command was registered with and add this name to the front of the list (if it isn't already in the list)
            for (Map.Entry<String, CommandLine> entry : commands.entrySet()) {
                List<String> aliases = done.get(entry.getValue());
                if (!aliases.contains(entry.getKey())) {
                    aliases.add(0, entry.getKey());
                }
            }
            // The aliases list for each command now has at least one entry, with the main name at the front.
            // Now we loop over the commands in the order that they were registered on their parent command.
            for (Map.Entry<String, CommandLine> entry : commands.entrySet()) {
                // not registering hidden commands is easier than suppressing display in Help.commandList():
                // if all subcommands are hidden, help should not show command list header
                if (!entry.getValue().getCommandSpec().usageMessage().hidden()) {
                    List<String> aliases = done.remove(entry.getValue());
                    if (aliases != null) { // otherwise we already processed this command by another alias
                        addSubcommand(aliases, entry.getValue());
                    }
                }
            }
        }
        return this;
    }

    /**
     * Registers the specified subcommand with this Help.
     * 
     * @param commandName
     *            the name of the subcommand to display in the usage message
     * @param command
     *            the {@code CommandSpec} or {@code @Command} annotated object to get more
     *            information from
     * @return this Help instance (for method chaining)
     * @deprecated
     */
    @Deprecated
    public Help addSubcommand(String commandName, Object command) {
        commands.put(commandName,
                commandSpec.commandLine().helpFactory().createHelp(
                        CommandSpec.forAnnotatedObject(command, commandSpec.commandLine().factory),
                        Help.defaultColorScheme(Ansi.AUTO)));
        return this;
    }

    /**
     * Returns whether ANSI escape codes are enabled or not.
     * 
     * @return whether ANSI escape codes are enabled or not
     */
    public Help.Ansi ansi() {
        return colorScheme.ansi;
    }

    public String buildUsageMessage() {
        StringBuilder sb = new StringBuilder();
        for (Section<?> section : sections().values()) {
            sb.append(section.render(this));
        }
        return sb.toString();
    }

    /**
     * Returns the {@code ColorScheme} model that this Help was constructed with.
     * 
     * @since 3.0
     */
    public Help.ColorScheme colorScheme() {
        return colorScheme;
    }

    public CommandListRenderer commandListRenderer() {
        if (commandListRenderer == null) {
            commandListRenderer(createCommandListRenderer());
        }
        return commandListRenderer;
    }

    public Help commandListRenderer(CommandListRenderer commandListRenderer) {
        this.commandListRenderer = commandListRenderer;
        return this;
    }

    /**
     * Returns a {@code Layout} instance configured with the user preferences captured in this Help
     * instance.
     * 
     * @return a Layout
     */
    public Help.Layout createDefaultLayout() {
        return createLayout(Help.defaultOptionsColumnWidth);
    }

    /**
     * Returns a new default OptionRenderer which converts {@link OptionSpec Options} to five
     * columns of text to match the default {@linkplain TextTable TextTable} column layout. The
     * first row of values looks like this:
     * <ol>
     * <li>the required option marker</li>
     * <li>2-character short option name (or empty string if no short option exists)</li>
     * <li>comma separator (only if both short option and long option exist, empty string
     * otherwise)</li>
     * <li>comma-separated string with long option name(s)</li>
     * <li>first element of the {@link OptionSpec#description()} array</li>
     * </ol>
     * <p>
     * Following this, there will be one row for each of the remaining elements of the
     * {@link OptionSpec#description()} array, and these rows look like {@code {"", "", "", "",
     * option.description()[i]}}.
     * </p>
     * <p>
     * If configured, this option renderer adds an additional row to display the default field
     * value.
     * </p>
     * 
     * @return a new default OptionRenderer
     */
    public Help.IOptionRenderer createDefaultOptionRenderer() {
        return new DefaultOptionRenderer(commandSpec.usageMessage().showDefaultValues(),
                "" + commandSpec.usageMessage().requiredOptionMarker());
    }

    /**
     * Returns a new default ParameterRenderer which converts {@linkplain PositionalParamSpec
     * positional parameters} to four columns of text to match the default {@linkplain TextTable
     * TextTable} column layout. The first row of values looks like this:
     * <ol>
     * <li>empty string</li>
     * <li>empty string</li>
     * <li>parameter(s) label as rendered by the {@link IParamLabelRenderer}</li>
     * <li>first element of the {@link PositionalParamSpec#description()} array</li>
     * </ol>
     * <p>
     * Following this, there will be one row for each of the remaining elements of the
     * {@link PositionalParamSpec#description()} array, and these rows look like {@code {"", "", "",
     * param.description()[i]}}.
     * </p>
     * <p>
     * If configured, this parameter renderer adds an additional row to display the default field
     * value.
     * </p>
     * 
     * @return a new default ParameterRenderer
     */
    public Help.IParameterRenderer createDefaultParameterRenderer() {
        return new DefaultParameterRenderer(commandSpec.usageMessage().showDefaultValues(),
                "" + commandSpec.usageMessage().requiredOptionMarker());
    }

    /**
     * Returns a new default param label renderer that separates option parameters from their option
     * name with the specified separator string, and, unless {@link ArgSpec#hideParamSyntax()} is
     * true, surrounds optional parameters with {@code '['} and {@code ']'} characters and uses
     * ellipses ("...") to indicate that any number of a parameter are allowed.
     * 
     * @return a new default ParamLabelRenderer
     */
    public Help.IParamLabelRenderer createDefaultParamLabelRenderer() {
        return new DefaultParamLabelRenderer(commandSpec);
    }

    public SectionHeadingRenderer headingRenderer() {
        if (headingRenderer == null) {
            headingRenderer(createSectionHeadingRenderer());
        }
        return headingRenderer;
    }

    public Help headingRenderer(SectionHeadingRenderer headingRenderer) {
        this.headingRenderer = headingRenderer;
        return this;
    }

    public ArgumentListRenderer<OptionSpec> optionListRenderer() {
        if (optionListRenderer == null) {
            optionListRenderer(createOptionListRenderer());
        }
        return optionListRenderer;
    }

    public Help optionListRenderer(ArgumentListRenderer<OptionSpec> optionListRenderer) {
        this.optionListRenderer = optionListRenderer;
        return this;
    }

    /**
     * Option and positional parameter value label renderer used for the synopsis line(s) and the
     * option list. By default initialized to the result of
     * {@link #createDefaultParamLabelRenderer()}, which takes a snapshot of the
     * {@link ParserSpec#separator()} at construction time. If the separator is modified after Help
     * construction, you may need to re-initialize this field by calling
     * {@link #createDefaultParamLabelRenderer()} again.
     */
    public Help.IParamLabelRenderer parameterLabelRenderer() {
        return parameterLabelRenderer;
    }

    public ArgumentListRenderer<PositionalParamSpec> parameterListRenderer() {
        if (parameterListRenderer == null) {
            parameterListRenderer(createParameterListRenderer());
        }
        return parameterListRenderer;
    }

    public Help parameterListRenderer(
            ArgumentListRenderer<PositionalParamSpec> parameterListRenderer) {
        this.parameterListRenderer = parameterListRenderer;
        return this;
    }

    public Section<?> sections(String name) {
        return sections().get(name);
    }

    public Sections sections() {
        if (sections == null) {
            sections = new Sections();
            sections.add(new Section<String[]>("header",
                    (help) -> help.commandSpec().usageMessage().headerHeading(),
                    (help) -> help.commandSpec().usageMessage().header(),
                    (help) -> help.simpleSectionBodyRenderer()));
            sections.add(new Section<UsageMessageSpec>("synopsis",
                    (help) -> help.commandSpec().usageMessage().synopsisHeading(),
                    (help) -> help.commandSpec().usageMessage(),
                    (help) -> help.synopsisRenderer()));
            sections.add(new Section<String[]>("description",
                    (help) -> help.commandSpec().usageMessage().descriptionHeading(),
                    (help) -> help.commandSpec().usageMessage().description(),
                    (help) -> help.simpleSectionBodyRenderer()));
            sections.add(new Section<List<PositionalParamSpec>>("parameterList",
                    (help) -> help.commandSpec().usageMessage().parameterListHeading(),
                    (help) -> help.commandSpec().positionalParameters(),
                    (help) -> help.parameterListRenderer()));
            sections.add(new Section<List<OptionSpec>>("optionList",
                    (help) -> help.commandSpec().usageMessage().optionListHeading(),
                    (help) -> help.commandSpec().options(), (help) -> help.optionListRenderer()));
            sections.add(new Section<Map<String, Help>>("commandList",
                    (help) -> help.commandSpec.usageMessage().commandListHeading(),
                    (help) -> help.commands, (help) -> help.commandListRenderer()));
            sections.add(new Section<String[]>("footer",
                    (help) -> help.commandSpec().usageMessage().footerHeading(),
                    (help) -> help.commandSpec().usageMessage().footer(),
                    (help) -> help.simpleSectionBodyRenderer()));
        }
        return sections;
    }

    public SimpleSectionBodyRenderer simpleSectionBodyRenderer() {
        if (simpleSectionBodyRenderer == null) {
            simpleSectionBodyRenderer(createSectionBodyRenderer());
        }
        return simpleSectionBodyRenderer;
    }

    public Help simpleSectionBodyRenderer(SimpleSectionBodyRenderer simpleSectionBodyRenderer) {
        this.simpleSectionBodyRenderer = simpleSectionBodyRenderer;
        return this;
    }

    public SynopsisRenderer synopsisRenderer() {
        if (synopsisRenderer == null) {
            synopsisRenderer(createSynopsisRenderer());
        }
        return synopsisRenderer;
    }

    public Help synopsisRenderer(SynopsisRenderer synopsisRenderer) {
        this.synopsisRenderer = synopsisRenderer;
        return this;
    }

    /**
     * Registers the specified subcommand with this Help.
     * 
     * @param commandNames
     *            the name and aliases of the subcommand to display in the usage message
     * @param commandLine
     *            the {@code CommandLine} object to get more information from
     * @return this Help instance (for method chaining)
     */
    Help addSubcommand(List<String> commandNames, CommandLine commandLine) {
        String all = commandNames.toString();
        commands.put(all.substring(1, all.length() - 1),
                commandLine.helpFactory().createHelp(commandLine.getCommandSpec(), colorScheme)
                        .withCommandNames(commandNames));
        return this;
    }

    /**
     * Returns the {@code CommandSpec} model that this Help was constructed with.
     * 
     * @since 3.0
     */
    CommandSpec commandSpec() {
        return commandSpec;
    }

    Help withCommandNames(List<String> aliases) {
        this.aliases = aliases;
        return this;
    }

    protected CommandListRenderer createCommandListRenderer() {
        return new CommandListRenderer();
    }

    protected ArgumentListRenderer<OptionSpec> createOptionListRenderer() {
        return new OptionListRenderer();
    }

    protected ArgumentListRenderer<PositionalParamSpec> createParameterListRenderer() {
        return new ParameterListRenderer();
    }

    protected SimpleSectionBodyRenderer createSectionBodyRenderer() {
        return new SimpleSectionBodyRenderer();
    }

    protected SectionHeadingRenderer createSectionHeadingRenderer() {
        return new SectionHeadingRenderer();
    }

    protected SynopsisRenderer createSynopsisRenderer() {
        return new SynopsisRenderer();
    }

    private Help.Layout createLayout(int longOptionsColumnWidth) {
        return new Layout(colorScheme,
                TextTable.forDefaultColumns(colorScheme.ansi(), longOptionsColumnWidth,
                        commandSpec.usageMessage().width()),
                createDefaultOptionRenderer(), createDefaultParameterRenderer());
    }
}