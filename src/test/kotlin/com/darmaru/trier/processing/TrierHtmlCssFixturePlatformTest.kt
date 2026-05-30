package com.darmaru.trier.processing

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TrierHtmlCssFixturePlatformTest : BasePlatformTestCase() {
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

    fun testHtmlFixturesThroughRealPsi() {
        listOf(
            "class-attribute" to settings,
            "custom-attribute" to settings.copy(tailwindAttributes = listOf("data-classes")),
            "malformed-class-noop" to settings,
        ).forEach { (name, fixtureSettings) ->
            SortingFixtureSupport.assertPsiFixture(
                fixture = myFixture,
                sortingFixture = SortingFixture("html", name, "html"),
                settings = fixtureSettings,
                useFallback = false,
            )
        }
    }

    fun testCssFixturesThroughRealPsi() {
        listOf(
            "apply-rule",
            "malformed-apply-noop",
        ).forEach { name ->
            SortingFixtureSupport.assertPsiFixture(
                fixture = myFixture,
                sortingFixture = SortingFixture("css", name, "css"),
                settings = settings,
                useFallback = true,
            )
        }
    }
}
