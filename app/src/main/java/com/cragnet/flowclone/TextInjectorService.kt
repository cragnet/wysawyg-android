package com.cragnet.flowclone

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class TextInjectorService : AccessibilityService() {

    companion object {
        private const val TAG = "TextInjectorService"
        var pendingText: String? = null

        fun inject(context: Context, text: String) {
            pendingText = text
            if (instance == null) {
                Toast.makeText(context, "Enable FlowClone accessibility service to insert text automatically", Toast.LENGTH_LONG).show()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("dictation", text))
            } else {
                instance?.performInjection(text)
            }
        }

        private var instance: TextInjectorService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op; injection is triggered explicitly from OverlayService.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private fun performInjection(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return

        if (focused.isEditable) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "Inject text success=$success")
            if (!success) {
                fallbackPaste(focused, text)
            }
        } else {
            fallbackToClipboard(text)
        }
    }

    private fun fallbackPaste(node: AccessibilityNodeInfo, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("dictation", text))
        node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun fallbackToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("dictation", text))
        Toast.makeText(this, "No focused text field — copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
