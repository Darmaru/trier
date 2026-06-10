package com.darmaru.trier.processing

private val DEFAULT_ATTRIBUTES =
    listOf("class", "className", ":class", "v-bind:class", "ngClass", "[class]", "[ngClass]", "class:list")

fun buildAttributePredicates(customValues: List<String>): List<(String) -> Boolean> =
    (DEFAULT_ATTRIBUTES + customValues)
        .distinct()
        .map(::toNamePredicate)

internal fun isDynamicClassAttributeName(name: String): Boolean =
    name.startsWith(":") || name.startsWith("v-bind:") || name.startsWith("[")

internal fun shouldSkipClassAttributeValue(
    name: String,
    value: String,
): Boolean =
    value.contains('<') ||
        (!isDynamicClassAttributeName(name) && containsStaticAttributeInterpolation(value)) ||
        (isAngularClassBindingName(name) && containsComplexAngularExpression(value))

internal fun containsStaticAttributeInterpolation(value: String): Boolean = value.contains("{{") || value.contains("$")

internal fun containsUnquotedPipe(value: String): Boolean {
    var index = 0
    while (index < value.length) {
        when (val char = value[index]) {
            '\'', '"', '`' -> index = findQuotedLiteralEnd(value, index, char) ?: return false
            '|' -> return true
        }
        index++
    }
    return false
}

internal fun containsUnquotedCall(value: String): Boolean {
    var index = 0
    while (index < value.length) {
        when (val char = value[index]) {
            '\'', '"', '`' -> index = findQuotedLiteralEnd(value, index, char) ?: return false
            '(' -> {
                val previous = value.previousNonWhitespace(index)
                if (previous != null && previous.isIdentifierPart()) {
                    return true
                }
            }
        }
        index++
    }
    return false
}

private fun isAngularClassBindingName(name: String): Boolean = name == "[class]" || name == "[ngClass]"

private fun containsComplexAngularExpression(value: String): Boolean =
    containsUnquotedPipe(value) || containsUnquotedCall(value)

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

private fun findQuotedLiteralEnd(
    text: String,
    start: Int,
    quote: Char,
): Int? {
    var index = start + 1
    var interpolationDepth = 0
    while (index < text.length) {
        val char = text[index]
        if (char == '\\') {
            index += 2
            continue
        }
        if (quote == '`') {
            if (char == '$' && index + 1 < text.length && text[index + 1] == '{') {
                interpolationDepth++
                index += 2
                continue
            }
            if (char == '}' && interpolationDepth > 0) {
                interpolationDepth--
                index++
                continue
            }
        }
        if (char == quote && interpolationDepth == 0) {
            return index
        }
        index++
    }
    return null
}

private fun String.previousNonWhitespace(index: Int): Char? {
    var cursor = index - 1
    while (cursor >= 0) {
        val char = this[cursor]
        if (!char.isWhitespace()) {
            return char
        }
        cursor--
    }
    return null
}

private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_' || this == '$'
