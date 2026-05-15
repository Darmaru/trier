package com.darmaru.trier.services

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.CacheDiffRequestChainProcessor
import com.intellij.diff.impl.DiffRequestProcessorListener
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
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
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Window
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Action
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.ListSelectionModel
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
            TrierDryRunDiffWindow(project, entries, 0).show()
        }

        else -> TrierDryRunDiffDialog(project, rootPath, report, entries).show()
    }
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

    private val reviewState = DryRunReviewState(entries)
    private val openedDiffWindows = mutableSetOf<Window>()
    private val summaryLabel = JBLabel(summaryText())
    private var applyInProgress = false
    private val closeDialogAction =
        object : DialogWrapperAction("Close") {
            override fun doAction(event: ActionEvent?) {
                closeOpenedDiffWindows()
                close(CANCEL_EXIT_CODE)
            }
        }
    private val copyReportAction =
        object : DialogWrapperAction("Copy Report") {
            override fun doAction(event: ActionEvent?) {
                CopyPasteManager.getInstance().setContents(StringSelection(currentReportText()))
            }
        }
    private val openDiffAction =
        object : DialogWrapperAction("Open Diff") {
            override fun doAction(event: ActionEvent?) {
                showDiffsFromSelection()
            }
        }
    private val applySelectedAction =
        object : DialogWrapperAction("Apply Selected") {
            override fun doAction(event: ActionEvent?) {
                applySelectedChanges()
            }
        }
    private val diffListModel = DefaultListModel<DryRunDiffEntry>()
    private val diffTree =
        Tree(DefaultTreeModel(buildDiffTreeRoot(reviewState.allEntries))).apply {
            isRootVisible = false
            showsRootHandles = true
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
            selectedIndex = 0
            cellRenderer = DiffListCellRenderer()
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        if (event.clickCount == 2 && selectedDiffIndexOrNull() != null) {
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
                val selectedEntries = selectedDiffEntries()
                cardLayout.show(diffViewPanel, if (isSelected) GROUPED_VIEW else LIST_VIEW)
                selectDiffEntries(selectedEntries)
                updateActionStates()
            }
        }

    init {
        title = "Trier Dry Run Report"
        setOKButtonText("Apply")
        resetListModel()
        init()
        configureSelectionModes()
        selectDiffIndex(0)
        diffTree.addTreeSelectionListener { updateActionStates() }
        diffList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateActionStates()
            }
        }
        updateActionStates()
    }

    override fun createCenterPanel(): JComponent {
        val header =
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.emptyBottom(8)
                add(summaryLabel, BorderLayout.CENTER)
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
            openDiffAction,
            applySelectedAction,
            okAction,
        )

    override fun createLeftSideActions(): Array<Action> =
        arrayOf(
            closeDialogAction,
            copyReportAction,
        )

    override fun doOKAction() {
        applyAllChanges()
    }

    override fun doCancelAction() {
        closeOpenedDiffWindows()
        super.doCancelAction()
    }

    private fun showDiffsFromSelection() {
        val chainSelection = reviewState.diffChainSelection(selectedDiffEntries()) ?: return
        showDiffs(chainSelection.entries, chainSelection.selectedIndex)
    }

    private fun showDiffs(
        entries: List<DryRunDiffEntry>,
        selectedIndex: Int,
    ) {
        if (entries.isEmpty()) {
            return
        }
        closeOpenedDiffWindows()
        val diffWindow =
            TrierDryRunDiffWindow(
                project = project,
                entries = entries,
                selectedIndex = selectedIndex,
                parent = window,
                onEntryApplied = ::applyEntryFromDiff,
            )
        diffWindow.show()
        diffWindow.window?.let(openedDiffWindows::add)
    }

    private fun applyEntryFromDiff(entry: DryRunDiffEntry) {
        if (reviewState.contains(entry)) {
            removeEntry(entry)
        }
    }

    private fun applyAllChanges() {
        applyChanges(reviewState.allEntries)
    }

    private fun applySelectedChanges() {
        applyChanges(selectedDiffEntries())
    }

    private fun applyChanges(entries: List<DryRunDiffEntry>) {
        val entriesToApply = entries.filter(reviewState::contains)
        if (entriesToApply.isEmpty() || applyInProgress) {
            return
        }
        closeOpenedDiffWindows()
        setApplyInProgress(true)
        val applyModalityState = ModalityState.stateForComponent(diffViewPanel)
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Trier: Apply Dry Run Changes", true) {
                private val batchResult = DryRunApplyBatchResultCollector()

                override fun run(indicator: ProgressIndicator) {
                    applyDryRunChanges(project, entriesToApply, indicator, applyModalityState, batchResult)
                }

                override fun onSuccess() {
                    finishBackgroundApply(batchResult.result())
                }

                override fun onCancel() {
                    finishBackgroundApply(batchResult.result())
                }

                override fun onThrowable(error: Throwable) {
                    val currentResult = batchResult.result()
                    val failedEntry = entriesToApply.firstOrNull { it !in currentResult.appliedEntries }
                    if (failedEntry != null) {
                        batchResult.addFailure(
                            failedEntry.toApplyFailure(error.localizedMessage ?: "Unexpected apply failure"),
                        )
                    }
                    finishBackgroundApply(batchResult.result())
                }
            },
        )
    }

    private fun selectedDiffIndex(): Int = selectedDiffIndexOrNull() ?: 0

    private fun selectedDiffIndexOrNull(): Int? {
        val entry = selectedDiffEntryOrNull() ?: return null
        return reviewState.indexOf(entry)
    }

    private fun selectedDiffEntryOrNull(): DryRunDiffEntry? = selectedDiffEntries().firstOrNull()

    private fun selectedDiffEntries(): List<DryRunDiffEntry> {
        val selectedEntries =
            if (!groupByBox.isSelected) {
                diffList.selectedValuesList
            } else {
                diffTree.selectionPaths
                    ?.mapNotNull { it.lastPathComponent as? DefaultMutableTreeNode }
                    ?.flatMap(::entriesInNode)
                    .orEmpty()
            }

        return reviewState.entriesInOrder(selectedEntries)
    }

    private fun entriesInNode(node: DefaultMutableTreeNode): List<DryRunDiffEntry> {
        val userObject = node.userObject
        if (userObject is DiffTreeFile) {
            return listOf(userObject.entry)
        }

        return buildList {
            for (index in 0 until node.childCount) {
                addAll(entriesInNode(node.getChildAt(index) as DefaultMutableTreeNode))
            }
        }
    }

    private fun selectDiffIndex(index: Int) {
        if (reviewState.isEmpty) {
            diffList.clearSelection()
            diffTree.clearSelection()
            return
        }

        val safeIndex = index.coerceIn(0, reviewState.lastIndex)
        selectDiffEntries(listOfNotNull(reviewState.entryAt(safeIndex)))
    }

    private fun selectDiffEntries(entries: List<DryRunDiffEntry>) {
        val entriesToSelect = entries.filter(reviewState::contains).ifEmpty { reviewState.allEntries.take(1) }
        if (entriesToSelect.isEmpty()) {
            diffList.clearSelection()
            diffTree.clearSelection()
            return
        }

        diffList.clearSelection()
        entriesToSelect
            .mapNotNull(reviewState::indexOf)
            .forEach { index ->
                diffList.addSelectionInterval(index, index)
            }

        val treePaths =
            entriesToSelect
                .mapNotNull { entry -> findFileNode(modelRoot(), entry)?.let { TreePath(it.path) } }
                .toTypedArray()
        if (treePaths.isEmpty()) {
            diffTree.clearSelection()
        } else {
            diffTree.selectionPaths = treePaths
        }
    }

    private fun removeEntry(entry: DryRunDiffEntry) {
        val result = reviewState.removeEntry(entry, selectedDiffIndex())
        resetListModel()
        resetTreeModel()
        updateSummary()
        if (result.shouldClose) {
            updateActionStates()
            close(OK_EXIT_CODE)
        } else {
            result.selectedIndex?.let(::selectDiffIndex)
            updateActionStates()
        }
    }

    private fun removeEntries(entries: List<DryRunDiffEntry>) {
        val result = reviewState.removeEntries(entries, selectedDiffIndex())
        if (!result.removed) {
            updateActionStates()
            return
        }

        resetListModel()
        resetTreeModel()
        updateSummary()
        if (result.shouldClose) {
            updateActionStates()
            close(OK_EXIT_CODE)
        } else {
            result.selectedIndex?.let(::selectDiffIndex)
            updateActionStates()
        }
    }

    private fun finishBackgroundApply(batchResult: DryRunApplyBatchResult) {
        setApplyInProgress(false)
        removeEntries(batchResult.appliedEntries)
        if (batchResult.failures.isNotEmpty()) {
            showBackgroundApplyFailures(batchResult.appliedEntries.size, batchResult.failures)
        }
    }

    private fun showBackgroundApplyFailures(
        appliedCount: Int,
        failedEntries: List<DryRunApplyFailure>,
    ) {
        Messages.showWarningDialog(project, buildDryRunApplyFailureMessage(appliedCount, failedEntries), "Trier")
    }

    private fun resetListModel() {
        diffListModel.clear()
        reviewState.allEntries.forEach(diffListModel::addElement)
    }

    private fun resetTreeModel() {
        diffTree.model = DefaultTreeModel(buildDiffTreeRoot(reviewState.allEntries))
        configureSelectionModes()
        diffTree.expandAllRows()
    }

    private fun configureSelectionModes() {
        diffTree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        diffList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }

    private fun updateSummary() {
        summaryLabel.text = summaryText()
    }

    private fun updateActionStates() {
        val hasSelection = selectedDiffEntries().isNotEmpty()
        closeDialogAction.isEnabled = !applyInProgress
        copyReportAction.isEnabled = !applyInProgress
        openDiffAction.isEnabled = !applyInProgress && !reviewState.isEmpty
        applySelectedAction.isEnabled = !applyInProgress && hasSelection
        okAction.isEnabled = !applyInProgress && !reviewState.isEmpty
        groupByBox.isEnabled = !applyInProgress
        diffTree.isEnabled = !applyInProgress
        diffList.isEnabled = !applyInProgress
    }

    private fun setApplyInProgress(value: Boolean) {
        applyInProgress = value
        updateActionStates()
    }

    private fun summaryText(): String = reviewState.summaryText()

    private fun currentReportText(): String =
        buildDryRunReportText(
            rootPath,
            buildRemainingDryRunReport(report, reviewState.remainingChanges),
        )

    private fun closeOpenedDiffWindows() {
        openedDiffWindows
            .filter(Window::isDisplayable)
            .forEach(Window::dispose)
        openedDiffWindows.clear()
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

private class TrierDryRunDiffWindow(
    private val project: Project,
    entries: List<DryRunDiffEntry>,
    selectedIndex: Int,
    parent: Component? = null,
    private val onEntryApplied: (DryRunDiffEntry) -> Unit = {},
) : DialogWrapper(project, parent, false, IdeModalityType.MODELESS) {
    private val diffState = DryRunDiffWindowState(entries, selectedIndex)
    private val diffPanel = JPanel(BorderLayout())
    private var diffProcessor: TrierDryRunDiffProcessor? = null
    private val previousAction =
        object : DialogWrapperAction("Previous") {
            override fun doAction(event: ActionEvent?) {
                showEntry(diffState.snapshot().currentIndex - 1)
            }
        }
    private val nextAction =
        object : DialogWrapperAction("Next") {
            override fun doAction(event: ActionEvent?) {
                showEntry(diffState.snapshot().currentIndex + 1)
            }
        }
    private val closeAction =
        object : DialogWrapperAction("Close") {
            override fun doAction(event: ActionEvent?) {
                close(CANCEL_EXIT_CODE)
            }
        }

    init {
        title = diffState.snapshot().title
        setOKButtonText("Apply")
        init()
        rebuildDiffProcessor(diffState.snapshot().currentIndex)
    }

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout()).apply {
            preferredSize = Dimension(1040, 680)
            border = JBUI.Borders.emptyBottom(8)
            add(diffPanel, BorderLayout.CENTER)
        }

    override fun getPreferredFocusedComponent(): JComponent? = diffProcessor?.preferredFocusedComponent

    override fun createLeftSideActions(): Array<Action> = arrayOf(previousAction, nextAction)

    override fun createActions(): Array<Action> =
        arrayOf(
            closeAction,
            okAction,
        )

    override fun doOKAction() {
        applyCurrentEntry()
    }

    override fun doCancelAction() {
        close(CANCEL_EXIT_CODE)
    }

    override fun dispose() {
        disposeDiffProcessor()
        super.dispose()
    }

    private fun applyCurrentEntry() {
        val entry = diffState.snapshot().currentEntry ?: return
        when (val result = applyDryRunChange(project, entry.change)) {
            DryRunApplyResult.Applied -> {
                val advance = diffState.removeCurrentEntryAfterApply() ?: return
                onEntryApplied(advance.appliedEntry)
                if (advance.nextIndex == null) {
                    close(OK_EXIT_CODE)
                } else {
                    rebuildDiffProcessor(advance.nextIndex)
                }
            }

            else ->
                Messages.showWarningDialog(
                    project,
                    dryRunApplyResultMessage(entry.change, result),
                    "Trier",
                )
        }
    }

    private fun showEntry(index: Int) {
        if (!diffState.showEntry(index)) {
            return
        }
        diffProcessor?.setCurrentRequest(index)
        updateTitleAndActions()
    }

    private fun rebuildDiffProcessor(selectedIndex: Int) {
        disposeDiffProcessor()
        diffState.showEntry(selectedIndex)
        val snapshot = diffState.snapshot()
        val processor =
            TrierDryRunDiffProcessor(
                project = project,
                requestChain =
                    SimpleDiffRequestChain(
                        snapshot.entries.map(DryRunDiffEntry::request),
                        snapshot.currentIndex,
                    ),
                onNavigate = ::syncCurrentIndexFromProcessor,
            )
        processor.addListener(
            object : DiffRequestProcessorListener {
                override fun onViewerChanged() {
                    syncCurrentIndexFromProcessor()
                }
            },
            processor,
        )
        diffProcessor = processor
        diffPanel.removeAll()
        diffPanel.add(processor.component, BorderLayout.CENTER)
        diffPanel.revalidate()
        diffPanel.repaint()
        processor.updateRequest()
        updateTitleAndActions()
    }

    private fun disposeDiffProcessor() {
        diffProcessor?.let { processor ->
            if (!processor.isDisposed) {
                Disposer.dispose(processor)
            }
        }
        diffProcessor = null
    }

    private fun syncCurrentIndexFromProcessor() {
        val activeRequest = diffProcessor?.activeRequest as? SimpleDiffRequest ?: return
        if (diffState.syncToRequest(activeRequest)) {
            updateTitleAndActions()
        }
    }

    private fun updateTitleAndActions() {
        title = diffState.snapshot().title
        updateActionStates()
    }

    private fun updateActionStates() {
        val snapshot = diffState.snapshot()
        previousAction.isEnabled = snapshot.canGoPrevious
        nextAction.isEnabled = snapshot.canGoNext
        okAction.isEnabled = snapshot.currentEntry != null
    }
}

