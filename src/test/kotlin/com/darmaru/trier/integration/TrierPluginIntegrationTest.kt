package com.darmaru.trier.integration

import com.darmaru.trier.actions.SortTailwindInEditorAction
import com.darmaru.trier.listeners.TrierActionListener
import com.darmaru.trier.listeners.TrierSaveListener
import com.darmaru.trier.services.TrierSortService
import com.darmaru.trier.services.buildDryRunDiffRequests
import com.darmaru.trier.services.buildDryRunReportText
import com.darmaru.trier.settings.TrierSettingsState
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText

class TrierPluginIntegrationTest : BasePlatformTestCase() {
    private lateinit var initialSettings: TrierSettingsState.State

    override fun setUp() {
        super.setUp()
        initialSettings = TrierSettingsState.getInstance().snapshot()
        TrierSortService.setTestSortOverride { _, _, values, _ ->
            values.map(::sortClassesStub)
        }
    }

    override fun tearDown() {
        TrierSortService.setTestSortOverride(null)
        TrierSettingsState.getInstance().loadState(initialSettings)
        super.tearDown()
    }

    fun testManualActionSortsCurrentEditor() {
        myFixture.configureByText("test.html", """<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")

        SortTailwindInEditorAction().actionPerformed(testEvent())

        myFixture.checkResult("""<div class="flex bg-red-500 p-4 text-center font-bold"></div>""")
    }

    fun testSaveListenerSortsDocumentWhenEnabled() {
        TrierSettingsState.getInstance().update { it.sortOnSave = true }
        val file =
            myFixture.configureByText(
                "test.html",
                """<div class="text-center p-4 flex bg-red-500 font-bold"></div>""",
            )
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!

        TrierSaveListener().beforeDocumentSaving(document)

        assertEquals(
            """<div class="flex bg-red-500 p-4 text-center font-bold"></div>""",
            document.text,
        )
    }

    fun testReformatListenerSortsDocumentWhenEnabled() {
        TrierSettingsState.getInstance().update { it.sortOnReformat = true }
        myFixture.configureByText("test.html", """<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")
        val action = ActionManager.getInstance().getAction("ReformatCode")
        checkNotNull(action) { "ReformatCode action is not available in test runtime" }

        TrierActionListener().afterActionPerformed(action, testEvent(action), AnActionResult.PERFORMED)

        myFixture.checkResult("""<div class="flex bg-red-500 p-4 text-center font-bold"></div>""")
    }

    fun testSortFolderRespectsGlobPattern() {
        val root = createTempDirectory("trier-folder-test")
        val nested = (root / "nested").createDirectories()
        val htmlFile = nested / "component.html"
        val cssFile = nested / "styles.css"
        val ignoredFile = nested / "notes.txt"

        htmlFile.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")
        cssFile.writeText(""".button { @apply text-center p-4 flex bg-red-500 font-bold; }""")
        ignoredFile.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")

        val report = TrierSortService.getInstance().sortFolder(project, root.toString(), "**/*.{html,css}")

        assertEquals(
            """<div class="flex bg-red-500 p-4 text-center font-bold"></div>""",
            htmlFile.readText(),
        )
        assertEquals(
            """.button { @apply flex bg-red-500 p-4 text-center font-bold; }""",
            cssFile.readText(),
        )
        assertEquals(
            """<div class="text-center p-4 flex bg-red-500 font-bold"></div>""",
            ignoredFile.readText(),
        )
        assertEquals(3, report.scanned)
        assertEquals(2, report.matched)
        assertEquals(2, report.changed)
        assertEquals(2, report.updated)
        assertEquals(0, report.unchanged)
        assertEquals(1, report.skipped)
        assertEquals(0, report.failed)
    }

    fun testSortFolderDryRunReportsChangesWithoutWritingFiles() {
        val root = createTempDirectory("trier-folder-dry-run-test")
        val file = root / "component.html"
        val original = """<div class="text-center p-4 flex bg-red-500 font-bold"></div>"""
        file.writeText(original)

        val report = TrierSortService.getInstance().sortFolder(project, root.toString(), "*.html", dryRun = true)

        assertEquals(original, file.readText())
        assertEquals(1, report.scanned)
        assertEquals(1, report.matched)
        assertEquals(1, report.changed)
        assertEquals(0, report.updated)
        assertEquals(0, report.unchanged)
        assertEquals(0, report.failed)
        assertEquals(1, report.changes.size)
        assertEquals(file.toString(), report.changes.single().path)
        assertEquals("component.html", report.changes.single().relativePath)
        assertEquals(original, report.changes.single().originalText)
        assertEquals(
            """<div class="flex bg-red-500 p-4 text-center font-bold"></div>""",
            report.changes.single().sortedText,
        )
        assertTrue(report.dryRun)

        val reportText = buildDryRunReportText(root.toString(), report)
        assertTrue(reportText.contains("Would update: 1"))
        assertTrue(reportText.contains("Files that would be updated:"))
        assertTrue(reportText.contains("component.html"))

        val diffRequests = buildDryRunDiffRequests(project, report)
        assertEquals(1, diffRequests.size)
        assertEquals("Trier Dry Run: component.html", diffRequests.single().title)
    }

    fun testSortFileSortsSelectedFile() {
        val root = createTempDirectory("trier-file-test")
        val file = root / "component.html"
        file.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(file)!!

        val report = TrierSortService.getInstance().sortFile(project, virtualFile)

        assertEquals("""<div class="flex bg-red-500 p-4 text-center font-bold"></div>""", file.readText())
        assertEquals(1, report.scanned)
        assertEquals(1, report.matched)
        assertEquals(1, report.changed)
        assertEquals(1, report.updated)
        assertEquals(0, report.failed)
    }

    fun testSortFolderAggregatesPerFileFailures() {
        TrierSortService.setTestSortOverride { _, filePath, values, _ ->
            if (filePath?.endsWith("broken.html") == true) {
                error("Broken file")
            }
            values.map(::sortClassesStub)
        }
        val root = createTempDirectory("trier-folder-failure-test")
        val validFile = root / "valid.html"
        val brokenFile = root / "broken.html"
        validFile.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")
        brokenFile.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")

        val report = TrierSortService.getInstance().sortFolder(project, root.toString(), "*.html")

        assertEquals("""<div class="flex bg-red-500 p-4 text-center font-bold"></div>""", validFile.readText())
        assertEquals("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""", brokenFile.readText())
        assertEquals(2, report.scanned)
        assertEquals(2, report.matched)
        assertEquals(1, report.changed)
        assertEquals(1, report.updated)
        assertEquals(1, report.failed)
        assertEquals(brokenFile.toString(), report.failures.single().path)
        assertEquals("java.lang.IllegalStateException: Broken file", report.failures.single().message)
    }

    fun testManualActionSortsOnlySelection() {
        val text = """<div class="aaa text-center p-4 flex bg-red-500 font-bold bbb"></div>"""
        myFixture.configureByText("test.html", text)
        val selectedText = "text-center p-4 flex bg-red-500 font-bold"
        val selectionStart = text.indexOf(selectedText)
        check(selectionStart >= 0)
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionStart + selectedText.length)

        assertEquals(selectedText, myFixture.editor.selectionModel.selectedText)

        SortTailwindInEditorAction().actionPerformed(testEvent())

        assertEquals(
            """<div class="aaa flex bg-red-500 p-4 text-center font-bold bbb"></div>""",
            myFixture.editor.document.text,
        )
    }

