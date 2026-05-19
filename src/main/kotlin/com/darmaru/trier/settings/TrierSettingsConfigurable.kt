package com.darmaru.trier.settings

import com.darmaru.trier.processing.toResolvedSettings
import com.darmaru.trier.processing.validateNamePatterns
import com.darmaru.trier.services.TrierNodeRuntimeValidator
import com.darmaru.trier.services.TrierRuntimeReport
import com.darmaru.trier.services.TrierSortService
import com.intellij.execution.ExecutionException
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterField
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.AbstractButton
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

@Volatile
internal var testNodeRuntimeValidator: (() -> String)? = null

@Volatile
internal var testRuntimeProbe: ((com.darmaru.trier.processing.TrierResolvedSettings) -> TrierRuntimeReport)? = null

class TrierSettingsConfigurable : Configurable {
    private val state get() = TrierSettingsState.getInstance()
    private val project: Project by lazy {
        ProjectManager.getInstance().openProjects.firstOrNull() ?: ProjectManager.getInstance().defaultProject
    }

    private var panel: JPanel? = null
    private var nodeInterpreterField: NodeJsInterpreterField? = null
    private val sortOnSaveBox = JBCheckBox("Sort on Save")
    private val sortOnReformatBox = JBCheckBox("Sort on Code Reformat")
    private val tailwindStylesheetField = createFileChooserField("Select stylesheet")
    private val tailwindConfigField = createFileChooserField("Select config")
    private val preserveWhitespaceBox = JBCheckBox("Preserve whitespace")
    private val preserveDuplicatesBox = JBCheckBox("Preserve duplicates")
    private val tailwindAttributesArea = JBTextArea(4, 60)
    private val tailwindFunctionsArea = JBTextArea(4, 60)
    private val testRuntimeButton = JButton("Test Trier runtime")
    private val nodeStatusLabel = createStatusLabel()
    private var runtimeTestInProgress = false
    private val stylesheetStatusLabel = createStatusLabel()
    private val configStatusLabel = createStatusLabel()

    override fun getDisplayName(): String = "Trier"

    override fun createComponent(): JComponent {
        val interpreterField =
            NodeJsInterpreterField(project, true, false).also {
                it.setPreferredWidthToFitText()
                nodeInterpreterField = it
            }
        testRuntimeButton.addActionListener { testTrierRuntime() }
        installValidationListeners(interpreterField)

        val form =
            FormBuilder
                .createFormBuilder()
                .addComponent(sectionLabel("Runtime"))
                .addLabeledComponent("Node interpreter:", interpreterField)
                .addComponent(nodeStatusLabel)
                .addComponent(testRuntimeButton)
                .addComponent(sectionLabel("Triggers"))
                .addComponent(sortOnSaveBox)
                .addComponent(sortOnReformatBox)
                .addComponent(sectionLabel("Tailwind CSS"))
                .addLabeledComponent("Stylesheet:", tailwindStylesheetField)
                .addComponent(stylesheetStatusLabel)
                .addLabeledComponent("Config:", tailwindConfigField)
                .addComponent(configStatusLabel)
                .addComponent(preserveWhitespaceBox)
                .addComponent(preserveDuplicatesBox)
                .addLabeledComponent("Attributes:", scrollable(tailwindAttributesArea))
                .addLabeledComponent("Functions:", scrollable(tailwindFunctionsArea))
                .addComponentFillVertically(JPanel(), 0)
                .panel

        return JPanel().apply {
            add(form)
            panel = this
            reset()
        }
    }

    override fun isModified(): Boolean {
        val snapshot = state.snapshot()
        return selectedInterpreterRef() != effectiveStoredInterpreterRef(snapshot) ||
            sortOnSaveBox.isSelected != snapshot.sortOnSave ||
            sortOnReformatBox.isSelected != snapshot.sortOnReformat ||
            tailwindStylesheetField.text != snapshot.tailwindStylesheet ||
            tailwindConfigField.text != snapshot.tailwindConfig ||
            preserveWhitespaceBox.isSelected != snapshot.tailwindPreserveWhitespace ||
            preserveDuplicatesBox.isSelected != snapshot.tailwindPreserveDuplicates ||
            tailwindAttributesArea.text != snapshot.tailwindAttributes ||
            tailwindFunctionsArea.text != snapshot.tailwindFunctions
    }

