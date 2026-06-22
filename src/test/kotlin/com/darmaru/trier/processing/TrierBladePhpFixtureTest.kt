package com.darmaru.trier.processing

import org.junit.Test

class TrierBladePhpFixtureTest {
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
    fun `sorts supported blade fallback fixtures`() {
        listOf(
            "static-class",
            "component-attributes",
            "class-directive",
            "mixed-template",
            "real-smoke",
        ).forEach(::assertBladeFixture)
    }

    @Test
    fun `leaves unsupported blade and php fallback fixtures unchanged`() {
        listOf(
            "attributes-class-merge-noop",
            "class-directive-interpolation-noop",
            "escaped-component-attribute-noop",
            "escaped-class-directive-noop",
            "generic-php-array-noop",
            "malformed-class-directive-noop",
            "static-class-interpolation-noop",
        ).forEach(::assertBladeFixture)
    }

    @Test
    fun `keeps ignored blade and php ranges unchanged while sorting supported siblings`() {
        listOf(
            "class-directive-comments",
            "heredoc-noop",
            "html-comment-noop",
            "verbatim-and-comments-noop",
        ).forEach(::assertBladeFixture)
    }

    private fun assertBladeFixture(name: String) {
        SortingFixtureSupport.assertTextFixture(
            sortingFixture = SortingFixture("blade", name, "blade.php"),
            settings = settings,
        )
    }
}
