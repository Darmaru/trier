package com.darmaru.trier.services

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Action
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

internal fun showTrierDryRunReport(
    project: Project,
    rootPath: String,
    report: FolderSortReport,
    forceListForChanges: Boolean = false,
) {
    val entries = buildDryRunDiffEntries(project, report)
    when {
        entries.isEmpty() -> TrierDryRunReportDialog(project, rootPath, report).show()
        entries.size == 1 && !forceListForChanges -> {
            val entry = entries.single()
            entry.attachApplyAction(project) { source ->
                closeDiffWindow(source)
            }
            showDiff(project, entry.request)
        }
        else -> TrierDryRunDiffDialog(project, rootPath, report, entries).show()
    }
}

private fun showDiff(
    project: Project,
    request: SimpleDiffRequest,
    parent: Component? = null,
) {
    DiffManager
        .getInstance()
        .showDiff(project, request, nonModalDiffHints(parent))
}

private fun showDiffChain(
    project: Project,
    entries: List<DryRunDiffEntry>,
    selectedIndex: Int = 0,
    parent: Component? = null,
) {
    DiffManager
        .getInstance()
        .showDiff(
            project,
            SimpleDiffRequestChain(entries.map(DryRunDiffEntry::request), selectedIndex.coerceIn(entries.indices)),
            nonModalDiffHints(parent),
        )
}

private fun nonModalDiffHints(parent: Component?): DiffDialogHints =
    if (parent != null) {
        DiffDialogHints(WindowWrapper.Mode.NON_MODAL, parent)
    } else {
        DiffDialogHints.NON_MODAL
    }

