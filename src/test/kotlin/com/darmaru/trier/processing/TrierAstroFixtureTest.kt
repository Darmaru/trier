package com.darmaru.trier.processing

import org.junit.Test

class TrierAstroFixtureTest {
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
    fun `sorts supported astro fallback fixtures`() {
        listOf(
            "static-class" to settings,
            "class-expression" to settings,
            "frontmatter-helper" to helperSettings,
            "style-apply" to settings,
        ).forEach { (name, fixtureSettings) ->
            assertAstroFixture(name, fixtureSettings)
        }
    }

    @Test
    fun `leaves unsupported astro fallback fixtures unchanged`() {
        listOf(
            "class-list-noop",
            "template-literal-interpolation-noop",
        ).forEach { name ->
            assertAstroFixture(name, settings)
        }
    }

    private fun assertAstroFixture(
        name: String,
        settings: TrierResolvedSettings,
    ) {
        SortingFixtureSupport.assertTextFixture(
            sortingFixture = SortingFixture("astro", name, "astro"),
            settings = settings,
        )
    }
}
