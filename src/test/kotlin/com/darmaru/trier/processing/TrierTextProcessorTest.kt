package com.darmaru.trier.processing

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrierTextProcessorTest {
    private val settings =
        TrierResolvedSettings(
            nodeInterpreterRef = null,
            sortOnSave = false,
            sortOnReformat = false,
            tailwindStylesheet = null,
            tailwindConfig = null,
            tailwindAttributes = emptyList(),
            tailwindFunctions = emptyList(),
            tailwindPreserveWhitespace = false,
            tailwindPreserveDuplicates = false,
        )

    @Test
    fun `sorts standard class attribute`() {
        val processor = processor()

        val result =
            processor.process(
                """<div class="text-center p-4 flex bg-red-500 font-bold"></div>""",
                settings,
            )

        assertEquals(
            """<div class="flex bg-red-500 p-4 text-center font-bold"></div>""",
            result,
        )
    }

    @Test
    fun `does not touch unrelated attribute`() {
        val processor = processor()
        val input = """<div data-value="text-center p-4 flex bg-red-500 font-bold"></div>"""

        val result = processor.process(input, settings)

        assertEquals(input, result)
    }

    @Test
    fun `sorts apply directive`() {
        val processor = processor()

        val result =
            processor.process(
                """.button { @apply text-center p-4 flex bg-red-500 font-bold; }""",
                settings,
            )

        assertEquals(
            """.button { @apply flex bg-red-500 p-4 text-center font-bold; }""",
            result,
        )
    }

    @Test
    fun `sorts custom function string literals`() {
        val processor = processor()
        val functionSettings = settings.copy(tailwindFunctions = listOf("cn"))

        val result =
            processor.process(
                """const value = cn("text-center p-4 flex bg-red-500 font-bold")""",
                functionSettings,
            )

        assertEquals(
            """const value = cn("flex bg-red-500 p-4 text-center font-bold")""",
            result,
        )
    }

    @Test
    fun `sorts tagged template content for configured function`() {
        val processor = processor()
        val functionSettings = settings.copy(tailwindFunctions = listOf("tw"))

        val result =
            processor.process(
                """const button = tw`text-center p-4 flex bg-red-500 font-bold`""",
                functionSettings,
            )

        assertEquals(
            """const button = tw`flex bg-red-500 p-4 text-center font-bold`""",
            result,
        )
    }

    @Test
    fun `sorts custom attribute`() {
        val processor = processor()
        val attributeSettings = settings.copy(tailwindAttributes = listOf("data-classes"))

        val result =
            processor.process(
                """<div data-classes="text-center p-4 flex bg-red-500 font-bold"></div>""",
                attributeSettings,
            )

        assertEquals(
            """<div data-classes="flex bg-red-500 p-4 text-center font-bold"></div>""",
            result,
        )
    }

    @Test
    fun `sorts v-bind class quoted expression fragment`() {
        val processor = processor()

        val result =
            processor.process(
                """<div v-bind:class="'text-center p-4 flex bg-red-500 font-bold'"></div>""",
                settings,
            )

        assertEquals(
            """<div v-bind:class="'flex bg-red-500 p-4 text-center font-bold'"></div>""",
            result,
        )
    }

    @Test
    fun `sorts quoted fragments inside dynamic fallback attributes without rewriting expression syntax`() {
        val processor = processor()

        val result =
            processor.process(
                """<div [ngClass]="{ 'text-center p-4 flex bg-red-500 font-bold': active }"></div>""",
                settings,
            )

        assertEquals(
            """<div [ngClass]="{ 'flex bg-red-500 p-4 text-center font-bold': active }"></div>""",
            result,
        )
    }

    @Test
    fun `does not sort quoted fragments inside dynamic attribute comments`() {
        val processor = processor()
        val input =
            """
            <div :class="[
              // 'text-center p-4 flex bg-red-500 font-bold'
              'font-bold flex p-4'
            ]"></div>
            """.trimIndent()

        val result = processor.process(input, settings)

        assertEquals(
            """
            <div :class="[
              // 'text-center p-4 flex bg-red-500 font-bold'
              'flex p-4 font-bold'
            ]"></div>
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `leaves unsupported svelte class directive unchanged`() {
        val processor = processor()
        val input = """<button class:active={isActive}></button>"""

        val result = processor.process(input, settings)

        assertEquals(input, result)
    }

    @Test
    fun `leaves unsupported astro class list expression unchanged`() {
        val processor = processor()
        val input = """<div class:list={["text-center p-4 flex bg-red-500 font-bold"]}></div>"""

        val result = processor.process(input, settings)

        assertEquals(input, result)
    }

    @Test
    fun `leaves unsupported angular class binding unchanged`() {
        val processor = processor()
        val input = """<div [class.active]="isActive"></div>"""

        val result = processor.process(input, settings)

        assertEquals(input, result)
    }

    @Test
    fun `leaves unsupported blade class directive unchanged`() {
        val processor = processor()
        val input = """<div @class(['text-center p-4 flex bg-red-500 font-bold' => active])></div>"""

        val result = processor.process(input, settings)

        assertEquals(input, result)
    }

    @Test
    fun `malformed class attribute is no-op`() {
        val processor = processor()
        val input = """<div class="text-center p-4 flex bg-red-500 font-bold></div>"""

        val result = processor.process(input, settings)

        assertEquals(input, result)
    }

    @Test
    fun `malformed apply directive without semicolon is no-op`() {
        val processor = processor()
        val input = """.button { @apply text-center p-4 flex bg-red-500 font-bold }"""

        val result = processor.process(input, settings)

        assertEquals(input, result)
    }

    @Test
    fun `malformed dynamic class binding is no-op`() {
        val processor = processor()
        val input = """<div :class="isActive ? 'text-center p-4 flex bg-red-500 font-bold"></div>"""

        val result = processor.process(input, settings)

        assertEquals(input, result)
    }

    @Test
    fun `malformed dynamic class binding with earlier valid fragment is no-op`() {
        val processor = processor()
        val input =
            """
            <div :class="[
              'text-center p-4 flex bg-red-500 font-bold',
              isActive && 'font-bold flex p-4
            ]"></div>
            """.trimIndent()

        val result = processor.process(input, settings)

        assertEquals(input, result)
    }

    @Test
    fun `sorts quoted string inside dynamic class attribute expression`() {
        val ranges = TrierPsiProcessor.findQuotedLiteralContentRanges("'text-center p-4 flex bg-red-500 font-bold'")

        assertEquals(1, ranges.size)
        assertEquals(
            "text-center p-4 flex bg-red-500 font-bold",
            ranges.single().substring("'text-center p-4 flex bg-red-500 font-bold'"),
        )
    }

    @Test
    fun `finds multiple quoted fragments inside dynamic expression`() {
        val text = "isActive ? 'text-center p-4 flex bg-red-500 font-bold' : `font-bold flex p-4`"

        val ranges = TrierPsiProcessor.findQuotedLiteralContentRanges(text)

        assertEquals(2, ranges.size)
        assertEquals("text-center p-4 flex bg-red-500 font-bold", ranges[0].substring(text))
        assertEquals("font-bold flex p-4", ranges[1].substring(text))
    }

    @Test
    fun `quoted literal detection skips escaped quotes and template interpolations`() {
        val text = """"text-center \"still-string\" p-4" + `flex ${'$'}{bar} p-4` + 'font-bold\'still-string'"""

        val ranges = TrierPsiProcessor.findQuotedLiteralContentRanges(text)

        assertEquals(3, ranges.size)
        assertTrue(ranges[0].substring(text).contains("still-string"))
        assertTrue(ranges[0].substring(text).startsWith("text-center"))
        assertEquals("""flex ${'$'}{bar} p-4""", ranges[1].substring(text))
        assertTrue(ranges[2].substring(text).startsWith("font-bold"))
        assertTrue(ranges[2].substring(text).contains("still-string"))
    }

    @Test
    fun `quoted literal detection ignores line and block comments`() {
        val text =
            """
            // 'text-center p-4 flex bg-red-500 font-bold'
            'font-bold flex p-4'
            /* `text-center p-4 flex bg-red-500 font-bold` */
            "text-center p-4 flex bg-red-500 font-bold"
            """.trimIndent()

        val ranges = TrierPsiProcessor.findQuotedLiteralContentRanges(text)

        assertEquals(2, ranges.size)
        assertEquals("font-bold flex p-4", ranges[0].substring(text))
        assertEquals("text-center p-4 flex bg-red-500 font-bold", ranges[1].substring(text))
    }

    @Test
    fun `applies multiple replacements in one document`() {
        val processor = processor()
        val functionSettings = settings.copy(tailwindFunctions = listOf("cn"))

        val result =
            processor.process(
                """
                <div class="text-center p-4 flex bg-red-500 font-bold"></div>
                const value = cn("text-center p-4 flex bg-red-500 font-bold")
                """.trimIndent(),
                functionSettings,
            )

        assertEquals(
            """
            <div class="flex bg-red-500 p-4 text-center font-bold"></div>
            const value = cn("flex bg-red-500 p-4 text-center font-bold")
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `regex custom attribute matcher works`() {
        val predicates = buildAttributePredicates(listOf("/data-.+/"))

        assertEquals(true, predicates.any { it("data-classes") })
        assertEquals(false, predicates.any { it("aria-label") })
    }

    @Test
    fun `finds apply value range`() {
        val text = "@apply text-center p-4 flex bg-red-500 font-bold;"

        val range = TrierPsiProcessor.findApplyValueRange(text)

        assertEquals("text-center p-4 flex bg-red-500 font-bold", range?.substring(text))
    }

    @Test
    fun `replacement range filter allows only fully enclosed replacements`() {
        val limit = TextRange(10, 30)

        assertEquals(true, TrierPsiProcessor.isReplacementAllowed(Replacement(12, 18, "x"), limit))
        assertEquals(false, TrierPsiProcessor.isReplacementAllowed(Replacement(5, 18, "x"), limit))
        assertEquals(false, TrierPsiProcessor.isReplacementAllowed(Replacement(12, 35, "x"), limit))
    }

    private fun processor(): TrierTextProcessor =
        TrierTextProcessor { values ->
            values.map(::sortClassesStub)
        }

    private fun sortClassesStub(value: String): String {
        val order = listOf("flex", "bg-red-500", "p-4", "text-center", "font-bold")
        return value
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .sortedBy { token ->
                val idx = order.indexOf(token)
                if (idx >= 0) idx else order.size + token.hashCode()
            }.joinToString(" ")
    }
}
