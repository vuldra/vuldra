package openai

import data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val exampleSourceCodeContext1 = Json.encodeToString(
    SourceCodeContext(
        "Python",
        "A simple web server handling database queries",
    )
)

val gatherSourceCodeContextPrompt = """
    Step 1:
    Identify the main programming language of the source code.
    Step 2:
    In less than 20 words, summarise the purpose of the source code.
    
    Always respond in JSON format.
    
    Example of JSON output:
    $exampleSourceCodeContext1
""".trimIndent()

private val exampleReasoning1 = Json.encodeToString(
    ReasonedVulnerabilities(
        "The source code seems to be not vulnerable.",
        listOf(),
    )
)
private val exampleReasoning2 = Json.encodeToString(
    ReasonedVulnerabilities(
        "Buffer Overflow vulnerability found by Semgrep OSS is unconvincing.",
        listOf(),
    )
)
private val exampleReasoning3 = Json.encodeToString(
    ReasonedVulnerabilities(
        "The source code is vulnerable to an SQL Injection due to no parameterized queries.",
        listOf(
            MinimizedRun(
                "GPT",
                listOf(
                    MinimizedRunResult(
                        listOf(MinimizedRegion(5, 6)),
                        "SQL Injection due to lack of input validation"
                    )
                )
            )
        ),
    )
)
private val exampleReasoning4 = Json.encodeToString(
    ReasonedVulnerabilities(
        "Semgrep OSS found a convincing Buffer Overflow vulnerability due to external data control.",
        listOf(
            MinimizedRun(
                "Semgrep OSS",
                listOf(
                    MinimizedRunResult(
                        listOf(MinimizedRegion(8, 8)),
                        "Buffer Overflow due to external data control"
                    )
                )
            )
        ),
    )
)
val reasonVulnerabilitiesPrompt = """
    Step 1:
    Find any vulnerabilities in the provided source code. Reason if they are exploitable. Reason in less than 50 words.
    Step 2:
    Based on the reasoning, only list convincing vulnerabilities that could be exploited.
    The message describing each vulnerability should be less than 20 words.
    Respond with an empty results array, if no vulnerabilities are convincing.
    
    Always respond in JSON format.

    Examples of JSON output:
    $exampleReasoning1
    $exampleReasoning2
    $exampleReasoning3
    $exampleReasoning4
""".trimIndent()

