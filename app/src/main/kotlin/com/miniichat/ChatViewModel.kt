package com.miniichat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miniichat.api.LlmClient
import com.miniichat.data.AppSettings
import com.miniichat.data.Attachment
import com.miniichat.data.Assistant
import com.miniichat.data.AssistantStore
import com.miniichat.data.Conversation
import com.miniichat.data.ConversationStore
import com.miniichat.data.ProviderConfig
import com.miniichat.data.ProviderStore
import com.miniichat.data.SettingsRepository
import com.miniichat.service.StreamingService
import com.miniichat.util.PromptVars
import com.miniichat.util.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ConversationStore(app)
    val settingsRepo = SettingsRepository(app)
    val providerStore = ProviderStore(app)
    val assistantStore = AssistantStore(app)
    private val client = LlmClient()

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val providers: StateFlow<List<ProviderConfig>> = providerStore.providersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val assistants: StateFlow<List<Assistant>> = assistantStore.assistantsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val conversations: StateFlow<List<Conversation>> = store.conversationsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** In-flight replies are keyed by conversation and survive Activity recreation. */
    val activeReplies = StreamingService.replies

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _fetchingModelsFor = MutableStateFlow<String?>(null)
    val fetchingModelsFor: StateFlow<String?> = _fetchingModelsFor.asStateFlow()

    init {
        viewModelScope.launch {
            store.recoverInterrupted { id -> !activeReplies.value.containsKey(id) }
        }
        viewModelScope.launch {
            store.conversationsFlow.collect { list ->
                if (_activeId.value == null) _activeId.value = list.firstOrNull { !it.archived }?.id
            }
        }
        viewModelScope.launch {
            StreamingService.error.collect { message -> if (message != null) _error.value = message }
        }
    }

    fun selectConversation(id: String?) { _activeId.value = id }

    fun newConversation(): String {
        val id = newId()
        _activeId.value = id
        return id
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            store.delete(id)
            if (_activeId.value == id) _activeId.value = null
        }
    }

    fun renameConversation(id: String, title: String) {
        viewModelScope.launch { store.rename(id, title) }
    }

    fun archiveConversation(id: String, archived: Boolean) {
        viewModelScope.launch {
            store.setArchived(id, archived)
            if (archived && _activeId.value == id) {
                _activeId.value = store.snapshot().firstOrNull { !it.archived }?.id
            }
        }
    }

    fun stopStreaming() {
        val id = _activeId.value ?: return
        if (activeReplies.value.containsKey(id)) StreamingService.stop(getApplication(), id)
    }

    fun clearError() { _error.value = null }
    fun clearToast() { _toast.value = null }

    fun activeProvider(): ProviderConfig? =
        providers.value.firstOrNull { it.id == settings.value.activeProviderId }

    fun activeAssistant(): Assistant? =
        assistants.value.firstOrNull { it.id == settings.value.activeAssistantId }

    fun selectModel(providerId: String, model: String) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(activeProviderId = providerId, activeModel = model) }
        }
    }

    fun selectAssistant(id: String) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(activeAssistantId = id) }
        }
    }

    fun upsertAssistant(a: Assistant) {
        viewModelScope.launch { assistantStore.upsert(a) }
    }

    fun deleteAssistant(id: String) {
        viewModelScope.launch {
            assistantStore.delete(id)
            if (settings.value.activeAssistantId == id) {
                val remaining = assistantStore.snapshot()
                settingsRepo.update {
                    it.copy(activeAssistantId = remaining.firstOrNull()?.id ?: "default")
                }
            }
        }
    }

    fun upsertProvider(p: ProviderConfig) {
        viewModelScope.launch {
            providerStore.upsert(p)
            val all = providerStore.snapshot()
            if (settings.value.activeProviderId.isBlank() && all.isNotEmpty()) {
                val target = all.firstOrNull { it.id == p.id } ?: all.first()
                val firstModel = target.models.firstOrNull() ?: ""
                settingsRepo.update {
                    it.copy(activeProviderId = target.id, activeModel = firstModel)
                }
            }
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            providerStore.delete(id)
            if (settings.value.activeProviderId == id) {
                val remaining = providerStore.snapshot()
                val nextProvider = remaining.firstOrNull()
                settingsRepo.update {
                    it.copy(
                        activeProviderId = nextProvider?.id ?: "",
                        activeModel = nextProvider?.models?.firstOrNull() ?: ""
                    )
                }
            }
        }
    }

    fun fetchModels(providerId: String) {
        viewModelScope.launch {
            val provider = providerStore.snapshot().firstOrNull { it.id == providerId } ?: return@launch
            _fetchingModelsFor.value = providerId
            try {
                val models = client.listModels(provider)
                if (models.isEmpty()) {
                    _toast.value = "No models returned from /models"
                } else {
                    val updated = provider.copy(models = (provider.models + models).distinct().sorted())
                    providerStore.upsert(updated)
                    _toast.value = "Fetched ${models.size} models"
                }
            } catch (e: Exception) {
                _error.value = "Fetch models failed: ${e.message}"
            } finally {
                _fetchingModelsFor.value = null
            }
        }
    }

    fun addManualModel(providerId: String, model: String) {
        viewModelScope.launch {
            val trimmed = model.trim()
            if (trimmed.isEmpty()) return@launch
            val provider = providerStore.snapshot().firstOrNull { it.id == providerId } ?: return@launch
            val updated = provider.copy(models = (provider.models + trimmed).distinct().sorted())
            providerStore.upsert(updated)
        }
    }

    fun removeModel(providerId: String, model: String) {
        viewModelScope.launch {
            val provider = providerStore.snapshot().firstOrNull { it.id == providerId } ?: return@launch
            val updated = provider.copy(models = provider.models - model)
            providerStore.upsert(updated)
            if (settings.value.activeProviderId == providerId && settings.value.activeModel == model) {
                settingsRepo.update {
                    it.copy(activeModel = updated.models.firstOrNull() ?: "")
                }
            }
        }
    }

    fun sendMessage(
        text: String,
        attachments: List<Attachment> = emptyList(),
        webSearch: Boolean = settings.value.webSearchEnabled
    ): Boolean = startReply(_activeId.value ?: newConversation(), text, attachments, webSearch)

    private fun startReply(
        convId: String,
        text: String,
        attachments: List<Attachment>,
        webSearch: Boolean = settings.value.webSearchEnabled,
        replaceFromMessageId: String? = null
    ): Boolean {
        if (activeReplies.value.containsKey(convId)) return false
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return false
        val current = settings.value
        val assistant = activeAssistant()
        val provider = assistant?.preferredProviderId
            ?.let { id -> providers.value.firstOrNull { it.id == id } }
            ?: activeProvider()
        if (provider == null) {
            _error.value = "No provider configured."
            return false
        }
        val model = assistant?.preferredModel?.takeIf { it.isNotBlank() } ?: current.activeModel
        if (model.isBlank()) {
            _error.value = "No model selected."
            return false
        }
        if (provider.apiKey.isBlank()
            && !provider.baseUrl.contains("localhost")
            && !provider.baseUrl.contains("10.0.2.2")
        ) {
            _error.value = "API key is empty for ${provider.name}."
            return false
        }
        val prompt = PromptVars.render(
            template = assistant?.systemPrompt?.takeIf { it.isNotBlank() } ?: current.systemPrompt,
            model = model,
            provider = provider.name,
            assistant = assistant?.name ?: ""
        )
        val request = StreamingService.Request(
            conversationId = convId,
            text = trimmed,
            attachments = attachments,
            provider = provider,
            settings = current.copy(temperature = assistant?.temperature ?: current.temperature),
            model = model,
            systemPrompt = prompt,
            webSearch = webSearch,
            replaceFromMessageId = replaceFromMessageId
        )
        val started = StreamingService.start(getApplication(), request)
        if (!started) {
            _error.value = StreamingService.error.value ?: "Could not start generation"
        }
        return started
    }
    fun regenerate() = regenerateReply(null)

    fun regenerateFrom(messageId: String) = regenerateReply(messageId)

    private fun regenerateReply(messageId: String?) {
        val convId = _activeId.value ?: return
        if (activeReplies.value.containsKey(convId)) return
        viewModelScope.launch {
            val conv = store.snapshot().firstOrNull { it.id == convId } ?: return@launch
            val target = conv.messages.lastOrNull {
                it.role == "user" && (messageId == null || it.id == messageId)
            } ?: return@launch
            startReply(convId, target.content, target.attachments, replaceFromMessageId = target.id)
        }
    }

    /** Delete a single message in the current conversation. */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            val convId = _activeId.value ?: return@launch
            store.update { list -> list.map { conv ->
                if (conv.id == convId) conv.copy(
                    messages = conv.messages.filterNot { it.id == messageId },
                    updatedAt = System.currentTimeMillis()
                ) else conv
            } }
        }
    }

    /** Edit a message's text content (no resend). */
    fun editMessage(messageId: String, newContent: String) {
        viewModelScope.launch {
            val convId = _activeId.value ?: return@launch
            store.updateMessage(convId, messageId, newContent)
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }
}
