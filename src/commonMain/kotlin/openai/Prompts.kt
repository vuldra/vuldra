package openai

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sarif.MinimizedRegion
import sarif.MinimizedRun
import sarif.MinimizedRunResult

val determineSourceCodeLanguagePrompt = """
    Determine most likely programming language for provided filename and source code. Answer in less than 10 words.
    
    Examples:
    
    Main.java
    
    int n = 10;
    int[] array = new int[n];
    
    Source code is likely Java.
    
    
    script.py
    
    def hello():
        print "Hello"
    
    Source code is likely Python.
""".trimIndent()

val determineCommonVulnerabilitiesPrompt = """
    Determine 10 common vulnerabilities related to the given programming language. Answer in less than 50 words.
    
    Example:
    
    Source code is likely Java.
    
    Some common vulnerabilities in Java are:
    SQL Injection
    Cross-Site Scripting (XSS)
    Cross-Site Request Forgery (CSRF)
    Insecure Deserialization
    Directory Traversal
    XML External Entity (XXE) Injection
    Insecure Cryptography
    Hard-Coded Credentials
    Insecure Default Configurations
    Improper Error Handling
""".trimIndent()

val exampleJsonOutput1 = Json.encodeToString(
    GptVulnerabilities(
        listOf(),
        "Neither the SAST tools nor I found any vulnerabilities.",
        listOf(),
    )
)
val exampleJsonOutput2 = Json.encodeToString(
    GptVulnerabilities(
        listOf(
            MinimizedRun(
                "GPT",
                listOf(MinimizedRunResult(listOf(MinimizedRegion(5, 6)), "SQL Injection"))
            )
        ),
        "I am certain I found a convincing vulnerability that the SAST tools missed.",
        listOf(
            MinimizedRun(
                "GPT",
                listOf(MinimizedRunResult(listOf(MinimizedRegion(5, 6)), "SQL Injection"))
            )
        ),
    )
)
val exampleJsonOutput3 = Json.encodeToString(
    GptVulnerabilities(
        listOf(),
        "The SAST tool Semgrep OSS found a vulnerability that I missed, that is convincing.",
        listOf(
            MinimizedRun(
                "Semgrep OSS",
                listOf(MinimizedRunResult(listOf(MinimizedRegion(8, 8)), "Buffer Overflows"))
            )
        ),
    )
)
val exampleJsonOutput4 = Json.encodeToString(
    GptVulnerabilities(
        listOf(),
        "The SAST tool Semgrep OSS found a vulnerability that I find unconvincing.",
        listOf(),
    )
)

val determineSourceCodeVulnerabilitiesPrompt = """
    You are a professional security analyst. You always answer in JSON format.
    
    Step 1:
    List all vulnerabilities in provided source code file. Look both for common and uncommon vulnerabilities.
    If there are no vulnerabilities, output an empty array. Answer in less than 100 words.
    
    Step 2:
    Compare your results to the given SAST tool results and reason about any differences. Answer in less than 100 words.
    
    Step 3:
    Finalize discoveries concisely based on step 2. Include any vulnerabilities that are convincing. Discard any vulnerabilities from SAST tools or you, which are not convincing anymore.
    Each discovery should be described with less than 30 words.

    Examples of JSON output you should produce:
    $exampleJsonOutput1
    $exampleJsonOutput2
    $exampleJsonOutput3
    $exampleJsonOutput4
""".trimIndent()

