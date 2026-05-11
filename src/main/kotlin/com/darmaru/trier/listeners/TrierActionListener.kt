package com.darmaru.trier.listeners

import com.darmaru.trier.services.TrierSortService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.util.TextRange

class TrierActionListener : AnActionListener {
    override fun afterActionPerformed(
        action: AnAction,
        event: AnActionEvent,
        result: AnActionResult,
    ) {
        val actionId = ActionManager.getInstance().getId(action)
        if (actionId != "ReformatCode" && actionId != "EditorReformat") {
            return
        }

        val dataContext = event.dataContext
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        val selectionRange =
            editor?.selectionModel?.takeIf { it.hasSelection() }?.let {
                TextRange(it.selectionStart, it.selectionEnd)
            }
        TrierSortService.getInstance().handleReformat(project, editor, selectionRange)
    }
}
