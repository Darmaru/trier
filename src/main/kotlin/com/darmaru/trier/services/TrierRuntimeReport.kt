package com.darmaru.trier.services

internal data class TrierRuntimeReport(
    val node: String,
    val nodeVersion: String,
    val bundledRuntime: String,
    val tailwindStylesheet: String?,
    val tailwindConfig: String?,
    val sampleSortResult: String,
) {
    fun rows(): List<TrierRuntimeReportRow> =
        listOf(
            TrierRuntimeReportRow("Node", node),
            TrierRuntimeReportRow("Node version", nodeVersion),
            TrierRuntimeReportRow("Bundled runtime", bundledRuntime),
            TrierRuntimeReportRow("Tailwind stylesheet", tailwindStylesheet ?: AUTO_DETECT_NOT_FOUND),
            TrierRuntimeReportRow("Tailwind config", tailwindConfig ?: AUTO_DETECT_NOT_FOUND),
            TrierRuntimeReportRow("Sample sort result", sampleSortResult),
        )

    companion object {
        private const val AUTO_DETECT_NOT_FOUND = "auto-detect did not find one"
    }
}

internal data class TrierRuntimeReportRow(
    val label: String,
    val value: String,
)
