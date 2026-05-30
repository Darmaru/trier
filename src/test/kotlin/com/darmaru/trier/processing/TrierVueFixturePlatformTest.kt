package com.darmaru.trier.processing

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TrierVueFixturePlatformTest : BasePlatformTestCase() {
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

    fun testVueFixturesThroughRealPsi() {
        listOf(
            "template-bindings",
            "comments",
            "script-setup",
            "style-apply",
            "malformed-noop",
            "advanced-malformed-noop",
        ).forEach { name ->
            SortingFixtureSupport.assertPsiFixture(
                fixture = myFixture,
                sortingFixture = SortingFixture("vue", name, "vue"),
                settings = settings,
                useFallback = false,
            )
        }
    }
}
