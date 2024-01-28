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
    var runs: List<MinimizedRun>? = null,
    val language: String? = null,
    val purpose: String? = null,
    val reasoning: String? = null,
    var vulnerabilities: List<MinimizedRun>,
    var isVulnerable: Boolean,
) {
    constructor(
        filepath: String,
        runs: List<MinimizedRun>,
        reasonedVulnerabilities: ReasonedVulnerabilities
    ) : this(
        filepath = filepath,
        runs = runs,
        language = reasonedVulnerabilities.language,
        purpose = reasonedVulnerabilities.purpose,
        reasoning = reasonedVulnerabilities.reasoning,
        vulnerabilities = reasonedVulnerabilities.vulnerabilities,
        isVulnerable = reasonedVulnerabilities.vulnerabilities.isNotEmpty(),
    )
}

@Serializable
data class ReasonedVulnerabilities(
    val language: String,
    val purpose: String,
    val reasoning: String,
    val vulnerabilities: List<MinimizedRun>,
)