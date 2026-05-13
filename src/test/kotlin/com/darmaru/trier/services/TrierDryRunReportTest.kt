package com.darmaru.trier.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

class TrierDryRunReportTest : BasePlatformTestCase() {
    fun testBuildDryRunReportTextIncludesEmptyChangesAndCancelledStatus() {
        val report =
            FolderSortReport(
                scanned = 3,
                matched = 2,
                changed = 0,
                unchanged = 2,
                skipped = 1,
                failed = 0,
                cancelled = true,
                dryRun = true,
            )

        val text = buildDryRunReportText("/tmp/project", report)

        assertTrue(text.contains("Root: /tmp/project"))
        assertTrue(text.contains("Status: Cancelled"))
        assertTrue(text.contains("Files that would be updated:\n  None"))
    }

    fun testBuildDryRunReportTextIncludesFailures() {
        val report =
            FolderSortReport(
                scanned = 2,
                matched = 2,
                changed = 1,
                failed = 1,
                dryRun = true,
                failures = listOf(FolderSortFailure("/tmp/project/broken.html", "Broken file")),
                changes = listOf(FolderSortChange("/tmp/project/changed.html", "changed.html")),
            )

        val text = buildDryRunReportText("/tmp/project", report)

        assertTrue(text.contains("  - changed.html"))
        assertTrue(text.contains("Failures:"))
        assertTrue(text.contains("  - /tmp/project/broken.html"))
        assertTrue(text.contains("    Broken file"))
    }

    fun testBuildDryRunDiffRequestsSkipsIncompleteChanges() {
        val report =
            FolderSortReport(
                dryRun = true,
                changes =
                    listOf(
                        FolderSortChange(
                            path = "/tmp/project/missing-original.html",
                            relativePath = "missing-original.html",
                            originalText = null,
                            sortedText = "<div></div>",
                        ),
                        FolderSortChange(
                            path = "/tmp/project/missing-sorted.html",
                            relativePath = "missing-sorted.html",
                            originalText = "<div></div>",
                            sortedText = null,
                        ),
                        FolderSortChange(
                            path = "/tmp/project/valid.html",
                            relativePath = "valid.html",
                            originalText = """<div class="p-4 flex"></div>""",
                            sortedText = """<div class="flex p-4"></div>""",
                        ),
                    ),
            )

        val requests = buildDryRunDiffRequests(project, report)

        assertEquals(1, requests.size)
        assertEquals("Trier Dry Run: valid.html", requests.single().getTitle())
        assertEquals(listOf("Original snapshot", "Sorted preview (not written)"), requests.single().contentTitles)
    }

    fun testBuildRemainingDryRunReportRemovesAppliedChangesFromReportText() {
        val first = FolderSortChange("/tmp/project/first.html", "first.html")
        val second = FolderSortChange("/tmp/project/second.html", "second.html")
        val report =
            FolderSortReport(
                scanned = 2,
                matched = 2,
                changed = 2,
                dryRun = true,
                changes = listOf(first, second),
            )

        val remainingReport = buildRemainingDryRunReport(report, listOf(second))
        val text = buildDryRunReportText("/tmp/project", remainingReport)

        assertEquals(1, remainingReport.changed)
        assertEquals(listOf(second), remainingReport.changes)
        assertFalse(text.contains("first.html"))
        assertTrue(text.contains("second.html"))
        assertTrue(text.contains("Would update: 1"))
    }

    fun testApplyDryRunChangeWritesFile() {
        val file = Files.createTempFile("trier-dry-run-apply", ".html")
        file.toFile().writeText("""<div class="p-4 flex"></div>""")

        val result =
            applyDryRunChange(
                project,
                FolderSortChange(
                    path = file.toString(),
                    relativePath = "component.html",
                    originalText = """<div class="p-4 flex"></div>""",
                    sortedText = """<div class="flex p-4"></div>""",
                ),
            )

        assertEquals(DryRunApplyResult.Applied, result)
        assertEquals("""<div class="flex p-4"></div>""", file.toFile().readText())
    }

    fun testApplyDryRunChangeSkipsChangedFile() {
        val file = Files.createTempFile("trier-dry-run-stale", ".html")
        file.toFile().writeText("""<div class="text-center flex"></div>""")

        val result =
            applyDryRunChange(
                project,
                FolderSortChange(
                    path = file.toString(),
                    relativePath = "component.html",
                    originalText = """<div class="p-4 flex"></div>""",
                    sortedText = """<div class="flex p-4"></div>""",
                ),
            )

        assertEquals(DryRunApplyResult.FileChanged, result)
        assertEquals("""<div class="text-center flex"></div>""", file.toFile().readText())
    }

    fun testApplyDryRunChangeSkipsIncompletePreview() {
        val result =
            applyDryRunChange(
                project,
                FolderSortChange(
                    path = "/tmp/project/component.html",
                    relativePath = "component.html",
                    originalText = null,
                    sortedText = """<div class="flex p-4"></div>""",
                ),
            )

        assertEquals(DryRunApplyResult.MissingPreview, result)
    }

    fun testApplyDryRunChangeSkipsMissingFile() {
        val file = Files.createTempFile("trier-dry-run-missing", ".html")
        Files.delete(file)

        val result =
            applyDryRunChange(
                project,
                FolderSortChange(
                    path = file.toString(),
                    relativePath = "component.html",
                    originalText = """<div class="p-4 flex"></div>""",
                    sortedText = """<div class="flex p-4"></div>""",
                ),
            )

        assertEquals(DryRunApplyResult.FileNotFound, result)
    }
}
