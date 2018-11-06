package picocli.examples

import picocli.annots.CommandLine
import picocli.Option
import picocli.annots.Command

@Command(name = "MyApp", version = "Groovy picocli demo v3.0",
        mixinStandardHelpOptions = true,
        description = "@|bold Groovy|@ @|underline picocli|@ example")
class UserManualExample implements Runnable {

    @Option(names = ["-c", "--count"], paramLabel = "COUNT",
            description = "the count")
    int count = 0

    void run() {
        count.times {
            println("hello world $it...")
        }
    }

    static void main(String[] args) {
        CommandLine.run(new UserManualExample(), args)
    }
}
