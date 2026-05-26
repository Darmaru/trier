package com.darmaru.trier.services

import com.intellij.openapi.project.Project
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name
import kotlin.io.path.pathString

internal data class TrierTailwindProjectPaths(
    val stylesheet: String?,
    val config: String?,
)

internal object TrierTailwindPathDetector {
    private const val MAX_STYLESHEET_SEARCH_DEPTH = 8
    private const val MAX_STYLESHEET_FILES_TO_READ = 500
    private const val MAX_STYLESHEET_CHARS_TO_READ = 1024 * 1024
    private const val MAX_CDN_SEARCH_DEPTH = 8
    private const val MAX_CDN_FILES_TO_READ = 1_000

    private val configCache = ConcurrentHashMap<String, String>()
    private val stylesheetCache = ConcurrentHashMap<String, String>()
    private val cdnCache = ConcurrentHashMap<String, Boolean>()

    private val configFileNames =
        listOf(
            "tailwind.config.js",
            "tailwind.config.cjs",
            "tailwind.config.mjs",
            "tailwind.config.ts",
            "tailwind.config.cts",
            "tailwind.config.mts",
        )

    private val stylesheetCandidates =
        listOf(
            "app.css",
            "styles.css",
            "style.css",
            "main.css",
            "index.css",
            "global.css",
            "globals.css",
            "src/app.css",
            "src/styles.css",
            "src/style.css",
            "src/main.css",
            "src/index.css",
            "src/global.css",
            "src/globals.css",
            "src/assets/style/tailwind.css",
            "src/assets/styles/tailwind.css",
            "src/styles/tailwind.css",
            "app/globals.css",
            "assets/css/app.css",
            "assets/style/tailwind.css",
            "assets/styles/tailwind.css",
            "resources/css/app.css",
            "resources/css/tailwind.css",
        )

    private val ignoredDirectories =
        setOf(
            ".cache",
            ".git",
            ".gradle",
            ".idea",
            ".next",
            ".nuxt",
            ".turbo",
            "build",
            "coverage",
            "dist",
            "node_modules",
            "out",
            "target",
            "vendor",
        )

    private val tailwindImportPattern = Regex("""@import\s+["']tailwindcss(?:/[^"']*)?["']""")
    private val tailwindPluginPattern = Regex("""@plugin\s+["']@tailwindcss(?:/[^"']*)?["']""")
    private val tailwindDirectivePattern = Regex("""@tailwind\s+(base|components|utilities)\b""")
    private val tailwindConfigPattern = Regex("""@config\s+["'][^"']+["']""")
    private val tailwindCdnPattern =
        Regex("""cdn\.tailwindcss\.com|cdn\.jsdelivr\.net/npm/@tailwindcss/browser(?:@[^"'<\s]+)?""")
    private val cdnSearchExtensions =
        setOf(
            "html",
            "htm",
            "php",
            "blade.php",
            "vue",
            "astro",
            "svelte",
            "twig",
            "njk",
            "liquid",
        )

    fun detect(
        project: Project,
        contextPath: String?,
    ): TrierTailwindProjectPaths {
        val projectBase = project.basePath?.let(::safePath)?.takeIf { Files.isDirectory(it) }
        val contextDirectory = contextDirectory(contextPath, projectBase) ?: projectBase
        val config = config(projectBase, contextDirectory)
        val stylesheetRoot = config?.parent ?: projectBase ?: contextDirectory
        val stylesheet = stylesheet(projectBase, stylesheetRoot)

        return TrierTailwindProjectPaths(
            stylesheet = stylesheet?.pathString,
            config = config?.pathString,
        )
    }

    internal fun clearCachesForTests() {
        configCache.clear()
        stylesheetCache.clear()
        cdnCache.clear()
    }

    fun detectTailwindCdn(
        project: Project,
        contextPath: String?,
    ): Boolean {
        val projectBase = project.basePath?.let(::safePath)?.takeIf { Files.isDirectory(it) }
        val context = contextPath?.let(::safePath)
        val key = cacheKey(projectBase, context)
        cdnCache[key]?.let { return it }

        val detected = context?.let(::containsTailwindCdn) == true || findTailwindCdnReference(projectBase)
        cdnCache[key] = detected
        return detected
    }

    private fun config(
        projectBase: Path?,
        contextDirectory: Path?,
    ): Path? {
        val key = cacheKey(projectBase, contextDirectory)
        configCache[key]
            ?.let(::safePath)
            ?.takeIf { Files.isRegularFile(it) }
            ?.let { return it }

        val detected = findConfig(projectBase, contextDirectory) ?: return null
        configCache[key] = detected.pathString
        return detected
    }

    private fun stylesheet(
        projectBase: Path?,
        root: Path?,
    ): Path? {
        val key = cacheKey(projectBase, root)
        stylesheetCache[key]
            ?.let(::safePath)
            ?.takeIf(::isTailwindStylesheet)
            ?.let { return it }

        val detected = findStylesheet(projectBase, root) ?: return null
        stylesheetCache[key] = detected.pathString
        return detected
    }

