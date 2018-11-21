package picocli.help;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import picocli.CommandLine;
import picocli.annots.Command;
import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.help.TextTable.Column;
import picocli.model.ArgSpec;
import picocli.model.CommandSpec;
import picocli.model.ITypeConverter;
import picocli.model.OptionSpec;
import picocli.model.ParserSpec;
import picocli.model.PositionalParamSpec;
import picocli.model.Range;
import picocli.model.UsageMessageSpec;
import picocli.util.Assert;
import picocli.util.ListMap;
import picocli.util.ObjectUtilsExt;
import picocli.util.StringUtilsExt;
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
    public static abstract class ArgumentListRenderer<T extends ArgSpec>
            implements IHelpElementRenderer<List<T>> {
        @Override
        public String render(Help help, List<T> arguments) {
            Comparator<T> comparator = getComparator(help.commandSpec());
            if (comparator != null) {
                Collections.sort(arguments = new ArrayList<>(arguments), comparator);
            }
            Layout layout = getLayout(help);
            IParamLabelRenderer labelRenderer = help.rendering().paramLabel();
            populate(layout, arguments, labelRenderer);
            return layout.toString();
        }

        protected abstract Comparator<T> getComparator(CommandSpec commandSpec);

        protected Layout getLayout(Help help) {
            return help.createLayout(getNameColumnWidth(help));
        }

        protected int getNameColumnWidth(Help help) {
            int max = 0;
            IOptionRenderer optionRenderer = new OptionRenderer(false, false, " ");
            for (OptionSpec option : help.commandSpec().options()) {
                Text[][] values = optionRenderer.render(option, help.rendering().paramLabel(),
                        help.colorScheme());
                int len = values[0][3].length();
                if (len < Help.defaultOptionsColumnWidth - 3) {
                    max = Math.max(max, len);
                }
            }
            IParameterRenderer paramRenderer = new ParameterRenderer(false, " ");
            for (PositionalParamSpec positional : help.commandSpec().positionalParameters()) {
                Text[][] values = paramRenderer.render(positional, help.rendering().paramLabel(),
                        help.colorScheme());
                int len = values[0][3].length();
                if (len < Help.defaultOptionsColumnWidth - 3) {
                    max = Math.max(max, len);
                }
            }
            return max + 3;
        }

        protected abstract void populate(Layout layout, List<T> arguments,
                Help.IParamLabelRenderer labelRenderer);
    }

    public static class CommandListRenderer implements IHelpElementRenderer<Map<String, Help>> {
        @Override
        public String render(Help help, Map<String, Help> value) {
            if (value.isEmpty())
                return StringUtils.EMPTY;

            int commandLength = StringUtilsExt.longest(value.keySet()).length();
            TextTable textTable = TextTable.forColumns(help.colorScheme().ansi(),
                    new TextTable.Column(commandLength + 2, 2, TextTable.Column.Overflow.SPAN),
                    new TextTable.Column(
                            help.commandSpec().usageMessage().width() - (commandLength + 2), 2,
                            TextTable.Column.Overflow.WRAP));
            for (Map.Entry<String, Help> entry : value.entrySet()) {
                Help helpObj = entry.getValue();
                CommandSpec command = helpObj.commandSpec();
                String header = command.usageMessage().header() != null
                        && command.usageMessage().header().length > 0
                                ? command.usageMessage().header()[0]
                                : (command.usageMessage().description() != null
                                        && command.usageMessage().description().length > 0
                                                ? command.usageMessage().description()[0]
                                                : StringUtils.EMPTY);
                Text[] lines = help.colorScheme().ansi().text(Utils.safeFormat(header))
                        .splitLines();
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
                result.append(", ").append(help.colorScheme.commandText(help.aliases.get(i)));
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
                ColorScheme scheme);
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
                ColorScheme scheme);
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
        Text renderParameterLabel(ArgSpec argSpec, Ansi ansi, List<Ansi.IStyle> styles);

        /**
         * Returns the separator between option name and param label.
         * 
         * @return the separator between option name and param label
         */
        String separator();
    }

    public interface IRendererFactory {
        CommandListRenderer createCommandListRenderer();

        /**
         * @see Rendering#minimalOption()
         */
        IOptionRenderer createMinimalOptionRenderer();

        /**
         * @see Rendering#minimalParameter()
         */
        IParameterRenderer createMinimalParameterRenderer();

        /**
         * @see Rendering#minimalParamLabel()
         */
        IParamLabelRenderer createMinimalParamLabelRenderer();

        ArgumentListRenderer<OptionSpec> createOptionListRenderer();

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
        IOptionRenderer createOptionRenderer();

        ArgumentListRenderer<PositionalParamSpec> createParameterListRenderer();

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
         * {@link PositionalParamSpec#description()} array, and these rows look like {@code {"", "",
         * "", param.description()[i]}}.
         * </p>
         * <p>
         * If configured, this parameter renderer adds an additional row to display the default
         * field value.
         * </p>
         *
         * @return a new default ParameterRenderer
         */
        IParameterRenderer createParameterRenderer();

        /**
         * Returns a new default param label renderer that separates option parameters from their
         * option name with the specified separator string, and, unless
         * {@link ArgSpec#hideParamSyntax()} is true, surrounds optional parameters with {@code '['}
         * and {@code ']'} characters and uses ellipses ("...") to indicate that any number of a
         * parameter are allowed.
         *
         * @return a new default ParamLabelRenderer
         */
        IParamLabelRenderer createParamLabelRenderer();

        SimpleSectionBodyRenderer createSectionBodyRenderer();

        SectionHeadingRenderer createSectionHeadingRenderer();

        SynopsisRenderer createSynopsisRenderer();
    }

    public static class OptionListRenderer extends ArgumentListRenderer<OptionSpec> {
        @Override
        protected Comparator<OptionSpec> getComparator(CommandSpec commandSpec) {
            return commandSpec.usageMessage().sortOptions()
                    ? Comparators.OptionNameAlphabeticalLength.Order
                    : null;
        }

        @Override
        protected void populate(Layout layout, List<OptionSpec> arguments,
                Help.IParamLabelRenderer labelRenderer) {
            layout.addOptions(arguments, labelRenderer);
        }
    }

    /**
     * The OptionRenderer converts {@link OptionSpec Options} to five columns of text to match the
     * default {@linkplain TextTable TextTable} column layout. The first row of values looks like
     * this:
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
    public static class OptionRenderer implements Help.IOptionRenderer {
        private String requiredMarker = " ";
        private boolean defaultValuesVisible;
        private boolean choiceValuesVisible;
        private String sep;

        public OptionRenderer(boolean defaultValuesVisible, boolean choiceValuesVisible,
                String requiredMarker) {
            this.defaultValuesVisible = defaultValuesVisible;
            this.choiceValuesVisible = choiceValuesVisible;
            this.requiredMarker = Assert.notNull(requiredMarker, "requiredMarker");
        }

        @Override
        public Text[][] render(OptionSpec option, Help.IParamLabelRenderer paramLabelRenderer,
                ColorScheme scheme) {
            String[] names = picocli.util.Comparators.Length.sortAsc(option.names());
            int shortOptionCount = names[0].length() == 2 ? 1 : 0;
            String shortOption = shortOptionCount > 0 ? names[0] : StringUtils.EMPTY;
            sep = shortOptionCount > 0 && names.length > 1 ? "," : StringUtils.EMPTY;

            String longOption = Utils.safeJoin(names, ", ", shortOptionCount, names.length);
            Text longOptionText = createLongOptionText(option, paramLabelRenderer, scheme,
                    longOption);

            String requiredOption = option.required() ? requiredMarker : StringUtils.EMPTY;
            return renderDescriptionLines(option, scheme, requiredOption, shortOption,
                    longOptionText);
        }

        private Text createLongOptionText(OptionSpec option, Help.IParamLabelRenderer renderer,
                ColorScheme scheme, String longOption) {
            Text paramLabelText = renderer.renderParameterLabel(option, scheme.ansi(),
                    scheme.optionParamStyles);

            // if no long option, fill in the space between the short option name and the param label value
            if (!paramLabelText.isEmpty() && longOption.isEmpty()) {
                sep = renderer.separator();
                // #181 paramLabelText may be =LABEL or [=LABEL...]
                int sepStart = paramLabelText.toPlainString().indexOf(sep);
                paramLabelText = paramLabelText.subSequence(0, sepStart)
                        .append(paramLabelText.subSequence(sepStart + sep.length()));
            }
            return scheme.optionText(longOption).append(paramLabelText);
        }

        private Text[][] renderDescriptionLines(OptionSpec option, ColorScheme scheme,
                String requiredOption, String shortOption, Text longOptionText) {
            Text EMPTY = Ansi.EMPTY_TEXT;
            boolean[] showDefault = { option.internalShowDefaultValue(defaultValuesVisible) };
            List<Text[]> result = new ArrayList<>();
            String[] description = option.renderedDescription();
            Text[] descriptionFirstLines = createDescriptionFirstLines(scheme, option, description,
                    showDefault);
            result.add(new Text[] { scheme.optionText(requiredOption),
                    scheme.optionText(shortOption), new Text(scheme.ansi(), sep), longOptionText,
                    descriptionFirstLines[0] });
            for (int i = 1; i < descriptionFirstLines.length; i++) {
                result.add(new Text[] { EMPTY, EMPTY, EMPTY, EMPTY, descriptionFirstLines[i] });
            }
            for (int i = 1; i < description.length; i++) {
                Text[] descriptionNextLines = new Text(scheme.ansi(), description[i]).splitLines();
                for (Text line : descriptionNextLines) {
                    result.add(new Text[] { EMPTY, EMPTY, EMPTY, EMPTY, line });
                }
            }
            if (showDefault[0]) {
                addTrailingDefaultLine(result, option, scheme);
            }
            if (choiceValuesVisible && Utils.isNotEmptyAtAll(option.choiceValues())) {
                ITypeConverter<?> converter = ObjectUtilsExt.safeGet(option.converters(), 0);
                Text valuesText = null;
                boolean fullListing = false;
                for (String choiceValue : option.choiceValues()) {
                    String valueDescription = null;
                    if (converter != null) {
                        valueDescription = StringUtils
                                .trimToNull(converter.descriptionOf(choiceValue));
                    }

                    if (!fullListing) {
                        if (valuesText != null) {
                            valuesText.append(", ");
                        } else {
                            valuesText = new Text(scheme.ansi, "VALUES: ");

                            fullListing = (valueDescription != null);
                            if (fullListing) {
                                result.add(new Text[] { EMPTY, EMPTY, EMPTY, EMPTY, valuesText });
                            }
                        }
                    }
                    if (fullListing) {
                        valuesText = new Text(scheme.ansi, "  * ");
                    }
                    valuesText.append(choiceValue);
                    if (valueDescription != null) {
                        valuesText.append(" (").append(StringUtils.uncapitalize(valueDescription))
                                .append(')');
                    }
                    if (fullListing) {
                        result.add(new Text[] { EMPTY, EMPTY, EMPTY, EMPTY, valuesText });
                    }
                }
                if (!fullListing) {
                    valuesText.append('.');
                    result.add(new Text[] { EMPTY, EMPTY, EMPTY, EMPTY, valuesText });
                }
            }
            return result.toArray(new Text[result.size()][]);
        }
    }

    public static class ParameterListRenderer extends ArgumentListRenderer<PositionalParamSpec> {
        @Override
        protected Comparator<PositionalParamSpec> getComparator(CommandSpec commandSpec) {
            return null;
        }

        @Override
        protected void populate(Layout layout, List<PositionalParamSpec> arguments,
                Help.IParamLabelRenderer labelRenderer) {
            layout.addPositionalParameters(arguments, labelRenderer);
        }
    }

    public static class RendererFactory implements IRendererFactory {
        protected Help help;

        public RendererFactory(Help help) {
            this.help = help;
        }

        @Override
        public CommandListRenderer createCommandListRenderer() {
            return new CommandListRenderer();
        }

        @Override
        public IOptionRenderer createMinimalOptionRenderer() {
            return (option, parameterLabelRenderer, scheme) -> {
                Text optionText = scheme.optionText(option.names()[0]).append(parameterLabelRenderer
                        .renderParameterLabel(option, scheme.ansi(), scheme.optionParamStyles));
                return new Text[][] { { optionText,
                        new Text(scheme.ansi(), Utils.safeGet(option.description(), 0)) } };
            };
        }

        @Override
        public IParameterRenderer createMinimalParameterRenderer() {
            return (param, parameterLabelRenderer,
                    scheme) -> new Text[][] { {
                            parameterLabelRenderer.renderParameterLabel(param, scheme.ansi(),
                                    scheme.parameterStyles),
                            new Text(scheme.ansi(), Utils.safeGet(param.description(), 0)) } };
        }

        @Override
        public IParamLabelRenderer createMinimalParamLabelRenderer() {
            return new IParamLabelRenderer() {
                @Override
                public Text renderParameterLabel(ArgSpec argSpec, Ansi ansi,
                        List<Ansi.IStyle> styles) {
                    return ansi.apply(argSpec.paramLabel(), styles);
                }

                @Override
                public String separator() {
                    return StringUtils.EMPTY;
                }
            };
        }

        @Override
        public ArgumentListRenderer<OptionSpec> createOptionListRenderer() {
            return new OptionListRenderer();
        }

        @Override
        public IOptionRenderer createOptionRenderer() {
            return new OptionRenderer(help.commandSpec().usageMessage().isDefaultValuesVisible(),
                    //TODO:parameterize choiceValuesVisible
                    true,
                    Character.toString(help.commandSpec().usageMessage().requiredOptionMarker()));
        }

        @Override
        public ArgumentListRenderer<PositionalParamSpec> createParameterListRenderer() {
            return new ParameterListRenderer();
        }

        @Override
        public IParameterRenderer createParameterRenderer() {
            return new ParameterRenderer(help.commandSpec().usageMessage().isDefaultValuesVisible(),
                    Character.toString(help.commandSpec().usageMessage().requiredOptionMarker()));
        }

        @Override
        public IParamLabelRenderer createParamLabelRenderer() {
            return new ParamLabelRenderer(help.commandSpec());
        }

        @Override
        public SimpleSectionBodyRenderer createSectionBodyRenderer() {
            return new SimpleSectionBodyRenderer();
        }

        @Override
        public SectionHeadingRenderer createSectionHeadingRenderer() {
            return new SectionHeadingRenderer();
        }

        @Override
        public SynopsisRenderer createSynopsisRenderer() {
            return new SynopsisRenderer();
        }
    }

    public static class Rendering {
        private IRendererFactory factory;

        private SynopsisRenderer synopsis;
        private SimpleSectionBodyRenderer simpleSectionBody;
        private SectionHeadingRenderer sectionHeading;
        private ArgumentListRenderer<OptionSpec> optionList;
        private ArgumentListRenderer<PositionalParamSpec> parameterList;
        private IParamLabelRenderer paramLabel;
        private CommandListRenderer commandList;
        private IParameterRenderer minimalParameter;
        private IOptionRenderer option;
        private IParameterRenderer parameter;
        private IOptionRenderer minimalOption;
        private IParamLabelRenderer minimalParamLabel;

        public Rendering(IRendererFactory factory) {
            this.factory = factory;
        }

        public CommandListRenderer commandList() {
            if (commandList == null) {
                commandList = factory.createCommandListRenderer();
            }
            return commandList;
        }

        /**
         * Converts {@link OptionSpec Options} to a single row with two columns of text: an option
         * name and a description.
         *
         * <p>
         * If multiple names or description lines exist, the first value is used.
         * </p>
         */
        public IOptionRenderer minimalOption() {
            if (minimalOption == null) {
                minimalOption = factory.createMinimalOptionRenderer();
            }
            return minimalOption;
        }

        /**
         * Converts {@linkplain PositionalParamSpec positional parameters} to a single row with two
         * columns of text: the parameters label and a description.
         *
         * <p>
         * If multiple description lines exist, the first value is used.
         * </p>
         */
        public IParameterRenderer minimalParameter() {
            if (minimalParameter == null) {
                minimalParameter = factory.createMinimalParameterRenderer();
            }
            return minimalParameter;
        }

        public IParamLabelRenderer minimalParamLabel() {
            if (minimalParamLabel == null) {
                minimalParamLabel = factory.createMinimalParamLabelRenderer();
            }
            return minimalParamLabel;
        }

        public IOptionRenderer option() {
            if (option == null) {
                option = factory.createOptionRenderer();
            }
            return option;
        }

        public ArgumentListRenderer<OptionSpec> optionList() {
            if (optionList == null) {
                optionList = factory.createOptionListRenderer();
            }
            return optionList;
        }

        public IParameterRenderer parameter() {
            if (parameter == null) {
                parameter = factory.createParameterRenderer();
            }
            return parameter;
        }

        public ArgumentListRenderer<PositionalParamSpec> parameterList() {
            if (parameterList == null) {
                parameterList = factory.createParameterListRenderer();
            }
            return parameterList;
        }

        /**
         * Option and positional parameter value label renderer used for the synopsis line(s) and
         * the option list. By default initialized to the result of
         * {@link #createDefaultParamLabelRenderer()}, which takes a snapshot of the
         * {@link ParserSpec#separator()} at construction time. If the separator is modified after
         * Help construction, you may need to re-initialize this field by calling
         * {@link #createDefaultParamLabelRenderer()} again.
         */
        public IParamLabelRenderer paramLabel() {
            if (paramLabel == null) {
                paramLabel = factory.createParamLabelRenderer();
            }
            return paramLabel;
        }

        public SectionHeadingRenderer sectionHeading() {
            if (sectionHeading == null) {
                sectionHeading = factory.createSectionHeadingRenderer();
            }
            return sectionHeading;
        }

        public SimpleSectionBodyRenderer simpleSectionBody() {
            if (simpleSectionBody == null) {
                simpleSectionBody = factory.createSectionBodyRenderer();
            }
            return simpleSectionBody;
        }

        public SynopsisRenderer synopsis() {
            if (synopsis == null) {
                synopsis = factory.createSynopsisRenderer();
            }
            return synopsis;
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
            return Utils.isEmptyAtAll(body.apply(help)) ? StringUtils.EMPTY
                    : renderHeading(help) + renderBody(help);
        }

        public String renderBody(Help help) {
            return bodyRenderer.apply(help).render(help, body.apply(help));
        }

        public String renderHeading(Help help) {
            return help.rendering().sectionHeading().render(help, heading.apply(help));
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
            return result + StringUtils.repeat(' ', Utils.countTrailingSpaces(value));
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
            if (Utils.isEmptyAtAll(values))
                return StringUtils.EMPTY;

            TextTable table = getTable(help);
            for (String value : values) {
                // TODO: params?
                //                Text[] lines = ansi().new Text(format(value, params)).splitLines();
                Text[] lines = new Text(help.colorScheme().ansi(), Utils.safeFormat(value))
                        .splitLines();
                for (Text line : lines) {
                    table.addRowValues(line);
                }
            }
            return table.toString();
        }

        protected TextTable getTable(Help help) {
            TextTable table = TextTable.forColumnWidths(help.colorScheme().ansi(),
                    help.commandSpec().usageMessage().width());
            table.indentWrappedLines = 0;
            return table;
        }
    }

    public static class SynopsisRenderer implements IHelpElementRenderer<UsageMessageSpec> {
        @Override
        public String render(Help help, UsageMessageSpec usageMessage) {
            if (Utils.isNotEmptyAtAll(usageMessage.customSynopsis()))
                return help.rendering().simpleSectionBody().render(help,
                        usageMessage.customSynopsis());
            else
                return usageMessage.abbreviateSynopsis() ? renderAbbreviated(help)
                        : renderDetailed(help, null,
                                help.commandSpec().parser().posixClusteredShortOptionsAllowed());
        }

        public String renderAbbreviated(Help help) {
            Text text = new Text(help.ansi(), 0);
            text = renderOptions(help, help.commandSpec().options(), text, false);
            text = renderParameters(help, help.commandSpec().positionalParameters(), text, false);
            text = renderSubcommands(help, help.commandSpec().subcommands(), text, false);
            return help.colorScheme().commandText(help.commandSpec().qualifiedName()).append(text)
                    .append(System.getProperty("line.separator")).toString();
        }

        public String renderDetailed(Help help, Comparator<OptionSpec> optionSort,
                boolean clusterBooleanOptions) {
            Text text = new Text(help.ansi(), 0);
            List<OptionSpec> options = new ArrayList<>(help.commandSpec().options());
            Collections.sort(options, optionSort != null ? optionSort
                    : Comparators.OptionArityAndNameAlphabeticalLength.Order);
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
                text.append(' ')
                        .append(help.colorScheme().optionText(clusteredRequired.toString()));
            }
            if (clusteredOptional.length() > 1) { // initial length was 1
                text.append(" [")
                        .append(help.colorScheme().optionText(clusteredOptional.toString()))
                        .append("]");
            }
            return text;
        }

        protected String renderDetailedValues(Help help, String commandName, Text optionText) {
            // Fix for #142: first line of synopsis overshoots max. characters
            int synopsisHeadingLength = synopsisHeadingLength(help);
            int firstColumnLength = commandName.length() + synopsisHeadingLength;

            // synopsis heading ("Usage: ") may be on the same line, so adjust column width
            TextTable textTable = TextTable.forColumnWidths(help.ansi(), firstColumnLength,
                    help.commandSpec().usageMessage().width() - firstColumnLength);
            textTable.indentWrappedLines = 1; // don't worry about first line: options (2nd column) always start with a space

            // right-adjust the command name by length of synopsis heading
            Text PADDING = new Text(Ansi.OFF, StringUtils.repeat('X', synopsisHeadingLength));
            textTable.addRowValues(PADDING.append(help.colorScheme().commandText(commandName)),
                    optionText);
            return textTable.toString().substring(synopsisHeadingLength); // cut off leading synopsis heading spaces
        }

        protected Text renderOption(Help help, OptionSpec option, Text text) {
            if (!option.hidden()) {
                Text name = help.colorScheme().optionText(option.shortestName());
                Text param = help.rendering().paramLabel().renderParameterLabel(option,
                        help.colorScheme().ansi(), help.colorScheme().optionParamStyles);
                if (option.required()) { // e.g., -x=VAL
                    text.append(" ").append(name).append(param);
                    if (option.isMultiValue()) { // e.g., -x=VAL [-x=VAL]...
                        text.append(" [").append(name).append(param).append("]...");
                    }
                } else {
                    text.append(" [").append(name).append(param).append("]");
                    if (option.isMultiValue()) { // add ellipsis to show option is repeatable
                        text.append("...");
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
                    text.append(" [OPTIONS]");
                }
            }
            return text;
        }

        protected Text renderParameter(Help help, PositionalParamSpec parameter, Text text) {
            if (!parameter.hidden()) {
                text.append(" ").append(help.rendering().paramLabel().renderParameterLabel(
                        parameter, help.colorScheme().ansi(), help.colorScheme().parameterStyles));
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
                text.append(" [").append("COMMAND").append("]");
            }
            return text;
        }

        protected int synopsisHeadingLength(Help help) {
            String[] lines = help.commandSpec().usageMessage().synopsisHeading()
                    .split("\\r?\\n|\\r|%n", -1);
            return lines[lines.length - 1].length();
        }
    }

    /** Controls the visibility of certain aspects of the usage help message. */
    public enum Visibility {
        ALWAYS, NEVER, ON_DEMAND
    }

    /**
     * The ParameterRenderer converts {@linkplain PositionalParamSpec positional parameters} to five
     * columns of text to match the default {@linkplain TextTable TextTable} column layout. The
     * first row of values looks like this:
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
    static class ParameterRenderer implements Help.IParameterRenderer {
        private String requiredMarker = " ";
        private boolean defaultValuesVisible;

        public ParameterRenderer(boolean defaultValuesVisible, String requiredMarker) {
            this.defaultValuesVisible = defaultValuesVisible;
            this.requiredMarker = Assert.notNull(requiredMarker, "requiredMarker");
        }

        @Override
        public Text[][] render(PositionalParamSpec param,
                Help.IParamLabelRenderer paramLabelRenderer, ColorScheme scheme) {
            Text label = paramLabelRenderer.renderParameterLabel(param, scheme.ansi(),
                    scheme.parameterStyles);
            Text requiredParameter = scheme
                    .parameterText(param.arity().min > 0 ? requiredMarker : StringUtils.EMPTY);

            Text EMPTY = Ansi.EMPTY_TEXT;
            boolean[] showDefault = { param.internalShowDefaultValue(defaultValuesVisible) };
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
                Text[] descriptionNextLines = new Text(scheme.ansi(), description[i]).splitLines();
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
     * ParamLabelRenderer separates option parameters from their {@linkplain OptionSpec option
     * names} with a {@linkplain ParserSpec#separator() separator} string, and, unless
     * {@link ArgSpec#hideParamSyntax()} is true, surrounds optional values with {@code '['} and
     * {@code ']'} characters and uses ellipses ("...") to indicate that any number of values is
     * allowed for options or parameters with variable arity.
     */
    static class ParamLabelRenderer implements Help.IParamLabelRenderer {
        private final CommandSpec commandSpec;

        /** Constructs a new ParamLabelRenderer with the specified separator string. */
        public ParamLabelRenderer(CommandSpec commandSpec) {
            this.commandSpec = Assert.notNull(commandSpec, "commandSpec");
        }

        @Override
        public Text renderParameterLabel(ArgSpec argSpec, Ansi ansi, List<Ansi.IStyle> styles) {
            Range capacity = argSpec.isOption() ? argSpec.arity()
                    : ((PositionalParamSpec) argSpec).capacity();
            if (capacity.max == 0)
                return new Text(ansi, StringUtils.EMPTY);
            if (argSpec.hideParamSyntax())
                return ansi.apply((argSpec.isOption() ? separator() : StringUtils.EMPTY)
                        + argSpec.paramLabel(), styles);

            Text paramName = ansi.apply(argSpec.paramLabel(), styles);
            String split = argSpec.splitRegex();
            String mandatorySep = StringUtils.isBlank(split) ? " " : split;
            String optionalSep = StringUtils.isBlank(split) ? " [" : "[" + split;

            boolean unlimitedSplit = !StringUtils.isBlank(split)
                    && !commandSpec.parser().limitSplit();
            boolean limitedSplit = !StringUtils.isBlank(split) && commandSpec.parser().limitSplit();
            Text repeating = paramName.clone();
            int paramCount = 1;
            if (unlimitedSplit) {
                repeating.append("[" + split).append(paramName).append("...]");
                paramCount++;
                mandatorySep = " ";
                optionalSep = " [";
            }
            Text result = repeating.clone();

            int done = 1;
            for (; done < capacity.min; done++) {
                result.append(mandatorySep).append(repeating); // " PARAM" or ",PARAM"
                paramCount += paramCount;
            }
            if (!capacity.isVariable) {
                for (int i = done; i < capacity.max; i++) {
                    result.append(optionalSep).append(paramName); // " [PARAM" or "[,PARAM"
                    paramCount++;
                }
                for (int i = done; i < capacity.max; i++) {
                    result.append("]");
                }
            }
            // show an extra trailing "[,PARAM]" if split and either max=* or splitting is not restricted to max
            boolean effectivelyVariable = capacity.isVariable || (limitedSplit && paramCount == 1);
            if (limitedSplit && effectivelyVariable && paramCount == 1) {
                result.append(optionalSep).append(repeating).append("]"); // PARAM[,PARAM]...
            }
            if (effectivelyVariable) {
                if (!argSpec.arity().isVariable && argSpec.arity().min > 1) {
                    result = new Text(ansi, "(").append(result).append(")"); // repeating group
                }
                result.append("..."); // PARAM...
            }
            String optionSeparator = argSpec.isOption() ? separator() : StringUtils.EMPTY;
            if (capacity.min == 0) { // optional
                String sep2 = StringUtils.isBlank(optionSeparator) ? optionSeparator + "["
                        : "[" + optionSeparator;
                result = new Text(ansi, sep2).append(result).append("]");
            } else {
                result = new Text(ansi, optionSeparator).append(result);
            }
            return result;
        }

        @Override
        public String separator() {
            return commandSpec.parser().separator();
        }
    }

    /**
     * Constant String holding the default program name, value defined in
     * {@link CommandSpec#DEFAULT_COMMAND_NAME}.
     */
    public static final String DEFAULT_COMMAND_NAME = CommandSpec.DEFAULT_COMMAND_NAME;

    /**
     * Constant String holding the default string that separates options from option parameters,
     * value defined in {@link ParserSpec#DEFAULT_SEPARATOR}.
     */
    protected static final String DEFAULT_SEPARATOR = ParserSpec.DEFAULT_SEPARATOR;

    final static int defaultOptionsColumnWidth = 24;

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
    public static StringBuilder join(Ansi ansi, int usageHelpWidth, String[] values,
            StringBuilder sb, Object... params) {
        if (values != null) {
            TextTable table = TextTable.forColumnWidths(ansi, usageHelpWidth);
            table.indentWrappedLines = 0;
            for (String summaryLine : values) {
                Text[] lines = new Text(ansi, Utils.safeFormat(summaryLine, params)).splitLines();
                for (Text line : lines) {
                    table.addRowValues(line);
                }
            }
            table.toString(sb);
        }
        return sb;
    }

    private static void addTrailingDefaultLine(List<Text[]> result, ArgSpec arg,
            ColorScheme scheme) {
        Text EMPTY = Ansi.EMPTY_TEXT;
        result.add(new Text[] { EMPTY, EMPTY, EMPTY, EMPTY, createDefaultValueText(arg, scheme) });
    }

    private static Text createDefaultValueText(ArgSpec arg, ColorScheme scheme) {
        return new Text(scheme.ansi(), "DEFAULT: ").append(arg.defaultValueString());
    }

    private static Text[] createDescriptionFirstLines(ColorScheme scheme, ArgSpec arg,
            String[] description, boolean[] showDefault) {
        Text[] result = new Text(scheme.ansi(), Utils.safeGet(description, 0)).splitLines();
        if (Utils.isEmptyAtAll(result)) {
            if (showDefault[0]) {
                result = new Text[] { createDefaultValueText(arg, scheme) };
                showDefault[0] = false; // don't show the default value twice
            } else {
                result = new Text[] { Ansi.EMPTY_TEXT };
            }
        }
        return result;
    }

    protected final CommandSpec commandSpec;

    protected final ColorScheme colorScheme;

    protected final Map<String, Help> commands = new LinkedHashMap<>();

    protected List<String> aliases = Collections.emptyList();

    protected Sections sections;

    private Rendering rendering;

    /**
     * Constructs a new {@code Help} instance with a default color scheme.
     *
     * @param commandSpec
     *            Command model to create usage help for.
     */
    public Help(CommandSpec commandSpec) {
        this(commandSpec, Ansi.AUTO);
    }

    /**
     * Constructs a new {@code Help} instance with a default color scheme.
     *
     * @param commandSpec
     *            Command model to create usage help for.
     * @param ansi
     *            whether to emit ANSI escape codes or not
     */
    public Help(CommandSpec commandSpec, Ansi ansi) {
        this(commandSpec, ColorScheme.createDefault(ansi));
    }

    /**
     * Constructs a new {@code Help} instance with the specified color scheme.
     *
     * @param commandSpec
     *            Command model to create usage help for.
     * @param colorScheme
     *            Color scheme to use.
     */
    public Help(CommandSpec commandSpec, ColorScheme colorScheme) {
        this.commandSpec = Assert.notNull(commandSpec, "commandSpec");
        this.colorScheme = Assert.notNull(colorScheme, "colorScheme").applySystemProperties();

        this.aliases = new ArrayList<>(commandSpec.aliases());
        this.aliases.add(0, commandSpec.name());
        this.addAllSubcommands(commandSpec.subcommands());

        this.rendering = commandSpec.commandLine() != null
                ? commandSpec.commandLine().helpFactory().createRendering(this)
                : new HelpFactory().createRendering(this);
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
                    done.put(cmd, new ArrayList<>(cmd.getCommandSpec().aliases()));
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
                        ColorScheme.createDefault(Ansi.AUTO)));
        return this;
    }

    /**
     * Returns whether ANSI escape codes are enabled or not.
     *
     * @return whether ANSI escape codes are enabled or not
     */
    public Ansi ansi() {
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
    public ColorScheme colorScheme() {
        return colorScheme;
    }

    /**
     * Returns the {@code CommandSpec} model that this Help was constructed with.
     *
     * @since 3.0
     */
    public CommandSpec commandSpec() {
        return commandSpec;
    }

    public Rendering rendering() {
        return rendering;
    }

    public Sections sections() {
        if (sections == null) {
            sections = new Sections();
            sections.add(new Section<String[]>("header",
                    (help) -> help.commandSpec().usageMessage().headerHeading(),
                    (help) -> help.commandSpec().usageMessage().header(),
                    (help) -> help.rendering().simpleSectionBody()));
            sections.add(new Section<UsageMessageSpec>("synopsis",
                    (help) -> help.commandSpec().usageMessage().synopsisHeading(),
                    (help) -> help.commandSpec().usageMessage(),
                    (help) -> help.rendering().synopsis()));
            sections.add(new Section<String[]>("description",
                    (help) -> help.commandSpec().usageMessage().descriptionHeading(),
                    (help) -> help.commandSpec().usageMessage().description(),
                    (help) -> help.rendering().simpleSectionBody()));
            sections.add(new Section<List<PositionalParamSpec>>("parameterList",
                    (help) -> help.commandSpec().usageMessage().parameterListHeading(),
                    (help) -> help.commandSpec().positionalParameters(),
                    (help) -> help.rendering().parameterList()));
            sections.add(new Section<List<OptionSpec>>("optionList",
                    (help) -> help.commandSpec().usageMessage().optionListHeading(),
                    (help) -> help.commandSpec().options(),
                    (help) -> help.rendering().optionList()));
            sections.add(new Section<Map<String, Help>>("commandList",
                    (help) -> help.commandSpec.usageMessage().commandListHeading(),
                    (help) -> help.commands, (help) -> help.rendering().commandList()));
            sections.add(new Section<String[]>("footer",
                    (help) -> help.commandSpec().usageMessage().footerHeading(),
                    (help) -> help.commandSpec().usageMessage().footer(),
                    (help) -> help.rendering().simpleSectionBody()));
        }
        return sections;
    }

    public Section<?> sections(String name) {
        return sections().get(name);
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
     * Returns a {@code Layout} instance configured with the user preferences captured in this Help
     * instance.
     *
     * @return a Layout
     */
    //TODO:check whether to remove it.
    Layout createDefaultLayout() {
        return createLayout(Help.defaultOptionsColumnWidth);
    }

    Help withCommandNames(List<String> aliases) {
        this.aliases = aliases;
        return this;
    }

    private Layout createLayout(int longOptionsColumnWidth) {
        return new Layout(colorScheme,
                TextTable.forDefaultColumns(colorScheme.ansi(), longOptionsColumnWidth,
                        commandSpec.usageMessage().width()),
                rendering().option(), rendering().parameter());
    }
}