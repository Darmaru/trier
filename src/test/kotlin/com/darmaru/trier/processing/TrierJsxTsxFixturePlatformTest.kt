package com.darmaru.trier.processing

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TrierJsxTsxFixturePlatformTest : BasePlatformTestCase() {
    private val settings =
        TrierResolvedSettings(
            nodeInterpreterRef = null,
            sortOnSave = false,
            sortOnReformat = false,
            tailwindStylesheet = null,
            tailwindConfig = null,
            tailwindAttributes = emptyList(),
            tailwindFunctions = listOf("cn"),
            tailwindPreserveWhitespace = false,
            tailwindPreserveDuplicates = false,
        )

    fun testJsxFixturesThroughRealPsi() {
        listOf(
            "classname-string",
            "classname-template",
            "classname-array-object",
        ).forEach { name ->
            SortingFixtureSupport.assertPsiFixture(
                fixture = myFixture,
                sortingFixture = SortingFixture("jsx", name, "jsx"),
                settings = settings,
                useFallback = false,
            )
        }
    }

    fun testTsxFixturesThroughRealPsi() {
        listOf(
            "classname-ternary",
            "helper-multiline",
            "helper-nested",
        ).forEach { name ->
            SortingFixtureSupport.assertPsiFixture(
                fixture = myFixture,
                sortingFixture = SortingFixture("tsx", name, "tsx"),
                settings = settings,
                useFallback = false,
            )
        }
    }
}
