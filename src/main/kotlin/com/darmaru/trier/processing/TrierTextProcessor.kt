package com.darmaru.trier.processing

class TrierTextProcessor(
    private val sortStrings: (List<String>) -> List<String>,
) {
    fun process(
        text: String,
        settings: TrierResolvedSettings,
    ): String {
        val candidates = mutableListOf<SortCandidate>()
        candidates += collectAttributeCandidates(text, settings)
        candidates += collectApplyCandidates(text)
        candidates += collectTaggedTemplateCandidates(text, settings)
        candidates += collectFunctionCallCandidates(text, settings)

        if (candidates.isEmpty()) {
            return text
        }

        val replacements = buildReplacements(candidates)
        if (replacements.isEmpty()) {
            return text
        }

        val result = StringBuilder(text)
        for (replacement in replacements.distinctBy { it.start to it.end }.sortedByDescending { it.start }) {
            result.replace(replacement.start, replacement.end, replacement.value)
        }
        return result.toString()
    }

    private fun collectAttributeCandidates(
        text: String,
        settings: TrierResolvedSettings,
    ): List<SortCandidate> {
        val predicates = buildAttributePredicates(settings.tailwindAttributes)
        val regex = Regex("""([:@\[\]\w-]+)\s*=\s*(["'])([\s\S]*?)\2""")
        return regex
            .findAll(text)
            .mapNotNull { match ->
                val name = match.groupValues[1]
                if (predicates.none { it(name) }) {
                    return@mapNotNull null
                }
                val value = match.groupValues[3]
                SortCandidate(
                    start = match.range.first,
                    end = match.range.last + 1,
                    originalValue = value,
                    renderReplacement = { sorted -> "$name=${match.groupValues[2]}$sorted${match.groupValues[2]}" },
                )
            }.toList()
    }

    private fun collectApplyCandidates(text: String): List<SortCandidate> {
        val regex = Regex("""@apply(\s+)([^;]+)(?=;)""")
        return regex
            .findAll(text)
            .mapNotNull { match ->
                SortCandidate(
                    start = match.range.first,
                    end = match.range.last + 1,
                    originalValue = match.groupValues[2],
                    renderReplacement = { sorted -> "@apply${match.groupValues[1]}$sorted" },
                )
            }.toList()
    }

    private fun collectTaggedTemplateCandidates(
        text: String,
        settings: TrierResolvedSettings,
    ): List<SortCandidate> {
        val predicates = buildFunctionPredicates(settings.tailwindFunctions)
        if (predicates.isEmpty()) {
            return emptyList()
        }

        val replacements = mutableListOf<SortCandidate>()
        var index = 0
        while (index < text.length) {
            val identifier =
                readIdentifierBackward(text, index) ?: run {
                    index++
                    continue
                }
            val identifierEnd = identifier.second
            var cursor = identifierEnd
            while (cursor < text.length && text[cursor].isWhitespace()) {
                cursor++
            }
            if (cursor >= text.length || text[cursor] != '`' || predicates.none { it(identifier.first) }) {
                index = identifierEnd + 1
                continue
            }
            val end = findQuotedLiteralEnd(text, cursor, '`') ?: break
            val contentStart = cursor + 1
            val contentEnd = end
            val value = text.substring(contentStart, contentEnd)
            replacements += SortCandidate(contentStart, contentEnd, value)
            index = end + 1
        }
        return replacements
    }

    private fun collectFunctionCallCandidates(
        text: String,
        settings: TrierResolvedSettings,
    ): List<SortCandidate> {
        val predicates = buildFunctionPredicates(settings.tailwindFunctions)
        if (predicates.isEmpty()) {
            return emptyList()
        }

        val replacements = mutableListOf<SortCandidate>()
        var index = 0
        while (index < text.length) {
            val identifier =
                readIdentifierBackward(text, index) ?: run {
                    index++
                    continue
                }
            val identifierEnd = identifier.second
            var cursor = identifierEnd
            while (cursor < text.length && text[cursor].isWhitespace()) {
                cursor++
            }
            if (cursor >= text.length || text[cursor] != '(' || predicates.none { it(identifier.first) }) {
                index = identifierEnd + 1
                continue
            }
            val end = findMatchingParen(text, cursor) ?: break
            replacements += collectStringLiteralCandidates(text, cursor + 1, end)
            index = end + 1
        }
        return replacements
    }

    private fun collectStringLiteralCandidates(
        text: String,
        start: Int,
        end: Int,
    ): List<SortCandidate> {
        val replacements = mutableListOf<SortCandidate>()
        var index = start
        while (index < end) {
            val char = text[index]
            if (char == '\'' || char == '"') {
                val literalEnd = findQuotedLiteralEnd(text, index, char, end) ?: break
                val value = text.substring(index + 1, literalEnd)
                replacements += SortCandidate(index + 1, literalEnd, value)
                index = literalEnd + 1
                continue
            }
            if (char == '`') {
                val literalEnd = findQuotedLiteralEnd(text, index, char, end) ?: break
                index = literalEnd + 1
                continue
            }
            index++
        }
        return replacements
    }

    private fun buildReplacements(candidates: List<SortCandidate>): List<Replacement> {
        val sortedValues = sortStrings(candidates.map(SortCandidate::originalValue))
        return candidates.mapIndexedNotNull { index, candidate ->
            val sortedValue = sortedValues.getOrNull(index) ?: candidate.originalValue
            if (sortedValue == candidate.originalValue) {
                null
            } else {
                Replacement(candidate.start, candidate.end, candidate.renderReplacement(sortedValue))
            }
        }
    }

    private fun readIdentifierBackward(
        text: String,
        index: Int,
    ): Pair<String, Int>? {
        if (!text[index].isIdentifierStart()) {
            return null
        }
        var end = index
        while (end < text.length && text[end].isIdentifierPart()) {
            end++
        }
        return text.substring(index, end) to end
    }

    private fun findQuotedLiteralEnd(
        text: String,
        start: Int,
        quote: Char,
        limit: Int = text.length,
    ): Int? {
        var index = start + 1
        var interpolationDepth = 0
        while (index < limit) {
            val char = text[index]
            if (char == '\\') {
                index += 2
                continue
            }
            if (quote == '`') {
                if (char == '$' && index + 1 < limit && text[index + 1] == '{') {
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

    private fun findMatchingParen(
        text: String,
        start: Int,
    ): Int? {
        var depth = 1
        var index = start + 1
        while (index < text.length) {
            when (val char = text[index]) {
                '\'', '"', '`' -> index = findQuotedLiteralEnd(text, index, char) ?: return null
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
            index++
        }
        return null
    }

    private fun Char.isIdentifierStart(): Boolean = isLetter() || this == '_' || this == '$'

    private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_' || this == '$'
}
