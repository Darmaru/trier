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
        candidates += collectBracedAttributeCandidates(text, settings)
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
            .flatMap { match ->
                val name = match.groupValues[1]
                if (predicates.none { it(name) }) {
                    return@flatMap emptyList()
                }
                val value = match.groupValues[3]
                if (value.contains('<')) {
                    return@flatMap emptyList()
                }
                if (isDynamicClassAttributeName(name) && TrierPsiProcessor.hasUnterminatedQuotedLiteral(value)) {
                    return@flatMap emptyList()
                }
                val valueGroup = match.groups[3] ?: return@flatMap emptyList()
                val valueStart = valueGroup.range.first
                val quotedRanges = TrierPsiProcessor.findQuotedLiteralContentRanges(value)
                if (quotedRanges.isNotEmpty()) {
                    quotedRanges.map { localRange ->
                        val start = valueStart + localRange.startOffset
                        SortCandidate(
                            start = start,
                            end = valueStart + localRange.endOffset,
                            originalValue = localRange.substring(value),
                        )
                    }
                } else if (!isDynamicClassAttributeName(name)) {
                    listOf(
                        SortCandidate(
                            start = valueStart,
                            end = valueStart + value.length,
                            originalValue = value,
                        ),
                    )
                } else {
                    emptyList()
                }
            }.toList()
    }

    private fun collectBracedAttributeCandidates(
        text: String,
        settings: TrierResolvedSettings,
    ): List<SortCandidate> {
        val predicates = buildAttributePredicates(settings.tailwindAttributes)
        val regex = Regex("""([:@\[\]\w-]+)\s*=\s*\{""")
        val replacements = mutableListOf<SortCandidate>()

        regex.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            if (predicates.none { it(name) }) {
                return@forEach
            }

            val expressionStart = match.range.last + 1
            val expressionEnd = findMatchingBrace(text, expressionStart - 1) ?: return@forEach
            val expression = text.substring(expressionStart, expressionEnd)
            if (TrierPsiProcessor.hasUnterminatedQuotedLiteral(expression)) {
                return@forEach
            }

            replacements +=
                TrierPsiProcessor
                    .findQuotedLiteralContentRanges(expression)
                    .filterNot { localRange ->
                        isInterpolatedTemplateLiteral(
                            expression = expression,
                            contentStart = localRange.startOffset,
                            contentEnd = localRange.endOffset,
                        )
                    }.map { localRange ->
                        val start = expressionStart + localRange.startOffset
                        SortCandidate(
                            start = start,
                            end = expressionStart + localRange.endOffset,
                            originalValue = localRange.substring(expression),
                        )
                    }
        }

        return replacements
    }

    private fun collectApplyCandidates(text: String): List<SortCandidate> {
        val regex = Regex("""@apply(\s+)([^;]+)(?=;)""")
        return regex
            .findAll(text)
            .map { match ->
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
            val value = text.substring(contentStart, end)
            if (value.contains("\${")) {
                index = end + 1
                continue
            }
            replacements += SortCandidate(contentStart, end, value)
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
                val value = text.substring(index + 1, literalEnd)
                if (!value.contains("\${")) {
                    replacements += SortCandidate(index + 1, literalEnd, value)
                }
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

    private fun isInterpolatedTemplateLiteral(
        expression: String,
        contentStart: Int,
        contentEnd: Int,
    ): Boolean =
        contentStart > 0 &&
            expression[contentStart - 1] == '`' &&
            expression.substring(contentStart, contentEnd).contains("\${")

    private fun findMatchingBrace(
        text: String,
        start: Int,
    ): Int? {
        var depth = 1
        var index = start + 1
        while (index < text.length) {
            when (val char = text[index]) {
                '\'', '"', '`' -> index = findQuotedLiteralEnd(text, index, char) ?: return null
                '{' -> depth++
                '}' -> {
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
