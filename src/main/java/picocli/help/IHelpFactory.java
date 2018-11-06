package picocli.help;

import picocli.CommandLine;
import picocli.CommandLine.Model;
import picocli.CommandLine.Model.CommandSpec;

public interface IHelpFactory {
    public Help createHelp(Model.CommandSpec commandSpec, ColorScheme colorScheme);
}