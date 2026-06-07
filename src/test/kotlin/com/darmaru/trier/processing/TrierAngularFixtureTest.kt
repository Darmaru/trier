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
            "static-class",
            "ngclass-string",
            "ngclass-expression",
            "ngclass-array-object",
        ).forEach(::assertAngularFixture)
    }

    @Test
    fun `leaves unsupported angular fallback fixtures unchanged`() {
        listOf(
            "class-binding-noop",
            "malformed-ngclass-noop",
        ).forEach(::assertAngularFixture)
    }

    private fun assertAngularFixture(name: String) {
        SortingFixtureSupport.assertTextFixture(
            sortingFixture = SortingFixture("angular", name, "html"),
            settings = settings,
        )
    }
}
