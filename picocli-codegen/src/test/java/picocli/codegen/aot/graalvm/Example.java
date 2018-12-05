package picocli.codegen.aot.graalvm;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import picocli.CommandLine;
import picocli.annot.Command;
import picocli.annot.Mixin;
import picocli.annot.Option;
import picocli.annot.Parameters;
import picocli.annot.Spec;
import picocli.annot.Unmatched;
import picocli.except.ParameterException;
import picocli.help.HelpCommand;
import picocli.model.CommandSpec;

@Command(name = "example", version = "3.7.0",
        mixinStandardHelpOptions = true, subcommands = HelpCommand.class)
public class Example implements Runnable {

    @Command public static class ExampleMixin {

        @Option(names = "-l")
        int length;
    }

    @Option(names = "-t")
    TimeUnit timeUnit;

    @Parameters(index = "0")
    File file;

    @Spec
    CommandSpec spec;

    @Mixin
    ExampleMixin mixin;

    @Unmatched
    List<String> unmatched;

    private int minimum;
    private File[] otherFiles;

    @Command
    int multiply(@Option(names = "--count") int count,
                 @Parameters int multiplier) {
        System.out.println("Result is " + count * multiplier);
        return count * multiplier;
    }

    @Option(names = "--minimum")
    public void setMinimum(int min) {
        if (min < 0) {
            throw new ParameterException(spec.commandLine(), "Minimum must be a positive integer");
        }
        minimum = min;
    }

    @Parameters(index = "1..*")
    public void setOtherFiles(File[] otherFiles) {
        for (File f : otherFiles) {
            if (!f.exists()) {
                throw new ParameterException(spec.commandLine(), "File " + f.getAbsolutePath() + " must exist");
            }
        }
        this.otherFiles = otherFiles;
    }

    public void run() {
        System.out.printf("timeUnit=%s, length=%s, file=%s, unmatched=%s, minimum=%s, otherFiles=%s%n",
                timeUnit, mixin.length, file, unmatched, minimum, Arrays.toString(otherFiles));
    }

    public static void main(String[] args) {
        CommandLine.run(new Example(), args);
    }
}