private class TrierDryRunDiffProcessor(
    project: Project,
    requestChain: DiffRequestChain,
    private val onNavigate: () -> Unit,
) : CacheDiffRequestChainProcessor(project, requestChain) {
    override fun createAfterNavigateCallback(): Runnable = Runnable(onNavigate)
}

internal data class DryRunDiffEntry(
    val relativePath: String,
    val change: FolderSortChange,
    val request: SimpleDiffRequest,
    val icon: Icon,
) {
    override fun toString(): String = relativePath
}

internal data class DryRunReviewRemoveResult(
    val removed: Boolean,
    val selectedIndex: Int?,
    val shouldClose: Boolean,
)

internal class DryRunReviewState(
    entries: List<DryRunDiffEntry>,
) {
    private val entries = entries.toMutableList()

    val allEntries: List<DryRunDiffEntry>
        get() = entries.toList()

    val remainingChanges: List<FolderSortChange>
        get() = entries.map(DryRunDiffEntry::change)

    val isEmpty: Boolean
        get() = entries.isEmpty()

    val lastIndex: Int
        get() = entries.lastIndex

    fun contains(entry: DryRunDiffEntry): Boolean = entry in entries

    fun indexOf(entry: DryRunDiffEntry): Int? = entries.indexOf(entry).takeIf { it >= 0 }

    fun entryAt(index: Int): DryRunDiffEntry? = entries.getOrNull(index)

    fun entriesInOrder(selectedEntries: Collection<DryRunDiffEntry>): List<DryRunDiffEntry> {
        val selectedEntrySet = selectedEntries.toSet()
        return entries.filter { it in selectedEntrySet }
    }

    fun diffChainSelection(selectedEntries: Collection<DryRunDiffEntry>): DryRunDiffChainSelection? =
        buildDryRunDiffChainSelection(entries, selectedEntries.toList())

    fun removeEntry(
        entry: DryRunDiffEntry,
        fallbackIndex: Int,
    ): DryRunReviewRemoveResult {
        val previousIndex = indexOf(entry) ?: fallbackIndex
        if (!entries.remove(entry)) {
            return DryRunReviewRemoveResult(
                removed = false,
                selectedIndex = selectedIndexAfterNoop(),
                shouldClose = false,
            )
        }
        return removeResult(previousIndex)
    }

    fun removeEntries(
        entriesToRemove: Collection<DryRunDiffEntry>,
        fallbackIndex: Int,
    ): DryRunReviewRemoveResult {
        val selectedEntrySet = entriesToRemove.filter(::contains).toSet()
        if (selectedEntrySet.isEmpty()) {
            return DryRunReviewRemoveResult(
                removed = false,
                selectedIndex = selectedIndexAfterNoop(),
                shouldClose = false,
            )
        }

        entries.removeAll(selectedEntrySet)
        return removeResult(fallbackIndex)
    }

    fun summaryText(): String {
        val remaining = entries.size
        val fileWord = if (remaining == 1) "file" else "files"
        return buildString {
            append("Dry run has $remaining remaining $fileWord that would be updated. ")
            append("Select files or directories to review or apply selected changes.")
        }
    }

    private fun removeResult(previousIndex: Int): DryRunReviewRemoveResult =
        if (entries.isEmpty()) {
            DryRunReviewRemoveResult(removed = true, selectedIndex = null, shouldClose = true)
        } else {
            DryRunReviewRemoveResult(
                removed = true,
                selectedIndex = previousIndex.coerceAtMost(entries.lastIndex),
                shouldClose = false,
            )
        }

    private fun selectedIndexAfterNoop(): Int? = entries.indices.firstOrNull()
}

