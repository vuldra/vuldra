package openai

import kotlinx.serialization.Serializable
import sarif.MinimizedRunResult

@Serializable
data class SourceCodeVulnerabilities(
    val gptDiscoveries: List<MinimizedRunResult>,
    val comparisonToSastResult: String,
    val finalizedDiscoveries: List<MinimizedRunResult>,
    val isVulnerable: Boolean,
)