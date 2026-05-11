package com.darmaru.trier.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlin.io.path.writeText

@Service(Service.Level.APP)
class TrierNodeHelperService {
    @Volatile
    private var helperPath: Path? = null

    @Volatile
    private var runtimePath: Path? = null

    fun helperScriptPath(): Path {
        helperPath?.let { return it }

        synchronized(this) {
            helperPath?.let { return it }
            val resource =
                javaClass.classLoader.getResource("js/tailwind-sorter.mjs")
                    ?: error("Missing Trier Node helper resource")
            val tempFile = Files.createTempFile("trier-tailwind-sorter", ".mjs")
            val script = resource.openStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
            tempFile.writeText(script, Charsets.UTF_8)
            tempFile.toFile().deleteOnExit()
            helperPath = tempFile
            return tempFile
        }
    }

    fun bundledRuntimePath(): Path {
        runtimePath?.let { return it }

        synchronized(this) {
            runtimePath?.let { return it }
            val resource =
                javaClass.classLoader.getResourceAsStream("node-runtime.zip")
                    ?: error("Missing bundled Trier Node runtime")
            val tempDir = Files.createTempDirectory("trier-node-runtime")
            ZipInputStream(BufferedInputStream(resource)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val output = tempDir.resolve(entry.name).normalize()
                    if (!output.startsWith(tempDir)) {
                        error("Invalid bundled runtime entry: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        Files.createDirectories(output)
                    } else {
                        Files.createDirectories(output.parent)
                        Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING)
                    }
                    zip.closeEntry()
                }
            }
            tempDir.toFile().deleteOnExit()
            runtimePath = tempDir
            return tempDir
        }
    }

    fun notifyError(
        title: String,
        content: String,
    ) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup("Trier")
            .createNotification(title, content, NotificationType.ERROR)
            .notify(null)
    }

    companion object {
        fun getInstance(): TrierNodeHelperService =
            ApplicationManager.getApplication().getService(TrierNodeHelperService::class.java)
    }
}
