package com.darmaru.trier.actions

import com.darmaru.trier.services.TrierSortService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class SortTailwindInProjectViewAction
    internal constructor(
        private val sortFolder: (Project, String, String, Boolean) -> Unit,
        private val sortFile: (Project, VirtualFile, Boolean) -> Unit,
    ) : DumbAwareAction() {
        constructor() : this(
            sortFolder = { project, rootPath, globPattern, dryRun ->
                TrierSortService.getInstance().sortFolderInBackground(project, rootPath, globPattern, dryRun)
            },
            sortFile = { project, file, dryRun ->
                TrierSortService.getInstance().sortFileInBackground(project, file, dryRun)
            },
        )

        override fun actionPerformed(event: AnActionEvent) {
            val project = event.project ?: return
            val selectedFile = event.selectedProjectViewFile() ?: return

            if (selectedFile.isDirectory) {
                sortFolder(project, selectedFile.path, DEFAULT_PROJECT_VIEW_GLOB, true)
            } else {
                sortFile(project, selectedFile, true)
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
