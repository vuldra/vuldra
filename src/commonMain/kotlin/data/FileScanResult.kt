package data

import kotlinx.serialization.Serializable

@Serializable
data class AggregatedScanResult(
    val statistics: Statistics,
    val fileScanResults: List<FileScanResult>,
    var evaluation: Evaluation? = null
)

@Serializable
data class FileScanResult(
    val filepath: String,
    var gptVulnerabilities: List<MinimizedRun>? = null,
    var sastVulnerabilities: List<MinimizedRun>? = null,
    var reasoning: String? = null,
    var finalizedVulnerabilities: List<MinimizedRun>,
    var isVulnerable: Boolean,
) {
    constructor(
        filepath: String,
        gptVulnerabilities: List<MinimizedRun>?,
        sastVulnerabilities: List<MinimizedRun>?,
        reasonedVulnerabilities: ReasonedVulnerabilities
    ) : this(
        filepath = filepath,
        gptVulnerabilities = gptVulnerabilities,
        sastVulnerabilities = sastVulnerabilities,
        reasoning = reasonedVulnerabilities.reasoning,
        finalizedVulnerabilities = reasonedVulnerabilities.vulnerabilities,
        isVulnerable = reasonedVulnerabilities.vulnerabilities.isNotEmpty(),
    )
}

@Serializable
data class ReasonedVulnerabilities(
    val reasoning: String,
    val vulnerabilities: List<MinimizedRun>,
)