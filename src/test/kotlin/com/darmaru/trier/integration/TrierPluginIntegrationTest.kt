package com.darmaru.trier.integration

import com.darmaru.trier.actions.SortTailwindInEditorAction
import com.darmaru.trier.actions.SortTailwindInProjectViewAction
import com.darmaru.trier.listeners.TrierActionListener
import com.darmaru.trier.listeners.TrierSaveListener
import com.darmaru.trier.services.TrierExecutionGuard
import com.darmaru.trier.services.TrierSortService
import com.darmaru.trier.services.TrierTailwindPathDetector
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
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
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
        TrierTailwindPathDetector.clearCachesForTests()
        TrierSortService.setTestSortOverride { _, _, values, _ ->
            values.map(::sortClassesStub)
        }
    }

    override fun tearDown() {
        TrierSortService.setTestSortOverride(null)
        TrierSortService.forceBackgroundDocumentSortForTest = false
        TrierTailwindPathDetector.clearCachesForTests()
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

    fun testDocumentBackgroundTaskReleasesGuardWhenNoChangesAreProduced() {
        myFixture.configureByText("test.html", """<div></div>""")
        val document = myFixture.editor.document
        val guard = ApplicationManager.getApplication().getService(TrierExecutionGuard::class.java)
        TrierSortService.setTestSortOverride { _, _, values, _ -> values }

        assertTrue(guard.tryEnter(document))
        TrierSortService
            .getInstance()
            .createSortDocumentTask(
                project = project,
                document = document,
                trigger = "Manual",
                selectionRange = null,
                commitPsi = true,
                useCommand = true,
                saveAfterApply = false,
            ).run(EmptyProgressIndicator())

        assertEquals("released", guard.guard(document) { "released" })
    }

    fun testDocumentBackgroundTaskReleasesGuardWhenSelectionSortFails() {
        val text = "text-center p-4 flex"
        myFixture.configureByText("test.html", text)
        val document = myFixture.editor.document
        val guard = ApplicationManager.getApplication().getService(TrierExecutionGuard::class.java)
        TrierSortService.setTestSortOverride { _, _, _, _ -> error("Sort failed") }

        assertTrue(guard.tryEnter(document))
        TrierSortService
            .getInstance()
            .createSortDocumentTask(
                project = project,
                document = document,
                trigger = "Manual",
                selectionRange = TextRange(0, text.length),
                commitPsi = true,
                useCommand = true,
                saveAfterApply = false,
            ).run(EmptyProgressIndicator())

        assertEquals("released", guard.guard(document) { "released" })
        assertEquals(text, document.text)
    }

    fun testDocumentBackgroundTaskReleasesGuardWhenCancelled() {
        val text = "text-center p-4 flex"
        myFixture.configureByText("test.html", text)
        val document = myFixture.editor.document
        val guard = ApplicationManager.getApplication().getService(TrierExecutionGuard::class.java)
        TrierSortService.setTestSortOverride { _, _, _, _ -> throw ProcessCanceledException() }

        assertTrue(guard.tryEnter(document))
        try {
            TrierSortService
                .getInstance()
                .createSortDocumentTask(
                    project = project,
                    document = document,
                    trigger = "Manual",
                    selectionRange = TextRange(0, text.length),
                    commitPsi = true,
                    useCommand = true,
                    saveAfterApply = false,
                ).run(EmptyProgressIndicator())
            fail("Expected ProcessCanceledException")
        } catch (_: ProcessCanceledException) {
            assertEquals("released", guard.guard(document) { "released" })
        }
    }

    fun testDocumentBackgroundTaskCancelCallbacksReleaseGuard() {
        myFixture.configureByText("test.html", """<div></div>""")
        val document = myFixture.editor.document
        val guard = ApplicationManager.getApplication().getService(TrierExecutionGuard::class.java)
        val task =
            TrierSortService
                .getInstance()
                .createSortDocumentTask(
                    project = project,
                    document = document,
                    trigger = "Manual",
                    selectionRange = null,
                    commitPsi = true,
                    useCommand = true,
                    saveAfterApply = false,
                )

        assertTrue(guard.tryEnter(document))
        task.onCancel()
        assertEquals("released after cancel", guard.guard(document) { "released after cancel" })

        assertTrue(guard.tryEnter(document))
        task.onThrowable(RuntimeException("failed"))
        assertEquals("released after throwable", guard.guard(document) { "released after throwable" })
    }

    fun testReformatBackgroundTaskRetriesWhenDocumentChangesBeforeApply() {
        val original = """<div class="text-center p-4 flex"></div>"""
        val changed = """<div class="font-bold text-center p-4 flex"></div>"""
        val sortedChanged = """<div class="flex p-4 text-center font-bold"></div>"""
        myFixture.configureByText("test.html", original)
        val document = myFixture.editor.document
        val guard = ApplicationManager.getApplication().getService(TrierExecutionGuard::class.java)

        assertTrue(guard.tryEnter(document))
        TrierSortService
            .getInstance()
            .createSortDocumentTask(
                project = project,
                document = document,
                trigger = "Reformat",
                original = original,
                originalModificationStamp = document.modificationStamp,
                selectionRange = null,
                commitPsi = true,
                useCommand = true,
                saveAfterApply = false,
                retryOnChangedDocument = true,
            ).run(EmptyProgressIndicator())

        com.intellij.openapi.command.WriteCommandAction
            .writeCommandAction(project)
            .run<RuntimeException> { document.setText(changed) }

        PlatformTestUtil.waitWithEventsDispatching(
            "Trier reformat retry did not apply the latest document content",
            { document.text == sortedChanged },
            5000,
        )
        assertEquals("released after retry", guard.guard(document) { "released after retry" })
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

        PlatformTestUtil.waitWithEventsDispatching(
            "Trier reformat sort did not run",
            { myFixture.editor.document.text == """<div class="flex bg-red-500 p-4 text-center font-bold"></div>""" },
            5000,
        )
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

    fun testSortFolderDoubleStarGlobMatchesFileAtSelectedRoot() {
        val root = createTempDirectory("trier-folder-selected-root-glob-test")
        val nested = (root / "nested").createDirectories()
        val file = nested / "component.html"
        file.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")

        val report = TrierSortService.getInstance().sortFolder(project, nested.toString(), "**/*.{html,css}")

        assertEquals("""<div class="flex bg-red-500 p-4 text-center font-bold"></div>""", file.readText())
        assertEquals(1, report.scanned)
        assertEquals(1, report.matched)
        assertEquals(1, report.changed)
        assertEquals(1, report.updated)
        assertEquals(0, report.skipped)
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

    fun testSortFolderDryRunCoversFrameworkExtensionGlobsSafely() {
        val root = createTempDirectory("trier-folder-framework-extension-dry-run-test")
        val vueFile = root / "Component.vue"
        val svelteFile = root / "Component.svelte"
        val astroFile = root / "Component.astro"
        val phpFile = root / "component.php"
        val vueOriginal =
            """
            <template>
              <div v-bind:class="'text-center p-4 flex bg-red-500 font-bold'"></div>
            </template>
            """.trimIndent()
        val svelteOriginal = """<button class:active={isActive}></button>"""
        val astroOriginal = """<div class:list={["text-center p-4 flex bg-red-500 font-bold"]}></div>"""
        val phpOriginal = """<div @class(['text-center p-4 flex bg-red-500 font-bold' => active])></div>"""
        vueFile.writeText(vueOriginal)
        svelteFile.writeText(svelteOriginal)
        astroFile.writeText(astroOriginal)
        phpFile.writeText(phpOriginal)

        val report =
            TrierSortService
                .getInstance()
                .sortFolder(project, root.toString(), "**/*.{vue,svelte,astro,php}", dryRun = true)

        assertEquals(vueOriginal, vueFile.readText())
        assertEquals(svelteOriginal, svelteFile.readText())
        assertEquals(astroOriginal, astroFile.readText())
        assertEquals(phpOriginal, phpFile.readText())
        assertEquals(4, report.scanned)
        assertEquals(4, report.matched)
        assertEquals(3, report.changed)
        assertEquals(1, report.unchanged)
        assertEquals(0, report.updated)
        assertEquals(0, report.failed)
        val vueChange = report.changes.single { it.relativePath == "Component.vue" }
        val astroChange = report.changes.single { it.relativePath == "Component.astro" }
        val phpChange = report.changes.single { it.relativePath == "component.php" }
        assertEquals(vueOriginal, vueChange.originalText)
        assertEquals(
            """
            <template>
              <div v-bind:class="'flex bg-red-500 p-4 text-center font-bold'"></div>
            </template>
            """.trimIndent(),
            vueChange.sortedText,
        )
        assertEquals(astroOriginal, astroChange.originalText)
        assertEquals(
            """<div class:list={["flex bg-red-500 p-4 text-center font-bold"]}></div>""",
            astroChange.sortedText,
        )
        assertEquals(phpOriginal, phpChange.originalText)
        assertEquals(
            """<div @class(['flex bg-red-500 p-4 text-center font-bold' => active])></div>""",
            phpChange.sortedText,
        )
    }

    fun testSortFolderDryRunReportsSupportedSvelteChanges() {
        val root = createTempDirectory("trier-folder-svelte-dry-run-test")
        val file = root / "Component.svelte"
        val original =
            """
            <button class={[
              "text-center p-4 flex bg-red-500 font-bold",
              active && "font-bold flex p-4",
              { "tracking-wide text-xs px-2 py-0.5": compact },
            ]}>
              Save
            </button>
            """.trimIndent()
        file.writeText(original)

        val report = TrierSortService.getInstance().sortFolder(project, root.toString(), "**/*.svelte", dryRun = true)

        assertEquals(original, file.readText())
        assertEquals(1, report.scanned)
        assertEquals(1, report.matched)
        assertEquals(1, report.changed)
        assertEquals(0, report.updated)
        assertEquals(0, report.failed)
        assertEquals("Component.svelte", report.changes.single().relativePath)
        assertEquals(
            """
            <button class={[
              "flex bg-red-500 p-4 text-center font-bold",
              active && "flex p-4 font-bold",
              { "px-2 py-0.5 text-xs tracking-wide": compact },
            ]}>
              Save
            </button>
            """.trimIndent(),
            report.changes.single().sortedText,
        )
    }

    fun testSortFolderDryRunReportsSupportedAstroChanges() {
        val root = createTempDirectory("trier-folder-astro-dry-run-test")
        val file = root / "Component.astro"
        val original =
            """
            <button class={active ? "text-center p-4 flex bg-red-500 font-bold" : "font-bold flex p-4"}>
              Save
            </button>
            <Card className={"text-center p-4 flex bg-red-500 font-bold"} />
            """.trimIndent()
        file.writeText(original)

        val report = TrierSortService.getInstance().sortFolder(project, root.toString(), "**/*.astro", dryRun = true)

        assertEquals(original, file.readText())
        assertEquals(1, report.scanned)
        assertEquals(1, report.matched)
        assertEquals(1, report.changed)
        assertEquals(0, report.updated)
        assertEquals(0, report.failed)
        assertEquals("Component.astro", report.changes.single().relativePath)
        assertEquals(
            """
            <button class={active ? "flex bg-red-500 p-4 text-center font-bold" : "flex p-4 font-bold"}>
              Save
            </button>
            <Card className={"flex bg-red-500 p-4 text-center font-bold"} />
            """.trimIndent(),
            report.changes.single().sortedText,
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

    fun testSortFileAppliesSupportedSvelteChanges() {
        val root = createTempDirectory("trier-file-svelte-apply-test")
        val file = root / "Component.svelte"
        file.writeText(
            """<button class={active ? "text-center p-4 flex bg-red-500 font-bold" : "font-bold flex p-4"}></button>""",
        )
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(file)!!

        val report = TrierSortService.getInstance().sortFile(project, virtualFile)

        assertEquals(
            """<button class={active ? "flex bg-red-500 p-4 text-center font-bold" : "flex p-4 font-bold"}></button>""",
            file.readText(),
        )
        assertEquals(1, report.changed)
        assertEquals(1, report.updated)
    }

    fun testSortFileAppliesSupportedAstroChanges() {
        val root = createTempDirectory("trier-file-astro-apply-test")
        val file = root / "Component.astro"
        file.writeText("""<Card className={"text-center p-4 flex bg-red-500 font-bold"} />""")
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(file)!!

        val report = TrierSortService.getInstance().sortFile(project, virtualFile)

        assertEquals("""<Card className={"flex bg-red-500 p-4 text-center font-bold"} />""", file.readText())
        assertEquals(1, report.changed)
        assertEquals(1, report.updated)
    }

    fun testSortFileAppliesSupportedAngularChanges() {
        val root = createTempDirectory("trier-file-angular-apply-test")
        val file = root / "component.html"
        file.writeText(
            """<div [ngClass]="active ? 'text-center p-4 flex bg-red-500 font-bold' : 'font-bold flex p-4'"></div>""",
        )
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(file)!!

        val report = TrierSortService.getInstance().sortFile(project, virtualFile)

        assertEquals(
            """<div [ngClass]="active ? 'flex bg-red-500 p-4 text-center font-bold' : 'flex p-4 font-bold'"></div>""",
            file.readText(),
        )
        assertEquals(1, report.changed)
        assertEquals(1, report.updated)
    }

    fun testSortFileAppliesSupportedBladeChanges() {
        val root = createTempDirectory("trier-file-blade-apply-test")
        val file = root / "component.blade.php"
        file.writeText("""<div @class(['text-center p-4 flex bg-red-500 font-bold' => active])></div>""")
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(file)!!

        val report = TrierSortService.getInstance().sortFile(project, virtualFile)

        assertEquals(
            """<div @class(['flex bg-red-500 p-4 text-center font-bold' => active])></div>""",
            file.readText(),
        )
        assertEquals(1, report.changed)
        assertEquals(1, report.updated)
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

    fun testSortFileBackgroundTaskAppliesResult() {
        val root = createTempDirectory("trier-file-background-task-test")
        val file = root / "component.html"
        file.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(file)!!
        var capturedReport: com.darmaru.trier.services.FolderSortReport? = null

        TrierSortService
            .getInstance()
            .createSortFileTask(project, virtualFile) { report ->
                capturedReport = report
            }.run(EmptyProgressIndicator())

        assertEquals("""<div class="flex bg-red-500 p-4 text-center font-bold"></div>""", file.readText())
        assertEquals(1, capturedReport?.scanned)
        assertEquals(1, capturedReport?.matched)
        assertEquals(1, capturedReport?.changed)
        assertEquals(1, capturedReport?.updated)
    }

    fun testSortFileBackgroundTaskSkipsDirectory() {
        val root = createTempDirectory("trier-file-background-directory-task-test")
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(root)!!
        var capturedReport: com.darmaru.trier.services.FolderSortReport? = null

        TrierSortService
            .getInstance()
            .createSortFileTask(project, virtualFile, dryRun = true) { report ->
                capturedReport = report
            }.run(EmptyProgressIndicator())

        assertEquals(1, capturedReport?.scanned)
        assertEquals(1, capturedReport?.skipped)
        assertEquals(true, capturedReport?.dryRun)
    }

    fun testSortFolderBackgroundTaskProducesDryRunReport() {
        val root = createTempDirectory("trier-folder-background-task-test")
        val file = root / "component.html"
        file.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")
        val virtualRoot =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(root)!!
        var capturedReport: com.darmaru.trier.services.FolderSortReport? = null

        TrierSortService
            .getInstance()
            .createSortFolderTask(project, virtualRoot, "*.html", dryRun = true) { report ->
                capturedReport = report
            }.run(EmptyProgressIndicator())

        assertEquals("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""", file.readText())
        assertEquals(1, capturedReport?.scanned)
        assertEquals(1, capturedReport?.matched)
        assertEquals(1, capturedReport?.changed)
        assertEquals(0, capturedReport?.updated)
        assertEquals(true, capturedReport?.dryRun)
        assertEquals("component.html", capturedReport?.changes?.single()?.relativePath)
    }

    fun testProjectViewActionIsEnabledForSelectedFile() {
        val root = createTempDirectory("trier-project-view-file-action-test")
        val file = root / "component.html"
        file.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(file)!!
        val action = SortTailwindInProjectViewAction()

        val event = projectViewEvent(virtualFile)
        action.update(event)

        assertTrue(event.presentation.isVisible)
        assertTrue(event.presentation.isEnabled)
    }

    fun testProjectViewActionIsEnabledForSelectedDirectory() {
        val root = createTempDirectory("trier-project-view-folder-action-test")
        val virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refreshAndFindFileByNioFile(root)!!
        val action = SortTailwindInProjectViewAction()

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

    fun testSortAutoDetectsTailwindProjectPathsWhenSettingsAreBlank() {
        TrierSettingsState.getInstance().update {
            it.tailwindStylesheet = ""
            it.tailwindConfig = ""
        }
        val root = createTempDirectory("trier-tailwind-autodetect-test")
        val componentDir = root / "src/components"
        val stylesheetDir = root / "src/assets/style"
        componentDir.createDirectories()
        stylesheetDir.createDirectories()
        val config = root / "tailwind.config.js"
        val stylesheet = stylesheetDir / "tailwind.css"
        val file = componentDir / "test.html"
        config.writeText("module.exports = {}")
        stylesheet.writeText(
            """
            @layer theme, base, components, utilities;

            @import "tailwindcss/theme.css" layer(theme);
            @import "tailwindcss/preflight.css" layer(base);
            @import "tailwindcss/utilities.css";

            @plugin "@tailwindcss/typography";
            """.trimIndent(),
        )
        file.writeText("""<div class="text-center p-4 flex bg-red-500 font-bold"></div>""")
        val captured = mutableListOf<com.darmaru.trier.processing.TrierResolvedSettings>()
        TrierSortService.setTestSortOverride { _, filePath, values, settings ->
            assertEquals(file.toString(), filePath)
            captured += settings
            values.map(::sortClassesStub)
        }

        TrierSortService.getInstance().sortFolder(project, root.toString(), "**/*.html")

        assertEquals(
            """<div class="flex bg-red-500 p-4 text-center font-bold"></div>""",
            file.readText(),
        )
        val settings = captured.single()
        assertEquals(stylesheet.toString(), settings.tailwindStylesheet)
        assertEquals(config.toString(), settings.tailwindConfig)
    }

    fun testDetectsLegacyTailwindCdnInContextFile() {
        val root = createTempDirectory("trier-tailwind-cdn-test")
        val file = root / "layout.blade.php"
        file.writeText("""<script src="https://cdn.tailwindcss.com"></script>""")

        assertTrue(TrierTailwindPathDetector.detectTailwindCdn(project, file.toString()))
    }

    fun testDetectsTailwindBrowserCdnInProjectTemplates() {
        val basePath = checkNotNull(project.basePath)
        val root =
            java.nio.file.Path
                .of(basePath)
        val file = root / "templates/page.blade.php"
        file.parent.createDirectories()
        file.writeText("""<script src="https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4"></script>""")

        assertTrue(TrierTailwindPathDetector.detectTailwindCdn(project, null))
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
            TextRange(selectionStart, selectionStart + selectedText.length),
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
        val order =
            listOf(
                "flex",
                "bg-red-500",
                "px-2",
                "py-0.5",
                "p-4",
                "text-xs",
                "text-center",
                "font-bold",
                "tracking-wide",
            )
        return value
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .sortedBy { token ->
                val idx = order.indexOf(token)
                if (idx >= 0) idx else order.size + token.hashCode()
            }.joinToString(" ")
    }
}
