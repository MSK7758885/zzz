package com.example.smartphoneagent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

@Suppress("DEPRECATION")
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AgentAccessibility"
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility Service destroyed")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun getUiTree(): String {
        val root = rootInActiveWindow ?: return "无法获取当前界面 UI 树"
        return try {
            val sb = StringBuilder()
            buildUiTree(root, 0, sb)
            sb.toString()
        } finally {
            root.recycle()
        }
    }

    private fun buildUiTree(node: AccessibilityNodeInfo, depth: Int, sb: StringBuilder) {
        val indent = "  ".repeat(depth)
        val className = node.className?.toString() ?: "unknown"
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val id = node.viewIdResourceName ?: ""

        sb.append("$indent[$className]")
        if (text.isNotEmpty()) sb.append(" text='$text'")
        if (contentDesc.isNotEmpty()) sb.append(" desc='$contentDesc'")
        if (id.isNotEmpty()) sb.append(" id='$id'")
        if (node.isClickable) sb.append(" clickable=true")
        if (node.isScrollable) sb.append(" scrollable=true")
        if (node.isEditable) sb.append(" editable=true")
        sb.append("\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    buildUiTree(child, depth + 1, sb)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    fun performClick(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 300))
        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun inputText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val editNode = findEditableField(root)
            if (editNode != null) {
                try {
                    editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            targetText
                        )
                    }
                    editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                } finally {
                    editNode.recycle()
                }
            } else {
                false
            }
        } finally {
            root.recycle()
        }
    }

    private fun findEditableField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableField(child)
            if (result != null) {
                if (result !== child) {
                    child.recycle()
                }
                return result
            }
            child.recycle()
        }
        return null
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val result = findNodeByTextRecursive(root, text)
        if (result !== root) {
            root.recycle()
        }
        return result
    }

    private fun findNodeByTextRecursive(
        node: AccessibilityNodeInfo, text: String
    ): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByTextRecursive(child, text)
            if (result != null) {
                if (result !== child) {
                    child.recycle()
                }
                return result
            }
            child.recycle()
        }
        return null
    }

    fun clickOnText(text: String): Boolean {
        val node = findNodeByText(text) ?: return false
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            true
        } finally {
            node.recycle()
        }
    }

    fun getNodeBoundsByText(text: String): Rect? {
        val node = findNodeByText(text) ?: return null
        return try {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            rect
        } finally {
            node.recycle()
        }
    }
}
