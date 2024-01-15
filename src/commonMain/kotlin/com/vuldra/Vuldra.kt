package com.vuldra

import com.github.ajalt.clikt.core.CliktCommand

class Vuldra : CliktCommand() {
    override fun run() {
        echo("Hello World!")
    }
}

fun main(args: Array<String>) = Vuldra().main(args)