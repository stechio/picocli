package picocli.help;

import picocli.CommandLine;
import picocli.CommandLine.Model;
import picocli.CommandLine.Model.CommandSpec;

public class HelpFactory implements IHelpFactory {
    public Help createHelp(Model.CommandSpec commandSpec, ColorScheme colorScheme) {
        return new Help(commandSpec, colorScheme);
    }
}