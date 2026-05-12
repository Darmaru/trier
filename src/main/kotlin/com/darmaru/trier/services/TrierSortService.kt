package com.darmaru.trier.services

import com.darmaru.trier.processing.TrierNodeRequest
import com.darmaru.trier.processing.TrierPsiProcessor
import com.darmaru.trier.processing.TrierResolvedSettings
import com.darmaru.trier.processing.TrierTextProcessor
import com.darmaru.trier.processing.toResolvedSettings
import com.darmaru.trier.settings.TrierSettingsState
import com.intellij.execution.ExecutionException
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ExceptionUtil
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString

data class FolderSortReport(
    val scanned: Int = 0,
    val matched: Int = 0,
    val changed: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val cancelled: Boolean = false,
    val dryRun: Boolean = false,
    val failures: List<FolderSortFailure> = emptyList(),
    val changes: List<FolderSortChange> = emptyList(),
)

data class FolderSortFailure(
    val path: String,
    val message: String,
)

data class FolderSortChange(
    val path: String,
    val relativePath: String,
    val originalText: String? = null,
    val sortedText: String? = null,
)

@Service(Service.Level.APP)
class TrierSortService {
    private val guard: TrierExecutionGuard
        get() = ApplicationManager.getApplication().getService(TrierExecutionGuard::class.java)

    private val helperService: TrierNodeHelperService
        get() = ApplicationManager.getApplication().getService(TrierNodeHelperService::class.java)

    private val workerService: TrierNodeWorkerService
        get() = ApplicationManager.getApplication().getService(TrierNodeWorkerService::class.java)

    fun sortCurrentEditor(
        project: Project,
        editor: Editor,
        trigger: String,
        selectionRange: TextRange? = currentSelectionRange(editor),
    ) {
        val document = editor.document
        if (!shouldRunDocumentSortSynchronously()) {
            sortDocumentInBackground(
                project = project,
                document = document,
                trigger = trigger,
                selectionRange = selectionRange,
                commitPsi = true,
                useCommand = true,
                saveAfterApply = false,
            )
            return
        }

        guard.guard(document) {
            sortDocumentSnapshot(
                project = project,
                document = document,
                trigger = trigger,
                original = document.text,
                originalModificationStamp = document.modificationStamp,
                filePath = currentFilePath(document),
                selectionRange = selectionRange,
                commitPsi = true,
                useCommand = true,
                saveAfterApply = false,
            )
        }
    }

    fun sortDocument(
        project: Project,
        document: Document,
        trigger: String,
    ) {
        if (!shouldRunDocumentSortSynchronously()) {
            sortDocumentInBackground(
                project = project,
                document = document,
                trigger = trigger,
                selectionRange = null,
                commitPsi = true,
                useCommand = true,
                saveAfterApply = false,
            )
            return
        }

        guard.guard(document) {
            sortDocumentSnapshot(
                project = project,
                document = document,
                trigger = trigger,
                original = document.text,
                originalModificationStamp = document.modificationStamp,
                filePath = currentFilePath(document),
                selectionRange = null,
                commitPsi = true,
                useCommand = true,
                saveAfterApply = false,
            )
        }
    }

    fun sortDocumentOnSave(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return
        val settings = TrierSettingsState.getInstance().snapshot().toResolvedSettings()
        if (!settings.sortOnSave) {
            return
        }

        if (!shouldRunDocumentSortSynchronously()) {
            sortDocumentInBackground(
                project = project,
                document = document,
                trigger = "Save",
                selectionRange = null,
                commitPsi = false,
                useCommand = false,
                saveAfterApply = true,
            )
            return
        }

        guard.guard(document) {
            sortDocumentSnapshot(
                project = project,
                document = document,
                trigger = "Save",
                original = document.text,
                originalModificationStamp = document.modificationStamp,
                filePath = currentFilePath(document),
                selectionRange = null,
                commitPsi = false,
                useCommand = false,
                saveAfterApply = false,
            )
        }
    }

    fun handleReformat(
        project: Project,
        editor: Editor?,
        selectionRange: TextRange? = editor?.let(::currentSelectionRange),
    ) {
        if (editor == null) {
            return
        }
        val settings = TrierSettingsState.getInstance().snapshot().toResolvedSettings()
        if (!settings.sortOnReformat) {
            return
        }
        sortCurrentEditor(project, editor, "Reformat", selectionRange)
    }

