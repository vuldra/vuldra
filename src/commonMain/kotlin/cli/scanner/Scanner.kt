package cli.scanner

import sarif.MinimizedRun

enum class Scanner{
    SEMGREP {
        override suspend fun scanFile(targetFile: String) = scanFileWithSemgrep(targetFile)
    },
    OPENAI {
        // TODO refactor
        override suspend fun scanFile(targetFile: String) = error("Not supported")
    };

    abstract suspend fun scanFile(targetFile: String): List<MinimizedRun>
}