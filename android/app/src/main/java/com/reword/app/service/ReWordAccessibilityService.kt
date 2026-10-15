package com.reword.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReWordAccessibilityService : AccessibilityService() {

    companion object {
        var currentInstance: ReWordAccessibilityService? = null
            private set
        var lastFocusedNode: AccessibilityNodeInfo? = null
            private set
        var currentPackage: String? = null
            private set

        fun isRunning(): Boolean = currentInstance != null

        fun fillTextIntoFocusedNode(text: String): Boolean {
            val node = lastFocusedNode ?: return false
            return try {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentInstance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank() && pkg != packageName) {
            currentPackage = pkg
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val source = event.source
                if (source != null && source.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
                    lastFocusedNode = source
                    FloatingShieldService.onInputFieldFocused(pkg)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (!pkg.isNullOrBlank() && pkg != packageName) {
                    FloatingShieldService.onPackageChanged(pkg)
                }
            }
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        currentInstance = null
        lastFocusedNode = null
    }
}
