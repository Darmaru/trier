package com.darmaru.trier.actions

import com.darmaru.trier.services.TrierSortService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class SortTailwindInFileAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.selectedProjectViewFile()?.takeUnless { it.isDirectory } ?: return
        TrierSortService.getInstance().sortFileInBackground(project, file)
    }

    override fun update(event: AnActionEvent) {
        val selectedFile = event.selectedProjectViewFile()
        event.presentation.isVisible = event.project != null
        event.presentation.isEnabled =
            selectedFile != null &&
            !selectedFile.isDirectory
    }
}