internal data class DryRunDiffWindowSnapshot(
    val currentEntry: DryRunDiffEntry?,
    val currentIndex: Int,
    val entries: List<DryRunDiffEntry>,
    val title: String,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
)

internal data class DryRunDiffApplyAdvance(
    val appliedEntry: DryRunDiffEntry,
    val nextIndex: Int?,
)

internal class DryRunDiffWindowState(
    entries: List<DryRunDiffEntry>,
    selectedIndex: Int,
) {
    private val entries = entries.toMutableList()
    private var currentIndex = if (entries.isEmpty()) 0 else selectedIndex.coerceIn(entries.indices)

    fun snapshot(): DryRunDiffWindowSnapshot =
        DryRunDiffWindowSnapshot(
            currentEntry = currentEntry(),
            currentIndex = currentIndex,
            entries = entries.toList(),
            title = currentTitle(),
            canGoPrevious = currentIndex > 0,
            canGoNext = currentIndex < entries.lastIndex,
        )

    fun showEntry(index: Int): Boolean {
        if (index !in entries.indices) {
            return false
        }
        currentIndex = index
        return true
    }

    fun syncToRequest(request: SimpleDiffRequest): Boolean {
        val activeIndex = entries.indexOfFirst { it.request === request }
        if (activeIndex < 0) {
            return false
        }
        currentIndex = activeIndex
        return true
    }

    fun removeCurrentEntryAfterApply(): DryRunDiffApplyAdvance? {
        val appliedEntry = currentEntry() ?: return null
        val appliedIndex = currentIndex
        entries.removeAt(appliedIndex)
        currentIndex = nextDryRunDiffIndexAfterRemoval(appliedIndex, entries.size) ?: 0
        return DryRunDiffApplyAdvance(
            appliedEntry = appliedEntry,
            nextIndex = entries.indices.firstOrNull()?.let { currentIndex },
        )
    }

    private fun currentEntry(): DryRunDiffEntry? = entries.getOrNull(currentIndex)

    private fun currentTitle(): String {
        val entry = currentEntry()
        return if (entry == null) {
            "Trier Dry Run"
        } else {
            "Trier Dry Run: ${entry.relativePath} (${currentIndex + 1}/${entries.size})"
        }
    }
}

