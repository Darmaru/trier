package com.darmaru.trier.processing

import org.junit.Test

class TrierAngularFixtureTest {
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
    fun `sorts supported angular fallback fixtures`() {
        listOf(
            "static-class" to "html",
            "class-binding" to "html",
            "inline-template" to "ts",
            "ngclass-string" to "html",
            "ngclass-expression" to "html",
            "ngclass-array-object" to "html",
            "ngclass-formatting" to "html",
            "real-smoke" to "html",
        ).forEach { (name, extension) -> assertAngularFixture(name, extension) }
    }

    @Test
    fun `leaves unsupported angular fallback fixtures unchanged`() {
        listOf(
            "attr-class-binding-noop",
            "class-binding-noop",
            "class-interpolation-noop",
            "ngclass-complex-expression-noop",
            "ngclass-method-call-noop",
            "ngclass-nested-ternary-pipe-noop",
            "ngclass-pipe-noop",
            "malformed-ngclass-noop",
        ).forEach(::assertAngularFixture)
    }

    private fun assertAngularFixture(
        name: String,
        extension: String = "html",
    ) {
        SortingFixtureSupport.assertTextFixture(
            sortingFixture = SortingFixture("angular", name, extension),
            settings = settings,
        )
    }
}
