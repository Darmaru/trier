package com.darmaru.trier.settings

import com.darmaru.trier.processing.TrierResolvedSettings
import com.darmaru.trier.services.TrierRuntimeReport
import com.intellij.openapi.options.ConfigurationException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.writeText

class TrierSettingsConfigurableTest : BasePlatformTestCase() {
    private lateinit var initialSettings: TrierSettingsState.State

    override fun setUp() {
        super.setUp()
        initialSettings = TrierSettingsState.getInstance().snapshot()
        testNodeRuntimeValidator = { "/usr/bin/node" }
    }

    override fun tearDown() {
        testNodeRuntimeValidator = null
        testRuntimeProbe = null
        TrierSettingsState.getInstance().loadState(initialSettings)
        super.tearDown()
    }

    fun testResetLoadsPersistedStateIntoForm() {
        TrierSettingsState.getInstance().loadState(
            TrierSettingsState.State(
                sortOnSave = true,
                sortOnReformat = false,
                tailwindStylesheet = "/tmp/app.css",
                tailwindConfig = "/tmp/tailwind.config.js",
                tailwindAttributes = "class,data-tw",
                tailwindFunctions = "cn,tw",
                tailwindPreserveWhitespace = true,
                tailwindPreserveDuplicates = true,
            ),
        )

        val configurable = TrierSettingsConfigurable()
        configurable.createComponent()
        configurable.reset()

        val view = configurable.testView()
        assertTrue(view.sortOnSaveBox.isSelected)
        assertFalse(view.sortOnReformatBox.isSelected)
        assertEquals("/tmp/app.css", view.tailwindStylesheetField.text)
        assertEquals("/tmp/tailwind.config.js", view.tailwindConfigField.text)
        assertEquals("class,data-tw", view.tailwindAttributesArea.text)
        assertEquals("cn,tw", view.tailwindFunctionsArea.text)
        assertTrue(view.preserveWhitespaceBox.isSelected)
        assertTrue(view.preserveDuplicatesBox.isSelected)

        configurable.disposeUIResources()
    }

    fun testApplyPersistsModifiedValues() {
        val configurable = TrierSettingsConfigurable()
        configurable.createComponent()
        val view = configurable.testView()
        val tempDir = createTempDirectory("trier-settings-test")
        val stylesheet = tempDir / "styles.css"
        val config = tempDir / "tailwind.config.ts"
        stylesheet.writeText("/* test */")
        config.writeText("export default {}")

        view.sortOnSaveBox.isSelected = true
        view.sortOnReformatBox.isSelected = false
        view.tailwindStylesheetField.text = stylesheet.toString()
        view.tailwindConfigField.text = config.toString()
        view.tailwindAttributesArea.text = "class,data-tw"
        view.tailwindFunctionsArea.text = "cn,clsx"
        view.preserveWhitespaceBox.isSelected = true
        view.preserveDuplicatesBox.isSelected = true

        assertTrue(configurable.isModified)
        configurable.apply()

        val state = TrierSettingsState.getInstance().snapshot()
        assertTrue(state.sortOnSave)
        assertFalse(state.sortOnReformat)
        assertEquals(stylesheet.toString(), state.tailwindStylesheet)
        assertEquals(config.toString(), state.tailwindConfig)
        assertEquals("class,data-tw", state.tailwindAttributes)
        assertEquals("cn,clsx", state.tailwindFunctions)
        assertTrue(state.tailwindPreserveWhitespace)
        assertTrue(state.tailwindPreserveDuplicates)
        assertFalse(configurable.isModified)

        configurable.disposeUIResources()
    }

    fun testApplyRejectsMissingTailwindStylesheetPath() {
        val configurable = TrierSettingsConfigurable()
        configurable.createComponent()
        val view = configurable.testView()
        view.tailwindStylesheetField.text = "/definitely/missing/trier.css"

        assertEquals(
            "Stylesheet path does not exist: /definitely/missing/trier.css",
            view.stylesheetStatusLabel.text,
        )

        try {
            configurable.apply()
            fail("Expected ConfigurationException for missing stylesheet path")
        } catch (error: ConfigurationException) {
            assertEquals(
                "Stylesheet path does not exist: /definitely/missing/trier.css",
                error.localizedMessage,
            )
        }

        configurable.disposeUIResources()
    }

