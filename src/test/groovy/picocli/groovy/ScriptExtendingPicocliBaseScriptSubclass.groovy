package picocli.groovy

import groovy.transform.Field
import picocli.annot.Command
import picocli.annot.Option

@Command(name = 'sub', description = 'my description')
@PicocliScript PicocliBaseScriptSubclass me

@Option(names = ["-h", "--help"], usageHelp = true)
@Field boolean usageHelpRequested = false

println getClass().getName()
println me.getClass().getName()
println getClass().getSuperclass().getName()
println getClass().getSuperclass().getSuperclass().getName()

hi()