private class TrierDryRunDiffDialog(
    private val project: Project,
    private val rootPath: String,
    private val report: FolderSortReport,
    entries: List<DryRunDiffEntry>,
) : DialogWrapper(project, false) {
    private companion object {
        const val GROUPED_VIEW = "grouped"
        const val LIST_VIEW = "list"
    }

    private val reportText = buildDryRunReportText(rootPath, report)
    private val diffEntries = entries.toMutableList()
    private val diffListModel = DefaultListModel<DryRunDiffEntry>()
    private val diffTree =
        Tree(DefaultTreeModel(buildDiffTreeRoot(diffEntries))).apply {
            isRootVisible = false
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            cellRenderer = DiffTreeCellRenderer()
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        if (event.clickCount == 2 && selectedDiffIndexOrNull() != null) {
                            showDiffsFromSelection()
                        }
                    }
                },
            )
            expandAllRows()
            selectFirstFile()
        }
    private val diffList =
        JBList(diffListModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            selectedIndex = 0
            cellRenderer = DiffListCellRenderer()
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        if (event.clickCount == 2 && selectedIndex >= 0) {
                            showDiffsFromSelection()
                        }
                    }
                },
            )
        }
    private val cardLayout = CardLayout()
    private val diffViewPanel =
        JPanel(cardLayout).apply {
            add(JBScrollPane(diffTree), GROUPED_VIEW)
            add(JBScrollPane(diffList), LIST_VIEW)
        }
    private val groupByBox =
        JBCheckBox("Group by directory", true).apply {
            addActionListener {
                val selectedIndex = selectedDiffIndex()
                cardLayout.show(diffViewPanel, if (isSelected) GROUPED_VIEW else LIST_VIEW)
                selectDiffIndex(selectedIndex)
            }
        }

    init {
        title = "Trier Dry Run Report"
        setOKButtonText("Close")
        diffEntries.forEach { entry ->
            entry.attachApplyAction(project) { source ->
                applyEntryFromDiff(entry, source)
            }
        }
        resetListModel()
        init()
    }

    override fun createCenterPanel(): JComponent {
        val summary =
            JBLabel(
                "Dry run found ${report.changed} files that would be updated. Select a file to start reviewing diffs from there.",
            )
        val header =
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.emptyBottom(8)
                add(summary, BorderLayout.CENTER)
                add(groupByBox, BorderLayout.EAST)
            }

        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(760, 420)
            border = JBUI.Borders.empty(8)
            add(header, BorderLayout.NORTH)
            add(diffViewPanel, BorderLayout.CENTER)
        }
    }

    override fun createActions(): Array<Action> =
        arrayOf(
            copyAction(),
            okAction,
        )

    override fun createLeftSideActions(): Array<Action> =
        arrayOf(
            applyAction(),
            openDiffsAction(),
        )

    private fun openDiffsAction(): Action =
        object : DialogWrapperAction("Open Diff") {
            override fun doAction(event: ActionEvent?) {
                showDiffsFromSelection()
            }
        }

    private fun applyAction(): Action =
        object : DialogWrapperAction("Apply") {
            override fun doAction(event: ActionEvent?) {
                applySelectedChange()
            }
        }

    private fun copyAction(): Action =
        object : DialogWrapperAction("Copy Report") {
            override fun doAction(event: ActionEvent?) {
                CopyPasteManager.getInstance().setContents(StringSelection(reportText))
            }
        }

    private fun showDiffsFromSelection() {
        showDiffsFromIndex(selectedDiffIndex())
    }

    private fun showDiffsFromIndex(index: Int) {
        if (diffEntries.isEmpty()) {
            return
        }
        showDiffChain(
            project = project,
            entries = diffEntries,
            selectedIndex = index,
            parent = window,
        )
    }

    private fun applySelectedChange() {
        val entry = selectedDiffEntryOrNull() ?: diffEntries.firstOrNull() ?: return
        applyDryRunEntry(project, entry) {
            removeEntry(entry)
        }
    }

    private fun applyEntryFromDiff(
        entry: DryRunDiffEntry,
        source: Component?,
    ) {
        val nextIndex = diffEntries.indexOf(entry).takeIf { it >= 0 } ?: selectedDiffIndex()
        closeDiffWindow(source)
        removeEntry(entry)
        if (diffEntries.isNotEmpty()) {
            showDiffsFromIndex(nextIndex.coerceAtMost(diffEntries.lastIndex))
        }
    }

    private fun selectedDiffIndex(): Int = selectedDiffIndexOrNull() ?: 0

    private fun selectedDiffIndexOrNull(): Int? {
        val entry = selectedDiffEntryOrNull() ?: return null
        return diffEntries.indexOf(entry).takeIf { it >= 0 }
    }

    private fun selectedDiffEntryOrNull(): DryRunDiffEntry? {
        if (!groupByBox.isSelected) {
            return diffList.selectedValue
        }
        val node = diffTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return (node.userObject as? DiffTreeFile)?.entry
    }

    private fun selectDiffIndex(index: Int) {
        if (diffEntries.isEmpty()) {
            diffList.clearSelection()
            diffTree.clearSelection()
            return
        }

        val safeIndex = index.coerceIn(diffEntries.indices)
        diffList.selectedIndex = safeIndex
        selectTreeEntry(diffEntries[safeIndex])
    }

    private fun selectTreeEntry(entry: DryRunDiffEntry) {
        val file = findFileNode(modelRoot(), entry) ?: return
        diffTree.selectionPath = TreePath(file.path)
    }

    private fun removeEntry(entry: DryRunDiffEntry) {
        val previousIndex = diffEntries.indexOf(entry).takeIf { it >= 0 } ?: selectedDiffIndex()
        diffEntries.remove(entry)
        resetListModel()
        resetTreeModel()
        if (diffEntries.isEmpty()) {
            close(OK_EXIT_CODE)
        } else {
            selectDiffIndex(previousIndex.coerceAtMost(diffEntries.lastIndex))
        }
    }

    private fun resetListModel() {
        diffListModel.clear()
        diffEntries.forEach(diffListModel::addElement)
    }

    private fun resetTreeModel() {
        diffTree.model = DefaultTreeModel(buildDiffTreeRoot(diffEntries))
        diffTree.expandAllRows()
    }

    private fun modelRoot(): DefaultMutableTreeNode = diffTree.model.root as DefaultMutableTreeNode

    private fun Tree.expandAllRows() {
        var row = 0
        while (row < rowCount) {
            expandRow(row)
            row += 1
        }
    }

    private fun Tree.selectFirstFile() {
        val root = model.root as? DefaultMutableTreeNode ?: return
        val firstFile = findFirstFileNode(root) ?: return
        selectionPath = TreePath(firstFile.path)
    }

    private fun findFirstFileNode(node: DefaultMutableTreeNode): DefaultMutableTreeNode? {
        if (node.userObject is DiffTreeFile) {
            return node
        }
        for (index in 0 until node.childCount) {
            findFirstFileNode(node.getChildAt(index) as DefaultMutableTreeNode)?.let { return it }
        }
        return null
    }

    private fun findFileNode(
        node: DefaultMutableTreeNode,
        entry: DryRunDiffEntry,
    ): DefaultMutableTreeNode? {
        if ((node.userObject as? DiffTreeFile)?.entry == entry) {
            return node
        }
        for (index in 0 until node.childCount) {
            findFileNode(node.getChildAt(index) as DefaultMutableTreeNode, entry)?.let { return it }
        }
        return null
    }
}

private data class DryRunDiffEntry(
    val relativePath: String,
    val change: FolderSortChange,
    val request: SimpleDiffRequest,
) {
    val fileName: String = relativePath.split(Regex("[/\\\\]+")).lastOrNull() ?: relativePath

    override fun toString(): String = relativePath
}

private fun DryRunDiffEntry.attachApplyAction(
    project: Project,
    onApplied: (Component?) -> Unit = {},
) {
    request.putUserData(
        DiffUserDataKeys.CONTEXT_ACTIONS,
        listOf(
            object : AnAction("Apply", "Apply this dry-run change", AllIcons.Actions.Commit) {
                override fun actionPerformed(event: AnActionEvent) {
                    val source = event.inputEvent?.component ?: event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
                    applyDryRunEntry(project, this@attachApplyAction) {
                        onApplied(source)
                    }
                }
            },
        ),
    )
}

private fun closeDiffWindow(source: Component?) {
    val window = source?.let(SwingUtilities::getWindowAncestor) ?: return
    window.dispose()
}

