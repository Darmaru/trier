package com.darmaru.trier.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrierSortServiceTest {
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
