package picocli.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import picocli.CommandLine;
import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.excepts.MissingParameterException;
import picocli.excepts.UnmatchedArgumentException;


public class ArgSplitTest {

    @Test
    public void testSplitInOptionArray() {
        class Args {
            @Option(names = "-a", split = ",") String[] values;
        }
        Args args = CommandLine.populateCommand(new Args(), "-a=a,b,c");
        assertArrayEquals(new String[] {"a", "b", "c"}, args.values);

        args = CommandLine.populateCommand(new Args(), "-a=a,b,c", "-a=B", "-a", "C");
        assertArrayEquals(new String[] {"a", "b", "c", "B", "C"}, args.values);

        args = CommandLine.populateCommand(new Args(), "-a", "a,b,c", "-a", "B", "-a", "C");
        assertArrayEquals(new String[] {"a", "b", "c", "B", "C"}, args.values);

        args = CommandLine.populateCommand(new Args(), "-a=a,b,c", "-a", "B", "-a", "C", "-a", "D,E,F");
        assertArrayEquals(new String[] {"a", "b", "c", "B", "C", "D", "E", "F"}, args.values);

        try {
            CommandLine.populateCommand(new Args(), "-a=a,b,c", "B", "C");
            fail("Expected UnmatchedArgEx");
        } catch (UnmatchedArgumentException ok) {
            assertEquals("Unmatched arguments: B, C", ok.getMessage());
        }
        try {
            CommandLine.populateCommand(new Args(), "-a=a,b,c", "B", "-a=C");
            fail("Expected UnmatchedArgEx");
        } catch (UnmatchedArgumentException ok) {
            assertEquals("Unmatched argument: B", ok.getMessage());
        }
    }

    @Test
    public void testSplitInOptionArrayWithSpaces() {
        class Args {
            @Option(names = "-a", split = " ") String[] values;
        }
        Args args = CommandLine.populateCommand(new Args(), "-a=\"a b c\"");
        assertArrayEquals(new String[] {"\"a b c\""}, args.values);

        args = CommandLine.populateCommand(new Args(), "-a=a b c", "-a", "B", "-a", "C");
        assertArrayEquals(new String[] {"a", "b", "c", "B", "C"}, args.values);

        args = CommandLine.populateCommand(new Args(), "-a", "\"a b c\"", "-a=B", "-a=C");
        assertArrayEquals(new String[] {"\"a b c\"", "B", "C"}, args.values);

        args = CommandLine.populateCommand(new Args(), "-a=\"a b c\"", "-a=B", "-a", "C", "-a=D E F");
        assertArrayEquals(new String[] {"\"a b c\"", "B", "C", "D", "E", "F"}, args.values);

        try {
            CommandLine.populateCommand(new Args(), "-a=a b c", "B", "C");
            fail("Expected UnmatchedArgEx");
        } catch (UnmatchedArgumentException ok) {
            assertEquals("Unmatched arguments: B, C", ok.getMessage());
        }
        try {
            CommandLine.populateCommand(new Args(), "-a=a b c", "B", "-a=C");
            fail("Expected UnmatchedArgEx");
        } catch (UnmatchedArgumentException ok) {
            assertEquals("Unmatched argument: B", ok.getMessage());
        }
    }

    @Test
    public void testSplitInOptionArrayWithArity() {
        class Args {
            @Option(names = "-a", split = ",", arity = "0..4") String[] values;
            @Parameters() String[] params;
        }
        Args args = CommandLine.populateCommand(new Args(), "-a=a,b,c"); // 1 args
        assertArrayEquals(new String[] {"a", "b", "c"}, args.values);

        args = CommandLine.populateCommand(new Args(), "-a"); // 0 args
        assertArrayEquals(new String[0], args.values);
        assertNull(args.params);

        args = CommandLine.populateCommand(new Args(), "-a=a,b,c", "B", "C"); // 3 args
        assertArrayEquals(new String[] {"a", "b", "c", "B", "C"}, args.values);
        assertNull(args.params);

        args = CommandLine.populateCommand(new Args(), "-a", "a,b,c", "B", "C"); // 3 args
        assertArrayEquals(new String[] {"a", "b", "c", "B", "C"}, args.values);
        assertNull(args.params);

        args = CommandLine.populateCommand(new Args(), "-a=a,b,c", "B", "C", "D,E,F"); // 4 args
        assertArrayEquals(new String[] {"a", "b", "c", "B", "C", "D", "E", "F"}, args.values);
        assertNull(args.params);

        args = CommandLine.populateCommand(new Args(), "-a=a,b,c,d", "B", "C", "D", "E,F"); // 5 args
        assertArrayEquals(new String[] {"a", "b", "c", "d", "B", "C", "D"}, args.values);
        assertArrayEquals(new String[] {"E,F"}, args.params);
    }