    override fun apply() {
        validateCurrentValues()
        state.update { snapshot ->
            snapshot.nodeInterpreterRef = selectedInterpreterRef()
            snapshot.nodeInterpreterPath = ""
            snapshot.sortOnSave = sortOnSaveBox.isSelected
            snapshot.sortOnReformat = sortOnReformatBox.isSelected
            snapshot.tailwindStylesheet = tailwindStylesheetField.text.trim()
            snapshot.tailwindConfig = tailwindConfigField.text.trim()
            snapshot.tailwindPreserveWhitespace = preserveWhitespaceBox.isSelected
            snapshot.tailwindPreserveDuplicates = preserveDuplicatesBox.isSelected
            snapshot.tailwindAttributes = tailwindAttributesArea.text.trim()
            snapshot.tailwindFunctions = tailwindFunctionsArea.text.trim()
        }
    }

    override fun reset() {
        val snapshot = state.snapshot()
        nodeInterpreterField?.setInterpreterRef(resolveStoredInterpreterRef(snapshot))
        sortOnSaveBox.isSelected = snapshot.sortOnSave
        sortOnReformatBox.isSelected = snapshot.sortOnReformat
        tailwindStylesheetField.text = snapshot.tailwindStylesheet
        tailwindConfigField.text = snapshot.tailwindConfig
        preserveWhitespaceBox.isSelected = snapshot.tailwindPreserveWhitespace
        preserveDuplicatesBox.isSelected = snapshot.tailwindPreserveDuplicates
        tailwindAttributesArea.text = snapshot.tailwindAttributes
        tailwindFunctionsArea.text = snapshot.tailwindFunctions
        updateValidationStatus()
    }

    override fun disposeUIResources() {
        nodeInterpreterField = null
        panel = null
    }

    private fun validateCurrentValues() {
        validateNodeInterpreter()
        validateOptionalPath("Stylesheet", tailwindStylesheetField.text)
        validateOptionalPath("Config", tailwindConfigField.text)
        validateCustomPatterns()
    }

    private fun scrollable(area: JBTextArea): JComponent = JBScrollPane(area)

    private fun createStatusLabel(): JBLabel =
        JBLabel(" ").apply {
            border = JBUI.Borders.emptyLeft(2)
        }

    private fun sectionLabel(text: String): JBLabel =
        JBLabel(text).apply {
            border = JBUI.Borders.emptyTop(8)
            font = font.deriveFont(Font.BOLD)
        }

    private fun installValidationListeners(interpreterField: NodeJsInterpreterField) {
        installComponentValidationListeners(interpreterField)
        tailwindStylesheetField.textField.document.addDocumentListener(validationDocumentListener())
        tailwindConfigField.textField.document.addDocumentListener(validationDocumentListener())
    }

    private fun installComponentValidationListeners(component: JComponent) {
        component.addPropertyChangeListener { updateValidationStatus() }
        when (component) {
            is JTextComponent -> component.document.addDocumentListener(validationDocumentListener())
            is JComboBox<*> -> component.addActionListener { updateValidationStatus() }
            is AbstractButton -> component.addActionListener { updateValidationStatus() }
        }
        component.components
            .filterIsInstance<JComponent>()
            .forEach(::installComponentValidationListeners)
    }

    private fun validationDocumentListener(): DocumentListener =
        object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = updateValidationStatus()

            override fun removeUpdate(event: DocumentEvent) = updateValidationStatus()

