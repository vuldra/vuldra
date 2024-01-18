package sarif

import io.github.detekt.sarif4k.Region
import io.github.detekt.sarif4k.SarifSchema210
import kotlinx.serialization.Serializable

@Serializable
data class MinimizedSarifResult(
    val runs: MutableList<MinimizedRun>,
) {
    constructor(sarifSchema210: SarifSchema210) : this(
        runs = mutableListOf()
    ) {
        sarifSchema210.runs.forEach {
            runs.add(MinimizedRun(
                it.tool.driver.name,
                it.results?.map { result ->
                    MinimizedRunResult(
                        result.locations?.mapNotNull { location ->
                            location.physicalLocation?.region?.let { region -> MinimizedRegion(region) }
                        },
                        result.message.text,
                        result.ruleID,
                    )
                }
            ))

        }
    }
}

@Serializable
data class MinimizedRun(
    val tool: String,
    val results: List<MinimizedRunResult>?,
)

@Serializable
data class MinimizedRunResult(
    val regions: List<MinimizedRegion>?,
    val message: String?,
    val ruleId: String? = null,
)


@Serializable
data class MinimizedRegion(
    val startLine: Long?,
    val endLine: Long?,
    val snippet: String? = null,
) {
    constructor(region: Region) : this(
        startLine = region.startLine,
        endLine = region.endLine,
        snippet = region.snippet?.text,
    )
}