package picocli.help;

import picocli.annots.Option;

public class AutoHelpMixin {
    public static final String KEY = "mixinStandardHelpOptions";

    @Option(names = { "-h",
            "--help" }, usageHelp = true, descriptionKey = "mixinStandardHelpOptions.help", description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(names = { "-V",
            "--version" }, versionHelp = true, descriptionKey = "mixinStandardHelpOptions.version", description = "Print version information and exit.")
    private boolean versionRequested;
}