            override fun changedUpdate(event: DocumentEvent) = updateValidationStatus()
        }

    private fun createFileChooserField(title: String): TextFieldWithBrowseButton =
        TextFieldWithBrowseButton().apply {
            val descriptor =
                FileChooserDescriptor(
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                ).withTitle(title)

            addActionListener {
                val toSelect = fileChooserInitialSelection(text)
                FileChooser.chooseFile(descriptor, project, toSelect) { file ->
                    text = file.path
                }
            }
        }

    internal fun fileChooserInitialSelectionForTest(currentPath: String): com.intellij.openapi.vfs.VirtualFile? =
        fileChooserInitialSelection(currentPath)

    private fun fileChooserInitialSelection(currentPath: String): com.intellij.openapi.vfs.VirtualFile? {
        val localFileSystem = LocalFileSystem.getInstance()
        return currentPath
            .trim()
            .takeIf(String::isNotBlank)
            ?.let(localFileSystem::findFileByPath)
            ?: project.basePath?.let(localFileSystem::findFileByPath)
    }

    private fun selectedInterpreterRef(): String = nodeInterpreterField?.getInterpreterRef()?.referenceName.orEmpty()

    private fun testTrierRuntime() {
        if (runtimeTestInProgress) {
            return
        }

        setRuntimeTestInProgress(true)
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Trier: Test Runtime", true) {
                private var report: TrierRuntimeReport? = null

                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Testing Trier runtime"
                    report = runRuntimeTest()
                }

                override fun onSuccess() {
                    setRuntimeTestInProgress(false)
                    updateValidationStatus()
                    TrierRuntimeReportDialog(project, checkNotNull(report)).show()
                }

                override fun onThrowable(error: Throwable) {
                    setRuntimeTestInProgress(false)
                    updateValidationStatus()
                    Messages.showErrorDialog(project, runtimeTestErrorMessage(error), "Trier Runtime")
                }

                override fun onCancel() {
                    setRuntimeTestInProgress(false)
                    updateValidationStatus()
                }
            },
        )
    }

    private fun setRuntimeTestInProgress(value: Boolean) {
        runtimeTestInProgress = value
        testRuntimeButton.isEnabled = !value
    }

    private fun updateValidationStatus() {
        val nodeValidation = runCatching { validateNodeInterpreter() }
        updateStatusLabel(
            nodeStatusLabel,
            nodeValidation.fold(
                onSuccess = { "Node runtime looks valid" },
                onFailure = {
                    (it as? ConfigurationException)?.localizedMessage ?: it.message ?: "Invalid Node runtime"
                },
            ),
            nodeValidation.isSuccess,
        )
        updateStatusLabelForOptionalPath(
            statusLabel = stylesheetStatusLabel,
            label = "Stylesheet",
            rawPath = tailwindStylesheetField.text,
        )
        updateStatusLabelForOptionalPath(
            statusLabel = configStatusLabel,
            label = "Config",
            rawPath = tailwindConfigField.text,
        )
    }

    private fun updateStatusLabelForOptionalPath(
        statusLabel: JBLabel,
        label: String,
        rawPath: String,
    ) {
        val path = rawPath.trim()
        if (path.isBlank()) {
            updateStatusLabel(statusLabel, "$label will be auto-detected when possible", true)
            return
        }
        val validation = runCatching { validateOptionalPath(label, rawPath) }
        updateStatusLabel(
            statusLabel,
            validation.fold(
                onSuccess = { "$label looks valid" },
                onFailure = { (it as? ConfigurationException)?.localizedMessage ?: it.message ?: "$label is invalid" },
            ),
            validation.isSuccess,
        )
    }

    private fun updateStatusLabel(
        label: JBLabel,
        text: String,
        ok: Boolean,
    ) {
        label.text = text
        label.foreground =
            if (ok) {
                JBColor(0x2E7D32, 0xA5D6A7)
            } else {
                JBColor(0xC62828, 0xEF9A9A)
            }
    }

    private fun validateNodeInterpreter(): String {
        testNodeRuntimeValidator?.let { return it() }
        val interpreterRef =
            resolveStoredInterpreterRef(TrierSettingsState.State(nodeInterpreterRef = selectedInterpreterRef()))
        val interpreter =
            interpreterRef.resolve(project)
                ?: throw ConfigurationException(
                    "Selected JavaScript Runtime could not be resolved. Choose a valid Node.js runtime.",
                )

        return try {
            val path = NodeJsLocalInterpreter.castAndValidate(interpreter).interpreterSystemDependentPath
            TrierNodeRuntimeValidator.validateLocalNodeRuntime(path)
        } catch (_: ExecutionException) {
            val validationError = interpreter.validate(project)
            if (!validationError.isNullOrBlank()) {
                throw ConfigurationException(validationError)
            }
            interpreter.presentableName
        } catch (error: IllegalStateException) {
            throw ConfigurationException(error.message ?: "Invalid Node.js runtime.")
        }
    }

    private fun runRuntimeTest(): TrierRuntimeReport {
        validateCurrentValues()
        testRuntimeProbe?.let { return it(currentResolvedSettings()) }
        return TrierSortService.getInstance().testRuntime(project, currentResolvedSettings())
    }

    private fun runtimeTestErrorMessage(error: Throwable): String =
        (error as? ConfigurationException)?.localizedMessage
            ?: error.localizedMessage
            ?: error.message
            ?: "Trier runtime test failed. Check the selected JavaScript Runtime and IDE logs for details."

    private fun validateOptionalPath(
        label: String,
        rawPath: String,
    ) {
        val path = rawPath.trim()
        if (path.isBlank()) {
            return
        }
        if (!Files.exists(Path.of(path))) {
            throw ConfigurationException("$label path does not exist: $path")
        }
        if (!Files.isRegularFile(Path.of(path))) {
            throw ConfigurationException("$label must point to a file: $path")
        }
    }

    private fun validateCustomPatterns() {
        val settings = currentResolvedSettings()
        try {
            validateNamePatterns("Attributes", settings.tailwindAttributes)
            validateNamePatterns("Functions", settings.tailwindFunctions)
        } catch (error: IllegalArgumentException) {
            throw ConfigurationException(error.message ?: "Invalid Trier custom pattern.")
        }
    }

    private fun effectiveStoredInterpreterRef(snapshot: TrierSettingsState.State): String =
        when {
            snapshot.nodeInterpreterRef.isNotBlank() -> snapshot.nodeInterpreterRef
            snapshot.nodeInterpreterPath.isNotBlank() -> snapshot.nodeInterpreterPath
            else -> NodeJsInterpreterManager.getInstance(project).interpreterRef.referenceName
        }

    private fun resolveStoredInterpreterRef(snapshot: TrierSettingsState.State): NodeJsInterpreterRef {
        val rawRef = effectiveStoredInterpreterRef(snapshot)
        return if (rawRef.isBlank()) {
            NodeJsInterpreterManager.getInstance(project).interpreterRef
        } else {
            NodeJsInterpreterRef.create(rawRef)
        }
    }

    private fun currentResolvedSettings(): com.darmaru.trier.processing.TrierResolvedSettings =
        TrierSettingsState
            .State(
                nodeInterpreterRef = selectedInterpreterRef(),
                sortOnSave = sortOnSaveBox.isSelected,
                sortOnReformat = sortOnReformatBox.isSelected,
                tailwindStylesheet = tailwindStylesheetField.text.trim(),
                tailwindConfig = tailwindConfigField.text.trim(),
                tailwindAttributes = tailwindAttributesArea.text.trim(),
                tailwindFunctions = tailwindFunctionsArea.text.trim(),
                tailwindPreserveWhitespace = preserveWhitespaceBox.isSelected,
                tailwindPreserveDuplicates = preserveDuplicatesBox.isSelected,
            ).toResolvedSettings()

    internal data class TestView(
        val sortOnSaveBox: JBCheckBox,
        val sortOnReformatBox: JBCheckBox,
        val testRuntimeButton: JButton,
        val nodeStatusLabel: JBLabel,
        val stylesheetStatusLabel: JBLabel,
        val configStatusLabel: JBLabel,
        val tailwindStylesheetField: TextFieldWithBrowseButton,
        val tailwindConfigField: TextFieldWithBrowseButton,
        val preserveWhitespaceBox: JBCheckBox,
        val preserveDuplicatesBox: JBCheckBox,
        val tailwindAttributesArea: JBTextArea,
        val tailwindFunctionsArea: JBTextArea,
    )

    internal fun testView(): TestView =
        TestView(
            sortOnSaveBox = sortOnSaveBox,
            sortOnReformatBox = sortOnReformatBox,
            testRuntimeButton = testRuntimeButton,
            nodeStatusLabel = nodeStatusLabel,
            stylesheetStatusLabel = stylesheetStatusLabel,
            configStatusLabel = configStatusLabel,
            tailwindStylesheetField = tailwindStylesheetField,
            tailwindConfigField = tailwindConfigField,
            preserveWhitespaceBox = preserveWhitespaceBox,
            preserveDuplicatesBox = preserveDuplicatesBox,
            tailwindAttributesArea = tailwindAttributesArea,
            tailwindFunctionsArea = tailwindFunctionsArea,
        )

    internal fun runRuntimeTestForTest(): TrierRuntimeReport = runRuntimeTest()
}

