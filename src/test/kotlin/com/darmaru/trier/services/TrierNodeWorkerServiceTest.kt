package com.darmaru.trier.services

import com.darmaru.trier.processing.TrierNodeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

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

    @Test
    fun workerResponseParserRejectsInvalidJson() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                with(TrierNodeWorkerService) {
                    parseWorkerResponse(7, "not json") { "stderr tail" }
                }
            }

        assertTrue(error.message?.startsWith("Trier Node worker returned invalid JSON. stderr tail") == true)
    }

    @Test
    fun workerResponseParserRejectsInvalidValuesPayload() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                with(TrierNodeWorkerService) {
                    parseWorkerResponse(7, """{"id":7,"values":"not-an-array"}""")
                }
            }

        assertEquals(
            "Trier Node worker returned invalid values payload. No stderr output from Trier Node worker.",
            error.message,
        )
    }

    @Test
    fun workerServiceRunsJsonlWorkerProcess() {
        val script =
            createTempFile("trier-worker-test", ".mjs").also {
                it.writeText(
                    """
                    import readline from 'node:readline';

                    const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
                    let count = 0;

                    for await (const line of rl) {
                        const payload = JSON.parse(line);
                        count++;
                        process.stdout.write(`${'$'}{JSON.stringify({
                            id: payload.id,
                            values: payload.values.map((value) => value.toUpperCase()),
                        })}\n`);
                        if (count >= 2) process.exit(0);
                    }
                    """.trimIndent(),
                )
            }
        val service = TrierNodeWorkerService()
        val request =
            TrierNodeRequest(
                moduleBase = "/tmp/runtime",
                base = "/tmp/project",
                filepath = null,
                configPath = null,
                stylesheetPath = null,
                preserveWhitespace = false,
                preserveDuplicates = false,
                values = listOf("flex p-4", "text-center"),
            )

        val first = service.sort("node", script, request)
        val second = service.sort("node", script, request.copy(values = listOf("font-bold")))

        assertEquals(listOf("FLEX P-4", "TEXT-CENTER"), first)
        assertEquals(listOf("FONT-BOLD"), second)
    }
}
