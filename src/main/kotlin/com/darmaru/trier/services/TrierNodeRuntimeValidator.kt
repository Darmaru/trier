package com.darmaru.trier.services

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal object TrierNodeRuntimeValidator {
    private val minimumVersion = NodeVersion(20, 19, 0)

    fun validateLocalNodeRuntime(path: String): String {
        if (path.isBlank() || !Files.isRegularFile(Path.of(path))) {
            throw IllegalStateException("Resolved Node.js executable was not found on disk: $path")
        }

        val versionText = readNodeVersion(path)
        val version =
            parseNodeVersion(versionText)
                ?: throw IllegalStateException(
                    "Could not determine Node.js version from '$versionText'. Trier requires Node.js 20.19 or newer.",
                )
        if (!version.isAtLeast(minimumVersion)) {
            throw IllegalStateException(
                "Trier requires Node.js 20.19 or newer. Selected runtime reports Node.js ${version.displayText}.",
            )
        }

        return path
    }

    internal fun parseNodeVersion(raw: String): NodeVersion? {
        val match = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?""").matchEntire(raw.trim()) ?: return null
        return NodeVersion(
            major = match.groupValues[1].toInt(),
            minor = match.groupValues[2].toInt(),
            patch = match.groupValues[3].toInt(),
        )
    }

    private fun readNodeVersion(path: String): String {
        val process =
            ProcessBuilder(path, "--version")
                .redirectErrorStream(true)
                .start()
        val completed =
            try {
                process.waitFor(5, TimeUnit.SECONDS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                process.destroyForcibly()
                throw IllegalStateException("Interrupted while checking Node.js version.", error)
            }
        if (!completed) {
            process.destroyForcibly()
            throw IllegalStateException("Timed out while checking Node.js version.")
        }

        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        if (process.exitValue() != 0) {
            throw IllegalStateException("Failed to check Node.js version: $output")
        }

        return output.lineSequence().firstOrNull(String::isNotBlank).orEmpty()
    }

    internal data class NodeVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) {
        val displayText: String = "$major.$minor.$patch"

        fun isAtLeast(other: NodeVersion): Boolean =
            compareValuesBy(this, other, NodeVersion::major, NodeVersion::minor, NodeVersion::patch) >= 0
    }
}
