package com.miniichat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.miniichat.ChatViewModel
import com.miniichat.R
import com.miniichat.data.ProviderConfig
import kotlin.math.abs

private enum class Screen { Chat, Settings, Providers, ProviderEdit, Assistants }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(vm: ChatViewModel) {
    val drawer = remember { MutableTransitionState(false) }
    val drawerVisible = drawer.currentState || drawer.targetState || !drawer.isIdle
    val drawerStateHolder = rememberSaveableStateHolder()
    val roundScreen = LocalConfiguration.current.isScreenRound
    val focus = LocalFocusManager.current
    val openDrawer = {
        focus.clearFocus()
        drawer.targetState = true
    }
    val closeDrawer = {
        focus.clearFocus()
        drawer.targetState = false
    }
    val density = LocalDensity.current
    val edgePx = with(density) { 40.dp.toPx() }
    val swipePx = with(density) { 52.dp.toPx() }
    var screen by rememberSaveable { mutableStateOf(Screen.Chat) }
    var providerReturnScreen by rememberSaveable { mutableStateOf(Screen.Settings) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }

    androidx.activity.compose.BackHandler(enabled = screen == Screen.Chat && drawerVisible,
        onBack = closeDrawer)
    androidx.activity.compose.BackHandler(enabled = showModelPicker) {
        showModelPicker = false
    }
    androidx.activity.compose.BackHandler(enabled = screen == Screen.Settings) {
        screen = Screen.Chat
    }
    androidx.activity.compose.BackHandler(enabled = screen == Screen.Providers) {
        screen = providerReturnScreen
    }
    androidx.activity.compose.BackHandler(enabled = screen == Screen.ProviderEdit) {
        screen = Screen.Providers
    }
    androidx.activity.compose.BackHandler(enabled = screen == Screen.Assistants) {
        screen = Screen.Settings
    }

    val conversations by vm.conversations.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val settings by vm.settings.collectAsState()
    val providers by vm.providers.collectAsState()
    val activeReplies by vm.activeReplies.collectAsState()
    val error by vm.error.collectAsState()
    val toast by vm.toast.collectAsState()
    val fetchingId by vm.fetchingModelsFor.collectAsState()

