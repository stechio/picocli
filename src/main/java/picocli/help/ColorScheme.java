package picocli.help;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import picocli.help.Ansi.IStyle;
import picocli.help.Ansi.Style;
import picocli.util.Assert;

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
public class ColorScheme {
    public final List<Ansi.IStyle> commandStyles = new ArrayList<>();
    public final List<Ansi.IStyle> optionStyles = new ArrayList<>();
    public final List<Ansi.IStyle> parameterStyles = new ArrayList<>();
    public final List<Ansi.IStyle> optionParamStyles = new ArrayList<>();
    final Ansi ansi;

    /** Constructs a new empty ColorScheme with {@link Ansi#AUTO}. */
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
    public ColorScheme(Ansi ansi) {
        this.ansi = Assert.notNull(ansi, "ansi");
    }

    public Ansi ansi() {
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
     * Property values can be anything that {@link Ansi.Style#parse(String)} can handle.
     * </p>
     * 
     * @return this ColorScheme
     */
    public ColorScheme applySystemProperties() {
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
    public ColorScheme commands(Ansi.IStyle... styles) {
        return addAll(commandStyles, styles);
    }

    /**
     * Returns a Text with all command styles applied to the specified command string.
     * 
     * @param command
     *            the command string to apply the registered command styles to
     * @return a Text with all command styles applied to the specified command string
     */
    public Text commandText(String command) {
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
    public ColorScheme optionParams(Ansi.IStyle... styles) {
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
    public Text optionParamText(String optionParam) {
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
    public ColorScheme options(Ansi.IStyle... styles) {
        return addAll(optionStyles, styles);
    }

    /**
     * Returns a Text with all option styles applied to the specified option string.
     * 
     * @param option
     *            the option string to apply the registered option styles to
     * @return a Text with all option styles applied to the specified option string
     */
    public Text optionText(String option) {
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
    public ColorScheme parameters(Ansi.IStyle... styles) {
        return addAll(parameterStyles, styles);
    }

    /**
     * Returns a Text with all parameter styles applied to the specified parameter string.
     * 
     * @param parameter
     *            the parameter string to apply the registered parameter styles to
     * @return a Text with all parameter styles applied to the specified parameter string
     */
    public Text parameterText(String parameter) {
        return ansi().apply(parameter, parameterStyles);
    }

    private ColorScheme addAll(List<Ansi.IStyle> styles, Ansi.IStyle... add) {
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