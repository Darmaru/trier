package com.darmaru.trier.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrierSortServiceTest {
    @Test
    fun buildsTailwindSorterFailureMessageWithResolvedContext() {
        val message =
            buildTailwindSorterFailureMessage(
                IllegalStateException(
                    "Error: Cannot apply unknown utility class `bg-a-orange`\n    at onInvalidCandidate",
                ),
                TrierTailwindSorterContext(
                    filePath = "/project/resources/views/layout.blade.php",
                    stylesheetPath = "/project/resources/css/app.css",
                    stylesheetSource = TrierTailwindPathSource.AUTO_DETECTED,
                    configPath = "/project/tailwind.config.js",
                    configSource = TrierTailwindPathSource.MANUAL,
                ),
            )

        assertTrue(message.contains("Tailwind sorter failed while initializing or sorting classes."))
        assertTrue(message.contains("Tailwind error: Error: Cannot apply unknown utility class `bg-a-orange`"))
        assertTrue(message.contains("Current file: /project/resources/views/layout.blade.php"))
        assertTrue(message.contains("Tailwind stylesheet: /project/resources/css/app.css (auto-detected)"))
        assertTrue(message.contains("Tailwind config: /project/tailwind.config.js (manual)"))
        assertTrue(message.contains("not necessarily from the current file"))
        assertTrue(message.contains("set Stylesheet and Config explicitly"))
    }

    @Test
    fun buildsTailwindSorterFailureMessageForMissingDetectedPaths() {
        val message =
            buildTailwindSorterFailureMessage(
                IllegalStateException("Sorter failed"),
                TrierTailwindSorterContext(
                    filePath = null,
                    stylesheetPath = null,
                    stylesheetSource = TrierTailwindPathSource.NOT_FOUND,
                    configPath = null,
                    configSource = TrierTailwindPathSource.NOT_FOUND,
                    tailwindCdnDetected = true,
                ),
            )

        assertTrue(message.contains("Current file: not available"))
        assertTrue(message.contains("Tailwind stylesheet: auto-detect did not find one"))
        assertTrue(message.contains("Tailwind config: auto-detect did not find one"))
        assertTrue(message.contains("Tailwind CDN usage was detected"))
    }

    @Test
    fun detectsTailwindLspUploadRootError() {
        val error =
            IllegalArgumentException("No upload root registered for /project/templates").withStackFrame(
                "com.intellij.tailwind.lsp.TailwindLspServerDescriptor",
            )

        assertTrue(isTailwindLspUploadRootError(error))
    }

    @Test
    fun detectsTailwindLspUploadRootErrorFromCause() {
        val cause = IllegalStateException("No upload root registered for /project/templates")
        val error =
            IllegalArgumentException("Path conversion failed", cause).withStackFrame(
                "com.intellij.tailwind.lsp.TailwindLspServerDescriptor",
            )

        assertTrue(isTailwindLspUploadRootError(error))
    }

    @Test
    fun ignoresUploadRootErrorFromUnrelatedCode() {
        val error =
            IllegalArgumentException("No upload root registered for /project/templates").withStackFrame(
                "com.intellij.javascript.nodejs.execution.NodeTargetRun",
            )

        assertFalse(isTailwindLspUploadRootError(error))
    }

    @Test
    fun ignoresTailwindLspErrorWithDifferentMessage() {
        val error =
            IllegalArgumentException("Unexpected path conversion error").withStackFrame(
                "com.intellij.tailwind.lsp.TailwindLspServerDescriptor",
            )

        assertFalse(isTailwindLspUploadRootError(error))
    }

    private fun <T : Throwable> T.withStackFrame(className: String): T =
        apply {
            stackTrace =
                arrayOf(
                    StackTraceElement(className, "getFilePath", "TailwindLspServerDescriptor.java", 259),
                )
        }
}
