package com.darmaru.trier.services

import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
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

    fun testAttachedDiffApplyActionAppliesDryRunEntry() {
        val file = Files.createTempFile("trier-dry-run-diff-action", ".html")
        val originalText = """<div class="p-4 flex"></div>"""
        val sortedText = """<div class="flex p-4"></div>"""
        file.toFile().writeText(originalText)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
        val entry =
            buildDryRunDiffEntries(
                project,
                FolderSortReport(
                    dryRun = true,
                    changes =
                        listOf(
                            FolderSortChange(
                                path = file.toString(),
                                relativePath = "component.html",
                                originalText = originalText,
                                sortedText = sortedText,
                            ),
                        ),
                ),
            ).single()
        var applied = false
        entry.attachApplyAction(project) {
            applied = true
        }
        val action = entry.request.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS)!!.single()

        action.actionPerformed(
            AnActionEvent.createEvent(
                SimpleDataContext.EMPTY_CONTEXT,
                Presentation(),
                ActionPlaces.UNKNOWN,
                ActionUiKind.TOOLBAR,
                null,
            ),
        )

        assertTrue(applied)
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
}
