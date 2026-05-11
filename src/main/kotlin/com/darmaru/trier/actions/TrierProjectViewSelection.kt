package com.darmaru.trier.actions

import com.intellij.ide.IdeView
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

internal fun AnActionEvent.isProjectViewPopup(): Boolean =
    place == ActionPlaces.PROJECT_VIEW_POPUP ||
        place == "ProjectViewPopupMenu" ||
        place.startsWith("ProjectView")

internal fun AnActionEvent.selectedProjectViewFile(): VirtualFile? {
    if (!isProjectViewPopup()) {
        return null
    }

    return getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.singleOrNull()
        ?: getData(CommonDataKeys.VIRTUAL_FILE)
        ?: getData(CommonDataKeys.NAVIGATABLE_ARRAY)?.singleVirtualFile()
        ?: getData(CommonDataKeys.NAVIGATABLE)?.toVirtualFile()
        ?: getData(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY)?.singleVirtualFile()
        ?: getData(PlatformCoreDataKeys.SELECTED_ITEMS)?.singleVirtualFile()
        ?: getData(PlatformCoreDataKeys.SELECTED_ITEM)?.toVirtualFile()
        ?: getData(CommonDataKeys.PSI_FILE)?.virtualFile
        ?: getData(CommonDataKeys.PSI_ELEMENT)?.toVirtualFile()
        ?: getData(LangDataKeys.IDE_VIEW)?.singleDirectoryVirtualFile()
        ?: getData(PlatformCoreDataKeys.PROJECT_FILE_DIRECTORY)
}

private fun Array<*>.singleVirtualFile(): VirtualFile? =
    singleOrNull()
        ?.toVirtualFile()

private fun Any.toVirtualFile(): VirtualFile? =
    when (this) {
        is VirtualFile -> this
        is AbstractTreeNode<*> -> value?.toVirtualFile()
        is PsiFile -> virtualFile
        is PsiDirectory -> virtualFile
        is PsiElement -> containingFile?.virtualFile
        else -> null
    }

private fun IdeView.singleDirectoryVirtualFile(): VirtualFile? =
    directories
        .singleOrNull()
        ?.virtualFile
