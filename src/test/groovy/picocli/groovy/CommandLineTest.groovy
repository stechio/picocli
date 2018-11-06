package picocli.groovy

import org.junit.Test
import picocli.CommandLine
import picocli.annots.Option
import picocli.annots.Parameters

import static org.junit.Assert.assertEquals

class CommandLineTest {
    private class Params {
        @Parameters String[] positional
        @Option(names = "-o") option
    }

    @Test
    public void testTracingLevelIsInfoIfNoValueSpecified() throws Exception {
        PrintStream originalErr = System.err
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2500)
        System.setErr(new PrintStream(baos))
        final String PROPERTY = "picocli.trace"
        String old = System.getProperty(PROPERTY)
        System.setProperty(PROPERTY, "true") // Groovy puts value 'true' if -D specified without value on command line
        CommandLine commandLine = new CommandLine(new Params())
        commandLine.parse("-o", "anOption", "A", "B")
        System.setErr(originalErr)
        if (old == null) {
            System.clearProperty(PROPERTY)
        } else {
            System.setProperty(PROPERTY, old)
        }
        String expected = String.format("" +
                "[picocli INFO] Parsing 4 command line args [-o, anOption, A, B]%n" +
                "[picocli INFO] Setting field Object picocli.groovy.CommandLineTest\$Params.option to 'anOption' (was 'null') for option -o%n" +
                "[picocli INFO] Adding [A] to field String[] picocli.groovy.CommandLineTest\$Params.positional for args[0..*] at position 0%n" +
                "[picocli INFO] Adding [B] to field String[] picocli.groovy.CommandLineTest\$Params.positional for args[0..*] at position 1%n")
        String actual = new String(baos.toByteArray(), "UTF8")
        // println actual
        assertEquals(expected, actual)
    }

}
