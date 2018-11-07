package picocli.help;

import picocli.help.Help.Rendering;
import picocli.model.CommandSpec;

public interface IHelpFactory {
    public Help createHelp(CommandSpec commandSpec, ColorScheme colorScheme);

    public Rendering createRendering(Help help);
}