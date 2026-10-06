package com.linuxterminal.android.terminal

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.termux.view.TerminalView

/** Real terminal rendering; composing text is staged by the upstream InputConnection. */
class TerminalSurface(context: Context) : TerminalView(context, null) {
    init {
        // This view is created in code, so no XML focusable attributes are applied.
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCreateInputConnection(attrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(attrs)
        // TYPE_NULL often hides Gboard voice/composition. Keep a text-class connection,
        // but disable suggestions: a terminal cannot replace previously executed input.
        attrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        attrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or
            (if (android.os.Build.VERSION.SDK_INT >= 26) EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING else 0) or EditorInfo.IME_ACTION_NONE
        return connection
    }
}
