package picocli.model;

import java.util.ResourceBundle;

import org.apache.commons.lang3.StringUtils;

import picocli.annots.Command;
import picocli.excepts.InitializationException;
import picocli.util.Tracer;

/**
 * Models the usage help message specification.
 * 
 * @since 3.0
 */
public class UsageMessageSpec {
    /** Constant holding the default usage message width: <code>{@value}</code>. */
    public final static int DEFAULT_USAGE_WIDTH = 80;
    private final static int MINIMUM_USAGE_WIDTH = 55;

    /** Constant String holding the default synopsis heading: <code>{@value}</code>. */
    static final String DEFAULT_SYNOPSIS_HEADING = "Usage: ";

    /** Constant String holding the default command list heading: <code>{@value}</code>. */
    static final String DEFAULT_COMMAND_LIST_HEADING = "Commands:%n";

    /**
     * Constant String holding the default string that separates options from option parameters:
     * {@code ' '} ({@value}).
     */
    static final char DEFAULT_REQUIRED_OPTION_MARKER = ' ';

    /**
     * Constant Boolean holding the default setting for whether to abbreviate the synopsis:
     * <code>{@value}</code>.
     */
    static final Boolean DEFAULT_ABBREVIATE_SYNOPSIS = Boolean.FALSE;

    /**
     * Constant Boolean holding the default setting for whether to sort the options alphabetically:
     * <code>{@value}</code>.
     */
    static final Boolean DEFAULT_SORT_OPTIONS = Boolean.TRUE;

    /**
     * Constant Boolean holding the default setting for whether to show default values in the usage
     * help message: <code>{@value}</code>.
     */
    static final Boolean DEFAULT_SHOW_DEFAULT_VALUES = Boolean.FALSE;

    /**
     * Constant Boolean holding the default setting for whether this command should be listed in the
     * usage help of the parent command: <code>{@value}</code>.
     */
    static final Boolean DEFAULT_HIDDEN = Boolean.FALSE;

    static final String DEFAULT_SINGLE_VALUE = "";
    static final String[] DEFAULT_MULTI_LINE = {};

