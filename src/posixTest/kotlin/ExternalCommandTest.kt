import io.ExecuteCommandOptions
import io.executeExternalCommandAndCaptureOutput
import io.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock.System.now
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ExternalCommandTest {
    @Test
    fun sleepAsync() {
        val startTime = now()
        runBlocking(Dispatchers.Default) {
            coroutineScope {
                (1..2).map {
                    async {
                        println("${now()} Started task $it")
                        executeExternalCommandAndCaptureOutput(
                            listOf("sleep", "1"),
                            ExecuteCommandOptions(".", false, false, true)
                        )
                        println("${now()} Finished task $it")
                    }
                }.awaitAll()
            }
        }
        val endTime = now()
        assertTrue { endTime - startTime < 2.seconds }
    }
}
