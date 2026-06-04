package com.darmaru.trier.processing

import org.junit.Test

class TrierSvelteFixtureTest {
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
    private val helperSettings = settings.copy(tailwindFunctions = listOf("cn"))

    @Test
    fun `sorts supported svelte fallback fixtures`() {
        listOf(
            "static-class" to settings,
            "class-expression" to settings,
            "static-template-literal" to settings,
            "class-array-object" to settings,
            "component-class-prop" to settings,
            "script-helper" to helperSettings,
            "script-helper-nested" to helperSettings,
            "script-helper-template-literal" to helperSettings,
            "sveltekit-component-props" to helperSettings,
            "style-apply" to settings,
        ).forEach { (name, fixtureSettings) ->
            assertSvelteFixture(name, fixtureSettings)
        }
    }

    @Test
    fun `leaves unsupported svelte fallback fixtures unchanged`() {
        listOf(
            "class-directive-noop",
            "malformed-class-expression-noop",
            "script-helper-interpolation-noop",
            "template-literal-interpolation-noop",
        ).forEach { name ->
            assertSvelteFixture(name, settings)
        }
    }

    private fun assertSvelteFixture(
        name: String,
        settings: TrierResolvedSettings,
    ) {
        SortingFixtureSupport.assertTextFixture(
            sortingFixture = SortingFixture("svelte", name, "svelte"),
            settings = settings,
        )
    }
}
