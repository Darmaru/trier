package com.darmaru.trier.processing

private val DEFAULT_ATTRIBUTES = listOf("class", "className", ":class", "[ngClass]")

fun buildAttributePredicates(customValues: List<String>): List<(String) -> Boolean> =
    (DEFAULT_ATTRIBUTES + customValues)
        .distinct()
        .map(::toNamePredicate)

fun buildFunctionPredicates(customValues: List<String>): List<(String) -> Boolean> =
    customValues.distinct().map(::toNamePredicate)

private fun toNamePredicate(rawValue: String): (String) -> Boolean {
    val trimmed = rawValue.trim()
    if (trimmed.startsWith("/") && trimmed.endsWith("/") && trimmed.length > 2) {
        val regex = Regex(trimmed.removePrefix("/").removeSuffix("/"))
        return { candidate -> regex.matches(candidate) }
    }

    return { candidate -> candidate == trimmed }
}
