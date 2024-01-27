package data

import kotlinx.serialization.Serializable

@Serializable
data class AggregatedScanResult(
    val fileScanResults: List<FileScanResult>,
    val statistics: Statistics,
    var evaluation: Evaluation? = null
)

@Serializable
data class FileScanResult(
    val filepath: String,
    val sourceCodeContext: SourceCodeContext? = null,
    var runs: List<MinimizedRun>? = null,
    var reasoning: String? = null,
    var vulnerabilities: List<MinimizedRun>,
    var isVulnerable: Boolean,
) {
    constructor(
        filepath: String,
        sourceCodeContext: SourceCodeContext?,
        runs: List<MinimizedRun>,
        reasonedVulnerabilities: ReasonedVulnerabilities
    ) : this(
        filepath = filepath,
        sourceCodeContext = sourceCodeContext,
        runs = runs,
        reasoning = reasonedVulnerabilities.reasoning,
        vulnerabilities = reasonedVulnerabilities.vulnerabilities,
        isVulnerable = reasonedVulnerabilities.vulnerabilities.isNotEmpty(),
    )
}

@Serializable
data class SourceCodeContext(
    val language: String,
    val purpose: String,
)

@Serializable
data class ReasonedVulnerabilities(
    val reasoning: String,
    val vulnerabilities: List<MinimizedRun>,
)