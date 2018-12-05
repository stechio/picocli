package picocli.examples.mixin;

import picocli.CommandLine;
import picocli.annot.Command;
import picocli.annot.Mixin;
import picocli.annot.Option;

@Command(name = "mixee", description = "This command has a footer and an option mixed in")
public class CommandWithMixin {
    @Mixin
    CommonOption commonOption = new CommonOption();

    @Option(names = "-y", description = "command option")
    int y;

    public static void main(String[] args) {
        CommandWithMixin cmd = new CommandWithMixin();
        new CommandLine(cmd).parseArgs("-x", "3", "-y", "4");

        System.out.printf("x=%s, y=%s%n", cmd.commonOption.x, cmd.y);
    }
}
