package data

import com.github.ajalt.mordant.table.table
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
    var accuracy: Double?,
    var precision: Double?,
    var recall: Double?,
    var f1: Double?,
) {
    constructor(fileScanResults: List<FileScanResult>, vulnerableFilesRegex: String) : this(
        0,
        0,
        0,
        0,
        0,
        0,
        null,
        null,
        null,
        null
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
        val numberOfSamples = positives + negatives
        if (numberOfSamples == 0) {
            error("A sample size of 0 cannot be evaluated.")
        }
        this.accuracy =
            (truePositives + trueNegatives).toDouble() / numberOfSamples.toDouble()
        if (truePositives + falsePositives != 0) {
            this.precision = truePositives.toDouble() / (truePositives + falsePositives).toDouble()
        }
        if (positives != 0) {
            this.recall = truePositives.toDouble() / (positives).toDouble()
        }
        precision?.let { precision ->
            recall?.let { recall ->
                if (precision + recall != 0.0) {
                    this.f1 = 2 * (precision * recall) / (precision + recall)
                }
            }
        }
    }

    fun generateTerminalTable() = table {
        header {
            style(bold = true)
            row("Evaluation Metric", "Value")
        }
        body {
            row {
                cell("Positives")
                cell(this@Evaluation.positives)
            }
            row {
                cell("Negatives")
                cell(this@Evaluation.negatives)
            }
            row {
                cell("True Positives")
                cell(this@Evaluation.truePositives)
            }
            row {
                cell("False Positives")
                cell(this@Evaluation.falsePositives)
            }
            row {
                cell("True Negatives")
                cell(this@Evaluation.trueNegatives)
            }
            row {
                cell("False Negatives")
                cell(this@Evaluation.falseNegatives)
            }
            row {
                cell("Accuracy")
                cell(this@Evaluation.accuracy)
            }
            row {
                cell("Precision")
                cell(this@Evaluation.precision)
            }
            row {
                cell("Recall")
                cell(this@Evaluation.recall)
            }
            row {
                cell("F1 Score")
                cell(this@Evaluation.f1)
            }
        }
    }
}