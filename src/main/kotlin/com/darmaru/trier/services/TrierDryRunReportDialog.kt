package com.darmaru.trier.services

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

internal fun showTrierDryRunReport(
    project: Project,
    rootPath: String,
    report: FolderSortReport,
) {
    val requests = buildDryRunDiffRequests(project, report)
    when {
        requests.isEmpty() -> TrierDryRunReportDialog(project, rootPath, report).show()
        requests.size == 1 -> showDiff(project, requests.single())
        else -> TrierDryRunDiffDialog(project, rootPath, report, requests).show()
    }
}

private fun showDiff(
    project: Project,
    request: SimpleDiffRequest,
) {
    DiffManager
        .getInstance()
        .showDiff(project, request, DiffDialogHints.NON_MODAL)
}

private fun showDiffChain(
    project: Project,
    requests: List<SimpleDiffRequest>,
    selectedIndex: Int = 0,
) {
    DiffManager
        .getInstance()
        .showDiff(
            project,
            SimpleDiffRequestChain(requests, selectedIndex.coerceIn(requests.indices)),
            DiffDialogHints.NON_MODAL,
        )
}

private class TrierDryRunDiffDialog(
    private val project: Project,
    private val rootPath: String,
    private val report: FolderSortReport,
    private val requests: List<SimpleDiffRequest>,
) : DialogWrapper(project, false) {
    private val reportText = buildDryRunReportText(rootPath, report)
    private val diffList =
        JBList(requests.map(SimpleDiffRequest::getTitle)).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            selectedIndex = 0
            visibleRowCount = 14
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        if (event.clickCount == 2 && selectedIndex >= 0) {
                            showSelectedDiff()
                        }
                    }
                },
            )
        }

    init {
        title = "Trier Dry Run Report"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val summary =
            JBLabel(
                "Dry run found ${report.changed} files that would be updated. Select a file to inspect the diff, or open the full diff chain.",
            ).apply {
                border = JBUI.Borders.emptyBottom(8)
            }

        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(760, 420)
            border = JBUI.Borders.empty(8)
            add(summary, BorderLayout.NORTH)
            add(JBScrollPane(diffList), BorderLayout.CENTER)
        }
    }

    override fun createActions(): Array<Action> =
        arrayOf(
            openSelectedDiffAction(),
            openAllDiffsAction(),
            copyAction(),
            okAction,
        )

    private fun openSelectedDiffAction(): Action =
        object : DialogWrapperAction("Open Selected Diff") {
            override fun doAction(event: ActionEvent?) {
                showSelectedDiff()
            }
        }

    private fun openAllDiffsAction(): Action =
        object : DialogWrapperAction("Open All Diffs") {
            override fun doAction(event: ActionEvent?) {
                showDiffChain(project, requests, selectedDiffIndex())
            }
        }

    private fun copyAction(): Action =
        object : DialogWrapperAction("Copy Report") {
            override fun doAction(event: ActionEvent?) {
                CopyPasteManager.getInstance().setContents(StringSelection(reportText))
            }
        }

    private fun showSelectedDiff() {
        showDiff(project, requests[selectedDiffIndex()])
    }

    private fun selectedDiffIndex(): Int = diffList.selectedIndex.takeIf { it >= 0 } ?: 0
}

internal class TrierDryRunReportDialog(
    project: Project,
    private val rootPath: String,
    private val report: FolderSortReport,
) : DialogWrapper(project, false) {
    private val reportText = buildDryRunReportText(rootPath, report)

    init {
        title = "Trier Dry Run Report"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val textArea =
            JBTextArea(reportText, 28, 110).apply {
                isEditable = false
                lineWrap = false
                border = JBUI.Borders.empty(8)
                caretPosition = 0
            }

        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(840, 560)
            border = JBUI.Borders.empty(8)
            add(JBScrollPane(textArea), BorderLayout.CENTER)
        }
    }

    override fun createActions(): Array<Action> = arrayOf(copyAction(), okAction)

    private fun copyAction(): Action =
        object : DialogWrapperAction("Copy Report") {
            override fun doAction(event: ActionEvent?) {
                CopyPasteManager.getInstance().setContents(StringSelection(reportText))
            }
        }
}

internal fun buildDryRunDiffRequests(
    project: Project,
    report: FolderSortReport,
): List<SimpleDiffRequest> {
    val contentFactory = DiffContentFactory.getInstance()
    val localFileSystem = LocalFileSystem.getInstance()

    return report.changes
        .sortedBy(FolderSortChange::relativePath)
        .mapNotNull { change ->
            val originalText = change.originalText ?: return@mapNotNull null
            val sortedText = change.sortedText ?: return@mapNotNull null
            val file = localFileSystem.findFileByPath(change.path)
            val originalContent =
                if (file != null) {
                    contentFactory.create(project, originalText, file)
                } else {
                    contentFactory.create(project, originalText)
                }
            val sortedContent =
                if (file != null) {
                    contentFactory.create(project, sortedText, file)
                } else {
                    contentFactory.create(project, sortedText)
                }

            SimpleDiffRequest(
                "Trier Dry Run: ${change.relativePath}",
                originalContent,
                sortedContent,
                "Current file",
                "Sorted preview (not written)",
            )
        }
}

internal fun buildDryRunReportText(
    rootPath: String,
    report: FolderSortReport,
): String =
    buildString {
        appendLine("Trier Dry Run Report")
        appendLine()
        appendLine("Root: $rootPath")
        appendLine("Scanned: ${report.scanned}")
        appendLine("Matched: ${report.matched}")
        appendLine("Would update: ${report.changed}")
        appendLine("Unchanged: ${report.unchanged}")
        appendLine("Skipped: ${report.skipped}")
        appendLine("Failed: ${report.failed}")
        if (report.cancelled) {
            appendLine("Status: Cancelled")
        }

        appendLine()
        appendLine("Files that would be updated:")
        if (report.changes.isEmpty()) {
            appendLine("  None")
        } else {
            report.changes
                .sortedBy(FolderSortChange::relativePath)
                .forEach { change ->
                    appendLine("  - ${change.relativePath}")
                    appendLine("    ${change.path}")
                }
        }

        if (report.failures.isNotEmpty()) {
            appendLine()
            appendLine("Failures:")
            report.failures.forEach { failure ->
                appendLine("  - ${failure.path}")
                appendLine("    ${failure.message}")
            }
        }
    }
