package com.darmaru.trier.services

import com.darmaru.trier.processing.TrierNodeRequest
import com.intellij.openapi.components.Service
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString

@Service(Service.Level.APP)
class TrierNodeWorkerService {
    private val lock = Any()
    private var worker: WorkerSession? = null

    fun sort(
        nodePath: String,
        scriptPath: Path,
        request: TrierNodeRequest,
        timeoutMillis: Long = DEFAULT_WORKER_TIMEOUT_MILLIS,
    ): List<String> =
        synchronized(lock) {
            var lastError: Exception? = null
            repeat(2) { attempt ->
                try {
                    return ensureWorker(nodePath, scriptPath).send(request, timeoutMillis)
                } catch (error: Exception) {
                    lastError = error
                    destroyWorker()
                    if (error is WorkerTimeoutException || attempt == 1) {
                        throw error
                    }
                }
            }
            throw lastError ?: IllegalStateException("Trier Node worker failed unexpectedly.")
        }

    private fun ensureWorker(
        nodePath: String,
        scriptPath: Path,
    ): WorkerSession {
        val existing = worker
        if (existing != null && existing.matches(nodePath, scriptPath) && existing.isAlive()) {
            return existing
        }

        destroyWorker()
        return WorkerSession.start(nodePath, scriptPath).also { worker = it }
    }

    private fun destroyWorker() {
        worker?.close()
        worker = null
    }

