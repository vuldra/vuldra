package cli

import cli.CliConfig.VULDRA_COMMAND
import cli.CliConfig.FIND
import cli.CliConfig.SEMGREP
import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal

/**
 * CliKt provides Kotlin Multiplatform command line interface parsing for Kotlin
 * https://ajalt.github.io/clikt/
 */
class CliCommand : CliktCommand(
    help = """
       Scan source code for vulnerabilities
    """.trimIndent(),
    epilog = """
        By default all files of the current directory are scanned recursively, unless arguments are provided to specify targets.
        
        Examples:
            $VULDRA_COMMAND vulnerable1.java
            $VULDRA_COMMAND vulnerable1.java vulnerable2.java
            $VULDRA_COMMAND src/main/java
    """.trimIndent(),
    name = VULDRA_COMMAND
) {
    init {
        completionOption()
    }
    val help: Boolean by option("-h", "--help", help = "Display this help screen").flag()
    val verbose by option("-v", "--verbose", help = "verbose").flag(defaultForHelp = "disabled")
    val colors by option("--colors", help = "print using colors").flag()
    val depth: Int? by option("--depth", "-d", help = "Specify the depth of recursive directory search").int()
    val pattern: String? by option("--pattern", "-p", help = "Specify a shell pattern to match filenames")
    val targets: List<String> by argument().multiple()

    override fun run() {
        if (verbose) println(this)
        if (colors) {
            val t = Terminal()
            t.println("${red("red")} ${white("white")} and ${blue("blue")}")
        }
    }

    fun semgrepScanCommand(targets: List<String>): List<String> {
        val args = mutableListOf<String>()
        args += listOf(SEMGREP, "scan", "--quiet", "--json")
        args += targets
        return args
    }

    fun findCommand(target: String = "."): List<String> {
        val args = mutableListOf<String>()

        args += listOf(FIND, target, "-type", "f")
        args += listOf("-mindepth", "0")
        if (depth != null) {
            args += "-maxdepth"
            args += (depth!! + 1).toString()
        }
        if (pattern != null) {
            args += "-name"
            args += pattern!!
        }
        return args.also { if (verbose) println("$ $it") }
    }
}

