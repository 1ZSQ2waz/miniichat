package com.miniichat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.conversationsDataStore: DataStore<Preferences> by preferencesDataStore(name = "conversations")

class ConversationStore(private val context: Context) {

    private val key = stringPreferencesKey("conversations_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val conversationsFlow: Flow<List<Conversation>> =
        context.conversationsDataStore.data.map { prefs ->
            val raw = prefs[key] ?: return@map emptyList()
            runCatching { json.decodeFromString<List<Conversation>>(raw) }.getOrDefault(emptyList())
        }

    suspend fun snapshot(): List<Conversation> = conversationsFlow.first()

    suspend fun save(list: List<Conversation>) {
        context.conversationsDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Conversation.serializer()), list)
        }
    }

    /** All mutations happen inside DataStore's atomic edit transaction. */
    suspend fun update(transform: (List<Conversation>) -> List<Conversation>) {
        context.conversationsDataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<List<Conversation>>(it) }.getOrNull()
            } ?: emptyList()
            prefs[key] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(Conversation.serializer()),
                transform(current)
            )
        }
    }

    suspend fun upsert(conv: Conversation) = update { current ->
        (current.filterNot { it.id == conv.id } + conv).sortedByDescending { it.updatedAt }
    }

    suspend fun updateMessage(convId: String, msgId: String, content: String) = update { list ->
        list.map { conv ->
            if (conv.id != convId || conv.messages.none { it.id == msgId }) conv
            else conv.copy(
                messages = conv.messages.map { if (it.id == msgId) it.copy(content = content) else it },
                updatedAt = System.currentTimeMillis()
            )
        }.sortedByDescending { it.updatedAt }
    }

    /** On a fresh process, an empty final assistant placeholder means generation was interrupted. */
    suspend fun recoverInterrupted(canRecover: (String) -> Boolean) = update { list ->
        list.map { conv ->
            val last = conv.messages.lastOrNull()
            if (last?.role == "assistant" && last.content.isEmpty() && canRecover(conv.id)) {
                conv.copy(messages = conv.messages.dropLast(1) + last.copy(content = "(generation interrupted)"))
            } else conv
        }
    }

    suspend fun delete(id: String) = update { it.filterNot { conv -> conv.id == id } }

    suspend fun setArchived(id: String, archived: Boolean) = update { list ->
        list.map { conv ->
            if (conv.id == id) conv.copy(archived = archived, updatedAt = System.currentTimeMillis()) else conv
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun rename(id: String, newTitle: String) = update { list ->
        list.map { conv ->
            if (conv.id == id) conv.copy(title = newTitle, updatedAt = System.currentTimeMillis()) else conv
        }.sortedByDescending { it.updatedAt }
    }
}
