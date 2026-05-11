package com.darmaru.trier.processing

import com.darmaru.trier.settings.TrierSettingsState
import java.nio.file.Path
import kotlin.io.path.absolutePathString

data class TrierResolvedSettings(
    val nodeInterpreterRef: String?,
    val sortOnSave: Boolean,
    val sortOnReformat: Boolean,
    val tailwindStylesheet: String?,
    val tailwindConfig: String?,
    val tailwindAttributes: List<String>,
    val tailwindFunctions: List<String>,
    val tailwindPreserveWhitespace: Boolean,
    val tailwindPreserveDuplicates: Boolean,
)

data class TrierNodeRequest(
    val moduleBase: String,
    val base: String,
    val filepath: String?,
    val configPath: String?,
    val stylesheetPath: String?,
    val preserveWhitespace: Boolean,
    val preserveDuplicates: Boolean,
    val values: List<String>,
)

internal data class Replacement(
    val start: Int,
    val end: Int,
    val value: String,
)

internal data class SortCandidate(
    val start: Int,
    val end: Int,
    val originalValue: String,
    val renderReplacement: (String) -> String = { it },
)

fun TrierSettingsState.State.toResolvedSettings(): TrierResolvedSettings =
    TrierResolvedSettings(
        nodeInterpreterRef =
            nodeInterpreterRef.ifBlank {
                nodeInterpreterPath.ifBlank { null }
            },
        sortOnSave = sortOnSave,
        sortOnReformat = sortOnReformat,
        tailwindStylesheet = tailwindStylesheet.ifBlank { null },
        tailwindConfig = tailwindConfig.ifBlank { null },
        tailwindAttributes = splitList(tailwindAttributes),
        tailwindFunctions = splitList(tailwindFunctions),
        tailwindPreserveWhitespace = tailwindPreserveWhitespace,
        tailwindPreserveDuplicates = tailwindPreserveDuplicates,
    )

fun Path.absolutePath(): String = toAbsolutePath().normalize().absolutePathString()

private fun splitList(raw: String): List<String> =
    raw
        .split('\n', ',')
        .map(String::trim)
        .filter(String::isNotEmpty)