    @Test
    public void testSplitInOptionCollection() {
        class Args {
            @Option(names = "-a", split = ",")
            List<String> values;
        }
        Args args = CommandLine.populateCommand(new Args(), "-a=a,b,c");
        assertEquals(Arrays.asList("a", "b", "c"), args.values);

        args = CommandLine.populateCommand(new Args(), "-a=a,b,c", "-a", "B", "-a=C");
        assertEquals(Arrays.asList("a", "b", "c", "B", "C"), args.values);

        args = CommandLine.populateCommand(new Args(), "-a", "a,b,c", "-a", "B", "-a", "C");
        assertEquals(Arrays.asList("a", "b", "c", "B", "C"), args.values);

        args = CommandLine.populateCommand(new Args(), "-a=a,b,c", "-a", "B", "-a", "C", "-a", "D,E,F");
        assertEquals(Arrays.asList("a", "b", "c", "B", "C", "D", "E", "F"), args.values);

        try {
            CommandLine.populateCommand(new Args(), "-a=a,b,c", "B", "C");
            fail("Expected UnmatchedArgumentException");
        } catch (UnmatchedArgumentException ok) {
            assertEquals("Unmatched arguments: B, C", ok.getMessage());
        }
    }

    @Test
    public void testSplitInParametersArray() {
        class Args {
            @Parameters(split = ",") String[] values;
        }
        Args args = CommandLine.populateCommand(new Args(), "a,b,c");
        assertArrayEquals(new String[] {"a", "b", "c"}, args.values);

        args = CommandLine.populateCommand(new Args(), "a,b,c", "B", "C");
        assertArrayEquals(new String[] {"a", "b", "c", "B", "C"}, args.values);

        args = CommandLine.populateCommand(new Args(), "a,b,c", "B", "C");
        assertArrayEquals(new String[] {"a", "b", "c", "B", "C"}, args.values);

        args = CommandLine.populateCommand(new Args(), "a,b,c", "B", "C", "D,E,F");
        assertArrayEquals(new String[] {"a", "b", "c", "B", "C", "D", "E", "F"}, args.values);
    }

    @Test
    public void testSplitInParametersArrayWithArity() {
        class Args {
            @Parameters(arity = "2..4", split = ",") String[] values;
        }
        Args args = CommandLine.populateCommand(new Args(), "a,b", "c,d"); // 2 args
        assertArrayEquals(new String[] {"a", "b", "c", "d"}, args.values);

        args = CommandLine.populateCommand(new Args(), "a,b", "c,d", "e,f"); // 3 args
        assertArrayEquals(new String[] {"a", "b", "c", "d", "e", "f"}, args.values);

        args = CommandLine.populateCommand(new Args(), "a,b,c", "B", "d", "e,f"); // 4 args
        assertArrayEquals(new String[] {"a", "b", "c", "B", "d", "e", "f"}, args.values);
        try {
            CommandLine.populateCommand(new Args(), "a,b,c,d,e"); // 1 arg: should fail
            fail("MissingParameterException expected");
        } catch (MissingParameterException ex) {
            assertEquals("positional parameter at index 0..* (<values>) requires at least 2 values, but only 1 were specified: [a,b,c,d,e]", ex.getMessage());
            assertEquals(1, ex.getMissing().size());
            assertTrue(ex.getMissing().get(0).toString(), ex.getMissing().get(0) instanceof PositionalParamSpec);
        }
        try {
            CommandLine.populateCommand(new Args()); // 0 arg: should fail
            fail("MissingParameterException expected");
        } catch (MissingParameterException ex) {
            assertEquals("positional parameter at index 0..* (<values>) requires at least 2 values, but none were specified.", ex.getMessage());
        }
        try {
            CommandLine.populateCommand(new Args(), "a,b,c", "B,C", "d", "e", "f,g"); // 5 args
            fail("MissingParameterException expected");
        } catch (MissingParameterException ex) {
            assertEquals("positional parameter at index 0..* (<values>) requires at least 2 values, but only 1 were specified: [f,g]", ex.getMessage());
        }
    }

