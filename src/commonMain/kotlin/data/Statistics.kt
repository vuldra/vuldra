package data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Statistics (
    val targetFiles: Int,
    val scannedFiles: Int,
    val vulnerableFiles: Int,
    val scanStartTime: LocalDateTime,
    val scanEndTime: LocalDateTime,
    val scanDurationSeconds: Long,
) {
    constructor(
        targetFiles: List<String>,
        fileScanResult: List<FileScanResult>,
        scanStartTime: Instant,
        scanEndTime: Instant,
    ) : this(
        targetFiles.size,
        fileScanResult.size,
        fileScanResult.filter { it.isVulnerable }.size,
        scanStartTime.toLocalDateTime(TimeZone.currentSystemDefault()),
        scanEndTime.toLocalDateTime(TimeZone.currentSystemDefault()),
        scanEndTime.epochSeconds - scanStartTime.epochSeconds
    )
}
