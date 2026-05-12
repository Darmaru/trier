package com.darmaru.trier.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import java.util.Collections
import java.util.IdentityHashMap

@Service(Service.Level.APP)
class TrierExecutionGuard {
    private val activeDocuments =
        Collections.synchronizedSet(
            Collections.newSetFromMap(IdentityHashMap<Document, Boolean>()),
        )

    fun <T> guard(
        document: Document?,
        action: () -> T,
    ): T? {
        if (!tryEnter(document)) {
            return null
        }
        return try {
            action()
        } finally {
            release(document)
        }
    }

    fun tryEnter(document: Document?): Boolean = document == null || activeDocuments.add(document)

    fun release(document: Document?) {
        if (document != null) {
            activeDocuments.remove(document)
        }
    }
}
