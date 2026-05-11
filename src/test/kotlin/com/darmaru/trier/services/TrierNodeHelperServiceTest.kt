package com.darmaru.trier.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

class TrierNodeHelperServiceTest : BasePlatformTestCase() {
    fun testHelperScriptPathExtractsAndCachesSorterScript() {
        val service = ApplicationManager.getApplication().getService(TrierNodeHelperService::class.java)

        val first = service.helperScriptPath()
        val second = service.helperScriptPath()

        assertEquals(first, second)
        assertTrue(first.isRegularFile())
        assertTrue(first.name.startsWith("trier-tailwind-sorter"))
        assertTrue(first.readText().contains("createSorter"))
    }

    fun testBundledRuntimePathExtractsAndCachesRuntimeArchive() {
        val service = ApplicationManager.getApplication().getService(TrierNodeHelperService::class.java)

        val first = service.bundledRuntimePath()
        val second = service.bundledRuntimePath()

        assertEquals(first, second)
        assertTrue(first.isDirectory())
        assertTrue(first.resolve("package.json").exists())
        assertTrue(first.resolve("node_modules/prettier-plugin-tailwindcss").isDirectory())
    }

    fun testNotifyErrorCreatesNotificationWithoutThrowing() {
        val service = TrierNodeHelperService.getInstance()

        service.notifyError("Trier test error", "Runtime failed")
    }
}
