package data

import kotlinx.serialization.Serializable
import okio.Path.Companion.toPath

@Serializable
data class Evaluation(
    var positives: Int,
    var negatives: Int,
    var truePositives: Int,
    var falsePositives: Int,
    var trueNegatives: Int,
    var falseNegatives: Int,
    var accuracy: Double,
    var precision: Double,
    var recall: Double,
    var f1: Double,
) {
    constructor(fileScanResults: List<FileScanResult>, vulnerableFilesRegex: String) : this(
        0,
        0,
        0,
        0,
        0,
        0,
        0.0,
        0.0,
        0.0,
        0.0
    ) {
        fileScanResults.forEach {
            val filename = it.filepath.toPath().name
            val isFileActuallyVulnerable = filename.contains(vulnerableFilesRegex.toRegex())
            if (isFileActuallyVulnerable) positives++ else negatives++
            when {
                isFileActuallyVulnerable && it.isVulnerable -> truePositives++
                isFileActuallyVulnerable && !it.isVulnerable -> falseNegatives++
                !isFileActuallyVulnerable && it.isVulnerable -> falsePositives++
                !isFileActuallyVulnerable && !it.isVulnerable -> trueNegatives++
            }
        }

        this.accuracy =
            (truePositives + trueNegatives).toDouble() / (truePositives + trueNegatives + falsePositives + falseNegatives).toDouble()
        this.precision = truePositives.toDouble() / (truePositives + falsePositives).toDouble()
        this.recall = truePositives.toDouble() / (truePositives + falseNegatives).toDouble()
        this.f1 = 2 * (precision * recall) / (precision + recall)
    }
}