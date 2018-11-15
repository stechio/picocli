/*
   Copyright 2017 Remko Popma

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package picocli;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import picocli.annots.Command;
import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.model.CommandSpec;

/**
 * Tests the scripts generated by AutoComplete.
 */
// http://hayne.net/MacDev/Notes/unixFAQ.html#shellStartup
// https://apple.stackexchange.com/a/13019
public class AutoCompleteTest {
    @Rule
    public final ProvideSystemProperty ansiOFF = new ProvideSystemProperty("picocli.ansi", "false");

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    public static void main(String[] args) {
        TopLevel.main(args);
    }
    public static class BasicExample implements Runnable {
        @Option(names = {"-u", "--timeUnit"}) private TimeUnit timeUnit;
        @Option(names = {"-t", "--timeout"}) private long timeout;
        public void run() {
            System.out.printf("BasicExample was invoked with %d %s.%n", timeout, timeUnit);
        }
        public static void main(String[] args) { CommandLine.run(new BasicExample(), System.out, args); }
    }
    @Test
    public void basic() throws Exception {
        String script = AutoComplete.bash("basicExample", new CommandLine(new BasicExample()));
        String expected = format(loadTextFromClasspath("/basic.bash"),
                CommandLine.VERSION, spaced(TimeUnit.values()));
        assertEquals(expected, script);
    }

    public static class TopLevel {
        @Option(names = {"-V", "--version"}, help = true) boolean versionRequested;
        @Option(names = {"-h", "--help"}, help = true) boolean helpRequested;
        public static void main(String[] args) {
            CommandLine hierarchy = new CommandLine(new TopLevel())
                    .addSubcommand("sub1", new Sub1())
                    .addSubcommand("sub2", new CommandLine(new Sub2())
                            .addSubcommand("subsub1", new Sub2Child1())
                            .addSubcommand("subsub2", new Sub2Child2())
                    );
            List<CommandLine> commandLines = hierarchy.parse(args);
            //Collections.reverse(commandLines);
            for (CommandLine cmdLine : commandLines) {
                Object command = cmdLine.getCommand();
                System.out.printf("Parsed command %s%n", AutoCompleteTest.toString(command));
            }
        }
    }
    static class Candidates extends ArrayList<String> {
        Candidates() {super(Arrays.asList("a", "b", "c"));}
    }
    @Command(description = "First level subcommand 1")
    public static class Sub1 {
        @Option(names = "--num", description = "a number") double number;
        @Option(names = "--str", description = "a String") String str;
        @Option(names = "--candidates", choiceValues = Candidates.class, description = "with candidates") String str2;
    }
    @Command(description = "First level subcommand 2")
    public static class Sub2 {
        @Option(names = "--num2", description = "another number") int number2;
        @Option(names = {"--directory", "-d"}, description = "a directory") File directory;
    }
    @Command(description = "Second level sub-subcommand 1")
    public static class Sub2Child1 {
        @Option(names = {"-h", "--host"}, description = "a host") InetAddress host;
    }
    @Command(description = "Second level sub-subcommand 2")
    public static class Sub2Child2 {
        @Option(names = {"-u", "--timeUnit"}) private TimeUnit timeUnit;
        @Option(names = {"-t", "--timeout"}) private long timeout;
        @Parameters(choiceValues = Candidates.class, description = "with candidates") String str2;
    }

    @Test
    public void nestedSubcommands() throws Exception {
        CommandLine hierarchy = new CommandLine(new TopLevel())
                .addSubcommand("sub1", new Sub1())
                .addSubcommand("sub2", new CommandLine(new Sub2())
                        .addSubcommand("subsub1", new Sub2Child1())
                        .addSubcommand("subsub2", new Sub2Child2())
                );
        String script = AutoComplete.bash("picocompletion-demo", hierarchy);
        String expected = format(loadTextFromClasspath("/picocompletion-demo_completion.bash"),
                CommandLine.VERSION, spaced(TimeUnit.values()));
        assertEquals(expected, script);
    }

