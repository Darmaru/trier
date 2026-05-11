package com.darmaru.trier.actions

import com.darmaru.trier.services.TrierSortService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.TextRange

class SortTailwindInEditorAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val selectionModel = editor.selectionModel
        val selectionRange =
            if (selectionModel.hasSelection()) {
                TextRange(selectionModel.selectionStart, selectionModel.selectionEnd)
            } else {
                null
            }
        TrierSortService.getInstance().sortCurrentEditor(project, editor, "Manual", selectionRange)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && event.getData(CommonDataKeys.EDITOR) != null
    }
}
