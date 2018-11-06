package picocli.help;

import picocli.model.CommandSpec;

public interface IHelpFactory {
    public Help createHelp(CommandSpec commandSpec, ColorScheme colorScheme);
}