    private static String spaced(Object[] values) {
        StringBuilder result = new StringBuilder();
        for (Object value : values) {
            result.append(value).append(' ');
        }
        return result.toString().substring(0, result.length() - 1);
    }

    static String loadTextFromClasspath(String path) {
        URL url = AutoCompleteTest.class.getResource(path);
        if (url == null) { throw new IllegalArgumentException("Could not find '" + path + "' in classpath."); }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuilder result = new StringBuilder(512);
            char[] buff = new char[4096];
            int read = 0;
            do {
                result.append(buff, 0, read);
                read = reader.read(buff);
            } while (read >= 0);
            return result.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + url + " for '" + path + "':", ex);
        } finally {
            if (reader != null) { try { reader.close(); } catch (IOException e) { /* ignore */ } }
        }
    }

    private static String toString(Object obj) {
        StringBuilder sb = new StringBuilder(256);
        Class<?> cls = obj.getClass();
        sb.append(cls.getSimpleName()).append("[");
        String sep = "";
        for (Field f : cls.getDeclaredFields()) {
            f.setAccessible(true);
            sb.append(sep).append(f.getName()).append("=");
            try { sb.append(f.get(obj)); } catch (Exception ex) { sb.append(ex); }
            sep = ", ";
        }
        return sb.append("]").toString();
    }

    private static final String AUTO_COMPLETE_APP_USAGE = String.format("" +
            "Usage: picocli.AutoComplete [-fhw] [-n=<commandName>] [-o=<autoCompleteScript>]%n" +
            "                            <commandLineFQCN>%n" +
            "Generates a bash completion script for the specified command class.%n" +
            "      <commandLineFQCN>      Fully qualified class name of the annotated @Command%n" +
            "                               class to generate a completion script for.%n" +
            "  -n, --name=<commandName>   Name of the command to create a completion script for.%n" +
            "                               When omitted, the annotated class @Command 'name'%n" +
            "                               attribute is used. If no @Command 'name' attribute%n" +
            "                               exists, '<CLASS-SIMPLE-NAME>' (in lower-case) is used.%n" +
            "  -o, --completionScript=<autoCompleteScript>%n" +
            "                             Path of the completion script file to generate. When%n" +
            "                               omitted, a file named '<commandName>_completion' is%n" +
            "                               generated in the current directory.%n" +
            "  -w, --writeCommandScript   Write a '<commandName>' sample command script to the%n" +
            "                               same directory as the completion script.%n" +
            "  -f, --force                Overwrite existing script files.%n" +
            "  -h, --help                 Display this help message and quit.%n");

    @Test
    public void testAutoCompleteAppHelp() throws Exception {
        String[][] argsList = new String[][] {
                {"-h"},
                {"--help"},
        };
        for (String[] args : argsList) {
            AutoComplete.main(args);
            assertEquals(AUTO_COMPLETE_APP_USAGE, systemErrRule.getLog());
            systemErrRule.clearLog();
        }
    }

    @Test
    public void testAutoCompleteRequiresCommandLineFQCN() throws Exception {
        AutoComplete.main();

        String expected = String.format("Missing required parameter: <commandLineFQCN>%n") + AUTO_COMPLETE_APP_USAGE;
        assertEquals(expected, systemErrRule.getLog());
    }

    @Test
    public void testAutoCompleteAppCannotInstantiate() throws Exception {
        @Command(name = "test")
        class TestApp {
            public TestApp(String noDefaultConstructor) { throw new RuntimeException();}
        }

        AutoComplete.main(TestApp.class.getName());

        String actual = systemErrRule.getLog();
        assertTrue(actual.startsWith("java.lang.NoSuchMethodException: picocli.AutoCompleteTest$1TestApp.<init>()"));
        assertTrue(actual.endsWith(AUTO_COMPLETE_APP_USAGE));
    }

    @Test
    public void testAutoCompleteAppCompletionScriptFileWillNotOverwrite() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        File completionScript = new File(dir, "App_completion");
        if (completionScript.exists()) {assertTrue(completionScript.delete());}
        completionScript.deleteOnExit();

        // create the file
        FileOutputStream fous = new FileOutputStream(completionScript, false);
        fous.close();

        AutoComplete.main(String.format("-o=%s", completionScript.getAbsolutePath()), "picocli.AutoComplete$App");

        String expected = String.format("" +
                "%s exists. Specify -f to overwrite.%n" +
                "%s", completionScript.getAbsolutePath(), AUTO_COMPLETE_APP_USAGE);
        assertEquals(expected, systemErrRule.getLog());
    }

    @Test
    public void testAutoCompleteAppCommandScriptFileWillNotOverwrite() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        File commandScript = new File(dir, "picocli.AutoComplete");
        if (commandScript.exists()) {assertTrue(commandScript.delete());}
        commandScript.deleteOnExit();

        // create the file
        FileOutputStream fous = new FileOutputStream(commandScript, false);
        fous.close();

        File completionScript = new File(dir, commandScript.getName() + "_completion");
        AutoComplete.main("--writeCommandScript", String.format("-o=%s", completionScript.getAbsolutePath()), "picocli.AutoComplete$App");

        String expected = String.format("" +
                "%s exists. Specify -f to overwrite.%n" +
                "%s", commandScript.getAbsolutePath(), AUTO_COMPLETE_APP_USAGE);
        assertEquals(expected, systemErrRule.getLog());
    }

    @Test
    public void testAutoCompleteAppBothScriptFilesForceOverwrite() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        File commandScript = new File(dir, "picocli.AutoComplete");
        if (commandScript.exists()) {assertTrue(commandScript.delete());}
        commandScript.deleteOnExit();

        // create the file
        FileOutputStream fous1 = new FileOutputStream(commandScript, false);
        fous1.close();

        File completionScript = new File(dir, commandScript.getName() + "_completion");
        if (completionScript.exists()) {assertTrue(completionScript.delete());}
        completionScript.deleteOnExit();

        // create the file
        FileOutputStream fous2 = new FileOutputStream(completionScript, false);
        fous2.close();

        AutoComplete.main("--force", "--writeCommandScript", String.format("-o=%s", completionScript.getAbsolutePath()), "picocli.AutoComplete$App");

        byte[] command = readBytes(commandScript);
        assertEquals(("" +
                "#!/usr/bin/env bash\n" +
                "\n" +
                "LIBS=path/to/libs\n" +
                "CP=\"${LIBS}/myApp.jar\"\n" +
                "java -cp \"${CP}\" 'picocli.AutoComplete$App' $@"), new String(command, "UTF8"));

        byte[] completion = readBytes(completionScript);

        String expected = ("" +
                "#!/usr/bin/env bash\n" +
                "#\n" +
                "# picocli.AutoComplete Bash Completion\n" +
                "# =======================\n" +
                "#\n" +
                "# Bash completion support for the `picocli.AutoComplete` command,\n" +
                "# generated by [picocli](http://picocli.info/) version 4.0.0-SNAPSHOT.\n" +
                "#\n" +
                "# Installation\n" +
                "# ------------\n" +
                "#\n" +
                "# 1. Source all completion scripts in your .bash_profile\n" +
                "#\n" +
                "#   cd $YOUR_APP_HOME/bin\n" +
                "#   for f in $(find . -name \"*_completion\"); do line=\". $(pwd)/$f\"; grep \"$line\" ~/.bash_profile || echo \"$line\" >> ~/.bash_profile; done\n" +
                "#\n" +
                "# 2. Open a new bash console, and type `picocli.AutoComplete [TAB][TAB]`\n" +
                "#\n" +
                "# 1a. Alternatively, if you have [bash-completion](https://github.com/scop/bash-completion) installed:\n" +
                "#     Place this file in a `bash-completion.d` folder:\n" +
                "#\n" +
                "#   * /etc/bash-completion.d\n" +
                "#   * /usr/local/etc/bash-completion.d\n" +
                "#   * ~/bash-completion.d\n" +
                "#\n" +
                "# Documentation\n" +
                "# -------------\n" +
                "# The script is called by bash whenever [TAB] or [TAB][TAB] is pressed after\n" +
                "# 'picocli.AutoComplete (..)'. By reading entered command line parameters,\n" +
                "# it determines possible bash completions and writes them to the COMPREPLY variable.\n" +
                "# Bash then completes the user input if only one entry is listed in the variable or\n" +
                "# shows the options if more than one is listed in COMPREPLY.\n" +
                "#\n" +
                "# References\n" +
                "# ----------\n" +
                "# [1] http://stackoverflow.com/a/12495480/1440785\n" +
                "# [2] http://tiswww.case.edu/php/chet/bash/FAQ\n" +
                "# [3] https://www.gnu.org/software/bash/manual/html_node/The-Shopt-Builtin.html\n" +
                "# [4] http://zsh.sourceforge.net/Doc/Release/Options.html#index-COMPLETE_005fALIASES\n" +
                "# [5] https://stackoverflow.com/questions/17042057/bash-check-element-in-array-for-elements-in-another-array/17042655#17042655\n" +
                "# [6] https://www.gnu.org/software/bash/manual/html_node/Programmable-Completion.html#Programmable-Completion\n" +
                "#\n" +
                "\n" +
                "if [ -n \"$BASH_VERSION\" ]; then\n" +
                "  # Enable programmable completion facilities when using bash (see [3])\n" +
                "  shopt -s progcomp\n" +
                "elif [ -n \"$ZSH_VERSION\" ]; then\n" +
                "  # Make alias a distinct command for completion purposes when using zsh (see [4])\n" +
                "  setopt COMPLETE_ALIASES\n" +
                "  alias compopt=complete\n" +
                "fi\n" +
                "\n" +
                "# ArrContains takes two arguments, both of which are the name of arrays.\n" +
                "# It creates a temporary hash from lArr1 and then checks if all elements of lArr2\n" +
                "# are in the hashtable.\n" +
                "#\n" +
                "# Returns zero (no error) if all elements of the 2nd array are in the 1st array,\n" +
                "# otherwise returns 1 (error).\n" +
                "#\n" +
                "# Modified from [5]\n" +
                "function ArrContains() {\n" +
                "  local lArr1 lArr2\n" +
                "  declare -A tmp\n" +
                "  eval lArr1=(\"\\\"\\${$1[@]}\\\"\")\n" +
                "  eval lArr2=(\"\\\"\\${$2[@]}\\\"\")\n" +
                "  for i in \"${lArr1[@]}\";{ [ -n \"$i\" ] && ((++tmp[$i]));}\n" +
                "  for i in \"${lArr2[@]}\";{ [ -n \"$i\" ] && [ -z \"${tmp[$i]}\" ] && return 1;}\n" +
                "  return 0\n" +
                "}\n" +
                "\n" +
                "# Bash completion entry point function.\n" +
                "# _complete_picocli.AutoComplete finds which commands and subcommands have been specified\n" +
                "# on the command line and delegates to the appropriate function\n" +
                "# to generate possible options and subcommands for the last specified subcommand.\n" +
                "function _complete_picocli.AutoComplete() {\n" +
                "\n" +
                "\n" +
                "  # No subcommands were specified; generate completions for the top-level command.\n" +
                "  _picocli_picocli.AutoComplete; return $?;\n" +
                "}\n" +
                "\n" +
                "# Generates completions for the options and subcommands of the `picocli.AutoComplete` command.\n" +
                "function _picocli_picocli.AutoComplete() {\n" +
                "  # Get completion data\n" +
                "  CURR_WORD=${COMP_WORDS[COMP_CWORD]}\n" +
                "  PREV_WORD=${COMP_WORDS[COMP_CWORD-1]}\n" +
                "\n" +
                "  COMMANDS=\"\"\n" +
                "  FLAG_OPTS=\"-w --writeCommandScript -f --force -h --help\"\n" +
                "  ARG_OPTS=\"-n --name -o --completionScript\"\n" +
                "\n" +
                "  compopt +o default\n" +
                "\n" +
                "  case ${PREV_WORD} in\n" +
                "    -n|--name)\n" +
                "      return\n" +
                "      ;;\n" +
                "    -o|--completionScript)\n" +
                "      compopt -o filenames\n" +
                "      COMPREPLY=( $( compgen -f -- ${CURR_WORD} ) ) # files\n" +
                "      return $?\n" +
                "      ;;\n" +
                "  esac\n" +
                "\n" +
                "  if [[ \"${CURR_WORD}\" == -* ]]; then\n" +
                "    COMPREPLY=( $(compgen -W \"${FLAG_OPTS} ${ARG_OPTS}\" -- ${CURR_WORD}) )\n" +
                "  else\n" +
                "    COMPREPLY=( $(compgen -W \"${COMMANDS}\" -- ${CURR_WORD}) )\n" +
                "  fi\n" +
                "}\n" +
                "\n" +
                "# Define a completion specification (a compspec) for the\n" +
                "# `picocli.AutoComplete`, `picocli.AutoComplete.sh`, and `picocli.AutoComplete.bash` commands.\n" +
                "# Uses the bash `complete` builtin (see [6]) to specify that shell function\n" +
                "# `_complete_picocli.AutoComplete` is responsible for generating possible completions for the\n" +
                "# current word on the command line.\n" +
                "# The `-o default` option means that if the function generated no matches, the\n" +
                "# default Bash completions and the Readline default filename completions are performed.\n" +
                "complete -F _complete_picocli.AutoComplete -o default picocli.AutoComplete picocli.AutoComplete.sh picocli.AutoComplete.bash\n");
        assertEquals(expected, new String(completion, "UTF8"));
    }
    public static byte[] readBytes(File f) throws IOException {
        int pos = 0;
        int len = 0;
        byte[] buffer = new byte[(int) f.length()];
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            while ((len = fis.read(buffer, pos, buffer.length - pos)) > 0) {
                pos += len;
            }
            return buffer;
        } finally {
            fis.close();
        }
    }
    @Test
    public void testCommandDescriptor() {
        AutoComplete.CommandDescriptor descriptor = new AutoComplete.CommandDescriptor("aaa", "bbb");
        assertEquals(descriptor, descriptor);

        AutoComplete.CommandDescriptor other = new AutoComplete.CommandDescriptor("111", "222");
        assertNotEquals(descriptor, other);

        assertEquals(descriptor.hashCode(), descriptor.hashCode());
        assertEquals(other.hashCode(), other.hashCode());
        assertNotEquals(other.hashCode(), descriptor.hashCode());
    }

    @Test
    public void testBashRejectsNullScript() {
        try {
            AutoComplete.bash(null, new CommandLine(new TopLevel()));
            fail("Expected NPE");
        } catch (NullPointerException ok) {
            assertEquals("scriptName", ok.getMessage());
        }
    }

    @Test
    public void testBashRejectsNullCommandLine() {
        try {
            AutoComplete.bash("script", null);
            fail("Expected NPE");
        } catch (NullPointerException ok) {
            assertEquals("commandLine", ok.getMessage());
        }
    }

    @Test
    public void testBashAcceptsNullCommand() throws Exception {
        File temp = File.createTempFile("abc", "b");
        temp.deleteOnExit();
        AutoComplete.bash("script", temp, null, new CommandLine(new TopLevel()));
        assertTrue(temp.length() > 0);
    }

    @Test
    public void testBashRejectsNullOut() throws Exception {
        File commandFile = File.createTempFile("abc", "b");
        commandFile.deleteOnExit();
        try {
            AutoComplete.bash("script", null, commandFile,  new CommandLine(new TopLevel()));
            fail("Expected NPE");
        } catch (NullPointerException ok) {
            assertEquals(null, ok.getMessage());
        }
    }

    @Command
    private static class PrivateCommandClass { }
    //Support generating autocompletion scripts for non-public @Command classes #306
    @Test
    public void test306_SupportGeneratingAutocompletionScriptForNonPublicCommandClasses() {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        File completionScript = new File(dir, "App_completion");
        if (completionScript.exists()) {assertTrue(completionScript.delete());}
        completionScript.deleteOnExit();

        AutoComplete.main(String.format("-o=%s", completionScript.getAbsolutePath()), PrivateCommandClass.class.getName());
        assertEquals("", systemErrRule.getLog());
        assertEquals("", systemOutRule.getLog());

        completionScript.delete();
    }

    @Test
    public void testComplete() {
        CommandLine hierarchy = new CommandLine(new TopLevel())
                .addSubcommand("sub1", new Sub1())
                .addSubcommand("sub2", new CommandLine(new Sub2())
                        .addSubcommand("subsub1", new Sub2Child1())
                        .addSubcommand("subsub2", new Sub2Child2())
                );

        CommandSpec spec = hierarchy.getCommandSpec();
        spec.parser().collectErrors(true);
        int cur = 500;

        test(spec, a(),                                       0, 0, cur, l("--help", "--version", "-V", "-h", "sub1", "sub2"));
        test(spec, a("-"),                                    0, 0, cur, l("--help", "--version", "-V", "-h", "sub1", "sub2"));
        test(spec, a("-"),                                    0, 1, cur, l("-help", "-version", "V", "h"));
        test(spec, a("-h"),                                   0, 1, cur, l("-help", "-version", "V", "h"));
        test(spec, a("-h"),                                   0, 2, cur, l(""));
        test(spec, a("s"),                                    0, 1, cur, l("ub1", "ub2"));
        test(spec, a("sub1"),                                 1, 0, cur, l("--candidates", "--num", "--str"));
        test(spec, a("sub1", "-"),                            1, 0, cur, l("--candidates", "--num", "--str"));
        test(spec, a("sub1", "-"),                            1, 1, cur, l("-candidates", "-num", "-str"));
        test(spec, a("sub1", "--"),                           1, 1, cur, l("-candidates", "-num", "-str"));
        test(spec, a("sub1", "--"),                           1, 2, cur, l("candidates", "num", "str"));
        test(spec, a("sub1", "--c"),                          1, 2, cur, l("candidates", "num", "str"));
        test(spec, a("sub1", "--c"),                          1, 3, cur, l("andidates"));
        test(spec, a("sub1", "--candidates"),                 2, 0, cur, l("a", "b", "c"));
        test(spec, a("sub1", "--candidates", "a"),            2, 1, cur, l(""));
        test(spec, a("sub1", "--candidates", "a"),            3, 0, cur, l("--candidates", "--num", "--str"));
        test(spec, a("sub1", "--candidates", "a", "-"),       3, 1, cur, l("-candidates", "-num", "-str"));
        test(spec, a("sub1", "--candidates", "a", "--"),      3, 2, cur, l("candidates", "num", "str"));
        test(spec, a("sub1", "--num"),                        2, 0, cur, l());
        test(spec, a("sub1", "--str"),                        2, 0, cur, l());
        test(spec, a("sub2"),                                 1, 0, cur, l("--directory", "--num2", "-d", "subsub1", "subsub2"));
        test(spec, a("sub2", "-"),                            1, 1, cur, l("-directory", "-num2", "d"));
        test(spec, a("sub2", "-d"),                           2, 0, cur, l());
        test(spec, a("sub2", "-d", "/"),                      3, 0, cur, l("--directory", "--num2", "-d", "subsub1", "subsub2"));
        test(spec, a("sub2", "-d", "/", "-"),                 3, 1, cur, l("-directory", "-num2", "d"));
        test(spec, a("sub2", "-d", "/", "--"),                3, 2, cur, l("directory", "num2"));
        test(spec, a("sub2", "-d", "/", "--n"),               3, 3, cur, l("um2"));
        test(spec, a("sub2", "-d", "/", "--num2"),            3, 6, cur, l(""));
        test(spec, a("sub2", "-d", "/", "--num2"),            4, 0, cur, l());
        test(spec, a("sub2", "-d", "/", "--num2", "0"),       4, 1, cur, l());
        test(spec, a("sub2", "-d", "/", "--num2", "0"),       5, 0, cur, l("--directory", "--num2", "-d", "subsub1", "subsub2"));
        test(spec, a("sub2", "-d", "/", "--num2", "0", "s"),  5, 1, cur, l("ubsub1", "ubsub2"));
        test(spec, a("sub2", "subsub1"),                      2, 0, cur, l("--host", "-h"));
        test(spec, a("sub2", "subsub2"),                      2, 0, cur, l("--timeUnit", "--timeout", "-t", "-u", "a", "b", "c"));
        test(spec, a("sub2", "subsub2", "-"),                 2, 1, cur, l("-timeUnit", "-timeout", "t", "u"));
        test(spec, a("sub2", "subsub2", "-t"),                2, 2, cur, l(""));
        test(spec, a("sub2", "subsub2", "-t"),                3, 0, cur, l());
        test(spec, a("sub2", "subsub2", "-t", "0"),           3, 1, cur, l());
        test(spec, a("sub2", "subsub2", "-t", "0"),           4, 0, cur, l("--timeUnit", "--timeout", "-t", "-u", "a", "b", "c"));
        test(spec, a("sub2", "subsub2", "-t", "0", "-"),      4, 1, cur, l("-timeUnit", "-timeout", "t", "u"));
        test(spec, a("sub2", "subsub2", "-t", "0", "--"),     4, 2, cur, l("timeUnit", "timeout"));
        test(spec, a("sub2", "subsub2", "-t", "0", "--t"),    4, 3, cur, l("imeUnit", "imeout"));
        test(spec, a("sub2", "subsub2", "-t", "0", "-u"),     4, 2, cur, l(""));
        test(spec, a("sub2", "subsub2", "-t", "0", "-u"),     5, 0, cur, timeUnitValues());
        test(spec, a("sub2", "subsub2", "-t", "0", "-u", "S"),5, 1, cur, l("ECONDS"));
        test(spec, a("sub2", "subsub2", "a"),                 2, 1, cur, l(""));
        test(spec, a("sub2", "subsub2", "a"),                 3, 0, cur, l("--timeUnit", "--timeout", "-t", "-u", "a", "b", "c"));
    }

    private static void test(CommandSpec spec, String[] args, int argIndex, int positionInArg, int cursor, List<CharSequence> expected) {
        List<CharSequence> actual = new ArrayList<CharSequence>();
        AutoComplete.complete(spec, args, argIndex, positionInArg, cursor, actual);
        Collections.sort(actual, new CharSequenceSort());
        Collections.sort(expected, new CharSequenceSort());
        assertEquals(expected, actual);
    }

    private static String[] a(String... args) {
        return args;
    }

    private static List<CharSequence> l(CharSequence... args) {
        return Arrays.asList(args);
    }

    private static List<CharSequence> timeUnitValues() {
        List<CharSequence> result = new ArrayList<CharSequence>();
        for (TimeUnit tu : TimeUnit.values()) { result.add(tu.toString()); }
        return result;
    }

    static class CharSequenceSort implements Comparator<CharSequence> {
        public int compare(CharSequence left, CharSequence right) {
            return left.toString().compareTo(right.toString());
        }
    }
}
