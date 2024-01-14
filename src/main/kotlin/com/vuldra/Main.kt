package com.vuldra

import com.github.ajalt.clikt.core.CliktCommand

class Main : CliktCommand() {
    override fun run() {
        echo("Hello World!")
    }
}

fun main(args: Array<String>) = Main().main(args)