    fun testApplyRejectsDirectoryAsTailwindConfigPath() {
        val configurable = TrierSettingsConfigurable()
        configurable.createComponent()
        val view = configurable.testView()
        val tempDir = createTempDirectory("trier-settings-dir-test")
        view.tailwindConfigField.text = tempDir.toString()

        try {
            configurable.apply()
            fail("Expected ConfigurationException for directory config path")
        } catch (error: ConfigurationException) {
            assertEquals(
                "Config must point to a file: $tempDir",
                error.localizedMessage,
            )
        }

        configurable.disposeUIResources()
    }

    fun testApplyRejectsInvalidCustomAttributeRegex() {
        val configurable = TrierSettingsConfigurable()
        configurable.createComponent()
        val view = configurable.testView()
        view.tailwindAttributesArea.text = "/[/"

        try {
            configurable.apply()
            fail("Expected ConfigurationException for invalid attribute regex")
        } catch (error: ConfigurationException) {
            assertTrue(error.localizedMessage.startsWith("Attributes contains invalid regex '/[/':"))
        }

        assertEquals("", TrierSettingsState.getInstance().snapshot().tailwindAttributes)
        configurable.disposeUIResources()
    }

    fun testApplyRejectsInvalidCustomFunctionRegex() {
        val configurable = TrierSettingsConfigurable()
        configurable.createComponent()
        val view = configurable.testView()
        view.tailwindFunctionsArea.text = "cn, /(/"

        try {
            configurable.apply()
            fail("Expected ConfigurationException for invalid function regex")
        } catch (error: ConfigurationException) {
            assertTrue(error.localizedMessage.startsWith("Functions contains invalid regex '/(/':"))
        }

        assertEquals("", TrierSettingsState.getInstance().snapshot().tailwindFunctions)
        configurable.disposeUIResources()
    }

    fun testRuntimeProbeUsesCurrentFormValues() {
        val configurable = TrierSettingsConfigurable()
        configurable.createComponent()
        val view = configurable.testView()
        val tempDir = createTempDirectory("trier-settings-runtime-test")
        val stylesheet = tempDir / "styles.css"
        val config = tempDir / "tailwind.config.ts"
        stylesheet.writeText("/* test */")
        config.writeText("export default {}")

        view.tailwindStylesheetField.text = stylesheet.toString()
        view.tailwindConfigField.text = config.toString()
        view.preserveWhitespaceBox.isSelected = true
        view.preserveDuplicatesBox.isSelected = true
        view.tailwindAttributesArea.text = "class,data-tw"
        view.tailwindFunctionsArea.text = "cn,clsx"

        var captured: TrierResolvedSettings? = null
        testRuntimeProbe = { settings ->
            captured = settings
            TrierRuntimeReport(
                node = "/usr/bin/node",
                bundledRuntime = "/tmp/trier-node-runtime",
                tailwindStylesheet = settings.tailwindStylesheet,
                tailwindConfig = settings.tailwindConfig,
                sampleSortResult = "ok",
            )
        }

        val result = configurable.runRuntimeTestForTest()

        assertEquals("ok", result.sampleSortResult)
        val resolved = checkNotNull(captured)
        assertEquals(stylesheet.toString(), resolved.tailwindStylesheet)
        assertEquals(config.toString(), resolved.tailwindConfig)
        assertEquals(listOf("class", "data-tw"), resolved.tailwindAttributes)
        assertEquals(listOf("cn", "clsx"), resolved.tailwindFunctions)
        assertTrue(resolved.tailwindPreserveWhitespace)
        assertTrue(resolved.tailwindPreserveDuplicates)
        assertEquals("Node runtime looks valid", view.nodeStatusLabel.text)
        assertEquals("Stylesheet looks valid", view.stylesheetStatusLabel.text)
        assertEquals("Config looks valid", view.configStatusLabel.text)

        configurable.disposeUIResources()
    }

    fun testRuntimeReportHtmlRendersHeadingsInBold() {
        val html =
            buildRuntimeReportHtml(
                TrierRuntimeReport(
                    node = "Docker node:22",
                    bundledRuntime = "/tmp/trier-node-runtime",
                    tailwindStylesheet = null,
                    tailwindConfig = null,
                    sampleSortResult = "flex p-4 text-center",
                ),
            )

        assertTrue(html.contains("<strong>Node:</strong>"))
        assertTrue(html.contains("<strong>Sample sort result:</strong>"))
        assertTrue(html.contains("Docker node:22"))
        assertTrue(html.contains("flex p-4 text-center"))
    }

    fun testFileChooserFallsBackToProjectRootWhenPathIsBlank() {
        val configurable = TrierSettingsConfigurable()
        configurable.createComponent()

        val selected = configurable.fileChooserInitialSelectionForTest("")

        assertEquals(project.basePath, selected?.path)
        configurable.disposeUIResources()
    }
}
