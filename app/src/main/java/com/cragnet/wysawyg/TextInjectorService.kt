package com.cragnet.wysawyg

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class TextInjectorService : AccessibilityService() {

    companion object {
        private const val TAG = "TextInjectorService"
        var focusedEditableNode: AccessibilityNodeInfo? = null
        var instance: TextInjectorService? = null
            private set

        fun inject(context: Context, text: String) {
            if (instance == null) {
                WysawygLogger.w("Accessibility service not enabled — copying to clipboard")
                copyToClipboard(context, text)
                Toast.makeText(context, "Enable Wysawyg accessibility service to insert text automatically", Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                instance?.performInjection(text)
            }
        }

        private fun copyToClipboard(context: Context, text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("dictation", text))
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        WysawygLogger.i("Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                try {
                    val node = event.source
                    if (node != null && node.isEditable) {
                        focusedEditableNode = node
                        WysawygLogger.d("Focused editable node: ${node.className} ${node.viewIdResourceName}")
                    }
                } catch (e: Exception) {
                    WysawygLogger.e("Error handling accessibility event", e)
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        focusedEditableNode = null
        super.onDestroy()
    }

    private fun performInjection(text: String) {
        var node = focusedEditableNode
        if (node == null) {
            val root = rootInActiveWindow
            if (root == null) {
                WysawygLogger.w("No active window — copying to clipboard")
                fallbackToClipboard(text)
                return
            }
            node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

        if (node == null) {
            WysawygLogger.w("No focused editable node — copying to clipboard")
            fallbackToClipboard(text)
            return
        }

        if (!node.isEditable) {
            WysawygLogger.w("Focused node is not editable — copying to clipboard")
            fallbackToClipboard(text)
            return
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        WysawygLogger.i("Inject text success=$success")

        if (!success) {
            fallbackPaste(node, text)
        }
    }

    private fun fallbackPaste(node: AccessibilityNodeInfo, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("dictation", text))
        val success = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        WysawygLogger.i("Fallback paste success=$success")
    }

    private fun fallbackToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("dictation", text))
        Toast.makeText(this, "No focused text field — copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
