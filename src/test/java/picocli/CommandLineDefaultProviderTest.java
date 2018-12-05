package picocli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import picocli.annot.Command;
import picocli.annot.Option;
import picocli.annot.Parameters;
import picocli.help.Ansi;
import picocli.help.Help;
import picocli.model.ArgSpec;
import picocli.model.IDefaultValueProvider;

public class CommandLineDefaultProviderTest {

    static class TestDefaultProvider implements IDefaultValueProvider {
        public String defaultValue(ArgSpec argSpec) {
            return "Default provider string value";
        }
    }

    static class TestNullDefaultProvider implements IDefaultValueProvider {
        public String defaultValue(ArgSpec argSpec) {
            return null;
        }
    }

    @Command(defaultValueProvider = TestDefaultProvider.class, abbreviateSynopsis = true)
    static class App {
        @Option(names = "-a")
        private String optionStringFieldWithoutDefaultNorInitialValue;
        @Option(names = "-b", defaultValue = "Annotated default value")
        private String optionStringFieldWithAnnotatedDefault;
        @Option(names = "-c",showDefaultValue = Help.Visibility.ALWAYS)
        private String optionStringFieldWithInitDefault = "Initial default value";

        @Parameters(arity = "0..1", showDefaultValue = Help.Visibility.ALWAYS)
        private String paramStringFieldWithoutDefaultNorInitialValue;
        @Parameters(arity = "0..1", defaultValue = "Annotated default value")
        private String paramStringFieldWithAnnotatedDefault;
        @Parameters(arity = "0..1")
        private String paramStringFieldWithInitDefault = "Initial default value";

        private String stringForSetterDefault;

        @Option(names = "-d", defaultValue = "Annotated setter default value")
        void setString(String val) {
            stringForSetterDefault = val;
        }
    }

    @Command(name = "sub")
    static class Sub {
        @Option(names = "-a")
        private String optionStringFieldWithoutDefaultNorInitialValue;
        @Option(names = "-b", defaultValue = "Annotated default value")
        private String optionStringFieldWithAnnotatedDefault;
        @Option(names = "-c")
        private String optionStringFieldWithInitDefault = "Initial default value";

        @Parameters(arity = "0..1")
        private String paramStringFieldWithoutDefaultNorInitialValue;
        @Parameters(arity = "0..1", defaultValue = "Annotated default value")
        private String paramStringFieldWithAnnotatedDefault;
        @Parameters(arity = "0..1")
        private String paramStringFieldWithInitDefault = "Initial default value";

        private String stringForSetterDefault;

        @Option(names = "-d", defaultValue = "Annotated setter default value")
        void setString(String val) {
            stringForSetterDefault = val;
        }
    }

    @Test
    public void testCommandDefaultProviderByAnnotationOverridesValues() {
        CommandLine cmd = new CommandLine(App.class);
        cmd.parse();

        App app = cmd.getCommand();
        // if no default defined on the option, command default provider should be used
        assertEquals("Default provider string value",
                app.optionStringFieldWithoutDefaultNorInitialValue);
        assertEquals("Default provider string value",
                app.paramStringFieldWithoutDefaultNorInitialValue);
        // if a default is defined on the option either by annotation or by initial value, it must
        // override the default provider.
        assertEquals("Default provider string value", app.optionStringFieldWithAnnotatedDefault);
        assertEquals("Default provider string value", app.paramStringFieldWithAnnotatedDefault);

        assertEquals("Default provider string value", app.optionStringFieldWithInitDefault);
        assertEquals("Default provider string value", app.paramStringFieldWithInitDefault);

        assertEquals("Default provider string value", app.stringForSetterDefault);
    }

    @Test
    public void testCommandDefaultProviderDoesntOverridesDefaultsIfValueIsNull() {
        CommandLine cmd = new CommandLine(App.class);

        cmd.setDefaultValueProvider(new TestNullDefaultProvider());

        cmd.parse();

        App app = cmd.getCommand();
        // if no default defined on the option, command default provider should be used
        assertNull(app.optionStringFieldWithoutDefaultNorInitialValue);
        assertNull(app.paramStringFieldWithoutDefaultNorInitialValue);
        // if a default is defined on the option either by annotation or by initial value, it must
        // override the default provider.
        assertEquals("Annotated default value", app.optionStringFieldWithAnnotatedDefault);
        assertEquals("Annotated default value", app.paramStringFieldWithAnnotatedDefault);

        assertEquals("Initial default value", app.optionStringFieldWithInitDefault);
        assertEquals("Initial default value", app.paramStringFieldWithInitDefault);

        assertEquals("Annotated setter default value", app.stringForSetterDefault);
    }

    @Test
    public void testDefaultProviderNullByDefault() {
        CommandLine cmd = new CommandLine(Sub.class);
        assertNull(cmd.getDefaultValueProvider());
    }

