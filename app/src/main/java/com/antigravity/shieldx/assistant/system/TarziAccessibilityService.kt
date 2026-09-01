package com.antigravity.shieldx.assistant.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A single interactive element Tarzi can see on screen, flattened from the
 * live accessibility node tree so the reasoning layer can act on it by index.
 */
data class ScreenElement(
    val index: Int,
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val className: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean
) {
    /** Best human-readable label for this element. */
    val label: String
        get() = when {
            text.isNotBlank() -> text
            contentDescription.isNotBlank() -> contentDescription
            viewId.isNotBlank() -> viewId.substringAfterLast('/')
            else -> className.substringAfterLast('.')
        }
}

/** An immutable snapshot of whatever is on screen right now. */
data class ScreenSnapshot(
    val packageName: String,
    val elements: List<ScreenElement>,
    val capturedAtMs: Long = System.currentTimeMillis()
) {
    /** Compact text rendering handed to the model as screen context. */
    fun toPromptContext(limit: Int = 60): String {
        if (elements.isEmpty()) return "Foreground app: $packageName (no readable elements)"
        val lines = elements.take(limit).joinToString("\n") { el ->
            val tags = buildList {
                if (el.isClickable) add("tappable")
                if (el.isEditable) add("input")
            }.joinToString(",")
            val suffix = if (tags.isNotEmpty()) " ($tags)" else ""
            "[${el.index}] ${el.label}$suffix"
        }
        return "Foreground app: $packageName\nOn-screen elements:\n$lines"
    }
}

/**
 * Gives Tarzi system-wide sight and control. Once enabled it stays resident, so
 * Tarzi keeps working after the user leaves the app - in YouTube, a browser, anywhere.
 */
class TarziAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TarziA11y"

        @Volatile
        private var instance: TarziAccessibilityService? = null

        private val _isConnectedFlow = MutableStateFlow(false)
        val isConnectedFlow: StateFlow<Boolean> = _isConnectedFlow.asStateFlow()

        private val _foregroundAppFlow = MutableStateFlow("")
        val foregroundAppFlow: StateFlow<String> = _foregroundAppFlow.asStateFlow()

        fun get(): TarziAccessibilityService? = instance

        /** True when the user has granted Tarzi the accessibility permission. */
        fun isEnabled(context: Context): Boolean {
            val expected = context.packageName + "/" + TarziAccessibilityService::class.java.name
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnectedFlow.value = true
        Log.i(TAG, "[A11Y_CONNECTED] Tarzi now has system-wide control")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg.isNotBlank()) {
            _foregroundAppFlow.value = pkg
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "[A11Y_INTERRUPT] Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isConnectedFlow.value = false
        Log.i(TAG, "[A11Y_DISCONNECTED] Tarzi lost system-wide control")
    }

    // ==========================================================
    // Reading the screen
    // ==========================================================

    /** Flatten the live window into an ordered, top-to-bottom element list. */
    fun captureScreen(): ScreenSnapshot {
        val root = rootInActiveWindow
            ?: return ScreenSnapshot(_foregroundAppFlow.value, emptyList())

        val collected = mutableListOf<ScreenElement>()
        try {
            walk(root, collected, 0)
        } catch (e: Exception) {
            Log.w(TAG, "[A11Y_CAPTURE_FAIL] " + e.message)
        }

        // Reading order: top to bottom, then left to right.
        val ordered = collected
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            .mapIndexed { i, el -> el.copy(index = i) }

        return ScreenSnapshot(root.packageName?.toString() ?: _foregroundAppFlow.value, ordered)
    }

    private fun walk(node: AccessibilityNodeInfo?, out: MutableList<ScreenElement>, depth: Int) {
        if (node == null || depth > 40 || out.size > 400) return

        val text = node.text?.toString().orEmpty().trim()
        val desc = node.contentDescription?.toString().orEmpty().trim()
        val viewId = node.viewIdResourceName.orEmpty()

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val onScreen = bounds.width() > 0 && bounds.height() > 0

        // Keep anything the user could act on, or anything that carries a label.
        val worthKeeping = onScreen && node.isVisibleToUser &&
            (node.isClickable || node.isEditable || text.isNotBlank() || desc.isNotBlank())

        if (worthKeeping) {
            out.add(
                ScreenElement(
                    index = out.size,
                    text = text,
                    contentDescription = desc,
                    viewId = viewId,
                    className = node.className?.toString().orEmpty(),
                    bounds = bounds,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable
                )
            )
        }

        for (i in 0 until node.childCount) {
            walk(node.getChild(i), out, depth + 1)
        }
    }

    // ==========================================================
    // Acting on the screen
    // ==========================================================

    /** Tap the element at the given index of a fresh capture. */
    fun tapElementAt(index: Int): Boolean {
        val snapshot = captureScreen()
        val target = snapshot.elements.getOrNull(index) ?: return false
        return tapBounds(target.bounds)
    }

    /**
     * Tap the first element whose visible label contains [query].
     * Prefers a genuinely clickable node, then falls back to any labelled one.
     */
    fun tapByText(query: String): Boolean {
        if (query.isBlank()) return false
        val snapshot = captureScreen()
        val needle = query.trim().lowercase()

        val match = snapshot.elements.firstOrNull {
            it.isClickable && it.label.lowercase().contains(needle)
        } ?: snapshot.elements.firstOrNull {
            it.label.lowercase().contains(needle)
        } ?: return false

        Log.i(TAG, "[A11Y_TAP_TEXT] '" + query + "' -> '" + match.label + "'")
        return tapBounds(match.bounds)
    }

    /**
     * Tap the Nth content item on screen - the "open that first video" case.
     * Prefers large clickable tiles, which is what a video or result card looks like.
     */
    fun tapContentItem(ordinal: Int): Boolean {
        val snapshot = captureScreen()
        if (snapshot.elements.isEmpty()) return false

        val screenWidth = snapshot.elements.maxOf { it.bounds.right }

        val cards = snapshot.elements.filter { el ->
            el.isClickable &&
                el.bounds.width() > screenWidth * 0.4 &&
                el.bounds.height() > 80 &&
                el.label.isNotBlank()
        }

        val target = cards.getOrNull(ordinal)
            ?: snapshot.elements.filter { it.isClickable && it.label.isNotBlank() }.getOrNull(ordinal)
            ?: return false

        Log.i(TAG, "[A11Y_TAP_ITEM] ordinal=" + ordinal + " -> '" + target.label + "'")
        return tapBounds(target.bounds)
    }

    /** Dispatch a real tap gesture at the centre of [bounds]. */
    private fun tapBounds(bounds: Rect): Boolean {
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        if (x <= 0f || y <= 0f) return false

        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /** Type [text] into the currently focused input field. */
    fun typeText(text: String): Boolean {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootInActiveWindow?.let { findFirstEditable(it) }
            ?: return false

        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child)
            if (found != null) return found
        }
        return null
    }

    /** Swipe the screen vertically. A positive [amount] scrolls down. */
    fun scroll(amount: Int = 1): Boolean {
        val root = rootInActiveWindow ?: return false
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        val midX = bounds.exactCenterX()

        val startY: Float
        val endY: Float
        if (amount > 0) {
            startY = bounds.height() * 0.75f
            endY = bounds.height() * 0.25f
        } else {
            startY = bounds.height() * 0.25f
            endY = bounds.height() * 0.75f
        }

        val path = Path()
        path.moveTo(midX, startY)
        path.lineTo(midX, endY)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 300L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    /** All readable text on screen, for "what does this say" style questions. */
    fun readScreenText(): String {
        val snapshot = captureScreen()
        return snapshot.elements
            .map { it.label }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" | ")
    }
}
