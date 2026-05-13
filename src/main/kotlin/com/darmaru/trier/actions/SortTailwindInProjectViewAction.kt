package com.darmaru.trier.actions

import com.darmaru.trier.services.TrierSortService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class SortTailwindInProjectViewAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val selectedFile = event.selectedProjectViewFile() ?: return
        val service = TrierSortService.getInstance()

        if (selectedFile.isDirectory) {
            service.sortFolderInBackground(project, selectedFile.path, DEFAULT_PROJECT_VIEW_GLOB, dryRun = true)
        } else {
            service.sortFileInBackground(project, selectedFile, dryRun = true)
        }
    }

    override fun update(event: AnActionEvent) {
        val selectedFile = event.selectedProjectViewFile()
        event.presentation.isVisible = event.project != null
        event.presentation.isEnabled = selectedFile != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private const val DEFAULT_PROJECT_VIEW_GLOB = "**/*.{html,js,jsx,ts,tsx,vue,astro,svelte,css,scss,php}"
