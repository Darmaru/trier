package com.darmaru.trier.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import javax.swing.tree.DefaultMutableTreeNode

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

    fun testBuildDryRunDiffTreeGroupsRelativePaths() {
        val report =
            FolderSortReport(
                dryRun = true,
                changes =
                    listOf(
                        FolderSortChange(
                            path = "/tmp/project/pages/home.html",
                            relativePath = "pages/home.html",
                            originalText = """<div class="p-4 flex"></div>""",
                            sortedText = """<div class="flex p-4"></div>""",
                        ),
                        FolderSortChange(
                            path = "/tmp/project/components/button.html",
                            relativePath = "components/button.html",
                            originalText = """<button class="p-2 flex"></button>""",
                            sortedText = """<button class="flex p-2"></button>""",
                        ),
                    ),
            )

        val root = buildDiffTreeRoot(buildDryRunDiffEntries(project, report))

        assertEquals("Changed files", root.userObject)
        assertEquals(2, root.childCount)
        val components = root.getChildAt(0) as DefaultMutableTreeNode
        val pages = root.getChildAt(1) as DefaultMutableTreeNode
        assertEquals("components", components.userObject.toString())
        assertEquals("button.html", (components.getChildAt(0) as DefaultMutableTreeNode).userObject.toString())
        assertEquals("pages", pages.userObject.toString())
        assertEquals("home.html", (pages.getChildAt(0) as DefaultMutableTreeNode).userObject.toString())
    }

    fun testBuildDryRunDiffChainSelectionKeepsAllEntriesAndStartsFromSelectedFile() {
        val entries =
            buildDryRunDiffEntries(
                project,
                FolderSortReport(
                    dryRun = true,
                    changes =
                        listOf(
                            FolderSortChange(
                                path = "/tmp/project/first.html",
                                relativePath = "first.html",
                                originalText = """<div class="p-4 flex"></div>""",
                                sortedText = """<div class="flex p-4"></div>""",
                            ),
                            FolderSortChange(
                                path = "/tmp/project/second.html",
                                relativePath = "second.html",
                                originalText = """<div class="p-2 flex"></div>""",
                                sortedText = """<div class="flex p-2"></div>""",
                            ),
                        ),
                ),
            )

        val selection = buildDryRunDiffChainSelection(entries, listOf(entries[1]))!!

        assertEquals(entries, selection.entries)
        assertEquals(1, selection.selectedIndex)
    }

    fun testBuildDryRunDiffChainSelectionStartsFromFirstFileWhenNothingIsSelected() {
        val entries =
            buildDryRunDiffEntries(
                project,
                FolderSortReport(
                    dryRun = true,
                    changes =
                        listOf(
                            FolderSortChange(
                                path = "/tmp/project/first.html",
                                relativePath = "first.html",
                                originalText = """<div class="p-4 flex"></div>""",
                                sortedText = """<div class="flex p-4"></div>""",
                            ),
                            FolderSortChange(
                                path = "/tmp/project/second.html",
                                relativePath = "second.html",
                                originalText = """<div class="p-2 flex"></div>""",
                                sortedText = """<div class="flex p-2"></div>""",
                            ),
                        ),
                ),
            )

        val selection = buildDryRunDiffChainSelection(entries, emptyList())!!

        assertEquals(entries, selection.entries)
        assertEquals(0, selection.selectedIndex)
    }

    fun testNextDryRunDiffIndexAfterRemovalStaysAtSamePositionWhenNextFileExists() {
        assertEquals(1, nextDryRunDiffIndexAfterRemoval(removedIndex = 1, remainingSize = 2))
    }

    fun testNextDryRunDiffIndexAfterRemovalMovesToPreviousPositionAfterLastFile() {
        assertEquals(1, nextDryRunDiffIndexAfterRemoval(removedIndex = 2, remainingSize = 2))
    }

    fun testNextDryRunDiffIndexAfterRemovalReturnsNullWhenNoFilesRemain() {
        assertNull(nextDryRunDiffIndexAfterRemoval(removedIndex = 0, remainingSize = 0))
    }

    fun testDryRunReviewStateRemovesEntryAndSelectsNextRemainingEntry() {
        val entries = buildEntries("first.html", "second.html", "third.html")
        val state = DryRunReviewState(entries)

        val result = state.removeEntry(entries[1], fallbackIndex = 1)

        assertTrue(result.removed)
        assertFalse(result.shouldClose)
        assertEquals(1, result.selectedIndex)
        assertEquals(listOf("first.html", "third.html"), state.allEntries.map(DryRunDiffEntry::relativePath))
    }

    fun testDryRunReviewStateClosesWhenLastEntryIsRemoved() {
        val entries = buildEntries("only.html")
        val state = DryRunReviewState(entries)

        val result = state.removeEntry(entries.single(), fallbackIndex = 0)

        assertTrue(result.removed)
        assertTrue(result.shouldClose)
        assertNull(result.selectedIndex)
        assertTrue(state.isEmpty)
    }

    fun testDryRunDiffWindowStateRemovesAppliedEntryAndAdvancesToNextEntry() {
        val entries = buildEntries("first.html", "second.html", "third.html")
        val state = DryRunDiffWindowState(entries, selectedIndex = 1)

        val advance = state.removeCurrentEntryAfterApply()!!

        assertEquals("second.html", advance.appliedEntry.relativePath)
        assertEquals(1, advance.nextIndex)
        assertEquals("third.html", state.snapshot().currentEntry!!.relativePath)
        assertEquals("Trier Dry Run: third.html (2/2)", state.snapshot().title)
    }

    fun testDryRunDiffWindowStateClosesWhenLastEntryIsApplied() {
        val entries = buildEntries("only.html")
        val state = DryRunDiffWindowState(entries, selectedIndex = 0)

        val advance = state.removeCurrentEntryAfterApply()!!

        assertEquals("only.html", advance.appliedEntry.relativePath)
        assertNull(advance.nextIndex)
        assertNull(state.snapshot().currentEntry)
        assertEquals("Trier Dry Run", state.snapshot().title)
    }

    fun testDryRunDiffWindowStateSyncsCurrentIndexToActiveRequest() {
        val entries = buildEntries("first.html", "second.html")
        val state = DryRunDiffWindowState(entries, selectedIndex = 0)

        assertTrue(state.syncToRequest(entries[1].request))

        assertEquals(1, state.snapshot().currentIndex)
        assertEquals("second.html", state.snapshot().currentEntry!!.relativePath)
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

    fun testApplyDryRunChangeWritesOpenDocument() {
        val file = Files.createTempFile("trier-dry-run-open-document", ".html")
        val originalText = """<div class="p-4 flex"></div>"""
        val sortedText = """<div class="flex p-4"></div>"""
        file.toFile().writeText(originalText)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)!!
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!

        val result =
            applyDryRunChange(
                project,
                FolderSortChange(
                    path = file.toString(),
                    relativePath = "component.html",
                    originalText = originalText,
                    sortedText = sortedText,
                ),
            )

        assertEquals(DryRunApplyResult.Applied, result)
        assertEquals(sortedText, document.text)
        assertEquals(sortedText, file.toFile().readText())
    }

    fun testApplyDryRunChangeSkipsReadOnlyFile() {
        val file = Files.createTempFile("trier-dry-run-read-only", ".html")
        val originalText = """<div class="p-4 flex"></div>"""
        file.toFile().writeText(originalText)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)!!

        ApplicationManager.getApplication().runWriteAction {
            virtualFile.isWritable = false
        }
        try {
            val result =
                applyDryRunChange(
                    project,
                    FolderSortChange(
                        path = file.toString(),
                        relativePath = "component.html",
                        originalText = originalText,
                        sortedText = """<div class="flex p-4"></div>""",
                    ),
                )

            assertEquals(DryRunApplyResult.ReadOnly, result)
            assertEquals(originalText, file.toFile().readText())
        } finally {
            ApplicationManager.getApplication().runWriteAction {
                virtualFile.isWritable = true
            }
        }
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

    fun testApplyDryRunChangesReturnsAppliedEntriesAndFailures() {
        val appliedFile = Files.createTempFile("trier-dry-run-batch-applied", ".html")
        val staleFile = Files.createTempFile("trier-dry-run-batch-stale", ".html")
        val originalText = """<div class="p-4 flex"></div>"""
        val sortedText = """<div class="flex p-4"></div>"""
        appliedFile.toFile().writeText(originalText)
        staleFile.toFile().writeText("""<div class="text-center flex"></div>""")
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(appliedFile)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(staleFile)
        val entries =
            buildDryRunDiffEntries(
                project,
                FolderSortReport(
                    dryRun = true,
                    changes =
                        listOf(
                            FolderSortChange(
                                path = appliedFile.toString(),
                                relativePath = "applied.html",
                                originalText = originalText,
                                sortedText = sortedText,
                            ),
                            FolderSortChange(
                                path = staleFile.toString(),
                                relativePath = "stale.html",
                                originalText = originalText,
                                sortedText = sortedText,
                            ),
                        ),
                ),
            )

        val result =
            applyDryRunChanges(
                project,
                entries,
                EmptyProgressIndicator(),
                ModalityState.defaultModalityState(),
            )

        assertEquals(listOf("applied.html"), result.appliedEntries.map(DryRunDiffEntry::relativePath))
        assertEquals(1, result.failures.size)
        assertEquals("stale.html", result.failures.single().relativePath)
        assertTrue(
            result.failures
                .single()
                .message
                .contains("The file changed after the dry run"),
        )
        assertEquals(sortedText, appliedFile.toFile().readText())
        assertEquals("""<div class="text-center flex"></div>""", staleFile.toFile().readText())
    }

    fun testBuildDryRunApplyFailureMessageShowsAppliedCountAndTruncatesFailures() {
        val failures =
            (1..6).map { index ->
                DryRunApplyFailure(
                    relativePath = "file-$index.html",
                    path = "/tmp/project/file-$index.html",
                    message = "Failure $index",
                )
            }

        val message = buildDryRunApplyFailureMessage(appliedCount = 2, failures = failures)

        assertTrue(message.contains("Applied 2 dry-run changes."))
        assertTrue(message.contains("Could not apply 6 dry-run changes:"))
        assertTrue(message.contains("- file-1.html: Failure 1"))
        assertTrue(message.contains("- file-5.html: Failure 5"))
        assertFalse(message.contains("- file-6.html: Failure 6"))
        assertTrue(message.contains("- ...and 1 more."))
    }

    private fun buildEntries(vararg relativePaths: String): List<DryRunDiffEntry> =
        buildDryRunDiffEntries(
            project,
            FolderSortReport(
                dryRun = true,
                changes =
                    relativePaths.map { relativePath ->
                        FolderSortChange(
                            path = "/tmp/project/$relativePath",
                            relativePath = relativePath,
                            originalText = """<div class="p-4 flex"></div>""",
                            sortedText = """<div class="flex p-4"></div>""",
                        )
                    },
            ),
        )
}
