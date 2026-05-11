package com.darmaru.trier.listeners

import com.darmaru.trier.services.TrierSortService
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener

class TrierSaveListener : FileDocumentManagerListener {
    override fun beforeDocumentSaving(document: Document) {
        TrierSortService.getInstance().sortDocumentOnSave(document)
    }
}
