package openai

import kotlinx.serialization.Serializable
import sarif.MinimizedRun

@Serializable
data class SourceCodeVulnerabilities(
    var gptVulnerabilities: List<MinimizedRun>? = null,
    var sastVulnerabilities: List<MinimizedRun>? = null,
    var reasoning: String? = null,
    var finalizedVulnerabilities: List<MinimizedRun>,
    var isVulnerable: Boolean,
) {
    constructor(gptVulnerabilities: GptVulnerabilities) : this(
        gptVulnerabilities.gptVulnerabilities,
        null,
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