    @Test
    public void testSplitInParametersCollection() {
        class Args {
            @Parameters(split = ",") List<String> values;
        }
        Args args = CommandLine.populateCommand(new Args(), "a,b,c");
        assertEquals(Arrays.asList("a", "b", "c"), args.values);

        args = CommandLine.populateCommand(new Args(), "a,b,c", "B", "C");
        assertEquals(Arrays.asList("a", "b", "c", "B", "C"), args.values);

        args = CommandLine.populateCommand(new Args(), "a,b,c", "B", "C");
        assertEquals(Arrays.asList("a", "b", "c", "B", "C"), args.values);

        args = CommandLine.populateCommand(new Args(), "a,b,c", "B", "C", "D,E,F");
        assertEquals(Arrays.asList("a", "b", "c", "B", "C", "D", "E", "F"), args.values);
    }

    @Test
    public void testSplitIgnoredInOptionSingleValueField() {
        class Args {
            @Option(names = "-a", split = ",") String value;
        }
        Args args = CommandLine.populateCommand(new Args(), "-a=a,b,c");
        assertEquals("a,b,c", args.value);
    }

    @Test
    public void testSplitIgnoredInParameterSingleValueField() {
        class Args {
            @Parameters(split = ",") String value;
        }
        Args args = CommandLine.populateCommand(new Args(), "a,b,c");
        assertEquals("a,b,c", args.value);
    }
    @Test
    public void testMapFieldWithSplitRegex() {
        class App {
            @Option(names = "-fix", split = "\\|", type = {Integer.class, String.class})
            Map<Integer,String> message;
            private void validate() {
                assertEquals(10, message.size());
                assertEquals(LinkedHashMap.class, message.getClass());
                assertEquals("FIX.4.4", message.get(8));
                assertEquals("69", message.get(9));
                assertEquals("A", message.get(35));
                assertEquals("MBT", message.get(49));
                assertEquals("TargetCompID", message.get(56));
                assertEquals("9", message.get(34));
                assertEquals("20130625-04:05:32.682", message.get(52));
                assertEquals("0", message.get(98));
                assertEquals("30", message.get(108));
                assertEquals("052", message.get(10));
            }
        }
        CommandLine.populateCommand(new App(), "-fix", "8=FIX.4.4|9=69|35=A|49=MBT|56=TargetCompID|34=9|52=20130625-04:05:32.682|98=0|108=30|10=052").validate();
    }
    @Test
    public void testMapFieldArityWithSplitRegex() {
        class App {
            @Option(names = "-fix", arity = "2", split = "\\|", type = {Integer.class, String.class})
            Map<Integer,String> message;
            private void validate() {
                assertEquals(message.toString(), 4, message.size());
                assertEquals(LinkedHashMap.class, message.getClass());
                assertEquals("a", message.get(1));
                assertEquals("b", message.get(2));
                assertEquals("c", message.get(3));
                assertEquals("d", message.get(4));
            }
        }
        CommandLine.populateCommand(new App(), "-fix", "1=a", "2=b|3=c|4=d").validate(); // 2 args
        //Arity should not limit the total number of values put in an array or collection #191
        CommandLine.populateCommand(new App(), "-fix", "1=a", "2=b", "-fix", "3=c", "4=d").validate(); // 2 args

        try {
            CommandLine.populateCommand(new App(), "-fix", "1=a|2=b|3=c|4=d"); // 1 arg
            fail("MissingParameterException expected");
        } catch (MissingParameterException ex) {
            assertEquals("option '-fix' at index 0 (<Integer=String>) requires at least 2 values, but only 1 were specified: [1=a|2=b|3=c|4=d]", ex.getMessage());
        }
        try {
            CommandLine.populateCommand(new App(), "-fix", "1=a", "2=b", "3=c|4=d"); // 3 args
            fail("UnmatchedArgumentException expected");
        } catch (UnmatchedArgumentException ex) {
            assertEquals("Unmatched argument: 3=c|4=d", ex.getMessage());
        }
    }

