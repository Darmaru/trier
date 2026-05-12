package com.darmaru.trier.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class TrierNodeRuntimeValidatorTest {
    @Test
    fun parsesNodeVersionText() {
        assertEquals(
            TrierNodeRuntimeValidator.NodeVersion(20, 19, 0),
            TrierNodeRuntimeValidator.parseNodeVersion("v20.19.0"),
        )
        assertEquals(
            TrierNodeRuntimeValidator.NodeVersion(22, 3, 1),
            TrierNodeRuntimeValidator.parseNodeVersion("22.3.1"),
        )
        assertEquals(
            TrierNodeRuntimeValidator.NodeVersion(21, 0, 0),
            TrierNodeRuntimeValidator.parseNodeVersion("v21.0.0-nightly"),
        )
        assertNull(TrierNodeRuntimeValidator.parseNodeVersion("not node"))
    }

    @Test
    fun validatesSupportedNodeRuntime() {
        val node = fakeNode("v20.19.0")

        assertEquals(node.toString(), TrierNodeRuntimeValidator.validateLocalNodeRuntime(node.toString()))
    }

    @Test
    fun rejectsUnsupportedNodeRuntime() {
        val node = fakeNode("v20.18.1")

        val error =
            assertThrows(IllegalStateException::class.java) {
                TrierNodeRuntimeValidator.validateLocalNodeRuntime(node.toString())
            }

        assertEquals(
            "Trier requires Node.js 20.19 or newer. Selected runtime reports Node.js 20.18.1.",
            error.message,
        )
    }

    @Test
    fun rejectsUnreadableNodeVersion() {
        val node = fakeNode("not node")

        val error =
            assertThrows(IllegalStateException::class.java) {
                TrierNodeRuntimeValidator.validateLocalNodeRuntime(node.toString())
            }

        assertTrue(error.message?.contains("Could not determine Node.js version") == true)
    }

    private fun fakeNode(versionOutput: String) =
        createTempFile("trier-node-version-test", ".sh").also { path ->
            path.writeText(
                """
                #!/bin/sh
                echo "$versionOutput"
                """.trimIndent(),
            )
            path.toFile().setExecutable(true)
        }
}
