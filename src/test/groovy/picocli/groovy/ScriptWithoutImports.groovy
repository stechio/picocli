package picocli.groovy

import picocli.annots.Command
import picocli.annots.Option

@Command(name = 'cmd', description = 'my description')

@picocli.groovy.PicocliScript

@Option(names = ["-h", "--help"], usageHelp = true)
@groovy.transform.Field boolean usageHelpRequested = false

["hi"]