    @Test
    public void testArgSpecSplitValue_SplitsQuotedValuesIfConfigured() {
        ParserSpec parser = new ParserSpec().splitQuotedStrings(true);
        ArgSpec spec = PositionalParamSpec.builder().splitRegex(",").build();
        String[] actual = spec.splitValue("a,b,\"c,d,e\",f", parser, Range.valueOf("0"), 0);
        assertArrayEquals(new String[]{"a", "b", "\"c", "d" , "e\"", "f"}, actual);
    }

    @Test
    public void testArgSpecSplitValue_RespectsQuotedValuesByDefault() {
        ParserSpec parser = new ParserSpec();
        ArgSpec spec = PositionalParamSpec.builder().splitRegex(",").build();
        String[] actual = spec.splitValue("a,b,\"c,d,e\",f", parser, Range.valueOf("0"), 0);
        assertArrayEquals(new String[]{"a", "b", "\"c,d,e\"", "f"}, actual);
    }

    @Test
    public void testArgSpecSplitValue_MultipleQuotedValues() {
        ParserSpec parser = new ParserSpec();
        ArgSpec spec = PositionalParamSpec.builder().splitRegex(",").build();
        String[] actual = spec.splitValue("a,b,\"c,d,e\",f,\"xxx,yyy\"", parser, Range.valueOf("0"), 0);
        assertArrayEquals(new String[]{"a", "b", "\"c,d,e\"", "f", "\"xxx,yyy\""}, actual);
    }

    @Test
    public void testArgSpecSplitValue_MultipleQuotedValues_QuotesTrimmedIfRequested() {
        ParserSpec parser = new ParserSpec().trimQuotes(true);
        ArgSpec spec = PositionalParamSpec.builder().splitRegex(",").build();
        String[] actual = spec.splitValue("a,b,\"c,d,e\",f,\"xxx,yyy\"", parser, Range.valueOf("0"), 0);
        assertArrayEquals(new String[]{"a", "b", "c,d,e", "f", "xxx,yyy"}, actual);
    }

    @Test
    public void testParseQuotedArgumentWithNestedQuotes() {
        class Example {
            @Option(names = "-x", split = ",")
            String[] parts;
        }
        String[] args = {"-x", "a,b,\"c,d,e\",f,\"xxx,yyy\""};
        Example example = new Example();
        new CommandLine(example).parseArgs(args);
        assertArrayEquals(new String[]{"a", "b", "\"c,d,e\"", "f", "\"xxx,yyy\"", }, example.parts);
    }

    @Test
    public void testParseQuotedArgumentWithNestedQuotes2() {
        class Example {
            @Option(names = "-x", split = ",")
            String[] parts;
        }
        String[] args = {"-x", "\"-Dvalues=a,b,c\",\"-Dother=1,2\""};
        Example example = new Example();
        new CommandLine(example).parseArgs(args);
        assertArrayEquals(new String[]{"\"-Dvalues=a,b,c\"", "\"-Dother=1,2\""}, example.parts);
    }


    // test https://github.com/remkop/picocli/issues/379
    @Test
    public void test379() {
        String[] args = {
                "-p",
                "AppOptions=\"-Dspring.profiles.active=foo,bar -Dspring.mail.host=smtp.mailtrap.io\",OtherOptions=\"\""
        };
        class App {
            @Option(names = {"-p", "--parameter"}, split = ",")
            Map<String, String> parameters;
        }
        App app = CommandLine.populateCommand(new App(), args);
        assertEquals(2, app.parameters.size());
        assertEquals("\"-Dspring.profiles.active=foo,bar -Dspring.mail.host=smtp.mailtrap.io\"", app.parameters.get("AppOptions"));
        assertEquals("\"\"", app.parameters.get("OtherOptions"));
    }
    @Test
    public void test379WithTrimQuotes() {
        String[] args = {
                "-p",
                "AppOptions=\"-Dspring.profiles.active=foo,bar -Dspring.mail.host=smtp.mailtrap.io\",OtherOptions=\"\""
        };
        class App {
            @Option(names = {"-p", "--parameter"}, split = ",")
            Map<String, String> parameters;
        }
        App app = new App();
        new CommandLine(app).setTrimQuotes(true).parse(args);
        assertEquals(2, app.parameters.size());
        assertEquals("-Dspring.profiles.active=foo,bar -Dspring.mail.host=smtp.mailtrap.io", app.parameters.get("AppOptions"));
        assertEquals("", app.parameters.get("OtherOptions"));
    }
}
