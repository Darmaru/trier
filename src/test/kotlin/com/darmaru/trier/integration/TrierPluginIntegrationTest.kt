package com.darmaru.trier.integration

import com.darmaru.trier.actions.SortTailwindInEditorAction
import com.darmaru.trier.actions.SortTailwindInFileAction
import com.darmaru.trier.actions.SortTailwindInFolderAction
import com.darmaru.trier.listeners.TrierActionListener
import com.darmaru.trier.listeners.TrierSaveListener
import com.darmaru.trier.services.TrierExecutionGuard
import com.darmaru.trier.services.TrierSortService
import com.darmaru.trier.services.buildDryRunDiffRequests
import com.darmaru.trier.services.buildDryRunReportText
import com.darmaru.trier.settings.TrierSettingsState
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
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
        TrierSortService.forceBackgroundDocumentSortForTest = false
        TrierSettingsState.getInstance().loadState(initialSettings)
        super.tearDown()
    }

    fun testManualActionSortsCurrentEditor() {
        myFixture.configureByText("test.html", """<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")

        SortTailwindInEditorAction().actionPerformed(testEvent())

        myFixture.checkResult("""<div class="flex bg-red-500 p-4 text-center font-bold"></div>""")
    }

    fun testManualActionBackgroundPathAppliesResult() {
        val original = """<div class="text-center p-4 flex bg-red-500 font-bold"></div>"""
        val sorted = """<div class="flex bg-red-500 p-4 text-center font-bold"></div>"""
        myFixture.configureByText("test.html", original)
        TrierSortService.forceBackgroundDocumentSortForTest = true

        SortTailwindInEditorAction().actionPerformed(testEvent())

        PlatformTestUtil.waitWithEventsDispatching(
            "background Trier sort result was not applied",
            { myFixture.editor.document.text == sorted },
            5000,
        )
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

    fun testSaveListenerDoesNothingWhenDisabled() {
        TrierSettingsState.getInstance().update { it.sortOnSave = false }
        val file =
            myFixture.configureByText(
                "test.html",
                """<div class="text-center p-4 flex bg-red-500 font-bold"></div>""",
            )
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!

        TrierSaveListener().beforeDocumentSaving(document)

        assertEquals(
            """<div class="text-center p-4 flex bg-red-500 font-bold"></div>""",
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

    fun testReformatListenerDoesNothingWhenDisabled() {
        TrierSettingsState.getInstance().update { it.sortOnReformat = false }
        myFixture.configureByText("test.html", """<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")
        val action = ActionManager.getInstance().getAction("ReformatCode")
        checkNotNull(action) { "ReformatCode action is not available in test runtime" }

        TrierActionListener().afterActionPerformed(action, testEvent(action), AnActionResult.PERFORMED)

        myFixture.checkResult("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")
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

    fun testSortFolderBlankGlobMatchesAllFiles() {
        val root = createTempDirectory("trier-folder-blank-glob-test")
        val nested = (root / "nested").createDirectories()
        val file = nested / "component.html"
        file.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")

        val report = TrierSortService.getInstance().sortFolder(project, root.toString(), "")

        assertEquals("""<div class="flex bg-red-500 p-4 text-center font-bold"></div>""", file.readText())
        assertEquals(1, report.scanned)
        assertEquals(1, report.matched)
        assertEquals(1, report.updated)
    }

    fun testSortFolderSkipsExcludedDirectoriesBinaryFilesAndLargeFiles() {
        val root = createTempDirectory("trier-folder-skip-safety-test")
        val validFile = root / "component.html"
        val nodeModules = (root / "node_modules").createDirectories()
        val excludedFile = nodeModules / "component.html"
        val binaryFile = root / "binary.html"
        val largeFile = root / "large.html"
        val original = """<div class="text-center p-4 flex bg-red-500 font-bold"></div>"""
        val binaryContent = byteArrayOf('<'.code.toByte(), 0, '>'.code.toByte())
        val largeContent = original + " ".repeat(2 * 1024 * 1024 + 1)

        validFile.writeText(original)
        excludedFile.writeText(original)
        binaryFile.writeBytes(binaryContent)
        largeFile.writeText(largeContent)

        val report = TrierSortService.getInstance().sortFolder(project, root.toString(), "*.html")

        assertEquals("""<div class="flex bg-red-500 p-4 text-center font-bold"></div>""", validFile.readText())
        assertEquals(original, excludedFile.readText())
        assertEquals(largeContent, largeFile.readText())
        assertTrue(binaryContent.contentEquals(binaryFile.readBytes()))
        assertEquals(3, report.scanned)
        assertEquals(3, report.matched)
        assertEquals(1, report.changed)
        assertEquals(1, report.updated)
        assertEquals(3, report.skipped)
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
        assertEquals("Trier Dry Run: component.html", diffRequests.single().getTitle())
    }

    fun testSortFolderDryRunBuildsDiffChainForMultipleChanges() {
        val root = createTempDirectory("trier-folder-dry-run-multi-test")
        val first = root / "first.html"
        val second = root / "second.html"
        val original = """<div class="text-center p-4 flex bg-red-500 font-bold"></div>"""
        first.writeText(original)
        second.writeText(original)

        val report = TrierSortService.getInstance().sortFolder(project, root.toString(), "*.html", dryRun = true)
        val diffRequests = buildDryRunDiffRequests(project, report)

        assertEquals(original, first.readText())
        assertEquals(original, second.readText())
        assertEquals(2, report.changed)
        assertEquals(0, report.updated)
        assertEquals(2, diffRequests.size)
        assertEquals(
            listOf("first.html", "second.html"),
            report.changes.map { it.relativePath }.sorted(),
        )
    }

    fun testSortFolderReportsInvalidGlobPattern() {
        val root = createTempDirectory("trier-folder-invalid-glob-test")
        val report = TrierSortService.getInstance().sortFolder(project, root.toString(), "[", dryRun = true)

        assertEquals(1, report.failed)
        assertTrue(report.dryRun)
        assertEquals(root.toString(), report.failures.single().path)
        assertTrue(
            report.failures
                .single()
                .message
                .startsWith("Invalid glob pattern:"),
        )
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

    fun testSortFileDryRunReportsChangeWithoutWritingFile() {
        val root = createTempDirectory("trier-file-dry-run-test")
        val file = root / "component.html"
        val original = """<div class="text-center p-4 flex bg-red-500 font-bold"></div>"""
        file.writeText(original)
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(file)!!

        val report = TrierSortService.getInstance().sortFile(project, virtualFile, dryRun = true)

        assertEquals(original, file.readText())
        assertEquals(1, report.scanned)
        assertEquals(1, report.matched)
        assertEquals(1, report.changed)
        assertEquals(0, report.updated)
        assertEquals(original, report.changes.single().originalText)
        assertEquals(
            """<div class="flex bg-red-500 p-4 text-center font-bold"></div>""",
            report.changes.single().sortedText,
        )
    }

    fun testSortFileSkipsDirectory() {
        val root = createTempDirectory("trier-file-directory-test")
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(root)!!

        val report = TrierSortService.getInstance().sortFile(project, virtualFile)

        assertEquals(1, report.scanned)
        assertEquals(1, report.skipped)
        assertEquals(0, report.changed)
        assertEquals(0, report.updated)
    }

    fun testProjectViewFileActionIsEnabledOnlyForSelectedFile() {
        val root = createTempDirectory("trier-project-view-file-action-test")
        val file = root / "component.html"
        file.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(file)!!
        val action = SortTailwindInFileAction()

        val event = projectViewEvent(virtualFile)
        action.update(event)

        assertTrue(event.presentation.isVisible)
        assertTrue(event.presentation.isEnabled)
    }

    fun testProjectViewFolderActionIsEnabledOnlyForSelectedDirectory() {
        val root = createTempDirectory("trier-project-view-folder-action-test")
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(root)!!
        val action = SortTailwindInFolderAction()

        val event = projectViewEvent(virtualFile)
        action.update(event)

        assertTrue(event.presentation.isVisible)
        assertTrue(event.presentation.isEnabled)
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

    fun testSortPassesResolvedTailwindSettingsToSorter() {
        val tempDir = createTempDirectory("trier-resolved-settings-test")
        val stylesheet = tempDir / "styles.css"
        val config = tempDir / "tailwind.config.ts"
        stylesheet.writeText("/* test */")
        config.writeText("export default {}")
        TrierSettingsState.getInstance().update {
            it.tailwindStylesheet = stylesheet.toString()
            it.tailwindConfig = config.toString()
            it.tailwindPreserveWhitespace = true
            it.tailwindPreserveDuplicates = true
            it.tailwindAttributes = "data-classes"
            it.tailwindFunctions = "cn"
        }
        val file =
            myFixture.configureByText(
                "test.html",
                """<div data-classes="text-center p-4 flex bg-red-500 font-bold"></div>""",
            )
        val captured = mutableListOf<com.darmaru.trier.processing.TrierResolvedSettings>()
        TrierSortService.setTestSortOverride { _, filePath, values, settings ->
            assertEquals(file.virtualFile.path, filePath)
            captured += settings
            values.map(::sortClassesStub)
        }

        TrierSortService.getInstance().sortCurrentEditor(project, myFixture.editor, "Manual")

        assertEquals(
            """<div data-classes="flex bg-red-500 p-4 text-center font-bold"></div>""",
            myFixture.editor.document.text,
        )
        val settings = captured.single()
        assertEquals(stylesheet.toString(), settings.tailwindStylesheet)
        assertEquals(config.toString(), settings.tailwindConfig)
        assertTrue(settings.tailwindPreserveWhitespace)
        assertTrue(settings.tailwindPreserveDuplicates)
        assertEquals(listOf("data-classes"), settings.tailwindAttributes)
        assertEquals(listOf("cn"), settings.tailwindFunctions)
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

    fun testExecutionGuardPreventsNestedProcessingForSameDocumentAndReleasesAfterward() {
        myFixture.configureByText("test.html", """<div></div>""")
        val document = myFixture.editor.document
        val guard = ApplicationManager.getApplication().getService(TrierExecutionGuard::class.java)

        val first =
            guard.guard(document) {
                val nested = guard.guard(document) { "nested" }
                assertNull(nested)
                "outer"
            }
        val after = guard.guard(document) { "after" }

        assertEquals("outer", first)
        assertEquals("after", after)
    }

    private fun testEvent(action: com.intellij.openapi.actionSystem.AnAction = SortTailwindInEditorAction()) =
        TestActionEvent.createTestEvent(action, dataContext())

    private fun projectViewEvent(file: VirtualFile): AnActionEvent =
        AnActionEvent.createEvent(
            com.intellij.openapi.actionSystem.impl.SimpleDataContext
                .builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.VIRTUAL_FILE, file)
                .build(),
            Presentation(),
            ActionPlaces.PROJECT_VIEW_POPUP,
            ActionUiKind.POPUP,
            null,
        )

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
