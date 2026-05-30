package com.darmaru.trier.processing

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import junit.framework.TestCase.assertEquals

internal data class SortingFixture(
    val area: String,
    val name: String,
    val extension: String,
) {
    val inputPath: String = "fixtures/sorting/$area/$name/input.$extension"
    val expectedPath: String = "fixtures/sorting/$area/$name/expected.$extension"
    val fileName: String = "$name.$extension"
}

internal object SortingFixtureSupport {
    fun assertPsiFixture(
        fixture: CodeInsightTestFixture,
        sortingFixture: SortingFixture,
        settings: TrierResolvedSettings,
        useFallback: Boolean = false,
    ) {
        val input = text(sortingFixture.inputPath)
        val expected = text(sortingFixture.expectedPath)
        val file = fixture.configureByText(sortingFixture.fileName, input)

        val result = processor().process(file, input, settings, useFallback = useFallback)

        assertEquals("Unexpected output for fixture '${sortingFixture.area}/${sortingFixture.name}'", expected, result)
    }

    private fun text(path: String): String =
        javaClass.classLoader
            .getResource(path)
            ?.readText()
            ?: error("Missing test fixture: $path")

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
                val index = order.indexOf(token)
                if (index >= 0) index else order.size + token.hashCode()
            }.joinToString(" ")
    }
}
