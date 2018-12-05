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

import static org.junit.Assert.assertEquals;
import static picocli.help.HelpTestUtil.usageString;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import picocli.annot.Option;
import picocli.annot.Parameters;
import picocli.help.Ansi;
import picocli.model.CommandSpec;
import picocli.model.OptionSpec;
import picocli.model.PositionalParamSpec;

/**
 * Tests valid values-related functionality.
 */
public class CompletionCandidatesTest {
    @Rule
    public final ProvideSystemProperty ansiOFF = new ProvideSystemProperty("picocli.ansi", "false");

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog()
            .muteForSuccessfulTests();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog()
            .muteForSuccessfulTests();

    static class MyAbcdCandidates extends ArrayList<String> {
        MyAbcdCandidates() {
            super(Arrays.asList("A", "B", "C", "D"));
        }
    }

    enum MyEfgEnum {
        E, F, G
    }

    private static List<String> extract(Iterable<String> generator) {
        List<String> result = new ArrayList<String>();
        for (String e : generator) {
            result.add(e);
        }
        return result;
    }

    @Test
    public void testCompletionCandidatesEnumValues_forOption() {
        class App {
            @Option(names = "-x")
            MyEfgEnum x;
        }
        App app = new App();
        CommandLine cmd = new CommandLine(app);
        assertEquals(Arrays.asList("E", "F", "G"),
                cmd.getCommandSpec().findOption("x").choiceValues());
    }

    @Test
    public void testCompletionCandidatesEnumValues_forParameters() {
        class App {
            @Parameters
            MyEfgEnum x;
        }
        App app = new App();
        CommandLine cmd = new CommandLine(app);
        assertEquals(Arrays.asList("E", "F", "G"),
                cmd.getCommandSpec().positionalParameters().get(0).choiceValues());
    }

    @Test
    public void testCompletionCandidatesPriority_forOption() {

        class App {
            @Option(names = "-x", choiceValues = MyAbcdCandidates.class)
            MyEfgEnum x;
        }
        App app = new App();
        CommandLine cmd = new CommandLine(app);
        assertEquals(Arrays.asList("A", "B", "C", "D"),
                cmd.getCommandSpec().findOption("x").choiceValues());
    }

    @Test
    public void testCompletionCandidatesPriority_forParameters() {

        class App {
            @Parameters(choiceValues = MyAbcdCandidates.class)
            MyEfgEnum x;
        }
        App app = new App();
        CommandLine cmd = new CommandLine(app);
        assertEquals(Arrays.asList("A", "B", "C", "D"),
                cmd.getCommandSpec().positionalParameters().get(0).choiceValues());
    }

    @Test
    public void testCompletionCandidatesValues_forOption() {
        class App {
            @Option(names = "-x", choiceValues = MyAbcdCandidates.class)
            String x;
        }
        CommandLine cmd = new CommandLine(new App());
        assertEquals(Arrays.asList("A", "B", "C", "D"),
                extract(cmd.getCommandSpec().findOption("x").choiceValues()));
    }

    @Test
    public void testCompletionCandidatesValues_forParameters() {
        class App {
            @Parameters(choiceValues = MyAbcdCandidates.class)
            String x;
        }
        CommandLine cmd = new CommandLine(new App());
        assertEquals(Arrays.asList("A", "B", "C", "D"),
                extract(cmd.getCommandSpec().positionalParameters().get(0).choiceValues()));
    }

    @Test
    public void testCompletionCandidatesValues_forOptionSpec() {
        CommandSpec spec = CommandSpec.create();
        spec.add(OptionSpec.builder("-x").choiceValues(Arrays.asList("x", "y", "z"))
                .build());
        assertEquals(Arrays.asList("x", "y", "z"),
                extract(spec.findOption("x").choiceValues()));
    }

    @Test
    public void testCompletionCandidatesValues_forPositionalParamSpec() {
        CommandSpec spec = CommandSpec.create();
        spec.add(PositionalParamSpec.builder().choiceValues(Arrays.asList("x", "y", "z"))
                .build());
        assertEquals(Arrays.asList("x", "y", "z"),
                extract(spec.positionalParameters().get(0).choiceValues()));
    }

