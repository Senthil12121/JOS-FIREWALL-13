package com.jos.firewall

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent.getStringExtra("crash_trace") ?: "No crash details captured."

        val textView = TextView(this).apply {
            text = "JOS Firewall crashed. Copy/screenshot this and share it:\n\n$trace"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            textSize = 12f
            setPadding(24, 48, 24, 48)
            setTextIsSelectable(true)
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(textView)
        }

        setContentView(scrollView)
    }
}