    val activeConv = conversations.firstOrNull { it.id == activeId }
    val activeProvider = providers.firstOrNull { it.id == settings.activeProviderId }
    val assistants by vm.assistants.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.clearToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(screen, drawerVisible, edgePx, swipePx) {
                if (screen == Screen.Chat) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val drawerOpen = drawerVisible
                        var direction = when {
                            drawerOpen -> 0
                            down.position.x <= edgePx -> 1
                            down.position.x >= size.width - edgePx -> -1
                            else -> 0
                        }
                        if (!drawerOpen && direction == 0) return@awaitEachGesture
                        var drag = 0f
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, amount ->
                            if (drawerOpen) {
                                // Close with a left swipe, or with the watch's rightward edge gesture.
                                direction = when {
                                    amount < 0 -> -1
                                    down.position.x <= edgePx && amount > 0 -> 1
                                    else -> 0
                                }
                            }
                            val delta = change.position - down.position
                            if (direction * amount > 0 && abs(delta.x) > abs(delta.y)) {
                                drag = delta.x
                                change.consume()
                            }
                        } ?: return@awaitEachGesture
                        val completed = horizontalDrag(start.id) { change ->
                            drag += change.positionChange().x
                            change.consume()
                        }
                        if (completed && direction * drag >= swipePx) {
                            if (drawerOpen) closeDrawer()
                            else if (direction > 0) openDrawer()
                            else {
                                providerReturnScreen = Screen.Chat
                                screen = Screen.Providers
                            }
                        }
                    }
                }
            }
    ) {
        AnimatedContent(targetState = screen,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
            label = "screen fade") { destination ->
        when (destination) {
            Screen.Chat -> {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize().then(
                            if (drawerVisible) Modifier.semantics { invisibleToUser() }
                            else Modifier
                        )
                    ) {
                        ChatScreen(
                            conversation = activeConv,
                            settings = settings,
                            activeProvider = activeProvider,
                            isStreaming = activeId?.let(activeReplies::containsKey) == true,
                            streamingOverlay = activeId?.let(activeReplies::get)?.let { it.messageId to it.content },
                            rotaryActive = !drawerVisible,
                            onMenu = openDrawer,
                            onSend = { text, atts -> vm.sendMessage(text, atts) },
                            onStop = { vm.stopStreaming() },
                            onRegenerate = { vm.regenerate() },
                            onRegenerateFrom = { msgId -> vm.regenerateFrom(msgId) },
                            onDeleteMessage = { msgId -> vm.deleteMessage(msgId) },
                            onEditMessage = { msgId, newText -> vm.editMessage(msgId, newText) },
                            onNew = { vm.newConversation() },
                            onOpenSettings = { screen = Screen.Settings },
                            onPickModel = {
                                if (providers.isEmpty()) {
                                    editingProvider = null
                                    screen = Screen.ProviderEdit
                                } else showModelPicker = true
                            },
                            onToggleWebSearch = { vm.updateSettings { it.copy(webSearchEnabled = !it.webSearchEnabled) } }
                        )
                    }
                    AnimatedVisibility(
                        visibleState = drawer,
                        modifier = Modifier.matchParentSize(),
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(150)),
                        label = "conversation list fade"
                    ) {
                        Box(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                // Keep blank areas modal while allowing list scrolling and root swipes.
                                awaitPointerEventScope { while (true) awaitPointerEvent() }
                            }
                        ) {
                            if (!roundScreen) {
                                val backToChat = stringResource(R.string.back_to_chat)
                                Box(
                                    Modifier.matchParentSize().background(DrawerDefaults.scrimColor)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClickLabel = backToChat,
                                            onClick = closeDrawer
                                        )
                                )
                            }
                            drawerStateHolder.SaveableStateProvider("conversation list") {
                                GlassDrawer(
                                    conversations = conversations,
                                    activeId = activeId,
                                    generatingIds = activeReplies.keys,
                                    onClose = closeDrawer,
                                    onSelect = { id ->
                                        vm.selectConversation(id)
                                        closeDrawer()
                                    },
                                    onNew = {
                                        vm.newConversation()
                                        closeDrawer()
                                    },
                                    onDelete = { vm.deleteConversation(it) },
                                    onRename = { id, t -> vm.renameConversation(id, t) },
                                    onArchive = { id, archived -> vm.archiveConversation(id, archived) },
                                    isOpen = drawer.targetState,
                                    onOpenSettings = {
                                        closeDrawer()
                                        screen = Screen.Settings
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Screen.Settings -> {
                SettingsScreen(
                    settings = settings,
                    providers = providers,
                    assistants = assistants,
                    onBack = { screen = Screen.Chat },
                    onChange = { vm.updateSettings(it) },
                    onOpenProviders = {
                        providerReturnScreen = Screen.Settings
                        screen = Screen.Providers
                    },
                    onOpenAssistants = { screen = Screen.Assistants }
                )
            }
            Screen.Assistants -> {
                AssistantsScreen(
                    assistants = assistants,
                    activeId = settings.activeAssistantId,
                    onBack = { screen = Screen.Settings },
                    onSelect = { vm.selectAssistant(it) },
                    onUpsert = { vm.upsertAssistant(it) },
                    onDelete = { vm.deleteAssistant(it) }
                )
            }
            Screen.Providers -> {
                ProvidersScreen(
                    providers = providers,
                    fetchingId = fetchingId,
                    activeProviderId = settings.activeProviderId,
                    activeModel = settings.activeModel,
                    onBack = { screen = providerReturnScreen },
                    onCreate = {
                        editingProvider = null
                        screen = Screen.ProviderEdit
                    },
                    onEdit = { p ->
                        editingProvider = p
                        screen = Screen.ProviderEdit
                    },
                    onDelete = { vm.deleteProvider(it) },
                    onFetchModels = { vm.fetchModels(it) },
                    onAddManualModel = { id, m -> vm.addManualModel(id, m) },
                    onRemoveModel = { id, m -> vm.removeModel(id, m) },
                    onSelectModel = { id, m -> vm.selectModel(id, m) }
                )
            }
            Screen.ProviderEdit -> {
                ProviderEditorScreen(
                    initial = editingProvider,
                    onCancel = { screen = Screen.Providers },
                    onSave = { p ->
                        vm.upsertProvider(p)
                        screen = Screen.Providers
                    }
                )
            }
        }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.fillMaxSize())
    }

    if (showModelPicker) {
        ModelPickerSheet(
            providers = providers,
            activeProviderId = settings.activeProviderId,
            activeModel = settings.activeModel,
            onPick = { pid, m -> vm.selectModel(pid, m) },
            onDismiss = { showModelPicker = false }
        )
    }

    error?.let { msg ->
        val needsProviderFix = msg.contains("provider", ignoreCase = true)
            || msg.contains("api key", ignoreCase = true)
            || msg.contains("model", ignoreCase = true)
            || msg.contains("401")
            || msg.contains("403")
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = {
                if (needsProviderFix) {
                    TextButton(onClick = {
                        vm.clearError()
                        screen = Screen.Providers
                    }) { Text(stringResource(R.string.error_open_providers)) }
                } else {
                    TextButton(onClick = { vm.clearError() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            },
            dismissButton = if (needsProviderFix) {
                {
                    TextButton(onClick = { vm.clearError() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            } else null,
            title = { Text(stringResource(R.string.error_dialog_title)) },
            text = { Text(msg) }
        )
    }
}