internal data class DryRunDiffChainSelection(
    val entries: List<DryRunDiffEntry>,
    val selectedIndex: Int,
)

internal fun buildDryRunDiffChainSelection(
    allEntries: List<DryRunDiffEntry>,
    selectedEntries: List<DryRunDiffEntry>,
): DryRunDiffChainSelection? {
    if (allEntries.isEmpty()) {
        return null
    }

    val selectedEntrySet = selectedEntries.toSet()
    val selectedIndex = allEntries.indexOfFirst { it in selectedEntrySet }.takeIf { it >= 0 } ?: 0
    return DryRunDiffChainSelection(allEntries, selectedIndex)
}

internal fun nextDryRunDiffIndexAfterRemoval(
    removedIndex: Int,
    remainingSize: Int,
): Int? {
    if (remainingSize <= 0) {
        return null
    }
    return removedIndex.coerceAtMost(remainingSize - 1)
}

internal data class DryRunApplyFailure(
    val relativePath: String,
    val path: String,
    val message: String,
)

internal data class DryRunApplyBatchResult(
    val appliedEntries: List<DryRunDiffEntry> = emptyList(),
    val failures: List<DryRunApplyFailure> = emptyList(),
)

internal class DryRunApplyBatchResultCollector {
    private val appliedEntries = mutableListOf<DryRunDiffEntry>()
    private val failures = mutableListOf<DryRunApplyFailure>()

