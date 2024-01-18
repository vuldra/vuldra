package openai

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sarif.MinimizedRegion
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
    Step 1: Determine common source code bugs related to the programming language. Answer in less than 100 words.
    
    Step 2: Determine common vulnerabilities related to the programming language. Answer in less than 100 words.
    
    Example:
    
    Source code is likely Java.
    
    Step 1: Common source code bugs in Java are:
    Null Pointer Exceptions
    Array Index Out of Bounds
    Class Cast Exceptions
    Concurrent Modification Exception
    Memory Leaks
    Resource Leaks
    Infinite Loops
    Deadlocks
    Incorrect Equality Checks
    Off-by-One Errors
    Unchecked Warnings
    Improper Exception Handling
    Synchronization Issues
    Misuse of API
    Logic Errors
    Incorrect Equality Checks
    Misunderstanding of Scope
    Incorrect Lazy Initialization
    Hardcoding Values
    Misuse of Static Members
    Leaking Sensitive Information
    Ignoring Return Values
    Mismanagement of Database Resources
    Overcomplicated Expressions
    Type Conversion Errors
    
    Step 2: Common vulnerabilities in Java are:
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
    Broken Access Control
    Exposure of Sensitive Data
    Buffer Overflows
    Unvalidated Redirects and Forwards
    Session Fixation
    Failure to Restrict URL Access
    Use of Outdated or Vulnerable Libraries
    Improper Input Validation
    Inadequate Logging and Monitoring
    Misconfiguration of Security Headers
    Insecure File Uploads
    Use of Insecure Randomness
    Misuse of Security Features
    Server-Side Request Forgery (SSRF)
    Lack of Resource & Rate Limiting
""".trimIndent()

val exampleJsonOutput1 =
    Json.encodeToString(
        SourceCodeVulnerabilities(
            listOf(),
            "Neither the SAST tool nor I found any vulnerabilities.",
            listOf(),
            false
        )
    )
val exampleJsonOutput2 =
    Json.encodeToString(
        SourceCodeVulnerabilities(
            listOf(
                MinimizedRunResult(listOf(MinimizedRegion(5, 6)), "SQL Injection")
            ),
            "I am certain I found a vulnerability that the SAST tool missed.",
            listOf(
                MinimizedRunResult(listOf(MinimizedRegion(5, 6)), "SQL Injection")
            ),
            true
        )
    )
val exampleJsonOutput3 =
    Json.encodeToString(
        SourceCodeVulnerabilities(
            listOf(),
            "The SAST tool found vulnerabilities that I missed and that are convincing.",
            listOf(
                MinimizedRunResult(listOf(MinimizedRegion(8, 8)), "Buffer Overflows")
            ),
            true
        )
    )

val determineSourceCodeVulnerabilitiesPrompt = """
    You are a professional security analyst. You always answer in JSON format.
    
    Step 1:
    List all vulnerabilities in provided source code file based on the language, common bugs and common vulnerabilities.
    If there are no vulnerabilities, output an empty array. Answer in less than 100 words.
    
    Step 2:
    Compare your results to the SAST tool results and reason about any differences. Answer in less than 100 words.
    
    Step 3:
    Finalize discoveries concisely based on step 2. Discard any discoveries from the SAST tool or you, which are not convincing anymore.
    Each discovery should be described with less than 10 words.
    
    Step 4:
    Determine a true/false answer if the provided source code is vulnerable depending on the existence of finalized discoveries.
    
    Examples of JSON output you should produce:
    $exampleJsonOutput1
    $exampleJsonOutput2
    $exampleJsonOutput3
""".trimIndent()