    @Test
    public void testDefaultProviderReturnsSetValue() {
        CommandLine cmd = new CommandLine(Sub.class);
        TestDefaultProvider provider = new TestDefaultProvider();
        cmd.setDefaultValueProvider(provider);
        assertSame(provider, cmd.getDefaultValueProvider());
    }

    @Test
    public void testDefaultProviderPropagatedToSubCommand() {
        CommandLine cmd = new CommandLine(App.class);

        cmd.addSubcommand("sub", new CommandLine(Sub.class));

        CommandLine subCommandLine = cmd.getSubcommands().get("sub");
        cmd.setDefaultValueProvider(new TestDefaultProvider());

        assertNotNull(subCommandLine.getCommandSpec().defaultValueProvider());
        assertEquals(TestDefaultProvider.class,
                subCommandLine.getCommandSpec().defaultValueProvider().getClass());
    }

    @Test
    public void testCommandDefaultProviderSetting() {

        CommandLine cmd = new CommandLine(App.class);
        cmd.setDefaultValueProvider(new TestDefaultProvider());
        cmd.parse();

        App app = cmd.getCommand();
        // if no default defined on the option, command default provider should be used
        assertEquals("Default provider string value",
                app.optionStringFieldWithoutDefaultNorInitialValue);
    }

    @Test
    public void testDefaultValueInDescription() {
        String expected = String.format(""
                + "Usage: <main class> [OPTIONS] [<paramStringFieldWithoutDefaultNorInitialValue>] [<paramStringFieldWithAnnotatedDefault>] [<paramStringFieldWithInitDefault>]%n"
                + "      [<paramStringFieldWithoutDefaultNorInitialValue>]%n"
                + "                 DEFAULT: Default provider string value%n"
                + "      [<paramStringFieldWithAnnotatedDefault>]%n"
                + "      [<paramStringFieldWithInitDefault>]%n"
                + "  -a=<optionStringFieldWithoutDefaultNorInitialValue>%n"
                + "  -b=<optionStringFieldWithAnnotatedDefault>%n"
                + "  -c=<optionStringFieldWithInitDefault>%n"
                + "                 DEFAULT: Default provider string value%n"
                + "  -d=<string>%n");
        CommandLine cmd = new CommandLine(App.class);
        assertEquals(expected, cmd.getUsageMessage(Ansi.OFF));
    }

    @Test
    public void testDefaultValueInDescriptionAfterSetProvider() {
        String expected2 = String.format(""
                + "Usage: <main class> [OPTIONS] [<paramStringFieldWithoutDefaultNorInitialValue>] [<paramStringFieldWithAnnotatedDefault>] [<paramStringFieldWithInitDefault>]%n"
                + "      [<paramStringFieldWithoutDefaultNorInitialValue>]%n"
                + "                 DEFAULT: XYZ%n"
                + "      [<paramStringFieldWithAnnotatedDefault>]%n"
                + "      [<paramStringFieldWithInitDefault>]%n"
                + "  -a=<optionStringFieldWithoutDefaultNorInitialValue>%n"
                + "  -b=<optionStringFieldWithAnnotatedDefault>%n"
                + "  -c=<optionStringFieldWithInitDefault>%n"
                + "                 DEFAULT: XYZ%n"
                + "  -d=<string>%n");
        CommandLine cmd = new CommandLine(App.class);
        cmd.setDefaultValueProvider(new IDefaultValueProvider() {
            public String defaultValue(ArgSpec argSpec) throws Exception {
                return "XYZ";
            }
        });
        assertEquals(expected2, cmd.getUsageMessage(Ansi.OFF));
    }

    @Test
    public void testDefaultValueInDescriptionWithErrorProvider() {
        String expected2 = String.format(""
                + "Usage: <main class> [OPTIONS] [<paramStringFieldWithoutDefaultNorInitialValue>] [<paramStringFieldWithAnnotatedDefault>] [<paramStringFieldWithInitDefault>]%n"
                + "      [<paramStringFieldWithoutDefaultNorInitialValue>]%n"
                + "                 DEFAULT: null%n"
                + "      [<paramStringFieldWithAnnotatedDefault>]%n"
                + "      [<paramStringFieldWithInitDefault>]%n"
                + "  -a=<optionStringFieldWithoutDefaultNorInitialValue>%n"
                + "  -b=<optionStringFieldWithAnnotatedDefault>%n"
                + "  -c=<optionStringFieldWithInitDefault>%n"
                + "                 DEFAULT: Initial default value%n"
                + "  -d=<string>%n");
        CommandLine cmd = new CommandLine(App.class);
        cmd.setDefaultValueProvider(new IDefaultValueProvider() {
            public String defaultValue(ArgSpec argSpec) throws Exception {
                throw new IllegalStateException("abc");
            }
        });
        assertEquals(expected2, cmd.getUsageMessage(Ansi.OFF));
    }
}
