package picocli.help;

import static java.util.Locale.ENGLISH;

import java.lang.reflect.Field;
import java.util.List;

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
    public enum Style implements IStyle {
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
        public static IStyle bg(String str) {
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
        public static IStyle fg(String str) {
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
        public static String off(IStyle... styles) {
            StringBuilder result = new StringBuilder();
            for (IStyle style : styles) {
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
        public static String on(IStyle... styles) {
            StringBuilder result = new StringBuilder();
            for (IStyle style : styles) {
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
        public static IStyle[] parse(String commaSeparatedCodes) {
            String[] codes = commaSeparatedCodes.split(",");
            IStyle[] styles = new IStyle[codes.length];
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
     * Defines a palette map of 216 colors: 6 * 6 * 6 cube (216 colors): 16 + 36 * r + 6 * g + b
     * (0 &lt;= r, g, b &lt;= 5).
     */
    static class Palette256Color implements IStyle {
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

    static Text EMPTY_TEXT = new Text(OFF, 0);

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
    public static Ansi valueOf(boolean enabled) {
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

    static <T> T[] reverse(T[] all) {
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
    public Text apply(String plainText, List<IStyle> styles) {
        if (plainText.length() == 0)
            return new Text(this, 0);
        Text result = new Text(this, plainText.length());
        IStyle[] all = styles.toArray(new IStyle[styles.size()]);
        result.sections.add(new Text.StyledSection(0, plainText.length(), Style.on(all),
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
        return new Text(this, stringWithMarkup).toString();
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
        return new Text(this, stringWithMarkup);
    }
}