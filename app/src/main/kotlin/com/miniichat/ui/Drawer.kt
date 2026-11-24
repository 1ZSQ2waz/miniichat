package com.miniichat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniichat.R
import com.miniichat.data.Conversation
import java.util.Calendar
import kotlinx.coroutines.launch
@Composable
fun GlassDrawer(
    conversations: List<Conversation>,
    activeId: String?,
    generatingIds: Set<String>,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    isOpen: Boolean = true
) {
    val roundScreen = LocalConfiguration.current.isScreenRound
    val listState = rememberLazyListState()
    val rotaryScope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    val filtered = remember(conversations, query, showArchived) {
        conversations.filter { it.archived == showArchived &&
            (query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                it.messages.any { message -> message.content.contains(query, ignoreCase = true) }) }
    }
    val grouped = remember(filtered) { groupConversationsByDate(filtered) }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        modifier = (if (roundScreen) Modifier.fillMaxSize()
            else Modifier.width(280.dp).fillMaxSize())
            .onPreRotaryScrollEvent { event ->
                if (!isOpen) false else {
                    rotaryScope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = if (roundScreen) 50.dp else 16.dp,
                    vertical = if (roundScreen) 8.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (roundScreen) {
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_chat),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (!roundScreen) Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                if (!roundScreen) Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (roundScreen) R.string.chats_title else R.string.app_name),
                    style = if (roundScreen) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onNew,
                    modifier = if (roundScreen) Modifier.size(32.dp) else Modifier) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.new_chat))
                }
                if (roundScreen) IconButton(onClick = onOpenSettings,
                    modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings),
                        modifier = Modifier.size(20.dp))
                }
            }

            // Search box
            Box(
                modifier = Modifier
                    .padding(horizontal = if (roundScreen) 28.dp else 12.dp, vertical = 2.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = if (roundScreen) 7.dp else 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Search, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    stringResource(R.string.search_chats),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                            inner()
                        }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(
                horizontal = if (roundScreen) 30.dp else 22.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (generatingIds.isNotEmpty()) {
                    stringResource(R.string.generating_count, generatingIds.size)
                } else if (showArchived) "已归档" else "最近对话",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (showArchived) "返回最近" else "查看归档",
                    modifier = Modifier.clickable { showArchived = !showArchived }.padding(4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (query.isBlank()) "No chats yet" else "No matches",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(state = listState,
                    modifier = Modifier.weight(1f).watchRotaryScroll(listState, isOpen)) {
                    grouped.forEach { (labelKey, items) ->
                        if (!roundScreen) item(key = "h-$labelKey") {
                            Text(
                                stringResource(labelKey),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(items, key = { it.id }) { conv ->
                            ChatRow(
                                conv = conv,
                                selected = conv.id == activeId,
                                generating = conv.id in generatingIds,
                                onClick = { onSelect(conv.id) },
                                onRename = { renameTarget = conv },
                                onArchive = { onArchive(conv.id, !conv.archived) },
                                onDelete = { deleteTarget = conv }
                            )
                        }
                    }
                }
            }

            if (!roundScreen) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Settings, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.settings),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
            }
        }
    }

    renameTarget?.let { conv ->
        var newTitle by remember(conv.id) { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    onRename(conv.id, newTitle.ifBlank { "Untitled" })
                    renameTarget = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, singleLine = true)
            }
        )
    }
    deleteTarget?.let { conv ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            confirmButton = {
                TextButton(onClick = { onDelete(conv.id); deleteTarget = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text("Delete \"${conv.title}\"?") }
        )
    }
}

@Composable
private fun ChatRow(
    conv: Conversation,
    selected: Boolean,
    generating: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val bg = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (generating) {
            val description = stringResource(R.string.generating_reply)
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp).semantics { contentDescription = description },
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            conv.title.ifBlank { "Untitled" },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.MoreVert, contentDescription = "more",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menuOpen = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(if (conv.archived) "取消归档" else "归档") },
                    leadingIcon = { Icon(if (conv.archived) Icons.Default.Unarchive else Icons.Default.Archive, null) },
                    onClick = { menuOpen = false; onArchive() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

private fun groupConversationsByDate(
    list: List<Conversation>
): List<Pair<Int, List<Conversation>>> {
    if (list.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    val yStart = todayStart - 86_400_000L
    val sStart = todayStart - 7 * 86_400_000L
    val tStart = todayStart - 30 * 86_400_000L
    val today = mutableListOf<Conversation>(); val yest = mutableListOf<Conversation>()
    val sevenDays = mutableListOf<Conversation>(); val thirtyDays = mutableListOf<Conversation>()
    val older = mutableListOf<Conversation>()
    list.sortedByDescending { it.updatedAt }.forEach { c ->
        when {
            c.updatedAt >= todayStart -> today += c
            c.updatedAt >= yStart -> yest += c
            c.updatedAt >= sStart -> sevenDays += c
            c.updatedAt >= tStart -> thirtyDays += c
            else -> older += c
        }
    }
    val out = mutableListOf<Pair<Int, List<Conversation>>>()
    if (today.isNotEmpty()) out += R.string.date_today to today
    if (yest.isNotEmpty()) out += R.string.date_yesterday to yest
    if (sevenDays.isNotEmpty()) out += R.string.date_previous_7 to sevenDays
    if (thirtyDays.isNotEmpty()) out += R.string.date_previous_30 to thirtyDays
    if (older.isNotEmpty()) out += R.string.date_older to older
    return out
}
