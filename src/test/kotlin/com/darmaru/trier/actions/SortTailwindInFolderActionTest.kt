package com.darmaru.trier.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SortTailwindInFolderActionTest : BasePlatformTestCase() {
    fun testActionNoOpsWithoutProject() {
        SortTailwindInFolderAction().actionPerformed(
            AnActionEvent.createEvent(
                SimpleDataContext.EMPTY_CONTEXT,
                Presentation(),
                ActionPlaces.MAIN_MENU,
                ActionUiKind.NONE,
                null,
            ),
        )
    }

    fun testFolderDialogDefaults() {
        val dialog = TrierFolderDialog(project, "/tmp/project")
        try {
            assertEquals("/tmp/project", dialog.rootPath())
            assertEquals("**/*.{html,js,jsx,ts,tsx,vue,astro,svelte,css,scss,php}", dialog.globPattern())
            assertFalse(dialog.isDryRun())
            assertNotNull(dialog.createFolderPanel())
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }
}