private class TrierRuntimeReportDialog(
    project: Project,
    private val report: TrierRuntimeReport,
) : DialogWrapper(project) {
    init {
        title = "Trier Runtime"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val width = 680
        val editor =
            JEditorPane("text/html", buildRuntimeReportHtml(report)).apply {
                isEditable = false
                isOpaque = false
                putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
                border = JBUI.Borders.empty(8)
            }
        editor.setSize(width, Int.MAX_VALUE)
        val contentHeight = editor.preferredSize.height.coerceIn(120, 420)
        editor.preferredSize = Dimension(width, contentHeight)

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            preferredSize = Dimension(width + 24, contentHeight + 24)
            add(
                if (contentHeight >= 420) {
                    JBScrollPane(editor)
                } else {
                    editor
                },
                BorderLayout.CENTER,
            )
        }
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)
}

internal fun buildRuntimeReportHtml(report: TrierRuntimeReport): String =
    buildString {
        append("<html><body style=\"margin:0;\">")
        report.rows().forEachIndexed { index, row ->
            if (index > 0) {
                append("<div style=\"height:8px;\"></div>")
            }
            append("<strong>")
            append(escapeRuntimeHtml(row.label))
            append(":</strong><br>")
            append("<span>")
            append(escapeRuntimeHtml(row.value).softBreakLongPath())
            append("</span><br>")
        }
        append("</body></html>")
    }

private fun escapeRuntimeHtml(value: String): String =
    buildString {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(char)
            }
        }
    }

private fun String.softBreakLongPath(): String = replace("/", "/&#8203;").replace("\\", "\\&#8203;")