    fun sortFolder(
        project: Project,
        rootPath: String,
        globPattern: String,
        dryRun: Boolean = false,
    ): FolderSortReport {
        val root = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath)
        if (root == null || !root.isDirectory) {
            Messages.showErrorDialog(project, "Folder not found: $rootPath", "Trier")
            return FolderSortReport(
                failed = 1,
                dryRun = dryRun,
                failures = listOf(FolderSortFailure(rootPath, "Folder not found")),
            )
        }

        val report = sortFolderInternal(project, root, globPattern, dryRun, null)
        notifyFolderReport(project, root, report)
        return report
    }

    fun sortFolderInBackground(
        project: Project,
        rootPath: String,
        globPattern: String,
        dryRun: Boolean = false,
    ) {
        val root = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath)
        if (root == null || !root.isDirectory) {
            Messages.showErrorDialog(project, "Folder not found: $rootPath", "Trier")
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Trier: Sort Tailwind Classes", true) {
                override fun run(indicator: ProgressIndicator) {
                    val report = sortFolderInternal(project, root, globPattern, dryRun, indicator)
                    notifyFolderReport(project, root, report)
                }
            },
        )
    }

    fun sortFile(
        project: Project,
        file: VirtualFile,
        dryRun: Boolean = false,
    ): FolderSortReport {
        if (file.isDirectory) {
            return FolderSortReport(scanned = 1, skipped = 1, dryRun = dryRun)
        }

        val report =
            sortFiles(
                project,
                file.parent ?: file,
                listOf(file),
                scanned = 1,
                skipped = 0,
                dryRun = dryRun,
                indicator = null,
            )
        notifyFolderReport(project, file, report)
        return report
    }

    fun sortFileInBackground(
        project: Project,
        file: VirtualFile,
        dryRun: Boolean = false,
    ) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Trier: Sort Tailwind Classes", true) {
                override fun run(indicator: ProgressIndicator) {
                    val report =
                        if (file.isDirectory) {
                            FolderSortReport(scanned = 1, skipped = 1, dryRun = dryRun)
                        } else {
                            sortFiles(
                                project,
                                file.parent ?: file,
                                listOf(file),
                                scanned = 1,
                                skipped = 0,
                                dryRun = dryRun,
                                indicator = indicator,
                            )
                        }
                    notifyFolderReport(project, file, report)
                }
            },
        )
    }

    private fun processText(
        project: Project,
        text: String,
        filePath: String?,
    ): String =
        try {
            processTextOrThrow(project, text, filePath)
        } catch (error: Exception) {
            helperService.notifyError("Trier failed", error.message ?: error.javaClass.simpleName)
            text
        }

    private fun processTextOrThrow(
        project: Project,
        text: String,
        filePath: String?,
    ): String {
        val settings = TrierSettingsState.getInstance().snapshot().toResolvedSettings()
        return processWithPsi(project, text, filePath, settings)
    }

    private fun sortFolderInternal(
        project: Project,
        root: VirtualFile,
        globPattern: String,
        dryRun: Boolean,
        indicator: ProgressIndicator?,
    ): FolderSortReport {
        val matcher =
            try {
                FileSystems.getDefault().getPathMatcher("glob:${globPattern.ifBlank { "**/*" }}")
            } catch (error: Exception) {
                return FolderSortReport(
                    failed = 1,
                    dryRun = dryRun,
                    failures = listOf(FolderSortFailure(root.path, "Invalid glob pattern: ${error.message}")),
                )
            }

        val matchedFiles = mutableListOf<VirtualFile>()
        var scanned = 0
        var skipped = 0
        val failures = mutableListOf<FolderSortFailure>()
        val scanFilter =
            VirtualFileFilter { file ->
                if (file.isDirectory && file != root && shouldSkipFolder(file)) {
                    skipped++
                    false
                } else {
                    true
                }
            }

        try {
            indicator?.text = "Scanning files"
            VfsUtilCore.iterateChildrenRecursively(root, scanFilter) { file ->
                indicator?.checkCanceled()
                if (!file.isDirectory) {
                    scanned++
                    val relative = root.toNioPath().relativize(file.toNioPath())
                    if (matcher.matches(relative) || matcher.matches(Path.of(file.name))) {
                        matchedFiles += file
                    } else {
                        skipped++
                    }
                }
                true
            }
        } catch (_: ProcessCanceledException) {
            return FolderSortReport(
                scanned = scanned,
                skipped = skipped,
                cancelled = true,
                dryRun = dryRun,
                failures = failures,
            )
        }

        return sortFiles(project, root, matchedFiles, scanned, skipped, dryRun, indicator, failures)
    }

    private fun sortFiles(
        project: Project,
        root: VirtualFile,
        matchedFiles: List<VirtualFile>,
        scanned: Int,
        skipped: Int,
        dryRun: Boolean,
        indicator: ProgressIndicator?,
        initialFailures: List<FolderSortFailure> = emptyList(),
    ): FolderSortReport {
        val failures = initialFailures.toMutableList()
        val changes = mutableListOf<FolderSortChange>()
        var changed = 0
        var updated = 0
        var unchanged = 0
        var skippedCount = skipped

        for ((index, file) in matchedFiles.withIndex()) {
            try {
                indicator?.checkCanceled()
                indicator?.text = "Sorting Tailwind classes"
                indicator?.text2 = file.path
                if (matchedFiles.isNotEmpty()) {
                    indicator?.fraction = index.toDouble() / matchedFiles.size.toDouble()
                }

                if (shouldSkipMatchedFile(file)) {
                    skippedCount++
                    continue
                }

                val original = VfsUtilCore.loadText(file)
                val sorted = processTextOrThrow(project, original, file.path)
                if (sorted == original) {
                    unchanged++
                    continue
                }

                changed++
                changes +=
                    FolderSortChange(
                        path = file.path,
                        relativePath = VfsUtilCore.getRelativePath(file, root, '/') ?: file.path,
                        originalText = if (dryRun) original else null,
                        sortedText = if (dryRun) sorted else null,
                    )
                if (dryRun) {
                    continue
                }

                if (!file.isWritable) {
                    failures += FolderSortFailure(file.path, "File is read-only")
                    continue
                }

                ApplicationManager.getApplication().runWriteAction {
                    file.setBinaryContent(sorted.toByteArray(file.charset))
                }
                updated++
            } catch (_: ProcessCanceledException) {
                return FolderSortReport(
                    scanned = scanned,
                    matched = matchedFiles.size,
                    changed = changed,
                    updated = updated,
                    unchanged = unchanged,
                    skipped = skippedCount,
                    failed = failures.size,
                    cancelled = true,
                    dryRun = dryRun,
                    failures = failures,
                    changes = changes,
                )
            } catch (error: Exception) {
                failures +=
                    FolderSortFailure(
                        file.path,
                        ExceptionUtil
                            .getThrowableText(error)
                            .lineSequence()
                            .firstOrNull()
                            .orEmpty(),
                    )
            }
        }

        indicator?.fraction = 1.0
        return FolderSortReport(
            scanned = scanned,
            matched = matchedFiles.size,
            changed = changed,
            updated = updated,
            unchanged = unchanged,
            skipped = skippedCount,
            failed = failures.size,
            dryRun = dryRun,
            failures = failures,
            changes = changes,
        )
    }

    private fun notifyFolderReport(
        project: Project,
        root: VirtualFile,
        report: FolderSortReport,
    ) {
        val type =
            when {
                report.cancelled -> NotificationType.WARNING
                report.failed > 0 -> NotificationType.WARNING
                else -> NotificationType.INFORMATION
            }
        val failureSummary =
            report.failures
                .take(3)
                .joinToString(separator = "\n") { "${it.path}: ${it.message}" }
                .takeIf(String::isNotBlank)
                ?.let { "\n\nFailures:\n$it" }
                .orEmpty()
        val cancelled = if (report.cancelled) "Cancelled. " else ""
        val actionSummary =
            if (report.dryRun) {
                "would update ${report.changed}"
            } else {
                "updated ${report.updated}"
            }
        val mode = if (report.dryRun) "Dry run. " else ""
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup("Trier")
            .createNotification(
                "Trier Folder Sort",
                "${mode}${cancelled}Scanned ${report.scanned}, matched ${report.matched}, $actionSummary, unchanged ${report.unchanged}, skipped ${report.skipped}, failed ${report.failed} under ${root.path}.$failureSummary",
                type,
            ).notify(project)
        showDryRunReport(project, root, report)
    }

    private fun showDryRunReport(
        project: Project,
        root: VirtualFile,
        report: FolderSortReport,
    ) {
        val application = ApplicationManager.getApplication()
        if (!report.dryRun || application.isUnitTestMode || application.isHeadlessEnvironment || project.isDisposed) {
            return
        }

        application.invokeLater {
            if (!project.isDisposed) {
                showTrierDryRunReport(project, root.path, report, forceListForChanges = root.isDirectory)
            }
        }
    }

    private fun shouldSkipFolder(file: VirtualFile): Boolean = file.name in FOLDER_SORT_EXCLUDED_DIRECTORIES

    private fun shouldSkipMatchedFile(file: VirtualFile): Boolean =
        file.length > MAX_SORTABLE_FILE_BYTES || isProbablyBinary(file)

    private fun isProbablyBinary(file: VirtualFile): Boolean {
        val sampleSize = minOf(file.length, BINARY_SAMPLE_BYTES.toLong()).toInt()
        if (sampleSize <= 0) {
            return false
        }

        val sample = ByteArray(sampleSize)
        val read =
            file.inputStream.use { input ->
                input.read(sample)
            }
        if (read <= 0) {
            return false
        }

        return sample
            .asSequence()
            .take(read)
            .any { it == 0.toByte() }
    }

    private fun processWithPsi(
        project: Project,
        text: String,
        filePath: String?,
        settings: TrierResolvedSettings,
        limitRange: TextRange? = null,
        useFallback: Boolean = true,
    ): String {
        val sorter = { values: List<String> -> sortClassAttributes(project, filePath, values, settings) }
        return ApplicationManager.getApplication().runReadAction<String> {
            val virtualFile = filePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            val psiFile =
                virtualFile?.let {
                    com.intellij.psi.PsiManager
                        .getInstance(project)
                        .findFile(it)
                }
            if (psiFile != null) {
                TrierPsiProcessor(
                    sorter,
                    TrierTextProcessor(sorter),
                ).process(psiFile, text, settings, limitRange, useFallback)
            } else if (!useFallback) {
                text
            } else {
                TrierTextProcessor(sorter).process(text, settings)
            }
        }
    }

    private fun processSelectionText(
        project: Project,
        text: String,
        filePath: String?,
        range: TextRange,
    ): String {
        val settings = TrierSettingsState.getInstance().snapshot().toResolvedSettings()
        val sorter = { values: List<String> -> sortClassAttributes(project, filePath, values, settings) }
        val selectionText = range.substring(text)

        if (looksLikePlainClassList(selectionText)) {
            val sortedSelectionText = sorter(listOf(selectionText)).firstOrNull().orEmpty()
            if (sortedSelectionText.isNotBlank() && sortedSelectionText != selectionText) {
                return StringBuilder(text).replace(range.startOffset, range.endOffset, sortedSelectionText).toString()
            }
        }

        val psiUpdated = processWithPsi(project, text, filePath, settings, range, useFallback = false)
        if (psiUpdated != text) {
            return psiUpdated
        }

        val updatedSelectionText = TrierTextProcessor(sorter).process(selectionText, settings)
        return if (updatedSelectionText == selectionText) {
            text
        } else {
            StringBuilder(text).replace(range.startOffset, range.endOffset, updatedSelectionText).toString()
        }
    }

    private fun sortDocumentInBackground(
        project: Project,
        document: Document,
        trigger: String,
        selectionRange: TextRange?,
        commitPsi: Boolean,
        useCommand: Boolean,
        saveAfterApply: Boolean,
    ) {
        if (!guard.tryEnter(document)) {
            return
        }

        val original = document.text
        val originalModificationStamp = document.modificationStamp
        val filePath = currentFilePath(document)

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Trier: Sort Tailwind Classes", true) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        indicator.text = "Sorting Tailwind classes"
                        val updated = computeDocumentUpdate(project, original, filePath, selectionRange)
                        if (updated == original || project.isDisposed) {
                            guard.release(document)
                            return
                        }

                        ApplicationManager.getApplication().invokeLater {
                            applyDocumentUpdate(
                                project = project,
                                document = document,
                                trigger = trigger,
                                original = original,
                                originalModificationStamp = originalModificationStamp,
                                updated = updated,
                                commitPsi = commitPsi,
                                useCommand = useCommand,
                                saveAfterApply = saveAfterApply,
                            )
                        }
                    } catch (error: ProcessCanceledException) {
                        guard.release(document)
                        throw error
                    } catch (error: Exception) {
                        guard.release(document)
                        helperService.notifyError("Trier failed", error.message ?: error.javaClass.simpleName)
                    }
                }

                override fun onCancel() {
                    guard.release(document)
                }

                override fun onThrowable(error: Throwable) {
                    guard.release(document)
                }
            },
        )
    }

    private fun sortDocumentSnapshot(
        project: Project,
        document: Document,
        trigger: String,
        original: String,
        originalModificationStamp: Long,
        filePath: String?,
        selectionRange: TextRange?,
        commitPsi: Boolean = true,
        useCommand: Boolean = true,
        saveAfterApply: Boolean = false,
    ) {
        val updated = computeDocumentUpdate(project, original, filePath, selectionRange)
        if (updated != original) {
            applyDocumentUpdate(
                project = project,
                document = document,
                trigger = trigger,
                original = original,
                originalModificationStamp = originalModificationStamp,
                updated = updated,
                commitPsi = commitPsi,
                useCommand = useCommand,
                saveAfterApply = saveAfterApply,
                releaseGuard = false,
            )
        }
    }

    private fun computeDocumentUpdate(
        project: Project,
        original: String,
        filePath: String?,
        selectionRange: TextRange?,
    ): String =
        if (selectionRange != null && !selectionRange.isEmpty) {
            processSelectionText(project, original, filePath, selectionRange)
        } else {
            processText(project, original, filePath)
        }

    private fun applyDocumentUpdate(
        project: Project,
        document: Document,
        trigger: String,
        original: String,
        originalModificationStamp: Long,
        updated: String,
        commitPsi: Boolean,
        useCommand: Boolean,
        saveAfterApply: Boolean,
        releaseGuard: Boolean = true,
    ) {
        try {
            if (project.isDisposed) {
                return
            }

            if (document.modificationStamp != originalModificationStamp || document.text != original) {
                NotificationGroupManager
                    .getInstance()
                    .getNotificationGroup("Trier")
                    .createNotification(
                        "Trier skipped sorting",
                        "The document changed while Trier was sorting it. Run sorting again to apply the latest content.",
                        NotificationType.WARNING,
                    ).notify(project)
                return
            }

            val applyChange = {
                document.setText(updated)
                if (commitPsi) {
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                }
            }

            when {
                useCommand -> {
                    com.intellij.openapi.command.WriteCommandAction
                        .writeCommandAction(project)
                        .withName("Trier Sort Tailwind Classes ($trigger)")
                        .run<RuntimeException>(applyChange)
                }
                ApplicationManager.getApplication().isWriteAccessAllowed -> applyChange()
                else -> {
                    CommandProcessor.getInstance().executeCommand(
                        project,
                        { ApplicationManager.getApplication().runWriteAction(applyChange) },
                        "Trier Sort Tailwind Classes ($trigger)",
                        null,
                    )
                }
            }

            if (saveAfterApply) {
                FileDocumentManager.getInstance().saveDocument(document)
            }
        } finally {
            if (releaseGuard) {
                guard.release(document)
            }
        }
    }

    private fun shouldRunDocumentSortSynchronously(): Boolean =
        ApplicationManager.getApplication().isUnitTestMode && !forceBackgroundDocumentSortForTest

    private fun sortClassAttributes(
        project: Project,
        filePath: String?,
        values: List<String>,
        settings: TrierResolvedSettings,
    ): List<String> {
        if (values.isEmpty()) {
            return values
        }

        testSortOverride?.let { override ->
            return override(project, filePath, values, settings)
        }

        val projectBase = project.basePath ?: guessBasePath()
        val request =
            TrierNodeRequest(
                moduleBase = helperService.bundledRuntimePath().absolutePathString(),
                base = projectBase,
                filepath = filePath,
                configPath = settings.tailwindConfig,
                stylesheetPath = settings.tailwindStylesheet,
                preserveWhitespace = settings.tailwindPreserveWhitespace,
                preserveDuplicates = settings.tailwindPreserveDuplicates,
                values = values,
            )
        return runNode(project, request, settings)
    }

    private fun runNode(
        project: Project,
        request: TrierNodeRequest,
        settings: TrierResolvedSettings,
    ): List<String> {
        val nodePath = resolveNodePath(project, settings)
        return workerService.sort(nodePath, helperService.helperScriptPath(), request)
    }

    fun testRuntime(
        project: Project,
        settings: TrierResolvedSettings,
    ): String {
        val nodePath = resolveNodePath(project, settings)
        val runtimePath = helperService.bundledRuntimePath()
        val scriptPath = helperService.helperScriptPath()
        val request =
            TrierNodeRequest(
                moduleBase = runtimePath.absolutePathString(),
                base = project.basePath ?: guessBasePath(),
                filepath = null,
                configPath = settings.tailwindConfig,
                stylesheetPath = settings.tailwindStylesheet,
                preserveWhitespace = settings.tailwindPreserveWhitespace,
                preserveDuplicates = settings.tailwindPreserveDuplicates,
                values = listOf("text-center p-4 flex"),
            )
        val sampleResult = workerService.sort(nodePath, scriptPath, request).singleOrNull().orEmpty()
        return buildString {
            appendLine("Node: $nodePath")
            appendLine("Helper: ${scriptPath.absolutePathString()}")
            appendLine("Bundled runtime: ${runtimePath.absolutePathString()}")
            append("Sample sort result: $sampleResult")
        }
    }

    private fun currentFilePath(document: Document): String? = FileDocumentManager.getInstance().getFile(document)?.path

    private fun currentSelectionRange(editor: Editor): TextRange? {
        val selectionModel = editor.selectionModel
        return if (selectionModel.hasSelection()) {
            TextRange(selectionModel.selectionStart, selectionModel.selectionEnd)
        } else {
            null
        }
    }

    private fun looksLikePlainClassList(selectionText: String): Boolean {
        val trimmed = selectionText.trim()
        if (trimmed.isEmpty()) {
            return false
        }
        return trimmed.none {
            it == '<' ||
                it == '>' ||
                it == '=' ||
                it == '"' ||
                it == '\'' ||
                it == '`' ||
                it == '\n' ||
                it == '\r'
        }
    }

    private fun guessBasePath(): String =
        ProjectManager
            .getInstance()
            .openProjects
            .firstOrNull()
            ?.basePath
            ?: Path
                .of(".")
                .toAbsolutePath()
                .normalize()
                .pathString

    private fun resolveNodePath(
        project: Project,
        settings: TrierResolvedSettings,
    ): String {
        val interpreterRef =
            settings.nodeInterpreterRef?.takeIf { it.isNotBlank() }?.let(NodeJsInterpreterRef::create)
                ?: NodeJsInterpreterManager.getInstance(project).interpreterRef

        val interpreter =
            interpreterRef.resolve(project)
                ?: error("Selected JavaScript Runtime could not be resolved for project '${project.name}'.")

        return try {
            TrierNodeRuntimeValidator.validateLocalNodeRuntime(
                NodeJsLocalInterpreter.castAndValidate(interpreter).interpreterSystemDependentPath,
            )
        } catch (_: ExecutionException) {
            error(
                "Trier currently supports only local Node.js runtimes. Select a local JavaScript Runtime in the IDE settings.",
            )
        }
    }

    companion object {
        @Volatile
        private var testSortOverride: ((Project, String?, List<String>, TrierResolvedSettings) -> List<String>)? = null

        @Volatile
        internal var forceBackgroundDocumentSortForTest: Boolean = false

        private const val MAX_SORTABLE_FILE_BYTES = 2L * 1024L * 1024L
        private const val BINARY_SAMPLE_BYTES = 8192

        private val FOLDER_SORT_EXCLUDED_DIRECTORIES =
            setOf(
                ".git",
                ".gradle",
                ".idea",
                ".next",
                ".nuxt",
                ".svelte-kit",
                "build",
                "cache",
                ".cache",
                "coverage",
                "dist",
                "node_modules",
                "out",
                "target",
                "vendor",
            )

        fun getInstance(): TrierSortService =
            ApplicationManager.getApplication().getService(TrierSortService::class.java)

        internal fun setTestSortOverride(
            override: ((Project, String?, List<String>, TrierResolvedSettings) -> List<String>)?,
        ) {
            testSortOverride = override
        }
    }
}
