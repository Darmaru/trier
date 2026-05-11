package com.darmaru.trier.actions

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TrierProjectViewSelectionTest : BasePlatformTestCase() {
    fun testRecognizesProjectViewPopupPlaces() {
        assertTrue(projectViewEvent(SimpleDataContext.EMPTY_CONTEXT).isProjectViewPopup())
        assertTrue(projectViewEvent(SimpleDataContext.EMPTY_CONTEXT, "ProjectViewPopupMenu").isProjectViewPopup())
        assertTrue(projectViewEvent(SimpleDataContext.EMPTY_CONTEXT, "ProjectViewSomething").isProjectViewPopup())
        assertFalse(projectViewEvent(SimpleDataContext.EMPTY_CONTEXT, ActionPlaces.MAIN_MENU).isProjectViewPopup())
    }

    fun testSelectsSingleVirtualFileArrayEntry() {
        val file = myFixture.configureByText("component.html", "<div></div>").virtualFile
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(file))
                    .build(),
            )

        assertEquals(file, event.selectedProjectViewFile())
    }

    fun testIgnoresMultipleVirtualFileArrayEntries() {
        val first = myFixture.configureByText("first.html", "<div></div>").virtualFile
        val second = myFixture.configureByText("second.html", "<div></div>").virtualFile
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(first, second))
                    .build(),
            )

        assertNull(event.selectedProjectViewFile())
    }

    fun testSelectsPlainVirtualFile() {
        val file = myFixture.configureByText("component.html", "<div></div>").virtualFile
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.VIRTUAL_FILE, file)
                    .build(),
            )

        assertEquals(file, event.selectedProjectViewFile())
    }

    fun testSelectsPsiFileVirtualFile() {
        val psiFile = myFixture.configureByText("component.html", "<div></div>")
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.PSI_FILE, psiFile)
                    .build(),
            )

        assertEquals(psiFile.virtualFile, event.selectedProjectViewFile())
    }

    fun testSelectsPsiElementContainingFile() {
        val psiFile = myFixture.configureByText("component.html", "<div></div>")
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.PSI_ELEMENT, psiFile.firstChild)
                    .build(),
            )

        assertEquals(psiFile.virtualFile, event.selectedProjectViewFile())
    }

    fun testSelectsSingleIdeViewDirectory() {
        val directory = myFixture.configureByText("component.html", "<div></div>").containingDirectory
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(LangDataKeys.IDE_VIEW, singleDirectoryView(directory))
                    .build(),
            )

        assertEquals(directory.virtualFile, event.selectedProjectViewFile())
    }

    fun testIgnoresProjectViewDataOutsideProjectViewPopup() {
        val file = myFixture.configureByText("component.html", "<div></div>").virtualFile
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.VIRTUAL_FILE, file)
                    .build(),
                ActionPlaces.MAIN_MENU,
            )

        assertNull(event.selectedProjectViewFile())
    }

    private fun singleDirectoryView(directory: PsiDirectory): IdeView =
        object : IdeView {
            override fun getDirectories(): Array<PsiDirectory> = arrayOf(directory)

            override fun getOrChooseDirectory(): PsiDirectory = directory

            override fun selectElement(element: PsiElement) = Unit
        }

    private fun projectViewEvent(
        dataContext: DataContext,
        place: String = ActionPlaces.PROJECT_VIEW_POPUP,
    ): AnActionEvent =
        AnActionEvent.createEvent(
            dataContext,
            Presentation(),
            place,
            ActionUiKind.POPUP,
            null,
        )
}
