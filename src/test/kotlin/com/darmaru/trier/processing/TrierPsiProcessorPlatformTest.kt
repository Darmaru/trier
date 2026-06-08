package com.darmaru.trier.processing

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TrierPsiProcessorPlatformTest : BasePlatformTestCase() {
    private val settings =
        TrierResolvedSettings(
            nodeInterpreterRef = null,
            sortOnSave = false,
            sortOnReformat = false,
            tailwindStylesheet = null,
            tailwindConfig = null,
            tailwindAttributes = emptyList(),
            tailwindFunctions = listOf("cn", "tw"),
            tailwindPreserveWhitespace = false,
            tailwindPreserveDuplicates = false,
        )

    fun testProcessesHtmlAttributeThroughRealPsi() {
        val text = """<div class="flex bg-red-500 p-4 text-center font-bold"></div>"""
        val file = myFixture.configureByText("test.html", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """<div class="flex bg-red-500 p-4 text-center font-bold"></div>""",
            result,
        )
    }

    fun testProcessesCssApplyThroughRealPsi() {
        val text = """.button { @apply flex bg-red-500 p-4 text-center font-bold; }"""
        val file = myFixture.configureByText("test.css", text)

        val result = processor().process(file, text, settings)

        assertEquals(
            """.button { @apply flex bg-red-500 p-4 text-center font-bold; }""",
            result,
        )
    }

    fun testProcessesDynamicAttributeQuotedFragmentsThroughRealPsi() {
        val text = """<div :class="isActive ? 'text-center font-bold' : `font-bold p-4`"></div>"""
        val file = myFixture.configureByText("test.html", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """<div :class="isActive ? 'text-center font-bold' : `p-4 font-bold`"></div>""",
            result,
        )
    }

    fun testLimitsChangesToRequestedRangeThroughRealPsi() {
        val text =
            """
            <div class="flex bg-red-500 p-4 text-center font-bold"></div>
            <span class="flex bg-red-500 p-4 text-center font-bold"></span>
            """.trimIndent()
        val file = myFixture.configureByText("test.html", text)
        val firstLineEnd = text.indexOf('\n')

        val result =
            processor().process(
                file,
                text,
                settings,
                limitRange = TextRange(0, firstLineEnd),
                useFallback = false,
            )

        assertEquals(
            """
            <div class="flex bg-red-500 p-4 text-center font-bold"></div>
            <span class="flex bg-red-500 p-4 text-center font-bold"></span>
            """.trimIndent(),
            result,
        )
    }

    fun testDoesNotExpandPartialSelectionOutsideRequestedRange() {
        val text = """<div class="aaa text-center p-4 flex bg-red-500 font-bold bbb"></div>"""
        val file = myFixture.configureByText("test.html", text)
        val selectedText = "text-center p-4 flex bg-red-500 font-bold"
        val start = text.indexOf(selectedText)

        val result =
            processor().process(
                file,
                text,
                settings,
                limitRange = TextRange(start, start + selectedText.length),
                useFallback = false,
            )

        assertEquals(text, result)
    }

    fun testPreservesDynamicAttributeFormattingWhenSortingQuotedFragments() {
        val text =
            """
            <div
                class="flex items-center gap-x-2 px-2 py-0.5 text-xs font-semibold tracking-wide"
                :class="{
                    'text-center': condition,
                }"
            >
            """.trimIndent()
        val file = myFixture.configureByText("test.html", text)

        val result = processor().process(file, text, settings)

        assertEquals(
            """
            <div
                class="flex items-center gap-x-2 px-2 py-0.5 text-xs font-semibold tracking-wide"
                :class="{
                    'text-center': condition,
                }"
            >
            """.trimIndent(),
            result,
        )
    }

    fun testMalformedHtmlClassAttributeIsNoOpWithoutFallbackRewrite() {
        val text = """<div class="text-center p-4 flex bg-red-500 font-bold></div>"""
        val file = myFixture.configureByText("test.html", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(text, result)
    }

    fun testMalformedCssApplyRuleIsNoOp() {
        val text = """.button { @apply text-center p-4 flex bg-red-500 font-bold }"""
        val file = myFixture.configureByText("test.css", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(text, result)
    }

    fun testMalformedVueClassBindingIsNoOpWithoutFallbackRewrite() {
        val text =
            """
            <template>
              <div :class="isActive ? 'text-center p-4 flex bg-red-500 font-bold"></div>
            </template>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(text, result)
    }

    fun testPreservesArrayObjectDynamicAttributeFormattingWhenNoSortingIsNeeded() {
        val text =
            """
            <div
                class="flex relative gap-2"
                :class="[
                    {
                        'flex-col': direction === DIRECTIONS.VERTICAL,
                    }
                ]"
            >
            """.trimIndent()
        val file = myFixture.configureByText("test.html", text)

        val result = processor().process(file, text, settings)

        assertEquals(
            """
            <div
                class="flex relative gap-2"
                :class="[
                    {
                        'flex-col': direction === DIRECTIONS.VERTICAL,
                    }
                ]"
            >
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesHtmlCustomAttributeThroughPsi() {
        val text = """<div data-classes="text-center p-4 flex bg-red-500 font-bold"></div>"""
        val file = myFixture.configureByText("test.html", text)
        val customSettings = settings.copy(tailwindAttributes = listOf("data-classes"))

        val result = processor().process(file, text, customSettings, useFallback = false)

        assertEquals(
            """<div data-classes="flex bg-red-500 p-4 text-center font-bold"></div>""",
            result,
        )
    }

    fun testProcessesJsxClassNameExpressionStringLiteral() {
        val text = """const view = <div className={"text-center p-4 flex bg-red-500 font-bold"} />;"""
        val file = myFixture.configureByText("test.jsx", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """const view = <div className={"flex bg-red-500 p-4 text-center font-bold"} />;""",
            result,
        )
    }

    fun testProcessesMultilineJsxCustomFunctionCall() {
        val text =
            """
            const classes = cn(
              "text-center p-4 flex bg-red-500 font-bold",
              active && "font-bold flex p-4",
            );
            """.trimIndent()
        val file = myFixture.configureByText("test.tsx", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            const classes = cn(
              "flex bg-red-500 p-4 text-center font-bold",
              active && "flex p-4 font-bold",
            );
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesNestedJsxHelperExpressionFragments() {
        val text =
            """
            const classes = cn([
              "text-center p-4 flex bg-red-500 font-bold",
              active && {
                "font-bold flex p-4": compact,
              },
            ]);
            """.trimIndent()
        val file = myFixture.configureByText("test.tsx", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            const classes = cn([
              "flex bg-red-500 p-4 text-center font-bold",
              active && {
                "flex p-4 font-bold": compact,
              },
            ]);
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesJsxClassNameExpressionTemplateLiteral() {
        val text = """const view = <div className={`text-center p-4 flex bg-red-500 font-bold`} />;"""
        val file = myFixture.configureByText("test.jsx", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """const view = <div className={`flex bg-red-500 p-4 text-center font-bold`} />;""",
            result,
        )
    }

    fun testProcessesVueLikeArrayBindingQuotedFragments() {
        val text =
            """
            <div :class="['text-center p-4 flex bg-red-500 font-bold', isActive && `font-bold flex p-4`]"></div>
            """.trimIndent()
        val file = myFixture.configureByText("test.html", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <div :class="['flex bg-red-500 p-4 text-center font-bold', isActive && `flex p-4 font-bold`]"></div>
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesRealVueClassAttribute() {
        val text =
            """
            <template>
              <div class="text-center p-4 flex bg-red-500 font-bold"></div>
            </template>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <template>
              <div class="flex bg-red-500 p-4 text-center font-bold"></div>
            </template>
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesRealVueDynamicClassBinding() {
        val text =
            """
            <template>
              <div :class="['text-center p-4 flex bg-red-500 font-bold', isActive && `font-bold flex p-4`]"></div>
            </template>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <template>
              <div :class="['flex bg-red-500 p-4 text-center font-bold', isActive && `flex p-4 font-bold`]"></div>
            </template>
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesRealVueVBindClassDirective() {
        val text =
            """
            <template>
              <div v-bind:class="'text-center p-4 flex bg-red-500 font-bold'"></div>
            </template>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <template>
              <div v-bind:class="'flex bg-red-500 p-4 text-center font-bold'"></div>
            </template>
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesRealVueObjectClassBindingQuotedKeys() {
        val text =
            """
            <template>
              <div :class="{ 'text-center p-4 flex bg-red-500 font-bold': isActive }"></div>
            </template>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <template>
              <div :class="{ 'flex bg-red-500 p-4 text-center font-bold': isActive }"></div>
            </template>
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesRealVueMixedArrayAndObjectClassBinding() {
        val text =
            """
            <template>
              <div :class="[
                'text-center p-4 flex bg-red-500 font-bold',
                { 'font-bold flex p-4': isActive },
              ]"></div>
            </template>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <template>
              <div :class="[
                'flex bg-red-500 p-4 text-center font-bold',
                { 'flex p-4 font-bold': isActive },
              ]"></div>
            </template>
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesRealVueNestedClassBindingQuotedFragments() {
        val text =
            """
            <template>
              <div :class="[
                isActive ? ['text-center p-4 flex bg-red-500 font-bold'] : [],
                {
                  'font-bold flex p-4': compact,
                },
              ]"></div>
            </template>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <template>
              <div :class="[
                isActive ? ['flex bg-red-500 p-4 text-center font-bold'] : [],
                {
                  'flex p-4 font-bold': compact,
                },
              ]"></div>
            </template>
            """.trimIndent(),
            result,
        )
    }

    fun testPreservesRealVueClassBindingCommentsWhileSortingQuotedFragments() {
        val text =
            """
            <template>
              <div :class="[
                // 'text-center p-4 flex bg-red-500 font-bold'
                'font-bold flex p-4',
                /*
                 * `text-center p-4 flex bg-red-500 font-bold`
                 */
                isActive && 'text-center p-4 flex bg-red-500 font-bold',
              ]"></div>
            </template>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <template>
              <div :class="[
                // 'text-center p-4 flex bg-red-500 font-bold'
                'flex p-4 font-bold',
                /*
                 * `text-center p-4 flex bg-red-500 font-bold`
                 */
                isActive && 'flex bg-red-500 p-4 text-center font-bold',
              ]"></div>
            </template>
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesRealVueScriptSetupCustomFunctionCall() {
        val text =
            """
            <script setup lang="ts">
            const classes = cn("text-center p-4 flex bg-red-500 font-bold")
            </script>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <script setup lang="ts">
            const classes = cn("flex bg-red-500 p-4 text-center font-bold")
            </script>
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesRealVueScriptSetupTaggedTemplateHelper() {
        val text =
            """
            <script setup lang="ts">
            const classes = tw`text-center p-4 flex bg-red-500 font-bold`
            </script>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <script setup lang="ts">
            const classes = tw`flex bg-red-500 p-4 text-center font-bold`
            </script>
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesRealVueStyleApplyBlock() {
        val text =
            """
            <template>
              <button class="text-center p-4 flex bg-red-500 font-bold"></button>
            </template>

            <style scoped>
            .button {
              @apply text-center p-4 flex bg-red-500 font-bold;
            }
            </style>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <template>
              <button class="flex bg-red-500 p-4 text-center font-bold"></button>
            </template>

            <style scoped>
            .button {
              @apply flex bg-red-500 p-4 text-center font-bold;
            }
            </style>
            """.trimIndent(),
            result,
        )
    }

    fun testPreservesRealVueObjectBindingFormattingWhileSortingSiblingClassAttribute() {
        val text =
            """
            <template>
              <div
                class="relative flex gap-2"
                :class="[
                  {
                    'flex-col': direction === DIRECTIONS.VERTICAL,
                  }
                ]"
              />
            </template>
            """.trimIndent()
        val file = myFixture.configureByText("Component.vue", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            <template>
              <div
                class="flex relative gap-2"
                :class="[
                  {
                    'flex-col': direction === DIRECTIONS.VERTICAL,
                  }
                ]"
              />
            </template>
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesTsxClassNameTernaryExpression() {
        val text =
            """
            const view = <div className={active ? "text-center p-4 flex bg-red-500 font-bold" : "font-bold flex p-4"} />;
            """.trimIndent()
        val file = myFixture.configureByText("test.tsx", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            const view = <div className={active ? "flex bg-red-500 p-4 text-center font-bold" : "flex p-4 font-bold"} />;
            """.trimIndent(),
            result,
        )
    }

    fun testProcessesJsxClassNameArrayObjectBindingQuotedFragments() {
        val text =
            """
            const view = <div className={["text-center p-4 flex bg-red-500 font-bold", { "font-bold flex p-4": active }]} />;
            """.trimIndent()
        val file = myFixture.configureByText("test.jsx", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(
            """
            const view = <div className={["flex bg-red-500 p-4 text-center font-bold", { "flex p-4 font-bold": active }]} />;
            """.trimIndent(),
            result,
        )
    }

    fun testPsiSkipsStaticClassAttributeWithInterpolation() {
        val text = """<div class="{{ active ? 'text-center p-4 flex bg-red-500 font-bold' : '' }}"></div>"""
        val file = myFixture.configureByText("test.html", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(text, result)
    }

    fun testPsiSkipsNgClassExpressionWithPipe() {
        val text = """<div [ngClass]="'text-center p-4 flex bg-red-500 font-bold' | classMap"></div>"""
        val file = myFixture.configureByText("test.html", text)

        val result = processor().process(file, text, settings, useFallback = false)

        assertEquals(text, result)
    }

    private fun processor(): TrierPsiProcessor =
        TrierPsiProcessor(
            sortStrings = { values -> values.map(::sortClassesStub) },
            fallbackTextProcessor = TrierTextProcessor { values -> values.map(::sortClassesStub) },
        )

    private fun sortClassesStub(value: String): String {
        val order =
            listOf(
                "flex",
                "items-center",
                "gap-x-2",
                "bg-red-500",
                "px-2",
                "py-0.5",
                "p-4",
                "text-xs",
                "text-center",
                "font-semibold",
                "font-bold",
                "tracking-wide",
                "relative",
                "gap-2",
                "flex-col",
            )
        return value
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .sortedBy { token ->
                val idx = order.indexOf(token)
                if (idx >= 0) idx else order.size + token.hashCode()
            }.joinToString(" ")
    }
}
