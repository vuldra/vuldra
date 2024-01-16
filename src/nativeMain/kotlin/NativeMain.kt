import cli.runVuldra
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    runBlocking {
        runVuldra(args)
    }
}
