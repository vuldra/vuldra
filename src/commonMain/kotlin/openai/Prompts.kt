package openai

import data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val exampleSourceCodeContext1 = Json.encodeToString(
    SourceCodeContext(
        "Python",
        "A simple web server",
        listOf("SQL Injection", "Directory Traversal")
    )
)

val gatherSourceCodeContextPrompt = """
    Step 1:
    Identify the main programming language of the source code.
    Step 2:
    In less than 20 words, summarise the purpose of the source code.
    Step 3:
    In less than 30 words, list common vulnerabilities that are related to this purpose or to the constructs used in the source code.
    
    Always respond in JSON format.
    
    Example of JSON output:
    $exampleSourceCodeContext1
""".trimIndent()

private val exampleReasoning1 = Json.encodeToString(
    ReasonedVulnerabilities(
        "Other tools and GPT found no vulnerabilities.",
        listOf(),
    )
)
private val exampleReasoning2 = Json.encodeToString(
    ReasonedVulnerabilities(
        "Other tools found no vulnerabilities. Buffer Overflow vulnerability found by GPT is unconvincing.",
        listOf(),
    )
)
private val exampleReasoning3 = Json.encodeToString(
    ReasonedVulnerabilities(
        "GPT found a convincing SQL Injection vulnerability that other tools missed.",
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
        "Semgrep OSS found a convincing Buffer Overflow vulnerability that GPT missed. SQL Injection vulnerability found by Snyk is unconvincing.",
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
    Find vulnerabilities in the source code provided.
    Step 2:
    Reason about vulnerabilities which were found by any other tools. Mention which vulnerabilities are unconvincing, as they for example are not likely exploitable.
    Reason which vulnerabilities you found that were missed by other tools. Reason in less than 50 words.
    Step 3:
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

