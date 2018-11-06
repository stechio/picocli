package picocli.help;

import picocli.model.CommandSpec;

public class HelpFactory implements IHelpFactory {
    public Help createHelp(CommandSpec commandSpec, ColorScheme colorScheme) {
        return new Help(commandSpec, colorScheme);
    }
}