    fun addApplied(entry: DryRunDiffEntry) {
        appliedEntries += entry
    }

    fun addFailure(failure: DryRunApplyFailure) {
        failures += failure
    }

    fun result(): DryRunApplyBatchResult =
        DryRunApplyBatchResult(
            appliedEntries = appliedEntries.toList(),
            failures = failures.toList(),
        )
}

internal fun applyDryRunChanges(
    project: Project,
    entries: List<DryRunDiffEntry>,
    indicator: ProgressIndicator,
    modalityState: ModalityState,
): DryRunApplyBatchResult {
    val collector = DryRunApplyBatchResultCollector()
    applyDryRunChanges(project, entries, indicator, modalityState, collector)
    return collector.result()
}

internal fun applyDryRunChanges(
    project: Project,
    entries: List<DryRunDiffEntry>,
    indicator: ProgressIndicator,
    modalityState: ModalityState,
    collector: DryRunApplyBatchResultCollector,
) {
    entries.forEachIndexed { index, entry ->
        indicator.checkCanceled()
        indicator.text = "Applying Trier dry-run changes"
        indicator.text2 = entry.relativePath
        indicator.fraction = index.toDouble() / entries.size.toDouble()

        runCatching {
            applyDryRunChangeOnEdt(project, entry.change, modalityState)
        }.onSuccess { applyResult ->
            if (applyResult == DryRunApplyResult.Applied) {
                collector.addApplied(entry)
            } else {
                collector.addFailure(entry.toApplyFailure(dryRunApplyResultMessage(entry.change, applyResult)))
            }
        }.onFailure { error ->
            collector.addFailure(entry.toApplyFailure(error.localizedMessage ?: "Unexpected apply failure"))
        }
    }

    indicator.fraction = 1.0
}

