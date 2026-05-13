package com.darmaru.trier.actions

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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

    fun testSelectsSinglePsiElementArrayEntry() {
        val psiFile = myFixture.configureByText("component.html", "<div></div>")
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, arrayOf(psiFile.firstChild))
                    .build(),
            )

        assertEquals(psiFile.virtualFile, event.selectedProjectViewFile())
    }

    fun testSelectsSingleSelectedItemEntry() {
        val file = myFixture.configureByText("component.html", "<div></div>").virtualFile
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(PlatformCoreDataKeys.SELECTED_ITEM, file)
                    .build(),
            )

        assertEquals(file, event.selectedProjectViewFile())
    }

    fun testSelectsSingleSelectedItemsEntry() {
        val file = myFixture.configureByText("component.html", "<div></div>").virtualFile
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(PlatformCoreDataKeys.SELECTED_ITEMS, arrayOf(file))
                    .build(),
            )

        assertEquals(file, event.selectedProjectViewFile())
    }

    fun testSelectsSingleNavigatableEntry() {
        val psiFile = myFixture.configureByText("component.html", "<div></div>")
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.NAVIGATABLE, psiFile)
                    .build(),
            )

        assertEquals(psiFile.virtualFile, event.selectedProjectViewFile())
    }

    fun testSelectsSingleNavigatableArrayEntry() {
        val psiFile = myFixture.configureByText("component.html", "<div></div>")
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.NAVIGATABLE_ARRAY, arrayOf(psiFile))
                    .build(),
            )

        assertEquals(psiFile.virtualFile, event.selectedProjectViewFile())
    }

    fun testSelectsProjectFileDirectoryFallback() {
        val directory = myFixture.configureByText("component.html", "<div></div>").containingDirectory
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(PlatformCoreDataKeys.PROJECT_FILE_DIRECTORY, directory.virtualFile)
                    .build(),
            )

        assertEquals(directory.virtualFile, event.selectedProjectViewFile())
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

    fun testIgnoresMultipleIdeViewDirectories() {
        val first = myFixture.configureByText("first.html", "<div></div>").containingDirectory
        val second = myFixture.configureByText("second.html", "<div></div>").containingDirectory
        val event =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(LangDataKeys.IDE_VIEW, directoryView(first, second))
                    .build(),
            )

        assertNull(event.selectedProjectViewFile())
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

    fun testProjectViewActionUpdateUsesProjectViewSelectionFallback() {
        val file = myFixture.configureByText("component.html", "<div></div>").virtualFile
        val action = SortTailwindInProjectViewAction()
        val noSelectionEvent =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .build(),
            )
        val selectedFileEvent =
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(CommonDataKeys.VIRTUAL_FILE, file)
                    .build(),
            )
        val noProjectEvent = projectViewEvent(SimpleDataContext.EMPTY_CONTEXT)

        action.update(noSelectionEvent)
        action.update(selectedFileEvent)
        action.update(noProjectEvent)

        assertTrue(noSelectionEvent.presentation.isVisible)
        assertTrue(noSelectionEvent.presentation.isEnabled)
        assertTrue(selectedFileEvent.presentation.isVisible)
        assertTrue(selectedFileEvent.presentation.isEnabled)
        assertFalse(noProjectEvent.presentation.isVisible)
        assertFalse(noProjectEvent.presentation.isEnabled)
    }

    fun testProjectViewActionNoOpsWithoutProjectViewTarget() {
        val action = SortTailwindInProjectViewAction()

        action.actionPerformed(projectViewEvent(SimpleDataContext.EMPTY_CONTEXT))
        action.actionPerformed(
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .build(),
                ActionPlaces.MAIN_MENU,
            ),
        )

        assertEquals(ActionUpdateThread.EDT, action.actionUpdateThread)
    }

    fun testProjectViewActionDispatchesSelectedFileAsDryRun() {
        val file = myFixture.configureByText("component.html", "<div></div>").virtualFile
        val folderCalls = mutableListOf<String>()
        val fileCalls = mutableListOf<Pair<VirtualFile, Boolean>>()
        val action =
            SortTailwindInProjectViewAction(
                sortFolder = { _, rootPath, _, _ -> folderCalls += rootPath },
                sortFile = { _: Project, selectedFile: VirtualFile, dryRun: Boolean ->
                    fileCalls += selectedFile to dryRun
                },
            )

        action.actionPerformed(
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(CommonDataKeys.VIRTUAL_FILE, file)
                    .build(),
            ),
        )

        assertTrue(folderCalls.isEmpty())
        assertEquals(listOf(file to true), fileCalls)
    }

    fun testProjectViewActionDispatchesSelectedDirectoryWithDefaultGlobAsDryRun() {
        val directory = myFixture.configureByText("component.html", "<div></div>").containingDirectory.virtualFile
        val folderCalls = mutableListOf<Pair<String, String>>()
        val fileCalls = mutableListOf<VirtualFile>()
        val action =
            SortTailwindInProjectViewAction(
                sortFolder = { _, rootPath, globPattern, dryRun ->
                    assertTrue(dryRun)
                    folderCalls += rootPath to globPattern
                },
                sortFile = { _, selectedFile, _ -> fileCalls += selectedFile },
            )

        action.actionPerformed(
            projectViewEvent(
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(CommonDataKeys.VIRTUAL_FILE, directory)
                    .build(),
            ),
        )

        assertTrue(fileCalls.isEmpty())
        assertEquals(
            listOf(directory.path to "**/*.{html,js,jsx,ts,tsx,vue,astro,svelte,css,scss,php}"),
            folderCalls,
        )
    }

    private fun singleDirectoryView(directory: PsiDirectory): IdeView = directoryView(directory)

    private fun directoryView(vararg directories: PsiDirectory): IdeView =
        object : IdeView {
            override fun getDirectories(): Array<PsiDirectory> = directories.toList().toTypedArray()

            override fun getOrChooseDirectory(): PsiDirectory = directories.first()

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
