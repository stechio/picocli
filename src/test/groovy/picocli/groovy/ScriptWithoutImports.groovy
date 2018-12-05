package picocli.groovy

import picocli.annot.Command
import picocli.annot.Option

@Command(name = 'cmd', description = 'my description')

@picocli.groovy.PicocliScript

@Option(names = ["-h", "--help"], usageHelp = true)
@groovy.transform.Field boolean usageHelpRequested = false

["hi"]