private fun DryRunDiffEntry.toApplyFailure(message: String): DryRunApplyFailure =
    DryRunApplyFailure(
        relativePath = relativePath,
        path = change.path,
        message = message,
    )

internal fun buildDryRunApplyFailureMessage(
    appliedCount: Int,
    failures: List<DryRunApplyFailure>,
    maxFailures: Int = 5,
): String =
    buildString {
        if (appliedCount > 0) {
            appendLine("Applied $appliedCount dry-run changes.")
            appendLine()
        }
        appendLine("Could not apply ${failures.size} dry-run changes:")
        failures.take(maxFailures).forEach { failure ->
            appendLine("- ${failure.relativePath}: ${failure.message}")
        }
        if (failures.size > maxFailures) {
            appendLine("- ...and ${failures.size - maxFailures} more.")
        }
    }.trim()

private fun applyDryRunChangeOnEdt(
    project: Project,
    change: FolderSortChange,
    modalityState: ModalityState,
): DryRunApplyResult {
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) {
        return applyDryRunChange(project, change)
    }

    val result = AtomicReference<DryRunApplyResult>()
    application.invokeAndWait(
        {
            result.set(applyDryRunChange(project, change))
        },
        modalityState,
    )
    return result.get()
}

internal fun dryRunApplyResultMessage(
    change: FolderSortChange,
    result: DryRunApplyResult,
): String =
    when (result) {
        DryRunApplyResult.Applied -> "Applied"
        DryRunApplyResult.FileChanged ->
            "The file changed after the dry run. Run dry run again to preview and apply the latest content."
        DryRunApplyResult.FileNotFound -> "File not found: ${change.path}"
        DryRunApplyResult.ReadOnly -> "File is read-only: ${change.path}"
        DryRunApplyResult.MissingPreview -> "Dry-run preview is incomplete for: ${change.path}"
    }

