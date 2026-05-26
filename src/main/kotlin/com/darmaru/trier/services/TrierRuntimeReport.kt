package com.darmaru.trier.services

internal data class TrierRuntimeReport(
    val node: String,
    val nodeVersion: String,
    val bundledRuntime: String,
    val tailwindStylesheet: String?,
    val tailwindStylesheetSource: TrierTailwindPathSource = TrierTailwindPathSource.NOT_FOUND,
    val tailwindConfig: String?,
    val tailwindConfigSource: TrierTailwindPathSource = TrierTailwindPathSource.NOT_FOUND,
    val tailwindCdnDetected: Boolean = false,
    val sampleSortResult: String,
) {
    fun rows(): List<TrierRuntimeReportRow> =
        buildList {
            add(TrierRuntimeReportRow("Node", node))
            add(TrierRuntimeReportRow("Node version", nodeVersion))
            add(TrierRuntimeReportRow("Bundled runtime", bundledRuntime))
            add(TrierRuntimeReportRow("Tailwind stylesheet", tailwindStylesheetSource.describe(tailwindStylesheet)))
            add(TrierRuntimeReportRow("Tailwind config", tailwindConfigSource.describe(tailwindConfig)))
            if (tailwindCdnDetected) {
                add(TrierRuntimeReportRow("Tailwind CDN", "detected"))
            }
            add(TrierRuntimeReportRow("Sample sort result", sampleSortResult))
        }
}

internal data class TrierRuntimeReportRow(
    val label: String,
    val value: String,
)
