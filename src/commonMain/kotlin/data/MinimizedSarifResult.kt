package data

import io.github.detekt.sarif4k.Region
import io.github.detekt.sarif4k.SarifSchema210
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
                        result.locations?.map { location ->
                            MinimizedLocation(location)
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
    var results: List<MinimizedRunResult>?,
)

@Serializable
data class MinimizedRunResult(
    var locations: List<MinimizedLocation>? = null,
    val message: String?,
    val ruleId: String? = null,
)

@Serializable
data class MinimizedLocation(
    var uri: String? = null,
    val region: MinimizedRegion? = null
) {
    constructor(location: io.github.detekt.sarif4k.Location) : this(
        uri = location.physicalLocation?.artifactLocation?.uri,
        region = location.physicalLocation?.region?.let { MinimizedRegion(it) }
    )
}

@Serializable
data class MinimizedRegion(
    val startLine: Long?,
    val endLine: Long?,
    var snippet: String? = null,
) {
    constructor(region: Region) : this(
        startLine = region.startLine,
        endLine = region.endLine,
        snippet = region.snippet?.text,
    )
}