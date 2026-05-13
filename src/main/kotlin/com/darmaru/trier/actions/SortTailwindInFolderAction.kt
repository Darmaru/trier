package com.darmaru.trier.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class SortTailwindInFolderAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val initialRootPath = project.basePath.orEmpty()
        val dialog = TrierFolderDialog(project, initialRootPath)
        if (!dialog.showAndGet()) {
            return
        }

        val rootPath = dialog.rootPath()
        if (rootPath.isBlank()) {
            Messages.showErrorDialog(project, "Folder path is required.", "Trier")
            return
        }

        com.darmaru.trier.services.TrierSortService.getInstance().sortFolderInBackground(
            project,
            rootPath,
            dialog.globPattern(),
            dialog.isDryRun(),
        )
    }
}

internal class TrierFolderDialog(
    private val project: Project,
    initialRootPath: String,
) : DialogWrapper(project) {
    private val rootField =
        TextFieldWithBrowseButton().apply {
            text = initialRootPath
        }
    private val globField = JBTextField("**/*.{html,js,jsx,ts,tsx,vue,astro,svelte,css,scss,php}", 50)
    private val dryRunBox = JBCheckBox("Dry run")

    init {
        title = "Sort Tailwind Classes in Folder"
        init()
    }

    fun rootPath(): String = rootField.text.trim()

    fun globPattern(): String = globField.text.trim()

    fun isDryRun(): Boolean = dryRunBox.isSelected

    override fun createCenterPanel(): JComponent = createFolderPanel()

    internal fun createFolderPanel(): JComponent {
        rootField.addActionListener {
            val descriptor =
                FileChooserDescriptor(
                    false,
                    true,
                    false,
                    false,
                    false,
                    false,
                ).withTitle("Select Folder")

            FileChooser.chooseFile(descriptor, project, folderChooserInitialSelection()) { file ->
                rootField.text = file.path
            }
        }

        return JPanel(BorderLayout()).apply {
            add(
                FormBuilder
                    .createFormBuilder()
                    .addLabeledComponent("Folder:", rootField)
                    .addLabeledComponent("Glob pattern:", globField)
                    .addComponent(dryRunBox)
                    .panel,
                BorderLayout.CENTER,
            )
        }
    }

    private fun folderChooserInitialSelection(): VirtualFile? {
        val localFileSystem = LocalFileSystem.getInstance()
        val currentFile =
            rootField.text
                .trim()
                .takeIf(String::isNotBlank)
                ?.let(localFileSystem::findFileByPath)
        if (currentFile != null) {
            return if (currentFile.isDirectory) currentFile else currentFile.parent
        }

        return project.basePath?.let(localFileSystem::findFileByPath)
    }
}