    private static int getSysPropertyWidthOrDefault(int defaultWidth) {
        String userValue = System.getProperty("picocli.usage.width");
        if (userValue == null)
            return defaultWidth;
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
            new Tracer().warn("Invalid picocli.usage.width value '%s'. Using usage width %d.%n",
                    userValue, defaultWidth);
            return defaultWidth;
        }
    }
    private String[] description;
    private String[] customSynopsis;
    private String[] header;
    private String[] footer;
    private Boolean abbreviateSynopsis;
    private Boolean sortOptions;
    private Boolean defaultValuesVisible;
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

    /**
     * Returns whether the synopsis line(s) should show an abbreviated synopsis without detailed
     * option names.
     */
    public boolean abbreviateSynopsis() {
        return (abbreviateSynopsis == null) ? DEFAULT_ABBREVIATE_SYNOPSIS : abbreviateSynopsis;
    }

    /**
     * Sets whether the synopsis line(s) should show an abbreviated synopsis without detailed option
     * names.
     * 
     * @return this UsageMessageSpec for method chaining
     */
    public UsageMessageSpec abbreviateSynopsis(boolean newValue) {
        abbreviateSynopsis = newValue;
        return this;
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
     * Sets the optional heading preceding the subcommand list.
     * 
     * @return this UsageMessageSpec for method chaining
     */
    public UsageMessageSpec commandListHeading(String newValue) {
        commandListHeading = newValue;
        return this;
    }

    /**
     * Returns the optional custom synopsis lines to use instead of the auto-generated synopsis.
     * Initialized from {@link Command#customSynopsis()} if the {@code Command} annotation is
     * present, otherwise this is an empty array and the synopsis is generated. Applications may
     * programmatically set this field to create a custom help message.
     */
    public String[] customSynopsis() {
        return arr(resourceArr("usage.customSynopsis"), customSynopsis, DEFAULT_MULTI_LINE);
    }

    /**
     * Sets the optional custom synopsis lines to use instead of the auto-generated synopsis.
     * 
     * @return this UsageMessageSpec for method chaining
     */
    public UsageMessageSpec customSynopsis(String... customSynopsis) {
        this.customSynopsis = customSynopsis;
        return this;
    }

    /**
     * Sets whether the options list in the usage help message should show default values for all
     * non-boolean options.
     * 
     * @return this UsageMessageSpec for method chaining
     */
    public UsageMessageSpec defaultValuesVisible(boolean newValue) {
        defaultValuesVisible = newValue;
        return this;
    }

    /**
     * Returns the optional text lines to use as the description of the help message, displayed
     * between the synopsis and the options list. Initialized from {@link Command#description()} if
     * the {@code Command} annotation is present, otherwise this is an empty array and the help
     * message has no description. Applications may programmatically set this field to create a
     * custom help message.
     */
    public String[] description() {
        return arr(resourceArr("usage.description"), description, DEFAULT_MULTI_LINE);
    }

    /**
     * Sets the optional text lines to use as the description of the help message, displayed between
     * the synopsis and the options list.
     * 
     * @return this UsageMessageSpec for method chaining
     */
    public UsageMessageSpec description(String... description) {
        this.description = description;
        return this;
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
     * Sets the heading preceding the description section.
     * 
     * @return this UsageMessageSpec for method chaining
     */
    public UsageMessageSpec descriptionHeading(String newValue) {
        descriptionHeading = newValue;
        return this;
    }

    /**
     * Returns the optional footer text lines displayed at the bottom of the help message.
     * Initialized from {@link Command#footer()} if the {@code Command} annotation is present,
     * otherwise this is an empty array and the help message has no footer. Applications may
     * programmatically set this field to create a custom help message.
     */
    public String[] footer() {
        return arr(resourceArr("usage.footer"), footer, DEFAULT_MULTI_LINE);
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
     * Returns the optional heading preceding the footer section. Initialized from
     * {@link Command#footerHeading()}, or null.
     */
    public String footerHeading() {
        return str(resourceStr("usage.footerHeading"), footerHeading, DEFAULT_SINGLE_VALUE);
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
     * Returns the optional header lines displayed at the top of the help message. For subcommands,
     * the first header line is displayed in the list of commands. Values are initialized from
     * {@link Command#header()} if the {@code Command} annotation is present, otherwise this is an
     * empty array and the help message has no header. Applications may programmatically set this
     * field to create a custom help message.
     */
    public String[] header() {
        return arr(resourceArr("usage.header"), header, DEFAULT_MULTI_LINE);
    }

    /**
     * Sets the optional header lines displayed at the top of the help message. For subcommands, the
     * first header line is displayed in the list of commands.
     * 
     * @return this UsageMessageSpec for method chaining
     */
    public UsageMessageSpec header(String... header) {
        this.header = header;
        return this;
    }

    /**
     * Returns the optional heading preceding the header section. Initialized from
     * {@link Command#headerHeading()}, or null.
     */
    public String headerHeading() {
        return str(resourceStr("usage.headerHeading"), headerHeading, DEFAULT_SINGLE_VALUE);
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
     * Returns whether this command should be hidden from the usage help message of the parent
     * command.
     * 
     * @return {@code true} if this command should not appear in the usage help message of the
     *         parent command
     */
    public boolean hidden() {
        return (hidden == null) ? DEFAULT_HIDDEN : hidden;
    }

    /**
     * Set the hidden flag on this command to control whether to show or hide it in the help usage
     * text of the parent command.
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
     * Returns whether the options list in the usage help message should show default values for all
     * non-boolean options.
     */
    public boolean isDefaultValuesVisible() {
        return (defaultValuesVisible == null) ? DEFAULT_SHOW_DEFAULT_VALUES : defaultValuesVisible;
    }

    /**
     * Returns the Messages for this usage help message specification, or {@code null}.
     * 
     * @return the Messages object that encapsulates this {@linkplain CommandSpec#resourceBundle()
     *         command's resource bundle}
     * @since 3.6
     */
    public Messages messages() {
        return messages;
    }

    /**
     * Sets the Messages for this usageMessage specification, and returns this UsageMessageSpec.
     * 
     * @param msgs
     *            the new Messages value that encapsulates this
     *            {@linkplain CommandSpec#resourceBundle() command's resource bundle}, may be
     *            {@code null}
     * @since 3.6
     */
    public UsageMessageSpec messages(Messages msgs) {
        messages = msgs;
        return this;
    }

    /**
     * Returns the optional heading preceding the options list. Initialized from
     * {@link Command#optionListHeading()}, or null.
     */
    public String optionListHeading() {
        return str(resourceStr("usage.optionListHeading"), optionListHeading, DEFAULT_SINGLE_VALUE);
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
     * Returns the optional heading preceding the parameter list. Initialized from
     * {@link Command#parameterListHeading()}, or null.
     */
    public String parameterListHeading() {
        return str(resourceStr("usage.parameterListHeading"), parameterListHeading,
                DEFAULT_SINGLE_VALUE);
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

    /** Returns the character used to prefix required options in the options list. */
    public char requiredOptionMarker() {
        return (requiredOptionMarker == null) ? DEFAULT_REQUIRED_OPTION_MARKER
                : requiredOptionMarker;
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
     * Returns whether the options list in the usage help message should be sorted alphabetically.
     */
    public boolean sortOptions() {
        return (sortOptions == null) ? DEFAULT_SORT_OPTIONS : sortOptions;
    }

    /**
     * Sets whether the options list in the usage help message should be sorted alphabetically.
     * 
     * @return this UsageMessageSpec for method chaining
     */
    public UsageMessageSpec sortOptions(boolean newValue) {
        sortOptions = newValue;
        return this;
    }

    /**
     * Returns the optional heading preceding the synopsis. Initialized from
     * {@link Command#synopsisHeading()}, {@code "Usage: "} by default.
     */
    public String synopsisHeading() {
        return str(resourceStr("usage.synopsisHeading"), synopsisHeading, DEFAULT_SYNOPSIS_HEADING);
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
     * Returns the maximum usage help message width. Derived from system property
     * {@code "picocli.usage.width"} if set, otherwise returns the value set via the
     * {@link #width(int)} method, or if not set, the {@linkplain #DEFAULT_USAGE_WIDTH default
     * width}.
     * 
     * @return the maximum usage help message width. Never returns less than 55.
     */
    public int width() {
        return getSysPropertyWidthOrDefault(width);
    }

    /**
     * Sets the maximum usage help message width to the specified value. Longer values are wrapped.
     * 
     * @param newValue
     *            the new maximum usage help message width. Must be 55 or greater.
     * @return this {@code UsageMessageSpec} for method chaining
     * @throws IllegalArgumentException
     *             if the specified width is less than 55
     */
    public UsageMessageSpec width(int newValue) {
        if (newValue < MINIMUM_USAGE_WIDTH)
            throw new InitializationException("Invalid usage message width " + newValue
                    + ". Minimum value is " + MINIMUM_USAGE_WIDTH);
        width = newValue;
        return this;
    }

    void initFrom(UsageMessageSpec settings, CommandSpec commandSpec) {
        description = settings.description;
        customSynopsis = settings.customSynopsis;
        header = settings.header;
        footer = settings.footer;
        abbreviateSynopsis = settings.abbreviateSynopsis;
        sortOptions = settings.sortOptions;
        defaultValuesVisible = settings.defaultValuesVisible;
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

    void initFromMixin(UsageMessageSpec mixin, CommandSpec commandSpec) {
        if (Model.initializable(synopsisHeading, mixin.synopsisHeading(),
                DEFAULT_SYNOPSIS_HEADING)) {
            synopsisHeading = mixin.synopsisHeading();
        }
        if (Model.initializable(commandListHeading, mixin.commandListHeading(),
                DEFAULT_COMMAND_LIST_HEADING)) {
            commandListHeading = mixin.commandListHeading();
        }
        if (Model.initializable(requiredOptionMarker, mixin.requiredOptionMarker(),
                DEFAULT_REQUIRED_OPTION_MARKER)) {
            requiredOptionMarker = mixin.requiredOptionMarker();
        }
        if (Model.initializable(abbreviateSynopsis, mixin.abbreviateSynopsis(),
                DEFAULT_ABBREVIATE_SYNOPSIS)) {
            abbreviateSynopsis = mixin.abbreviateSynopsis();
        }
        if (Model.initializable(sortOptions, mixin.sortOptions(), DEFAULT_SORT_OPTIONS)) {
            sortOptions = mixin.sortOptions();
        }
        if (Model.initializable(defaultValuesVisible, mixin.isDefaultValuesVisible(),
                DEFAULT_SHOW_DEFAULT_VALUES)) {
            defaultValuesVisible = mixin.isDefaultValuesVisible();
        }
        if (Model.initializable(hidden, mixin.hidden(), DEFAULT_HIDDEN)) {
            hidden = mixin.hidden();
        }
        if (Model.initializable(customSynopsis, mixin.customSynopsis(), DEFAULT_MULTI_LINE)) {
            customSynopsis = mixin.customSynopsis().clone();
        }
        if (Model.initializable(description, mixin.description(), DEFAULT_MULTI_LINE)) {
            description = mixin.description().clone();
        }
        if (Model.initializable(descriptionHeading, mixin.descriptionHeading(),
                DEFAULT_SINGLE_VALUE)) {
            descriptionHeading = mixin.descriptionHeading();
        }
        if (Model.initializable(header, mixin.header(), DEFAULT_MULTI_LINE)) {
            header = mixin.header().clone();
        }
        if (Model.initializable(headerHeading, mixin.headerHeading(), DEFAULT_SINGLE_VALUE)) {
            headerHeading = mixin.headerHeading();
        }
        if (Model.initializable(footer, mixin.footer(), DEFAULT_MULTI_LINE)) {
            footer = mixin.footer().clone();
        }
        if (Model.initializable(footerHeading, mixin.footerHeading(), DEFAULT_SINGLE_VALUE)) {
            footerHeading = mixin.footerHeading();
        }
        if (Model.initializable(parameterListHeading, mixin.parameterListHeading(),
                DEFAULT_SINGLE_VALUE)) {
            parameterListHeading = mixin.parameterListHeading();
        }
        if (Model.initializable(optionListHeading, mixin.optionListHeading(),
                DEFAULT_SINGLE_VALUE)) {
            optionListHeading = mixin.optionListHeading();
        }
        if (Messages.empty(messages)) {
            messages(Messages.copy(commandSpec, mixin.messages()));
        }
    }

    void updateFromCommand(Command cmd, CommandSpec commandSpec) {
        if (Model.isNonDefault(cmd.synopsisHeading(), DEFAULT_SYNOPSIS_HEADING)) {
            synopsisHeading = cmd.synopsisHeading();
        }
        if (Model.isNonDefault(cmd.commandListHeading(), DEFAULT_COMMAND_LIST_HEADING)) {
            commandListHeading = cmd.commandListHeading();
        }
        if (Model.isNonDefault(cmd.requiredOptionMarker(), DEFAULT_REQUIRED_OPTION_MARKER)) {
            requiredOptionMarker = cmd.requiredOptionMarker();
        }
        if (Model.isNonDefault(cmd.abbreviateSynopsis(), DEFAULT_ABBREVIATE_SYNOPSIS)) {
            abbreviateSynopsis = cmd.abbreviateSynopsis();
        }
        if (Model.isNonDefault(cmd.sortOptions(), DEFAULT_SORT_OPTIONS)) {
            sortOptions = cmd.sortOptions();
        }
        if (Model.isNonDefault(cmd.showDefaultValues(), DEFAULT_SHOW_DEFAULT_VALUES)) {
            defaultValuesVisible = cmd.showDefaultValues();
        }
        if (Model.isNonDefault(cmd.hidden(), DEFAULT_HIDDEN)) {
            hidden = cmd.hidden();
        }
        if (Model.isNonDefault(cmd.customSynopsis(), DEFAULT_MULTI_LINE)) {
            customSynopsis = cmd.customSynopsis().clone();
        }
        if (Model.isNonDefault(cmd.description(), DEFAULT_MULTI_LINE)) {
            description = cmd.description().clone();
        }
        if (Model.isNonDefault(cmd.descriptionHeading(), DEFAULT_SINGLE_VALUE)) {
            descriptionHeading = cmd.descriptionHeading();
        }
        if (Model.isNonDefault(cmd.header(), DEFAULT_MULTI_LINE)) {
            header = cmd.header().clone();
        }
        if (Model.isNonDefault(cmd.headerHeading(), DEFAULT_SINGLE_VALUE)) {
            headerHeading = cmd.headerHeading();
        }
        if (Model.isNonDefault(cmd.footer(), DEFAULT_MULTI_LINE)) {
            footer = cmd.footer().clone();
        }
        if (Model.isNonDefault(cmd.footerHeading(), DEFAULT_SINGLE_VALUE)) {
            footerHeading = cmd.footerHeading();
        }
        if (Model.isNonDefault(cmd.parameterListHeading(), DEFAULT_SINGLE_VALUE)) {
            parameterListHeading = cmd.parameterListHeading();
        }
        if (Model.isNonDefault(cmd.optionListHeading(), DEFAULT_SINGLE_VALUE)) {
            optionListHeading = cmd.optionListHeading();
        }
        if (Model.isNonDefault(cmd.usageHelpWidth(), DEFAULT_USAGE_WIDTH)) {
            width(cmd.usageHelpWidth());
        } // validate

        ResourceBundle rb = StringUtils.isBlank(cmd.resourceBundle()) ? null
                : ResourceBundle.getBundle(cmd.resourceBundle());
        if (rb != null) {
            messages(new Messages(commandSpec, rb));
        } // else preserve superclass bundle
    }

    private String[] arr(String[] localized, String[] value, String[] defaultValue) {
        return localized != null ? localized : (value != null ? value.clone() : defaultValue);
    }

    private String[] resourceArr(String key) {
        return messages == null ? null : messages.getStringArray(key, null);
    }

    private String resourceStr(String key) {
        return messages == null ? null : messages.getString(key, null);
    }

    private String str(String localized, String value, String defaultValue) {
        return localized != null ? localized : (value != null ? value : defaultValue);
    }
}