    private static Map<String, String> createLongMap() {
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("key1", "veryveryverylonglonglongvaluevaluevalue");
        result.put("key2", "very2very2very2longlonglongvaluevaluevalue2");
        result.put("key3", "very3very3very3longlonglongvaluevaluevalue3");
        return result;
    }

    enum Lang {
        java, kotlin, groovy, javascript, frege, clojure
    }

    @Test
    public void testUsageHelpVariableReplacement() {
        class MyLongCandidates extends ArrayList<String> {
            MyLongCandidates() {
                super(Arrays.asList(
                        "This is a very long list of completion candidates that is intended to wrap to the next line. I wonder if it is long enough."
                                .split(" ")));
            }
        }
        class App {
            @Option(names = "--logfile", description = "Use given file for log.")
            File file = new File("/a/b/c");

            @Option(names = "-P", arity = "0..*", paramLabel = "<key=ppp>", description = "Use value for project key.")
            Map<String, String> projectMap = createLongMap();

            @Option(names = "--x", split = ",", choiceValues = MyAbcdCandidates.class, description = "Comma-separated list of some xxx's.")
            String[] x;

            @Option(names = "--y", description = "Test long default.")
            String y = "This is a very long default value that is intended to wrap to the next line. I wonder if it is long enough.";

            @Option(names = "--lib", choiceValues = MyLongCandidates.class, description = "comma-separated list of up to 3 paths to search for jars and classes.")
            String lib;

            @Option(names = "--boolF", description = "Boolean variable 1.")
            boolean initiallyFalse;

            @Option(names = "--boolT", description = "Boolean variable 2.")
            boolean initiallyTrue = true;

            @Option(names = "--strNull", description = "String without default.")
            String str = null;

            @Option(names = "--enum", description = "Enum.")
            Lang lang = null;
        }
        String expected = String.format(""
                + "Usage: <main class> [--boolF] [--boolT] [--enum=<lang>] [--lib=<lib>]%n"
                + "                    [--logfile=<file>] [--strNull=<str>] [--y=<y>] [--x=<x>[,%n"
                + "                    <x>...]]... [-P[=<key=ppp>...]]...%n"
                + "      --boolF            Boolean variable 1.%n"
                + "      --boolT            Boolean variable 2.%n"
                + "      --enum=<lang>      Enum.%n"
                + "                         VALUES: java, kotlin, groovy, javascript, frege, clojure.%n"
                + "      --lib=<lib>        comma-separated list of up to 3 paths to search for jars%n"
                + "                           and classes.%n"
                + "                         VALUES: This, is, a, very, long, list, of, completion,%n"
                + "                           candidates, that, is, intended, to, wrap, to, the, next,%n"
                + "                           line., I, wonder, if, it, is, long, enough..%n"
                + "      --logfile=<file>   Use given file for log.%n"
                + "      --strNull=<str>    String without default.%n"
                + "      --x=<x>[,<x>...]   Comma-separated list of some xxx's.%n"
                + "                         VALUES: A, B, C, D.%n"
                + "      --y=<y>            Test long default.%n"
                + "  -P=[<key=ppp>...]      Use value for project key.%n",
                new File("/a/b/c"));
        String actual = usageString(new CommandLine(new App(), new InnerClassFactory(this)),
                Ansi.OFF);
        assertEquals(expected, actual);
    }

    @Test
    public void testEnumInCollection() {
        class EnumTest {
            @Option(names = "-list", description = "Multiple languages.")
            List<Lang> langList;

            @Option(names = "-single", description = "Single language.")
            Lang lang;
        }
        String expected = String.format(""
                + "Usage: <main class> [-single=<lang>] [-list=<langList>]...%n"
                + "      -list=<langList>   Multiple languages.%n"
                + "                         VALUES: java, kotlin, groovy, javascript, frege, clojure.%n"
                + "      -single=<lang>     Single language.%n"
                + "                         VALUES: java, kotlin, groovy, javascript, frege, clojure.%n");
        assertEquals(expected, new CommandLine(new EnumTest()).getUsageMessage());
    }
}
