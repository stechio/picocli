package picocli;

import picocli.annots.Command;
import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.help.HelpCommand;

@Command(name = "i18n",
        resourceBundle = "picocli.I18nSuperclass_Messages",
        subcommands = HelpCommand.class,
        mixinStandardHelpOptions = true,
        description = {"super desc 1", "super desc 2", "super desc 3"},
        descriptionHeading = "super desc heading%n",
        header = {"super header 1", "super header 2", "super header 3"},
        headerHeading = "super header heading%n",
        footer = {"super footer 1", "super footer 2", "super footer 3"},
        footerHeading = "super footer heading%n",
        commandListHeading = "super command list heading%n",
        optionListHeading = "super option list heading%n",
        parameterListHeading = "super param list heading%n")
public class I18nSuperclass {
    @Option(names = {"-x", "--xxx"})
    String x;

    @Option(names = {"-y", "--yyy"}, description = {"super yyy description 1", "super yyy description 2"})
    String y;

    @Option(names = {"-z", "--zzz"}, description = "super zzz description")
    String z;

    @Parameters(index = "0")
    String param0;

    @Parameters(index = "1", description = "super param1 description")
    String param1;
}