    private fun findConfig(
        projectBase: Path?,
        contextDirectory: Path?,
    ): Path? {
        val start = contextDirectory ?: return null
        return ancestorsWithinProject(start, projectBase)
            .asSequence()
            .flatMap { directory -> configFileNames.asSequence().map { directory.resolve(it) } }
            .firstOrNull { Files.isRegularFile(it) }
    }

    private fun findStylesheet(
        projectBase: Path?,
        root: Path?,
    ): Path? {
        val start = root ?: return null
        val candidates =
            ancestorsWithinProject(start, projectBase)
                .asSequence()
                .flatMap { directory -> stylesheetCandidates.asSequence().map { directory.resolve(it) } }
        candidates.firstOrNull(::isTailwindStylesheet)?.let { return it }
        return findStylesheetByContent(start)
    }

    private fun findStylesheetByContent(root: Path): Path? {
        if (!Files.isDirectory(root)) {
            return null
        }

        var found: Path? = null
        var cssFilesRead = 0
        runCatching {
            Files.walkFileTree(
                root,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        dir: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        if (found != null) {
                            return FileVisitResult.TERMINATE
                        }
                        if (dir != root && dir.name in ignoredDirectories) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        if (root.relativize(dir).nameCount > MAX_STYLESHEET_SEARCH_DEPTH) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        if (found != null || !attrs.isRegularFile || file.name.endsWith(".css").not()) {
                            return FileVisitResult.CONTINUE
                        }
                        cssFilesRead++
                        if (cssFilesRead > MAX_STYLESHEET_FILES_TO_READ) {
                            return FileVisitResult.TERMINATE
                        }
                        if (isTailwindStylesheet(file)) {
                            found = file
                            return FileVisitResult.TERMINATE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(
                        file: Path,
                        exc: IOException,
                    ): FileVisitResult = FileVisitResult.CONTINUE
                },
            )
        }
        return found
    }

    private fun isTailwindStylesheet(path: Path): Boolean {
        if (!Files.isRegularFile(path)) {
            return false
        }

        val text = readFilePrefix(path) ?: return false

        return tailwindImportPattern.containsMatchIn(text) ||
            tailwindPluginPattern.containsMatchIn(text) ||
            tailwindDirectivePattern.containsMatchIn(text) ||
            tailwindConfigPattern.containsMatchIn(text)
    }

    private fun containsTailwindCdn(path: Path): Boolean {
        if (!Files.isRegularFile(path)) {
            return false
        }
        return readFilePrefix(path)?.let(tailwindCdnPattern::containsMatchIn) == true
    }

    private fun findTailwindCdnReference(projectBase: Path?): Boolean {
        val root = projectBase ?: return false
        if (!Files.isDirectory(root)) {
            return false
        }

        var found = false
        var filesRead = 0
        runCatching {
            Files.walkFileTree(
                root,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        dir: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        if (found) {
                            return FileVisitResult.TERMINATE
                        }
                        if (dir != root && dir.name in ignoredDirectories) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        if (root.relativize(dir).nameCount > MAX_CDN_SEARCH_DEPTH) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        if (found || !attrs.isRegularFile || !isCdnSearchCandidate(file)) {
                            return FileVisitResult.CONTINUE
                        }
                        filesRead++
                        if (filesRead > MAX_CDN_FILES_TO_READ) {
                            return FileVisitResult.TERMINATE
                        }
                        if (containsTailwindCdn(file)) {
                            found = true
                            return FileVisitResult.TERMINATE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(
                        file: Path,
                        exc: IOException,
                    ): FileVisitResult = FileVisitResult.CONTINUE
                },
            )
        }
        return found
    }

    private fun isCdnSearchCandidate(path: Path): Boolean =
        cdnSearchExtensions.any { extension -> path.name.endsWith(".$extension") }

    private fun readFilePrefix(path: Path): String? =
        runCatching {
            Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                val buffer = CharArray(MAX_STYLESHEET_CHARS_TO_READ)
                val length = reader.read(buffer)
                if (length <= 0) {
                    ""
                } else {
                    String(buffer, 0, length)
                }
            }
        }.getOrNull()

    private fun ancestorsWithinProject(
        start: Path,
        projectBase: Path?,
    ): List<Path> {
        val normalizedStart = start.toAbsolutePath().normalize()
        val normalizedProjectBase = projectBase?.toAbsolutePath()?.normalize()
        val searchBoundary = normalizedProjectBase?.takeIf { normalizedStart.startsWith(it) }

        val ancestors = mutableListOf<Path>()
        var current: Path? = normalizedStart
        while (current != null) {
            ancestors.add(current)
            if (searchBoundary != null && current == searchBoundary) {
                break
            }
            current = current.parent
        }
        return ancestors
    }

    private fun contextDirectory(
        contextPath: String?,
        projectBase: Path?,
    ): Path? {
        val context = contextPath?.let(::safePath) ?: return projectBase
        return when {
            Files.isDirectory(context) -> context
            else -> context.parent
        }
    }

    private fun safePath(path: String): Path? = runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull()

    private fun cacheKey(
        projectBase: Path?,
        context: Path?,
    ): String = "${projectBase?.pathString.orEmpty()}|${context?.pathString.orEmpty()}"
}
