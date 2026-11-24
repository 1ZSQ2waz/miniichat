package com.miniichat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.miniichat.R
import com.miniichat.data.AppSettings
import com.miniichat.data.Conversation
import com.miniichat.data.Message
import com.miniichat.data.ProviderConfig

@Composable
fun ChatScreen(
    conversation: Conversation?,
    settings: AppSettings,
    activeProvider: ProviderConfig?,
    isStreaming: Boolean,
    streamingOverlay: Pair<String, String>? = null,
    rotaryActive: Boolean = true,
    onMenu: () -> Unit,
    onSend: (String, List<com.miniichat.data.Attachment>) -> Boolean,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
    onRegenerateFrom: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onEditMessage: (String, String) -> Unit = { _, _ -> },
    onNew: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickModel: () -> Unit,
    onToggleWebSearch: () -> Unit = {}
) {
    val roundScreen = LocalConfiguration.current.isScreenRound
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 18.dp.toPx() }
    var chromeVisible by rememberSaveable(conversation?.id) { mutableStateOf(true) }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var composerHeightPx by remember { mutableIntStateOf(0) }
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var pendingAttachments by remember { mutableStateOf<List<com.miniichat.data.Attachment>>(emptyList()) }
    val listState = rememberLazyListState()
    val rotaryScope = rememberCoroutineScope()
    val rawMessages = conversation?.messages ?: emptyList()
    // Apply in-memory streaming overlay so the assistant message updates per-token
    // without DataStore writes.
    val messages = remember(rawMessages, streamingOverlay) {
        val ov = streamingOverlay
        if (ov == null) rawMessages
        else rawMessages.map { if (it.id == ov.first) it.copy(content = ov.second) else it }
    }
    var editingMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingDraft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(conversation?.id, messages.size) {
        if (messages.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    val scrollChrome = remember(roundScreen, conversation?.id, swipeThreshold, imeVisible) {
        object : NestedScrollConnection {
            private var distance = 0f

            fun record(delta: Float) {
                if (!roundScreen || imeVisible || delta == 0f) return
                distance = if (distance * delta < 0f) delta else distance + delta
                if (abs(distance) >= swipeThreshold) {
                    chromeVisible = distance > 0
                    distance = 0f
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > abs(available.x)) {
                    record(available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                distance = 0f
                return Velocity.Zero
            }
        }
    }
    val surface = MaterialTheme.colorScheme.background
    val root = Modifier.fillMaxSize().background(surface).imePadding()
        .onPreRotaryScrollEvent { event ->
            if (rotaryActive) {
                if (messages.isNotEmpty()) scrollChrome.record(-event.verticalScrollPixels)
                rotaryScope.launch { listState.scrollBy(event.verticalScrollPixels) }
            }
            rotaryActive
        }
    val modelLabel = settings.activeModel.ifBlank { stringResource(R.string.select_model) }
    val header: @Composable () -> Unit = {
        ChatTopBar(
            title = conversation?.title?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.app_name),
            modelLabel = modelLabel,
            providerLabel = activeProvider?.name,
            onMenu = onMenu,
            onPickModel = onPickModel,
            onNew = onNew,
            roundScreen = roundScreen
        )
    }
    val composer: @Composable () -> Unit = {
        InputBar(
            value = input,
            onValueChange = { input = it },
            attachments = pendingAttachments,
            onAttachmentsChange = { pendingAttachments = it },
            onSend = {
                if (onSend(input.text, pendingAttachments)) {
                    input = TextFieldValue("")
                    pendingAttachments = emptyList()
                    chromeVisible = true
                }
            },
            onStop = onStop,
            isStreaming = isStreaming,
            enabled = activeProvider != null && settings.activeModel.isNotBlank(),
            roundScreen = roundScreen,
            webSearchEnabled = settings.webSearchEnabled,
            webSearchKeyConfigured = settings.webSearchApiKey.isNotBlank(),
            onToggleWebSearch = onToggleWebSearch,
            onConfigureWebSearch = onOpenSettings
        )
    }
    val timeline: @Composable (Modifier, PaddingValues) -> Unit = { modifier, padding ->
        if (messages.isEmpty()) {
            EmptyState(
                onPick = { onSend(it, emptyList()) },
                onOpenSettings = onOpenSettings,
                showSettingsHint = activeProvider == null,
                roundScreen = roundScreen,
                modifier = modifier.padding(padding)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxWidth()
                    .nestedScroll(scrollChrome)
                    .watchRotaryScroll(listState, rotaryActive),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(if (roundScreen) 8.dp else 20.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageItem(
                        message = msg,
                        isLastAssistant = msg.id == messages.lastOrNull()?.id && msg.role == "assistant",
                        isStreaming = isStreaming,
                        editing = editingMessageId == msg.id,
                        editingDraft = if (editingMessageId == msg.id) editingDraft else "",
                        onEditingDraftChange = { editingDraft = it },
                        onStartEdit = {
                            editingMessageId = msg.id
                            editingDraft = msg.content
                        },
                        onCommitEdit = {
                            editingMessageId?.let { id ->
                                onEditMessage(id, editingDraft)
                            }
                            editingMessageId = null
                            editingDraft = ""
                        },
                        onCancelEdit = {
                            editingMessageId = null
                            editingDraft = ""
                        },
                        onDelete = { onDeleteMessage(msg.id) },
                        onRegenerateFrom = { onRegenerateFrom(msg.id) }
                    )
                }
                if (roundScreen && messages.lastOrNull()?.role == "assistant"
                    && !isStreaming && messages.last().content.isNotEmpty()) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                                    .clickable(onClick = onRegenerate)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.regenerate),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (roundScreen) {
        val showControls = chromeVisible || messages.isEmpty() || imeVisible
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // These insets depend on control geometry, never on fade progress.
        val bottomInset = with(density) { composerHeightPx.toDp() }.coerceAtLeast(56.dp)
        Box(root) {
            timeline(
                Modifier.fillMaxSize(),
                PaddingValues(start = 26.dp, end = 26.dp, top = statusTop + 56.dp, bottom = bottomInset)
            )
            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150)),
                label = "watch chat header"
            ) {
                Box(Modifier.fillMaxWidth().drawBehind {
                    drawRect(Brush.verticalGradient(listOf(surface, surface.copy(alpha = 0.85f), Color.Transparent)))
                }) { header() }
            }
            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150)),
                label = "watch chat composer"
            ) {
                Box(Modifier.fillMaxWidth().onSizeChanged { composerHeightPx = it.height }.drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, surface.copy(alpha = 0.9f), surface)))
                }) { composer() }
            }
        }
    } else Column(root) {
        header()
        timeline(Modifier.weight(1f), PaddingValues(horizontal = 18.dp, vertical = 16.dp))
        AnimatedVisibility(
            visible = messages.lastOrNull()?.let { it.role == "assistant" && it.content.isNotEmpty() } == true
                && !isStreaming,
            enter = fadeIn(), exit = fadeOut()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .clickable(onClick = onRegenerate)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.regenerate),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        composer()
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    modelLabel: String,
    providerLabel: String?,
    onMenu: () -> Unit,
    onPickModel: () -> Unit,
    onNew: () -> Unit,
    roundScreen: Boolean
) {
    if (roundScreen) {
        val pill = MaterialTheme.colorScheme.surfaceContainerHigh
        val foreground = MaterialTheme.colorScheme.onSurface
        val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(horizontal = 36.dp)
                .padding(top = 17.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(pill)
                    .border(0.5.dp, outline, CircleShape).clickable(onClick = onMenu),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.chats_title),
                    tint = foreground, modifier = Modifier.size(18.dp))
            }
            Row(
                modifier = Modifier.weight(1f).height(32.dp).clip(CircleShape).background(pill)
                    .border(0.5.dp, outline, CircleShape)
                    .clickable(onClickLabel = stringResource(R.string.select_model), onClick = onPickModel)
                    .padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    modelLabel,
                    modifier = Modifier.weight(1f, fill = false),
                    color = foreground,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.ExpandMore, contentDescription = null,
                    tint = foreground, modifier = Modifier.size(12.dp))
            }
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(pill)
                    .border(0.5.dp, outline, CircleShape).clickable(onClick = onNew),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.new_chat),
                    tint = foreground, modifier = Modifier.size(17.dp))
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        // Row 1: nav icons + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = "menu",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                title,
                modifier = Modifier.weight(1f).padding(start = 4.dp, end = 4.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onNew) {
                Icon(Icons.Default.Edit, contentDescription = "new chat",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        // Row 2: small model chip aligned right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onPickModel)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        modelLabel,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp,
                            fontWeight = FontWeight.Medium)
                    )
                    if (!providerLabel.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            providerLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

@Composable
private fun MessageItem(
    message: Message,
    isLastAssistant: Boolean,
    isStreaming: Boolean,
    editing: Boolean = false,
    editingDraft: String = "",
    onEditingDraftChange: (String) -> Unit = {},
    onStartEdit: () -> Unit = {},
    onCommitEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRegenerateFrom: () -> Unit = {}
) {
    val isUser = message.role == "user"
    if (isUser) {
        UserBubble(
            message = message,
            editing = editing,
            editingDraft = editingDraft,
            onEditingDraftChange = onEditingDraftChange,
            onStartEdit = onStartEdit,
            onCommitEdit = onCommitEdit,
            onCancelEdit = onCancelEdit,
            onDelete = onDelete,
            onRegenerateFrom = onRegenerateFrom
        )
    } else {
        AssistantRow(
            message = message,
            isLastAssistant = isLastAssistant,
            isStreaming = isStreaming,
            onDelete = onDelete
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(
    message: Message,
    editing: Boolean,
    editingDraft: String,
    onEditingDraftChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit,
    onRegenerateFrom: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.82f),
            horizontalAlignment = Alignment.End
        ) {
            if (message.attachments.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.attachments.forEach { att ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (att.type == "image")
                                    Icons.Default.Image
                                else
                                    Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                att.name,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                maxLines = 1
                            )
                        }
                    }
                }
                if (message.content.isNotEmpty() || editing) Spacer(Modifier.height(4.dp))
            }

            if (editing) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = editingDraft,
                        onValueChange = onEditingDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.TextButton(onClick = onCancelEdit) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(Modifier.width(4.dp))
                        androidx.compose.material3.TextButton(onClick = onCommitEdit) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            } else if (message.content.isNotEmpty()) {
                Box {
                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = 18.dp,
                                    bottomEnd = 4.dp
                                )
                            )
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = {
                                clipboard.setText(AnnotatedString(message.content))
                                menuOpen = false
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuOpen = false
                                onStartEdit()
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.regenerate_from_here)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = {
                                menuOpen = false
                                onRegenerateFrom()
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "message actions",
                    modifier = Modifier.size(28.dp).clickable { menuOpen = true }.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AssistantRow(
    message: Message,
    isLastAssistant: Boolean,
    isStreaming: Boolean,
    onDelete: () -> Unit
) {
    var menuOpen by remember(message.id) { mutableStateOf(false) }
    val generating = isLastAssistant && isStreaming

    Column(modifier = Modifier.fillMaxWidth()) {
        if (message.content.isEmpty() && generating) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.thinking),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                TypingDots()
            }
        } else {
            SelectionContainer {
                MarkdownText(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (!generating) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.content.isNotEmpty()) ReplyCopyButton(message.content)
                Box {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.reply_actions),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReplyCopyButton(content: String, codeBlock: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(content) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1200)
            copied = false
        }
    }
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape)
            .clickable(enabled = content.isNotEmpty()) {
                clipboard.setText(AnnotatedString(content))
                copied = true
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = stringResource(when {
                copied -> R.string.copied
                codeBlock -> R.string.copy_code
                else -> R.string.copy_reply
            }),
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TypingDots() {
    val infinite = rememberInfiniteTransition(label = "typing")
    val alpha by infinite.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (i == 0) alpha
                            else if (i == 1) (1f - alpha)
                            else alpha * 0.7f + 0.3f
                        )
                    )
            )
        }
    }
}

@Composable
private fun EmptyState(
    onPick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    showSettingsHint: Boolean,
    roundScreen: Boolean,
    modifier: Modifier = Modifier
) {
    if (roundScreen) {
        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showSettingsHint) {
                Text(
                    stringResource(R.string.setup_provider),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            } else {
                Text(
                    stringResource(R.string.empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        }
        return
    }
    val examples = listOf(
        stringResource(R.string.example_prompt_1),
        stringResource(R.string.example_prompt_2),
        stringResource(R.string.example_prompt_3),
        stringResource(R.string.example_prompt_4)
    )
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = if (roundScreen) 36.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (roundScreen) 48.dp else 72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("M", color = MaterialTheme.colorScheme.onSurface,
                fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (showSettingsHint) {
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Settings, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.error_no_provider),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            examples.forEach { example ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clickable { onPick(example) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(example, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
