package picocli.help;

import java.util.List;

import picocli.CommandLine;
import picocli.help.Help.IOptionRenderer;
import picocli.help.Help.IParameterRenderer;
import picocli.help.Help.OptionRenderer;
import picocli.help.Help.ParameterRenderer;
import picocli.model.ArgSpec;
import picocli.model.OptionSpec;
import picocli.model.PositionalParamSpec;
import picocli.util.Assert;

/**
 * Use a Layout to format usage help text for options and parameters in tabular format.
 * <p>
 * Delegates to the renderers to create {@link Text} values for the annotated fields, and uses a
 * {@link TextTable} to display these values in tabular format. Layout is responsible for deciding
 * which values to display where in the table. By default, Layout shows one option or parameter per
 * table row.
 * </p>
 * <p>
 * Customize by overriding the {@link #layout(CommandLine.Model.ArgSpec, Text[][])} method.
 * </p>
 * 
 * @see IOptionRenderer rendering options to text
 * @see IParameterRenderer rendering parameters to text
 * @see TextTable showing values in a tabular format
 */
public class Layout {
    protected final ColorScheme colorScheme;
    protected final TextTable table;
    protected Help.IOptionRenderer optionRenderer;
    protected Help.IParameterRenderer parameterRenderer;

    /**
     * Constructs a Layout with the specified color scheme, a new default TextTable, the
     * {@linkplain Help#createDefaultOptionRenderer() default option renderer}, and the
     * {@linkplain Help#createDefaultParameterRenderer() default parameter renderer}.
     * 
     * @param colorScheme
     *            the color scheme to use for common, auto-generated parts of the usage help message
     */
    public Layout(ColorScheme colorScheme, int tableWidth) {
        this(colorScheme, TextTable.forDefaultColumns(colorScheme.ansi(), tableWidth));
    }

    /**
     * Constructs a Layout with the specified color scheme, the specified TextTable, the
     * {@linkplain Help#createDefaultOptionRenderer() default option renderer}, and the
     * {@linkplain Help#createDefaultParameterRenderer() default parameter renderer}.
     * 
     * @param colorScheme
     *            the color scheme to use for common, auto-generated parts of the usage help message
     * @param textTable
     *            the TextTable to lay out parts of the usage help message in tabular format
     */
    public Layout(ColorScheme colorScheme, TextTable textTable) {
        this(colorScheme, textTable, new OptionRenderer(false, false, " "),
                new ParameterRenderer(false, " "));
    }

    /**
     * Constructs a Layout with the specified color scheme, the specified TextTable, the specified
     * option renderer and the specified parameter renderer.
     * 
     * @param colorScheme
     *            the color scheme to use for common, auto-generated parts of the usage help message
     * @param optionRenderer
     *            the object responsible for rendering Options to Text
     * @param parameterRenderer
     *            the object responsible for rendering Parameters to Text
     * @param textTable
     *            the TextTable to lay out parts of the usage help message in tabular format
     */
    public Layout(ColorScheme colorScheme, TextTable textTable, Help.IOptionRenderer optionRenderer,
            Help.IParameterRenderer parameterRenderer) {
        this.colorScheme = Assert.notNull(colorScheme, "colorScheme");
        this.table = Assert.notNull(textTable, "textTable");
        this.optionRenderer = Assert.notNull(optionRenderer, "optionRenderer");
        this.parameterRenderer = Assert.notNull(parameterRenderer, "parameterRenderer");
    }

    /**
     * Delegates to the {@link #optionRenderer option renderer} of this layout to obtain text values
     * for the specified {@link OptionSpec}, and then calls the
     * {@link #layout(CommandLine.Model.ArgSpec, Text[][])} method to write these text values into
     * the correct cells in the TextTable.
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
    public void addOptions(List<OptionSpec> options, Help.IParamLabelRenderer paramLabelRenderer) {
        for (OptionSpec option : options) {
            if (!option.hidden()) {
                addOption(option, paramLabelRenderer);
            }
        }
    }

    /**
     * Delegates to the {@link #parameterRenderer parameter renderer} of this layout to obtain text
     * values for the specified {@linkplain PositionalParamSpec positional parameter}, and then
     * calls {@link #layout(CommandLine.Model.ArgSpec, Text[][])} to write these text values into
     * the correct cells in the TextTable.
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
     * implementation delegates to {@link TextTable#addRowValues(Text...)} for each row of values.
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