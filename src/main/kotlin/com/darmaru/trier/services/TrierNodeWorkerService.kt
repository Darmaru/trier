package com.darmaru.trier.services

import com.darmaru.trier.processing.TrierNodeRequest
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.target.value.TargetValue
import com.intellij.javascript.nodejs.execution.NodeTargetRun
import com.intellij.javascript.nodejs.execution.NodeTargetRunOptions
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString
import kotlin.io.path.invariantSeparatorsPathString

internal sealed interface TrierNodeRuntime {
    val cacheKey: String
    val presentableName: String

    data class Local(
        val nodePath: String,
    ) : TrierNodeRuntime {
        override val cacheKey: String = "local:$nodePath"
        override val presentableName: String = nodePath
    }

    data class Target(
        val interpreter: NodeJsInterpreter,
        override val presentableName: String,
        val project: Project,
    ) : TrierNodeRuntime {
        override val cacheKey: String = "target:${interpreter.referenceName}"
    }
}

@Service(Service.Level.APP)
class TrierNodeWorkerService {
    private val lock = Any()
    private var worker: WorkerSession? = null

    fun sort(
        nodePath: String,
        scriptPath: Path,
        request: TrierNodeRequest,
        timeoutMillis: Long = DEFAULT_WORKER_TIMEOUT_MILLIS,
    ): List<String> = sort(TrierNodeRuntime.Local(nodePath), scriptPath, request, timeoutMillis)

