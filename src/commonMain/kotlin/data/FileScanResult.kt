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
        gptVulnerabilities: GptVulnerabilities,
        sastVulnerabilities: List<MinimizedRun>?
    ) : this(
        filepath,
        gptVulnerabilities.gptVulnerabilities,
        sastVulnerabilities,
        gptVulnerabilities.reasoning,
        gptVulnerabilities.finalizedVulnerabilities,
        gptVulnerabilities.finalizedVulnerabilities.isNotEmpty(),
    )
}

@Serializable
data class GptVulnerabilities(
    val gptVulnerabilities: List<MinimizedRun>,
    val reasoning: String,
    val finalizedVulnerabilities: List<MinimizedRun>,
)