    private class WorkerSession private constructor(
        private val nodePath: String,
        private val scriptPath: Path,
        private val process: Process,
        private val stdin: BufferedWriter,
        private val stdout: BufferedReader,
        private val stderrTail: StringBuilder,
    ) {
        private val requestId = AtomicLong(0)

        fun matches(
            nodePath: String,
            scriptPath: Path,
        ): Boolean = this.nodePath == nodePath && this.scriptPath == scriptPath

        fun isAlive(): Boolean = process.isAlive

        fun send(
            request: TrierNodeRequest,
            timeoutMillis: Long,
        ): List<String> {
            if (!isAlive()) {
                throw IllegalStateException("Trier Node worker is not running. ${stderrMessage()}")
            }

            val id = requestId.incrementAndGet()
            stdin.write(request.toJson(id))
            stdin.newLine()
            stdin.flush()

            val line =
                readStdoutLine(timeoutMillis)
                    ?: throw IllegalStateException("Trier Node worker closed stdout. ${stderrMessage()}")

            return parseResponse(id, line)
        }

        private fun readStdoutLine(timeoutMillis: Long): String? {
            val result = AtomicReference<Result<String?>>()
            val done = CountDownLatch(1)
            Thread
                .ofVirtual()
                .name("trier-node-worker-stdout")
                .start {
                    result.set(runCatching { stdout.readLine() })
                    done.countDown()
                }

            val completed =
                try {
                    done.await(timeoutMillis, TimeUnit.MILLISECONDS)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    process.destroyForcibly()
                    throw IllegalStateException("Interrupted while waiting for Trier Node worker response.", error)
                }

            if (!completed) {
                process.destroyForcibly()
                throw WorkerTimeoutException(
                    "Trier Node worker did not respond within ${timeoutMillis}ms. The worker was restarted.",
                )
            }

            return result
                .get()
                .getOrElse { error ->
                    throw IllegalStateException(
                        "Trier Node worker failed while reading stdout. ${stderrMessage()}",
                        error,
                    )
                }
        }

        fun close() {
            runCatching { stdin.close() }
            runCatching { stdout.close() }
            if (process.isAlive) {
                process.destroy()
                val stopped =
                    try {
                        process.waitFor(500, TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        false
                    }
                if (!stopped && process.isAlive) {
                    process.destroyForcibly()
                }
            }
        }

        private fun parseResponse(
            expectedId: Long,
            raw: String,
        ): List<String> = parseWorkerResponse(expectedId, raw) { stderrMessage() }

        private fun stderrMessage(): String {
            val stderr =
                synchronized(stderrTail) {
                    stderrTail.toString().trim()
                }
            return stderr.ifBlank { "No stderr output from Trier Node worker." }
        }

        companion object {
            fun start(
                nodePath: String,
                scriptPath: Path,
            ): WorkerSession {
                val process =
                    ProcessBuilder(nodePath, scriptPath.absolutePathString())
                        .redirectErrorStream(false)
                        .start()

                val stdin = OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8).buffered()
                val stdout = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
                val stderrTail = StringBuilder()
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8))

                Thread
                    .ofVirtual()
                    .name("trier-node-worker-stderr")
                    .start {
                        stderrReader.useLines { lines ->
                            lines.forEach { line ->
                                synchronized(stderrTail) {
                                    if (stderrTail.isNotEmpty()) {
                                        stderrTail.append('\n')
                                    }
                                    stderrTail.append(line)
                                    if (stderrTail.length > 8000) {
                                        stderrTail.delete(0, stderrTail.length - 8000)
                                    }
                                }
                            }
                        }
                    }

                return WorkerSession(nodePath, scriptPath, process, stdin, stdout, stderrTail)
            }
        }
    }

    companion object {
        private const val DEFAULT_WORKER_TIMEOUT_MILLIS = 30_000L

        internal fun parseWorkerResponse(
            expectedId: Long,
            raw: String,
            stderrMessage: () -> String = { "No stderr output from Trier Node worker." },
        ): List<String> {
            val response =
                runCatching { JsonCursor(raw).parseObject() }
                    .getOrElse {
                        throw IllegalStateException("Trier Node worker returned invalid JSON. ${stderrMessage()}", it)
                    }

            if (response["id"] != expectedId) {
                throw IllegalStateException("Trier Node worker returned mismatched response id. ${stderrMessage()}")
            }

            val error = response["error"] as? String
            if (error != null) {
                throw IllegalStateException(error.ifBlank { stderrMessage() })
            }

            val values = response["values"] ?: return emptyList()
            return (values as? List<*>)
                ?.filterIsInstance<String>()
                ?: throw IllegalStateException("Trier Node worker returned invalid values payload. ${stderrMessage()}")
        }

        internal fun TrierNodeRequest.toJson(id: Long): String =
            buildString {
                append('{')
                append("\"id\":").append(id)
                append(",\"moduleBase\":").append(json(moduleBase))
                append(",\"base\":").append(json(base))
                append(",\"filepath\":").append(jsonOrNull(filepath))
                append(",\"configPath\":").append(jsonOrNull(configPath))
                append(",\"stylesheetPath\":").append(jsonOrNull(stylesheetPath))
                append(",\"preserveWhitespace\":").append(preserveWhitespace)
                append(",\"preserveDuplicates\":").append(preserveDuplicates)
                append(",\"values\":[")
                append(values.joinToString(",") { json(it) })
                append("]}")
            }

        private fun json(value: String): String =
            buildString {
                append('"')
                value.forEach { char ->
                    when (char) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(char)
                    }
                }
                append('"')
            }

        private fun jsonOrNull(value: String?): String = value?.let(::json) ?: "null"

        private class JsonCursor(
            private val raw: String,
        ) {
            private var index = 0

            fun parseObject(): Map<String, Any?> {
                skipWhitespace()
                expect('{')
                val result = linkedMapOf<String, Any?>()
                skipWhitespace()
                if (consume('}')) {
                    return result
                }

                while (true) {
                    val key = parseString()
                    skipWhitespace()
                    expect(':')
                    result[key] = parseValue()
                    skipWhitespace()
                    if (consume('}')) {
                        return result
                    }
                    expect(',')
                }
            }

            private fun parseValue(): Any? {
                skipWhitespace()
                return when (peek()) {
                    '"' -> parseString()
                    '[' -> parseArray()
                    '{' -> parseObject()
                    'n' -> {
                        expectLiteral("null")
                        null
                    }
                    't' -> {
                        expectLiteral("true")
                        true
                    }
                    'f' -> {
                        expectLiteral("false")
                        false
                    }
                    else -> parseNumber()
                }
            }

            private fun parseArray(): List<Any?> {
                expect('[')
                val result = mutableListOf<Any?>()
                skipWhitespace()
                if (consume(']')) {
                    return result
                }

                while (true) {
                    result += parseValue()
                    skipWhitespace()
                    if (consume(']')) {
                        return result
                    }
                    expect(',')
                }
            }

            private fun parseString(): String {
                expect('"')
                return buildString {
                    while (index < raw.length) {
                        val char = raw[index++]
                        when (char) {
                            '"' -> return@buildString
                            '\\' -> append(parseEscape())
                            else -> append(char)
                        }
                    }
                    error("Unterminated JSON string.")
                }
            }

            private fun parseEscape(): Char {
                val escaped = raw.getOrNull(index++) ?: error("Unterminated JSON escape.")
                return when (escaped) {
                    '"' -> '"'
                    '\\' -> '\\'
                    '/' -> '/'
                    'b' -> '\b'
                    'f' -> '\u000C'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'u' -> parseUnicodeEscape()
                    else -> error("Unsupported JSON escape: \\$escaped")
                }
            }

            private fun parseUnicodeEscape(): Char {
                if (index + 4 > raw.length) {
                    error("Incomplete JSON unicode escape.")
                }

                val value =
                    raw
                        .substring(index, index + 4)
                        .toIntOrNull(16)
                        ?: error("Invalid JSON unicode escape.")
                index += 4
                return value.toChar()
            }

            private fun parseNumber(): Long {
                val start = index
                if (peek() == '-') {
                    index++
                }
                while (peek()?.isDigit() == true) {
                    index++
                }
                if (start == index) {
                    error("Expected JSON value at offset $index.")
                }
                return raw.substring(start, index).toLong()
            }

            private fun expectLiteral(value: String) {
                if (!raw.startsWith(value, index)) {
                    error("Expected JSON literal '$value' at offset $index.")
                }
                index += value.length
            }

            private fun expect(char: Char) {
                skipWhitespace()
                if (!consume(char)) {
                    error("Expected '$char' at offset $index.")
                }
            }

            private fun consume(char: Char): Boolean {
                if (peek() != char) {
                    return false
                }
                index++
                return true
            }

            private fun peek(): Char? = raw.getOrNull(index)

            private fun skipWhitespace() {
                while (peek()?.isWhitespace() == true) {
                    index++
                }
            }
        }
    }

    private class WorkerTimeoutException(
        message: String,
    ) : IllegalStateException(message)
}