private data class DiffTreeDirectory(
    val name: String,
) {
    override fun toString(): String = name
}

private data class DiffTreeFile(
    val name: String,
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
                icon = userObject.entry.icon
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
            component.icon = entry.icon
        }
        return component
    }
}

private fun fileIcon(relativePath: String) =
    FileTypeManager.getInstance().getFileTypeByFileName(relativePath).icon ?: AllIcons.Nodes.Unknown

internal fun buildDiffTreeRoot(entries: List<DryRunDiffEntry>): DefaultMutableTreeNode {
    val root = DefaultMutableTreeNode("Changed files")
    val directories = mutableMapOf(emptyList<String>() to root)

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

        parent.add(DefaultMutableTreeNode(DiffTreeFile(fileName, entry)))
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

@TestOnly
internal var dryRunApplyChangeOverride: ((Project, FolderSortChange) -> DryRunApplyResult)? = null

internal fun applyDryRunChange(
    project: Project,
    change: FolderSortChange,
): DryRunApplyResult {
    dryRunApplyChangeOverride?.let { return it(project, change) }
    return applyDryRunChangeImpl(project, change)
}

private fun applyDryRunChangeImpl(
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
    rootPath: String,
    report: FolderSortReport,
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

internal fun buildDryRunDiffEntries(
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
                icon = fileIcon(change.relativePath),
                request =
                    SimpleDiffRequest(
                        "Trier Dry Run: ${change.relativePath}",
                        originalContent,
                        sortedContent,
                        "Original snapshot",
                        "Sorted preview (not written)",
                    ),
            )
        }
}

internal fun buildRemainingDryRunReport(
    report: FolderSortReport,
    remainingChanges: List<FolderSortChange>,
): FolderSortReport =
    report.copy(
        changed = remainingChanges.size,
        changes = remainingChanges,
    )

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
