package com.darmaru.trier.services

internal data class TrierTailwindSorterContext(
    val filePath: String?,
    val stylesheetPath: String?,
    val stylesheetSource: TrierTailwindPathSource = TrierTailwindPathSource.UNKNOWN,
    val configPath: String?,
    val configSource: TrierTailwindPathSource = TrierTailwindPathSource.UNKNOWN,
    val tailwindCdnDetected: Boolean = false,
)

internal class TrierTailwindSorterException(
    message: String,
    cause: Throwable,
) : IllegalStateException(message, cause)

internal fun buildTailwindSorterFailureMessage(
    error: Throwable,
    context: TrierTailwindSorterContext,
): String {
    val tailwindError = error.conciseMessage()
    return buildString {
        appendLine("Tailwind sorter failed while initializing or sorting classes.")
        appendLine("Tailwind error: $tailwindError")
        appendLine("Current file: ${context.filePath ?: "not available"}")
        appendLine("Tailwind stylesheet: ${context.stylesheetDescription()}")
        appendLine("Tailwind config: ${context.configDescription()}")
        appendLine()
        appendLine(
            "This can come from Tailwind stylesheet/config initialization, not necessarily from the current file.",
        )
        appendLine(
            "If the detected paths look wrong, set Stylesheet and Config explicitly in Settings > Tools > Trier.",
        )
        if (context.tailwindCdnDetected) {
            append(
                "Tailwind CDN usage was detected. Trier cannot infer that CDN runtime context automatically.",
            )
        } else {
            append(
                "If this project uses Tailwind only through the CDN, Trier cannot infer that CDN runtime context automatically.",
            )
        }
    }
}

internal enum class TrierTailwindPathSource(
    private val label: String,
) {
    MANUAL("manual"),
    AUTO_DETECTED("auto-detected"),
    NOT_FOUND("auto-detect did not find one"),
    UNKNOWN("unknown"),
    ;

    fun describe(path: String?): String =
        if (path == null) {
            label
        } else {
            "$path ($label)"
        }
}

private fun TrierTailwindSorterContext.stylesheetDescription(): String = stylesheetSource.describe(stylesheetPath)

private fun TrierTailwindSorterContext.configDescription(): String = configSource.describe(configPath)

private fun Throwable.conciseMessage(): String =
    generateSequence(this) { it.cause }
        .mapNotNull { throwable ->
            throwable.message
                ?.lineSequence()
                ?.firstOrNull(String::isNotBlank)
                ?.trim()
        }.firstOrNull()
        ?: javaClass.simpleName
