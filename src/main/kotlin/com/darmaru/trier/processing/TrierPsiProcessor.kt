package com.darmaru.trier.processing

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.ecma6.ES6TaggedTemplateExpression
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.css.CssAtRule
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag

class TrierPsiProcessor(
    private val sortStrings: (List<String>) -> List<String>,
    private val fallbackTextProcessor: TrierTextProcessor,
) {
    fun process(
        file: PsiFile,
        originalText: String,
        settings: TrierResolvedSettings,
        limitRange: TextRange? = null,
        useFallback: Boolean = true,
    ): String {
        val candidates = mutableListOf<SortCandidate>()
        var foundPsiTargetInRange = false
        val attributePredicates = buildAttributePredicates(settings.tailwindAttributes)
        val functionPredicates = buildFunctionPredicates(settings.tailwindFunctions)

        file.viewProvider.allFiles.forEach { psiRoot ->
            psiRoot.accept(
                object : PsiRecursiveElementWalkingVisitor() {
                    override fun visitElement(element: PsiElement) {
                        when (element) {
                            is XmlAttribute -> {
                                if (hasXmlAttributeTarget(element, attributePredicates, limitRange)) {
                                    foundPsiTargetInRange = true
                                }
                                collectXmlAttributeCandidates(element, attributePredicates)
                                    ?.filter { isCandidateAllowed(it, limitRange) }
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let(candidates::addAll)
                            }
                            is CssAtRule -> {
                                if (hasCssAtRuleTarget(element, limitRange)) {
                                    foundPsiTargetInRange = true
                                }
                                collectCssAtRuleCandidate(element)
                                    ?.takeIf { isCandidateAllowed(it, limitRange) }
                                    ?.let(candidates::add)
                            }
                            is XmlTag -> {
                                if (hasStyleTagTarget(element, limitRange)) {
                                    foundPsiTargetInRange = true
                                }
                                collectStyleTagCandidates(element)
                                    ?.filter { isCandidateAllowed(it, limitRange) }
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let(candidates::addAll)
                            }
                            is JSLiteralExpression -> {
                                if (hasJsLiteralTarget(element, functionPredicates, limitRange)) {
                                    foundPsiTargetInRange = true
                                }
                                collectJsLiteralCandidate(element, functionPredicates)
                                    ?.takeIf { isCandidateAllowed(it, limitRange) }
                                    ?.let(candidates::add)
                            }
                            is JSStringTemplateExpression -> {
                                if (hasJsTemplateTarget(element, functionPredicates, limitRange)) {
                                    foundPsiTargetInRange = true
                                }
                                collectJsTemplateCandidates(element, functionPredicates)
                                    ?.filter { isCandidateAllowed(it, limitRange) }
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let(candidates::addAll)
                            }
                        }
                        super.visitElement(element)
                    }
                },
            )
        }

        if (candidates.isEmpty()) {
            return if (useFallback && !foundPsiTargetInRange) {
                fallbackTextProcessor.process(originalText, settings)
            } else {
                originalText
            }
        }

        val replacements = buildReplacements(candidates)
        if (replacements.isEmpty()) {
            return originalText
        }

        val result = StringBuilder(originalText)
        for (replacement in replacements.distinctBy { it.start to it.end }.sortedByDescending { it.start }) {
            result.replace(replacement.start, replacement.end, replacement.value)
        }
        return result.toString()
    }

    private fun collectXmlAttributeCandidates(
        attribute: XmlAttribute,
        predicates: List<(String) -> Boolean>,
    ): List<SortCandidate>? {
        if (predicates.none { it(attribute.name) }) {
            return null
        }

        val value = attribute.value ?: return null
        val valueElement = attribute.valueElement ?: return null
        val absoluteValueRange = attribute.valueTextRange.shiftRight(valueElement.textRange.startOffset)
        val valueStart = absoluteValueRange.startOffset
        val quotedRanges = findQuotedLiteralContentRanges(value)
        if (quotedRanges.isNotEmpty()) {
            return quotedRanges
                .map { localRange ->
                    val absolute = localRange.shiftRight(valueStart)
                    SortCandidate(
                        start = absolute.startOffset,
                        end = absolute.endOffset,
                        originalValue = localRange.substring(value),
                    )
                }.ifEmpty { null }
        }

        return listOf(SortCandidate(absoluteValueRange.startOffset, absoluteValueRange.endOffset, value))
    }

    private fun collectJsLiteralCandidate(
        literal: JSLiteralExpression,
        predicates: List<(String) -> Boolean>,
    ): SortCandidate? {
        if (!literal.isStringLiteral || !isInsideConfiguredFunction(literal, predicates)) {
            return null
        }

        val value = literal.stringValue ?: return null
        val text = literal.text
        if (text.length < 2) {
            return null
        }

        val range = TextRange(literal.textRange.startOffset + 1, literal.textRange.endOffset - 1)
        return SortCandidate(range.startOffset, range.endOffset, value)
    }

    private fun hasXmlAttributeTarget(
        attribute: XmlAttribute,
        predicates: List<(String) -> Boolean>,
        limitRange: TextRange?,
    ): Boolean {
        if (predicates.none { it(attribute.name) }) {
            return false
        }
        val valueElement = attribute.valueElement ?: return false
        val absoluteValueRange = attribute.valueTextRange.shiftRight(valueElement.textRange.startOffset)
        return intersectsLimitRange(absoluteValueRange, limitRange)
    }

    private fun hasCssAtRuleTarget(
        atRule: CssAtRule,
        limitRange: TextRange?,
    ): Boolean {
        if (!atRule.name.equals("apply", ignoreCase = true)) {
            return false
        }
        val localRange = findApplyValueRange(atRule.text) ?: return false
        val absolute = localRange.shiftRight(atRule.textRange.startOffset)
        return intersectsLimitRange(absolute, limitRange)
    }

    private fun hasJsLiteralTarget(
        literal: JSLiteralExpression,
        predicates: List<(String) -> Boolean>,
        limitRange: TextRange?,
    ): Boolean {
        if (!literal.isStringLiteral || !isInsideConfiguredFunction(literal, predicates) || literal.textLength < 2) {
            return false
        }
        val range = TextRange(literal.textRange.startOffset + 1, literal.textRange.endOffset - 1)
        return intersectsLimitRange(range, limitRange)
    }

    private fun hasStyleTagTarget(
        tag: XmlTag,
        limitRange: TextRange?,
    ): Boolean {
        if (!tag.name.equals("style", ignoreCase = true)) {
            return false
        }
        val contentRange = tag.value.textRange
        return findApplyValueRanges(tag.value.text)
            .map { it.shiftRight(contentRange.startOffset) }
            .any { intersectsLimitRange(it, limitRange) }
    }

    private fun hasJsTemplateTarget(
        template: JSStringTemplateExpression,
        predicates: List<(String) -> Boolean>,
        limitRange: TextRange?,
    ): Boolean {
        if (!isInsideConfiguredFunction(template, predicates)) {
            return false
        }
        return template.stringRanges.any { relativeRange ->
            intersectsLimitRange(relativeRange.shiftRight(template.textRange.startOffset), limitRange)
        }
    }

    private fun collectJsTemplateCandidates(
        template: JSStringTemplateExpression,
        predicates: List<(String) -> Boolean>,
    ): List<SortCandidate>? {
        if (!isInsideConfiguredFunction(template, predicates)) {
            return null
        }

        val replacements =
            template.stringRanges.map { relativeRange ->
                val absolute = relativeRange.shiftRight(template.textRange.startOffset)
                SortCandidate(
                    start = absolute.startOffset,
                    end = absolute.endOffset,
                    originalValue = relativeRange.substring(template.text),
                )
            }

        return replacements.ifEmpty { null }
    }

    private fun collectCssAtRuleCandidate(atRule: CssAtRule): SortCandidate? {
        if (!atRule.name.equals("apply", ignoreCase = true)) {
            return null
        }

        val localRange = findApplyValueRange(atRule.text) ?: return null
        val value = localRange.substring(atRule.text)
        val absolute = localRange.shiftRight(atRule.textRange.startOffset)
        return SortCandidate(absolute.startOffset, absolute.endOffset, value)
    }

    private fun collectStyleTagCandidates(tag: XmlTag): List<SortCandidate>? {
        if (!tag.name.equals("style", ignoreCase = true)) {
            return null
        }
        val content = tag.value.text
        val contentRange = tag.value.textRange
        return findApplyValueRanges(content)
            .map { localRange ->
                val absolute = localRange.shiftRight(contentRange.startOffset)
                SortCandidate(
                    start = absolute.startOffset,
                    end = absolute.endOffset,
                    originalValue = localRange.substring(content),
                )
            }.ifEmpty { null }
    }

    private fun isInsideConfiguredFunction(
        element: PsiElement,
        predicates: List<(String) -> Boolean>,
    ): Boolean {
        if (predicates.isEmpty()) {
            return false
        }

        val taggedTemplate = PsiTreeUtil.getParentOfType(element, ES6TaggedTemplateExpression::class.java)
        if (taggedTemplate != null && matchesFunctionName(taggedTemplate.tag, predicates)) {
            return true
        }

        val callExpression = PsiTreeUtil.getParentOfType(element, JSCallExpression::class.java)
        return callExpression != null && matchesFunctionName(callExpression.methodExpression, predicates)
    }

    private fun matchesFunctionName(
        expression: PsiElement?,
        predicates: List<(String) -> Boolean>,
    ): Boolean {
        val candidates =
            buildList {
                when (expression) {
                    is JSReferenceExpression -> expression.referenceNameElement?.text?.let(::add)
                }
                expression?.text?.takeIf { it.isNotBlank() }?.let(::add)
            }
        return candidates.any { candidate -> predicates.any { it(candidate) } }
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

    companion object {
        internal fun findApplyValueRange(text: String): TextRange? {
            val match = Regex("""@apply(\s+)([^;{}]+)(?=\s*;)""").find(text) ?: return null
            val value = match.groups[2] ?: return null
            return value.range.let { TextRange(it.first, it.last + 1) }
        }

        internal fun findApplyValueRanges(text: String): List<TextRange> =
            Regex("""@apply(\s+)([^;{}]+)(?=\s*;)""")
                .findAll(text)
                .mapNotNull { match ->
                    match.groups[2]?.range?.let { TextRange(it.first, it.last + 1) }
                }.toList()

        internal fun findQuotedLiteralContentRanges(text: String): List<TextRange> {
            val ranges = mutableListOf<TextRange>()
            var index = 0
            while (index < text.length) {
                val quote = text[index]
                if (quote != '\'' && quote != '"' && quote != '`') {
                    index++
                    continue
                }

                val end = findQuotedLiteralEnd(text, index, quote) ?: break
                ranges += TextRange(index + 1, end)
                index = end + 1
            }
            return ranges
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

        internal fun isReplacementAllowed(
            replacement: Replacement,
            limitRange: TextRange?,
        ): Boolean = limitRange == null || limitRange.contains(TextRange(replacement.start, replacement.end))

        internal fun isCandidateAllowed(
            candidate: SortCandidate,
            limitRange: TextRange?,
        ): Boolean = limitRange == null || limitRange.contains(TextRange(candidate.start, candidate.end))

        private fun intersectsLimitRange(
            targetRange: TextRange,
            limitRange: TextRange?,
        ): Boolean = limitRange == null || targetRange.intersectsStrict(limitRange)
    }
}