    internal fun sort(
        runtime: TrierNodeRuntime,
        scriptPath: Path,
        request: TrierNodeRequest,
        timeoutMillis: Long = DEFAULT_WORKER_TIMEOUT_MILLIS,
    ): List<String> =
        synchronized(lock) {
            var lastError: Exception? = null
            repeat(2) { attempt ->
                try {
                    return ensureWorker(runtime, scriptPath, request).send(request, timeoutMillis)
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

    internal fun version(
        runtime: TrierNodeRuntime,
        timeoutMillis: Long = DEFAULT_WORKER_TIMEOUT_MILLIS,
    ): String {
        val process =
            when (runtime) {
                is TrierNodeRuntime.Local -> ProcessBuilder(runtime.nodePath, "--version").start()
                is TrierNodeRuntime.Target -> {
                    val targetRun =
                        NodeTargetRun(
                            runtime.interpreter,
                            runtime.project,
                            null,
                            NodeTargetRunOptions.of(false),
                        )
                    targetRun.commandLineBuilder.addParameter("--version")
                    targetRun.startProcessEx().processHandler.process
                }
            }

        return readProcessOutput(process, timeoutMillis, "Node version check")
    }

    private fun ensureWorker(
        runtime: TrierNodeRuntime,
        scriptPath: Path,
        request: TrierNodeRequest,
    ): WorkerSession {
        val existing = worker
        if (existing != null && existing.matches(runtime, scriptPath, request) && existing.isAlive()) {
            return existing
        }

        destroyWorker()
        return WorkerSession.start(runtime, scriptPath, request).also { worker = it }
    }

    private fun destroyWorker() {
        worker?.close()
        worker = null
    }

    private abstract class WorkerSession(
        private val runtimeKey: String,
        private val scriptPath: Path,
        private val process: Process,
        private val stdin: BufferedWriter,
        private val stdout: BufferedReader,
        private val stderrTail: StringBuilder,
    ) {
        private val requestId = AtomicLong(0)

        open fun matches(
            runtime: TrierNodeRuntime,
            scriptPath: Path,
            request: TrierNodeRequest,
        ): Boolean = runtime.cacheKey == runtimeKey && this.scriptPath == scriptPath

        fun isAlive(): Boolean = process.isAlive

        fun send(
            request: TrierNodeRequest,
            timeoutMillis: Long,
        ): List<String> {
            if (!isAlive()) {
                throw IllegalStateException("Trier Node worker is not running. ${stderrMessage()}")
            }

            val id = requestId.incrementAndGet()
            stdin.write(prepareRequest(request).toJson(id))
            stdin.newLine()
            stdin.flush()

            val line =
                readStdoutLine(timeoutMillis)
                    ?: throw IllegalStateException("Trier Node worker closed stdout. ${stderrMessage()}")

            return parseResponse(id, line)
        }

        protected open fun prepareRequest(request: TrierNodeRequest): TrierNodeRequest = request

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
                runtime: TrierNodeRuntime,
                scriptPath: Path,
                request: TrierNodeRequest,
            ): WorkerSession {
                if (runtime is TrierNodeRuntime.Target) {
                    return TargetWorkerSession.start(runtime, scriptPath, request)
                }
                runtime as TrierNodeRuntime.Local
                val process =
                    ProcessBuilder(runtime.nodePath, scriptPath.absolutePathString())
                        .redirectErrorStream(false)
                        .start()

                return LocalWorkerSession(runtime, scriptPath, process)
            }
        }
    }

    private class LocalWorkerSession(
        runtime: TrierNodeRuntime.Local,
        scriptPath: Path,
        process: Process,
    ) : WorkerSession(
            runtimeKey = runtime.cacheKey,
            scriptPath = scriptPath,
            process = process,
            stdin = OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8).buffered(),
            stdout = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)),
            stderrTail = startStderrReader(process),
        )

    private class TargetWorkerSession(
        runtime: TrierNodeRuntime.Target,
        scriptPath: Path,
        processHandler: KillableProcessHandler,
        private val localBasePath: String,
        private val targetBasePath: String,
        private val targetRuntimePath: String,
    ) : WorkerSession(
            runtimeKey = runtime.cacheKey,
            scriptPath = scriptPath,
            process = processHandler.process,
            stdin = OutputStreamWriter(processHandler.process.outputStream, StandardCharsets.UTF_8).buffered(),
            stdout = BufferedReader(InputStreamReader(processHandler.process.inputStream, StandardCharsets.UTF_8)),
            stderrTail = startStderrReader(processHandler.process),
        ) {
        override fun matches(
            runtime: TrierNodeRuntime,
            scriptPath: Path,
            request: TrierNodeRequest,
        ): Boolean =
            super.matches(runtime, scriptPath, request) &&
                request.base == localBasePath &&
                targetRuntimePath.isNotBlank() &&
                targetBasePath.isNotBlank()

        override fun prepareRequest(request: TrierNodeRequest): TrierNodeRequest =
            request.copy(
                moduleBase = targetRuntimePath,
                base = targetBasePath,
                filepath = request.filepath?.let { mapLocalPathToTargetPath(it, localBasePath, targetBasePath) },
                configPath = request.configPath?.let { mapLocalPathToTargetPath(it, localBasePath, targetBasePath) },
                stylesheetPath =
                    request.stylesheetPath?.let {
                        mapLocalPathToTargetPath(
                            it,
                            localBasePath,
                            targetBasePath,
                        )
                    },
            )

        companion object {
            fun start(
                runtime: TrierNodeRuntime.Target,
                scriptPath: Path,
                request: TrierNodeRequest,
            ): TargetWorkerSession {
                val targetRun =
                    NodeTargetRun(
                        runtime.interpreter,
                        runtime.project,
                        null,
                        NodeTargetRunOptions.of(false),
                    )
                val targetScriptPath = targetRun.path(scriptPath)
                val targetRuntimePath = targetRun.path(request.moduleBase)
                val targetBasePath = targetRun.requestUploadProjectRootAndGetPath(request.base)
                targetRun.commandLineBuilder.addParameter(targetScriptPath)
                val process = targetRun.startProcessEx()

                return TargetWorkerSession(
                    runtime = runtime,
                    scriptPath = scriptPath,
                    processHandler = process.processHandler,
                    localBasePath = request.base,
                    targetBasePath = targetBasePath.targetString("project root"),
                    targetRuntimePath = targetRuntimePath.targetString("bundled runtime"),
                )
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

private fun readProcessOutput(
    process: Process,
    timeoutMillis: Long,
    description: String,
): String {
    val stderrTail = startStderrReader(process)
    val stdout = AtomicReference("")
    val stdoutDone = CountDownLatch(1)
    Thread
        .ofVirtual()
        .name("trier-node-process-stdout")
        .start {
            try {
                stdout.set(BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).readText())
            } finally {
                stdoutDone.countDown()
            }
        }

    val completed =
        try {
            process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            throw IllegalStateException("Interrupted while running Trier $description.", error)
        }

    if (!completed) {
        process.destroyForcibly()
        throw IllegalStateException("Trier $description did not finish within ${timeoutMillis}ms.")
    }

    stdoutDone.await(1, TimeUnit.SECONDS)
    val output = stdout.get().trim()
    if (process.exitValue() != 0) {
        val stderr =
            synchronized(stderrTail) {
                stderrTail.toString().trim()
            }.ifBlank { "No stderr output from Trier $description." }
        throw IllegalStateException("Trier $description failed. $stderr")
    }

    return output
}

internal fun mapLocalPathToTargetPath(
    localPath: String,
    localBasePath: String,
    targetBasePath: String,
): String {
    val local = Path.of(localPath).normalize()
    val base = Path.of(localBasePath).normalize()
    if (!local.startsWith(base)) {
        return localPath
    }

    val relativePath = base.relativize(local).invariantSeparatorsPathString
    return joinTargetPath(targetBasePath, relativePath)
}

private fun joinTargetPath(
    base: String,
    relativePath: String,
): String {
    if (relativePath.isBlank()) {
        return base
    }
    val separator = if (base.contains('\\') && !base.contains('/')) "\\" else "/"
    return base.trimEnd('/', '\\') + separator + relativePath.replace("/", separator)
}

private fun TargetValue<String>.targetString(label: String): String =
    try {
        targetValue.blockingGet(0) ?: error("Could not resolve Trier $label path inside the selected Node target.")
    } catch (error: TimeoutException) {
        throw IllegalStateException(
            "Timed out while resolving Trier $label path inside the selected Node target.",
            error,
        )
    } catch (error: ExecutionException) {
        throw IllegalStateException("Failed to resolve Trier $label path inside the selected Node target.", error)
    }

private fun startStderrReader(process: Process): StringBuilder {
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
    return stderrTail
}
