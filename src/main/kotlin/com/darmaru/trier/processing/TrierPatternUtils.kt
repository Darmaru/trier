package com.darmaru.trier.processing

private val DEFAULT_ATTRIBUTES = listOf("class", "className", ":class", "v-bind:class", "[ngClass]")

fun buildAttributePredicates(customValues: List<String>): List<(String) -> Boolean> =
    (DEFAULT_ATTRIBUTES + customValues)
        .distinct()
        .map(::toNamePredicate)

internal fun isDynamicClassAttributeName(name: String): Boolean =
    name.startsWith(":") || name.startsWith("v-bind:") || name.startsWith("[")

fun buildFunctionPredicates(customValues: List<String>): List<(String) -> Boolean> =
    customValues.distinct().map(::toNamePredicate)

fun validateNamePatterns(
    label: String,
    customValues: List<String>,
) {
    customValues.forEach { rawValue ->
        val trimmed = rawValue.trim()
        if (trimmed.startsWith("/") && trimmed.endsWith("/") && trimmed.length > 2) {
            try {
                Regex(trimmed.removePrefix("/").removeSuffix("/"))
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("$label contains invalid regex '$trimmed': ${error.message}", error)
            }
        }
    }
}

private fun toNamePredicate(rawValue: String): (String) -> Boolean {
    val trimmed = rawValue.trim()
    if (trimmed.startsWith("/") && trimmed.endsWith("/") && trimmed.length > 2) {
        val regex = Regex(trimmed.removePrefix("/").removeSuffix("/"))
        return { candidate -> regex.matches(candidate) }
    }

    return { candidate -> candidate == trimmed }
}
