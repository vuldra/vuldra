package openai

import data.*
import kotlinx.serialization.encodeToString
import unstrictJson

private val exampleReasoning1 = unstrictJson.encodeToString(
    ReasonedVulnerabilities(
        "Python",
        "A simple web server handling database queries",
        "No vulnerabilities found.",
        listOf(),
    )
)
private val exampleReasoning2 = unstrictJson.encodeToString(
    ReasonedVulnerabilities(
        "C",
        "A service handling file uploads",
        "Buffer Overflow vulnerability found by Semgrep OSS is unconvincing.",
        listOf(),
    )
)
private val exampleReasoning3 = unstrictJson.encodeToString(
    ReasonedVulnerabilities(
        "Java",
        "A simple web server handling database queries",
        "The source code is vulnerable to an SQL Injection due to no parameterized queries.",
        listOf(
            MinimizedRun(
                "GPT",
                listOf(
                    MinimizedRunResult(
                        locations = listOf(MinimizedLocation(region = MinimizedRegion(5, 6))),
                        message = "SQL Injection"
                    )
                )
            )
        ),
    )
)
private val exampleReasoning4 = unstrictJson.encodeToString(
    ReasonedVulnerabilities(
        "C",
        "A service handling file uploads",
        "Semgrep OSS found a convincing Buffer Overflow vulnerability due to external data control.",
        listOf(
            MinimizedRun(
                "Semgrep OSS",
                listOf(
                    MinimizedRunResult(
                        locations = listOf(MinimizedLocation(region = MinimizedRegion(8, 8))),
                        message = "Buffer Overflow"
                    )
                )
            )
        ),
    )
)
val reasonVulnerabilitiesPrompt = """
    Step 1:
    Identify the main programming language of the source code.
    Step 2:
    In less than 20 words, summarise the purpose of the source code.
    Step 3:
    Find any vulnerabilities in the provided source code. Reason if they are exploitable. Reason in less than 50 words.
    Step 4:
    Based on the reasoning, list at most 2 convincing vulnerabilities that could be exploited.
    The message describing each vulnerability should be less than 20 words.
    Respond with an empty results array, if no vulnerabilities are convincing.
    
    Always respond in JSON format.

    Examples of JSON output:
    $exampleReasoning1
    $exampleReasoning2
    $exampleReasoning3
    $exampleReasoning4
""".trimIndent()

