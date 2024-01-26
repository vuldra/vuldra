package openai

import cli.MAX_CODE_LINES_PER_REGION
import cli.MAX_CODE_REGIONS_PER_VULNERABILITY
import data.MinimizedRegion
import data.MinimizedRun
import data.MinimizedRunResult
import data.ReasonedVulnerabilities
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val exampleVulnerabilities1 = MinimizedRun(
    "GPT",
    listOf(MinimizedRunResult(listOf(MinimizedRegion(5, 5)), "SQL Injection due to lack of input validation"))
)
private val exampleVulnerabilities2 = MinimizedRun(
    "GPT",
    listOf(MinimizedRunResult(listOf(MinimizedRegion(3, 5), MinimizedRegion(12, 19)), "Directory Traversal due to lack of input validation"))
)
private val exampleVulnerabilities3 = MinimizedRun(
    "GPT",
    listOf()
)

val findVulnerabilitiesPrompt = """
    Find any vulnerabilities in the source code provided.
    Describe each vulnerability found in less than 20 words.
    Include relevant source code line regions for vulnerabilities found.
    The regions should include all the context needed to understand the vulnerability.
    Include maximum $MAX_CODE_REGIONS_PER_VULNERABILITY regions per vulnerability.
    Each region should have maximum $MAX_CODE_LINES_PER_REGION lines.
    If the code is not vulnerable, return an empty results array.
    Always respond in JSON format.

    Examples of JSON output you should produce:
    $exampleVulnerabilities1
    $exampleVulnerabilities2
    $exampleVulnerabilities3
""".trimIndent()


private val exampleReasoning1 = Json.encodeToString(
    ReasonedVulnerabilities(
        "SAST tools and GPT found no vulnerabilities.",
        listOf(),
    )
)
private val exampleReasoning2 = Json.encodeToString(
    ReasonedVulnerabilities(
        "SAST tools found no vulnerabilities. Buffer Overflow vulnerability found by GPT is unconvincing.",
        listOf(),
    )
)
private val exampleReasoning3 = Json.encodeToString(
    ReasonedVulnerabilities(
        "GPT found a convincing SQL Injection vulnerability that SAST tools missed.",
        listOf(
            MinimizedRun(
                "GPT",
                listOf(MinimizedRunResult(listOf(MinimizedRegion(5, 6)), "SQL Injection due to lack of input validation"))
            )
        ),
    )
)
private val exampleReasoning4 = Json.encodeToString(
    ReasonedVulnerabilities(
        "SAST tool Semgrep OSS found a convincing Buffer Overflow vulnerability that GPT missed. SQL Injection vulnerability found by SAST tool Snyk is unconvincing.",
        listOf(
            MinimizedRun(
                "Semgrep OSS",
                listOf(MinimizedRunResult(listOf(MinimizedRegion(8, 8)), "Buffer Overflow due to external data control"))
            )
        ),
    )
)
val reasonVulnerabilitiesPrompt = """
    Reason about vulnerabilities which were previously found by you (GPT) and given vulnerabilities found by SAST tools.
    Reason in less than 100 words.
    After reasoning, only list vulnerabilities that are convincing.
    Only include line numbers but no code snippets in the response.
    The message describing each vulnerability should be less than 20 words.
    Respond with an empty results array, if no vulnerabilities are convincing.
    Always respond in JSON format.

    Examples of JSON output you should produce:
    $exampleReasoning1
    $exampleReasoning2
    $exampleReasoning3
    $exampleReasoning4
""".trimIndent()

