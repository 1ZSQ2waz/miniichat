package com.miniichat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.net.Uri
import androidx.annotation.MainThread
import com.miniichat.MainActivity
import com.miniichat.api.ChatMessage
import com.miniichat.api.ChatPart
import com.miniichat.api.LlmClient
import com.miniichat.api.SearchClient
import com.miniichat.data.AppSettings
import com.miniichat.data.Attachment
import com.miniichat.data.Conversation
import com.miniichat.data.ConversationStore
import com.miniichat.data.Message
import com.miniichat.data.ProviderConfig
import com.miniichat.util.AttachmentLoader
import com.miniichat.util.newId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns generation independently of the Activity and its ViewModel. */
class StreamingService : Service() {
    data class Request(
        val conversationId: String,
        val text: String,
        val attachments: List<Attachment>,
        val provider: ProviderConfig,
        val settings: AppSettings,
        val model: String,
        val systemPrompt: String,
        val webSearch: Boolean = false,
        val replaceFromMessageId: String? = null
    )

    data class ReplyDraft(val messageId: String, val content: String)

    companion object {
        private const val CHANNEL = "chat_generation"
        private const val NOTIFICATION_ID = 42
        private const val START = "com.miniichat.service.START"
        private const val STOP = "com.miniichat.service.STOP"
        private const val CONVERSATION_ID = "conversation_id"
        private val activeReplies = MutableStateFlow<Map<String, ReplyDraft?>>(emptyMap())
        val replies = activeReplies.asStateFlow()
        val error = MutableStateFlow<String?>(null)
        // Reserved on the main thread before the service receives its start intent.
        private val pending = mutableMapOf<String, Request>()

        @MainThread
        fun start(context: Context, request: Request): Boolean {
            val id = request.conversationId
            if (replies.value.containsKey(id)) return false
            activeReplies.update { it + (id to null) }
            error.value = null
            pending[id] = request
            try {
                val intent = Intent(context, StreamingService::class.java).setAction(START)
                    .putExtra(CONVERSATION_ID, id)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                pending.remove(id)
                activeReplies.update { it - id }
                error.value = e.message ?: "Could not start generation"
                return false
            }
            return true
        }

        @MainThread
        fun stop(context: Context, conversationId: String) {
            context.startService(Intent(context, StreamingService::class.java).setAction(STOP)
                .putExtra(CONVERSATION_ID, conversationId))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<String, Job>()
    private var latestStartId = 0
    private var inForeground = false
    private val store by lazy { ConversationStore(applicationContext) }
    private val client = lazy { LlmClient() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val id = intent?.getStringExtra(CONVERSATION_ID)
        if (intent?.action == STOP) {
            if (id == null) jobs.values.toList().forEach { it.cancel() }
            else jobs[id]?.cancel()
            stopIfIdle()
            return START_NOT_STICKY
        }
        val request = id?.let(pending::remove)
        if (request == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        try {
            if (!inForeground) {
                createChannel()
                val notice = notification()
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTIFICATION_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notice)
                }
                inForeground = true
            }
        } catch (e: Exception) {
            error.value = e.message ?: "Could not show generation notification"
            activeReplies.update { it - request.conversationId }
            stopIfIdle()
            return START_NOT_STICKY
        }
        val convId = request.conversationId
        // Install cleanup before yielding, even if the user immediately taps Stop.
        jobs[convId] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(Dispatchers.IO) { generate(request) }
            } finally {
                if (jobs.remove(convId) != null) {
                    activeReplies.update { it - convId }
                    stopIfIdle()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Response generation", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val cancel = PendingIntent.getService(this, 1, Intent(this, StreamingService::class.java).setAction(STOP), flags)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Generating response")
            .setContentText("Tap to return to chat")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel all", cancel)
            .build()
    }

    private suspend fun generate(request: Request) {
        val convId = request.conversationId
        val assistantId = newId()
        val builder = StringBuilder()
        var persisted = false
        var cancelled = false
        try {
            val user = Message(id = newId(), role = "user", content = request.text, attachments = request.attachments)
            val baseTitle = request.text.take(30).replace("\n", " ")
            var updated: Conversation? = null
            store.update { current ->
                val existing = current.firstOrNull { it.id == convId }
                val conv = existing ?: Conversation(id = convId, title = baseTitle)
                val priorMessages = if (request.replaceFromMessageId == null) conv.messages else {
                    val index = conv.messages.indexOfFirst {
                        it.id == request.replaceFromMessageId && it.role == "user"
                    }
                    check(index >= 0) { "The message to regenerate no longer exists." }
                    conv.messages.take(index)
                }
                updated = conv.copy(
                    title = if (conv.messages.isEmpty()) baseTitle else conv.title,
                    messages = priorMessages + user + Message(id = assistantId, role = "assistant", content = ""),
                    updatedAt = System.currentTimeMillis()
                )
                (current.filterNot { it.id == convId } + updated!!).sortedByDescending { it.updatedAt }
            }
            persisted = true
            publishReply(convId, ReplyDraft(assistantId, ""))
            val history = mutableListOf<ChatMessage>()
            if (request.systemPrompt.isNotBlank()) history.add(ChatMessage("system", request.systemPrompt))
            if (request.webSearch) {
                val context = SearchClient().search(request.text, request.settings.webSearchApiKey)
                history.add(ChatMessage("system", "Current web search results for the latest user request. Cite source URLs when used:\n$context"))
            }
            for (message in updated!!.messages.dropLast(1)) history.add(toApiMessage(message))
            var lastFlush = 0L
            client.value.chatStream(request.provider, request.settings, request.model, history).collect { delta ->
                builder.append(delta)
                publishReply(convId, ReplyDraft(assistantId, builder.toString()))
                val now = System.currentTimeMillis()
                if (now - lastFlush >= 1_000L) {
                    store.updateMessage(convId, assistantId, builder.toString())
                    lastFlush = now
                }
            }
            check(builder.isNotBlank()) { "The provider ended the request without returning an answer." }
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } catch (e: Exception) {
            error.value = e.message ?: "Request failed"
            if (persisted && builder.isBlank()) store.updateMessage(convId, assistantId, "(error: ${e.message})")
        } finally {
            withContext(NonCancellable) {
                if (persisted && builder.isNotBlank()) store.updateMessage(convId, assistantId, builder.toString())
                else if (persisted && cancelled) store.updateMessage(convId, assistantId, "(cancelled)")
            }
        }
    }

    private fun publishReply(convId: String, draft: ReplyDraft) {
        activeReplies.update { if (it.containsKey(convId)) it + (convId to draft) else it }
    }

    private fun toApiMessage(message: Message): ChatMessage {
        val images = message.attachments.filter { it.type == "image" }
        val files = message.attachments.filter { it.type != "image" }
        val fileText = files.joinToString("\n\n") { attachment ->
            AttachmentLoader.loadText(contentResolver, Uri.parse(attachment.uri), attachment.mimeType)
        }
        val text = listOf(message.content, fileText).filter { it.isNotBlank() }.joinToString("\n\n")
        if (message.role != "user" || images.isEmpty()) return ChatMessage(message.role, text)
        val parts = mutableListOf<ChatPart>()
        if (text.isNotBlank()) parts.add(ChatPart(type = "text", text = text))
        images.forEach { attachment ->
            val loaded = try {
                AttachmentLoader.load(contentResolver, Uri.parse(attachment.uri), attachment.mimeType.ifBlank { "image/jpeg" })
            } catch (e: Exception) {
                throw IllegalArgumentException("Cannot read image ${attachment.name}: ${e.message}", e)
            }
            parts.add(ChatPart(type = "image_url", imageUrl = ChatPart.ImageUrl("data:${loaded.mimeType};base64,${loaded.base64}")))
        }
        return ChatMessage(message.role, text, parts.takeIf { it.isNotEmpty() })
    }

    private fun stopIfIdle() {
        if (jobs.isNotEmpty() || pending.isNotEmpty()) return
        if (inForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
        stopSelfResult(latestStartId)
    }

    override fun onDestroy() {
        val interrupted = jobs.keys.toSet()
        jobs.clear()
        activeReplies.update { it - interrupted }
        scope.cancel()
        if (client.isInitialized()) client.value.close()
        super.onDestroy()
    }
}
