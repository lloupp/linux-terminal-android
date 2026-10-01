package com.linuxterminal.android

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

class MainActivity : Activity() {
    private lateinit var terminal: TerminalInputView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff101010.toInt())
        }
        terminal = TerminalInputView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            appendOutput("Linux Terminal Android\nIME/Gboard input ready. Runtime Linux ainda não provisionado.\n\n$ ")
        }
        root.addView(terminal)

        val scroll = HorizontalScrollView(this)
        val keys = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Ctrl", "Alt", "Esc", "Tab", "↑", "↓", "←", "→", "|", "/", "-").forEach { label ->
            keys.addView(Button(this).apply {
                text = label
                minWidth = 0
                setOnClickListener { terminal.sendSpecialKey(label) }
            })
        }
        scroll.addView(keys)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
        terminal.requestFocus()
        terminal.post {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
