package picocli.help;

import picocli.help.Help.RendererFactory;
import picocli.help.Help.Rendering;
import picocli.model.CommandSpec;

public class HelpFactory implements IHelpFactory {
    public Help createHelp(CommandSpec commandSpec, ColorScheme colorScheme) {
        return new Help(commandSpec, colorScheme);
    }

    @Override
    public Rendering createRendering(Help help) {
        return new Rendering(new RendererFactory(help));
    }
}