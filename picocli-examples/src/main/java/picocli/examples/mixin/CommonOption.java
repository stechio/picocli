package picocli.examples.mixin;

import picocli.annots.Command;
import picocli.annots.Option;

@Command(footer = "Common footer")
public class CommonOption {
    @Option(names = "-x", description = "reusable option you want in many commands")
    int x;
}
