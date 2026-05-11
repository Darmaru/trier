package com.darmaru.trier.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "TrierSettings", storages = [Storage("trier.xml")])
class TrierSettingsState : PersistentStateComponent<TrierSettingsState.State> {
    data class State(
        var nodeInterpreterRef: String = "",
        var nodeInterpreterPath: String = "",
        var sortOnSave: Boolean = false,
        var sortOnReformat: Boolean = true,
        var tailwindStylesheet: String = "",
        var tailwindConfig: String = "",
        var tailwindAttributes: String = "",
        var tailwindFunctions: String = "",
        var tailwindPreserveWhitespace: Boolean = false,
        var tailwindPreserveDuplicates: Boolean = false,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    fun snapshot(): State = state.copy()

    fun update(mutator: (State) -> Unit) {
        mutator(state)
    }

    companion object {
        fun getInstance(): TrierSettingsState =
            ApplicationManager.getApplication().getService(TrierSettingsState::class.java)
    }
}