    fun testServiceSortsOnlySelectionWhenRangeProvidedExplicitly() {
        val text = """<div class="aaa text-center p-4 flex bg-red-500 font-bold bbb"></div>"""
        myFixture.configureByText("test.html", text)
        val selectedText = "text-center p-4 flex bg-red-500 font-bold"
        val selectionStart = text.indexOf(selectedText)
        check(selectionStart >= 0)

        TrierSortService.getInstance().sortCurrentEditor(
            project,
            myFixture.editor,
            "Manual",
            com.intellij.openapi.util
                .TextRange(selectionStart, selectionStart + selectedText.length),
        )

        assertEquals(
            """<div class="aaa flex bg-red-500 p-4 text-center font-bold bbb"></div>""",
            myFixture.editor.document.text,
        )
    }

    private fun testEvent(action: com.intellij.openapi.actionSystem.AnAction = SortTailwindInEditorAction()) =
        TestActionEvent.createTestEvent(action, dataContext())

    private fun dataContext(): DataContext =
        com.intellij.openapi.actionSystem.impl.SimpleDataContext
            .builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .build()

    private fun sortClassesStub(value: String): String {
        val order = listOf("flex", "bg-red-500", "p-4", "text-center", "font-bold")
        return value
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .sortedBy { token ->
                val idx = order.indexOf(token)
                if (idx >= 0) idx else order.size + token.hashCode()
            }.joinToString(" ")
    }
}
