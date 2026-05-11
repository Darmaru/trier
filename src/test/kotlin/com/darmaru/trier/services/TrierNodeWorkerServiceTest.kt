package com.darmaru.trier.services

import com.darmaru.trier.processing.TrierNodeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrierNodeWorkerServiceTest {
    @Test
    fun requestJsonIsSerializedOnSingleLineForJsonlWorkerProtocol() {
        val request =
            TrierNodeRequest(
                moduleBase = "/tmp/runtime",
                base = "/tmp/project",
                filepath = "/tmp/project/src/test.vue",
                configPath = "/tmp/project/tailwind.config.ts",
                stylesheetPath = "/tmp/project/src/style.css",
                preserveWhitespace = false,
                preserveDuplicates = true,
                values = listOf("text-center p-4", "font-bold\nflex"),
            )

        val json = with(TrierNodeWorkerService) { request.toJson(42) }

        assertFalse(json.contains('\n'))
        assertFalse(json.contains('\r'))
        assertTrue(json.startsWith("{\"id\":42,"))
        assertTrue(json.contains("\"values\":["))
    }

    @Test
    fun workerResponseParserHandlesJsonEscapesAndUnicode() {
        val values =
            with(TrierNodeWorkerService) {
                parseWorkerResponse(
                    7,
                    """{"id":7,"values":["font-bold\nflex","before:content-\"✓\"","icon-\u2713"]}""",
                )
            }

        assertEquals(listOf("font-bold\nflex", "before:content-\"✓\"", "icon-✓"), values)
    }

    @Test
    fun workerResponseParserUsesWorkerErrorMessage() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                with(TrierNodeWorkerService) {
                    parseWorkerResponse(7, """{"id":7,"error":"Sorter failed"}""")
                }
            }

        assertEquals("Sorter failed", error.message)
    }

    @Test
    fun workerResponseParserAddsStderrForMismatchedResponseId() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                with(TrierNodeWorkerService) {
                    parseWorkerResponse(7, """{"id":8,"values":[]}""") { "stderr tail" }
                }
            }

        assertEquals("Trier Node worker returned mismatched response id. stderr tail", error.message)
    }
}
