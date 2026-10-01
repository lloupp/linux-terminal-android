package com.linuxterminal.android

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText

/**
 * IME-first terminal surface. Text committed by Gboard (including voice typing)
 * lands in this view. PTY transport will replace the local echo in the next layer.
 */
class TerminalInputView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {
    private var ctrl = false
    private var alt = false

    init {
        typeface = Typeface.MONOSPACE
        setTextColor(0xffeeeeee.toInt())
        setBackgroundColor(0xff101010.toInt())
        textSize = 15f
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        isSingleLine = false
        setPadding(16, 16, 16, 16)
    }

    fun appendOutput(value: String) {
        append(value)
        setSelection(text?.length ?: 0)
    }

    fun sendSpecialKey(label: String) {
        when (label) {
            "Ctrl" -> ctrl = !ctrl
            "Alt" -> alt = !alt
            "Esc" -> append("\u001b")
            "Tab" -> append("\t")
            "↑" -> append("\u001b[A")
            "↓" -> append("\u001b[B")
            "→" -> append("\u001b[C")
            "←" -> append("\u001b[D")
            else -> append(label)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (ctrl && keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val ch = (keyCode - KeyEvent.KEYCODE_A + 1).toChar().toString()
            append(ch)
            ctrl = false
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
