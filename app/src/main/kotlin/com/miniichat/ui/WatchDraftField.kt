package com.miniichat.ui

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams
import android.widget.EditText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.miniichat.R

/** Let Android's editor keep the watch IME's fullscreen text and selection in sync. */
@Composable
internal fun WatchDraftField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val hintText = stringResource(R.string.hint_watch_reply)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    AndroidView(
        factory = { WatchDraftEditText(it) },
        modifier = modifier,
        update = { editor ->
            editor.onDraftChange = onValueChange
            if (editor.isEnabled != enabled) editor.isEnabled = enabled
            if (editor.hint != hintText) editor.hint = hintText
            if (editor.currentTextColor != textColor) editor.setTextColor(textColor)
            if (editor.currentHintTextColor != hintColor) editor.setHintTextColor(hintColor)
            editor.syncDraft(value)
        }
    )
}

private class WatchDraftEditText(context: Context) : EditText(context) {
    var onDraftChange: ((TextFieldValue) -> Unit)? = null
    private var syncingDraft = false

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        maxLines = 2
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        textSize = 14f
        includeFontPadding = false
        background = null
        setPadding(0, 0, 0, 0)
        doAfterTextChanged { publishDraft() }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        publishDraft()
    }

    fun syncDraft(value: TextFieldValue) {
        // Replacing the Editable during typing would interrupt the IME's composing session.
        if (text?.toString() == value.text) return
        syncingDraft = true
        try {
            setText(value.text)
            setSelection(value.selection.start, value.selection.end)
        } finally {
            syncingDraft = false
        }
    }

    private fun publishDraft() {
        if (syncingDraft) return
        val onChange = onDraftChange ?: return
        val draft = text?.toString().orEmpty()
        onChange(TextFieldValue(draft, TextRange(
            selectionStart.coerceIn(0, draft.length),
            selectionEnd.coerceIn(0, draft.length)
        )))
    }
}
