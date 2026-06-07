package com.darmaru.trier.processing

import org.junit.Test

class TrierJavaScriptFallbackFixtureTest {
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

    @Test
    fun `sorts supported javascript fallback fixtures`() {
        listOf(
            "helper-call",
            "helper-template-literal",
            "tagged-template",
        ).forEach(::assertJavaScriptFixture)
    }

    @Test
    fun `leaves unsupported javascript fallback fixtures unchanged`() {
        listOf(
            "helper-template-interpolation-noop",
            "tagged-template-interpolation-noop",
        ).forEach(::assertJavaScriptFixture)
    }

    private fun assertJavaScriptFixture(name: String) {
        SortingFixtureSupport.assertTextFixture(
            sortingFixture = SortingFixture("js", name, "js"),
            settings = settings,
        )
    }
}
