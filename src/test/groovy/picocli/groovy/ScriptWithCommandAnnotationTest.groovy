package picocli.groovy

@Command(name = 'package-test-name22', separator = ';', version = 'package-test-version')
@PicocliScript
import picocli.groovy.PicocliScript

import org.junit.Test
import picocli.CommandLine
import picocli.annots.Command

/**
 * Tests putting @Command annotations on the package declaration of a class.
 *
 * @author Remko Popma
 * @since 2.0
 */
@Test
public void testPackageAnnotation() {
    CommandLine commandLine = new CommandLine(this)
    assert "package-test-name22" == commandLine.commandName
}