private fun applyDryRunEntry(
    project: Project,
    entry: DryRunDiffEntry,
    onApplied: () -> Unit,
) {
    when (applyDryRunChange(project, entry.change)) {
        DryRunApplyResult.Applied -> onApplied()
        DryRunApplyResult.FileChanged ->
            Messages.showWarningDialog(
                project,
                "The file changed after the dry run. Run dry run again to preview and apply the latest content.",
                "Trier",
            )
        DryRunApplyResult.FileNotFound ->
            Messages.showWarningDialog(project, "File not found: ${entry.change.path}", "Trier")
        DryRunApplyResult.ReadOnly ->
            Messages.showWarningDialog(project, "File is read-only: ${entry.change.path}", "Trier")
        DryRunApplyResult.MissingPreview ->
            Messages.showWarningDialog(project, "Dry-run preview is incomplete for: ${entry.change.path}", "Trier")
    }
}

private data class DiffTreeDirectory(
    val name: String,
) {
    override fun toString(): String = name
}

private data class DiffTreeFile(
    val name: String,
    val relativePath: String,
    val entry: DryRunDiffEntry,
) {
    override fun toString(): String = name
}

private class DiffTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val userObject = (value as? DefaultMutableTreeNode)?.userObject
        when (userObject) {
            is DiffTreeDirectory -> {
                icon = AllIcons.Nodes.Folder
                append(userObject.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
            is DiffTreeFile -> {
                icon = fileIcon(userObject.relativePath)
                append(userObject.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
            else -> append(value?.toString().orEmpty(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
    }
}

private class DiffListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
        val entry = value as? DryRunDiffEntry
        if (entry != null) {
            component.text = entry.relativePath
            component.icon = fileIcon(entry.relativePath)
        }
        return component
    }
}

private fun fileIcon(relativePath: String) =
    FileTypeManager.getInstance().getFileTypeByFileName(relativePath).icon ?: AllIcons.Nodes.Unknown

private fun buildDiffTreeRoot(entries: List<DryRunDiffEntry>): DefaultMutableTreeNode {
    val root = DefaultMutableTreeNode("Changed files")
    val directories = mutableMapOf<List<String>, DefaultMutableTreeNode>(emptyList<String>() to root)

    entries.forEach { entry ->
        val parts = entry.relativePath.split(Regex("[/\\\\]+")).filter(String::isNotBlank)
        val fileName = parts.lastOrNull() ?: entry.relativePath
        val directoryParts = parts.dropLast(1)
        var parent = root

        directoryParts.forEachIndexed { index, part ->
            val key = directoryParts.take(index + 1)
            parent =
                directories.getOrPut(key) {
                    DefaultMutableTreeNode(DiffTreeDirectory(part)).also(parent::add)
                }
        }

        parent.add(DefaultMutableTreeNode(DiffTreeFile(fileName, entry.relativePath, entry)))
    }

    return root
}

internal sealed interface DryRunApplyResult {
    data object Applied : DryRunApplyResult

    data object FileChanged : DryRunApplyResult

    data object FileNotFound : DryRunApplyResult

    data object ReadOnly : DryRunApplyResult

    data object MissingPreview : DryRunApplyResult
}

internal fun applyDryRunChange(
    project: Project,
    change: FolderSortChange,
): DryRunApplyResult {
    val originalText = change.originalText ?: return DryRunApplyResult.MissingPreview
    val sortedText = change.sortedText ?: return DryRunApplyResult.MissingPreview
    val file =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(change.path) ?: return DryRunApplyResult.FileNotFound
    if (!file.isWritable) {
        return DryRunApplyResult.ReadOnly
    }

    val documentManager = FileDocumentManager.getInstance()
    val document = documentManager.getDocument(file)
    if (document != null) {
        if (document.text != originalText) {
            return DryRunApplyResult.FileChanged
        }
        WriteCommandAction
            .writeCommandAction(project)
            .withName("Trier Apply Dry Run Change")
            .run<RuntimeException> {
                document.setText(sortedText)
            }
        documentManager.saveDocument(document)
        return DryRunApplyResult.Applied
    }

    if (VfsUtilCore.loadText(file) != originalText) {
        return DryRunApplyResult.FileChanged
    }

    WriteCommandAction
        .writeCommandAction(project)
        .withName("Trier Apply Dry Run Change")
        .run<RuntimeException> {
            file.setBinaryContent(sortedText.toByteArray(file.charset))
        }
    return DryRunApplyResult.Applied
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
): List<SimpleDiffRequest> = buildDryRunDiffEntries(project, report).map(DryRunDiffEntry::request)

private fun buildDryRunDiffEntries(
    project: Project,
    report: FolderSortReport,
): List<DryRunDiffEntry> {
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

            DryRunDiffEntry(
                relativePath = change.relativePath,
                change = change,
                request =
                    SimpleDiffRequest(
                        "Trier Dry Run: ${change.relativePath}",
                        originalContent,
                        sortedContent,
                        "Current file",
                        "Sorted preview (not written)",
                    ),
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
