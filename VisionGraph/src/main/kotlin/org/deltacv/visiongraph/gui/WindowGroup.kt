package org.deltacv.visiongraph.gui

import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import org.deltacv.visiongraph.util.flags

enum class LayoutDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP
}

sealed class SizingMode {
    /**
     * Windows keep their original size
     */
    object None : SizingMode()

    /**
     * All windows get equal size based on available space
     * For horizontal layout: equal widths, full height
     * For vertical layout: full width, equal heights
     */
    object Grid : SizingMode()

    /**
     * All windows get the specified fixed size, and the WindowGroup size is calculated
     * For horizontal layout: itemSize.x per window, itemSize.y for height
     * For vertical layout: itemSize.x for width, itemSize.y per window
     */
    data class GridFixed(val itemSizeProvider: () -> ImVec2) : SizingMode() {
        constructor(itemSize: ImVec2) : this({ itemSize })
    }

    /**
     * All windows get the size of the largest window (dynamically updated each frame)
     * For horizontal layout: largest width for all, largest height for all
     * For vertical layout: largest width for all, largest height for all
     * WindowGroup size is calculated based on this largest size
     */
    object GridDynamic : SizingMode()

    /**
     * Custom sizing logic (extensible for future use)
     */
    data class Custom(val sizer: (Window, Int, Int, ImVec2) -> ImVec2) : SizingMode()
}

class WindowGroup(
    vararg windows: Window,
    var direction: LayoutDirection = LayoutDirection.LEFT_TO_RIGHT,
    var spacing: Float = 10f,
    var padding: Float = 0f,
    var sizingMode: SizingMode = SizingMode.None
) : Window() {

    private val orderedWindows = mutableListOf<Window>()
    private val pendingWindows = mutableListOf<Window>()
    private val initialWindows = windows.toList()

    override var title = "Window Group"
    override val windowFlags = flags(
        ImGuiWindowFlags.NoBackground,
        ImGuiWindowFlags.NoDecoration,
        ImGuiWindowFlags.NoMove,
        ImGuiWindowFlags.NoResize
    )

    override fun onEnable() {
        for (window in initialWindows) add(window)
        enablePendingWindows()
    }

    fun add(vararg windows: Window) {
        for (window in windows) {
            if (window.isEnabled)
                error("Cannot add already enabled window '${window.title}' to WindowGroup")

            if (isEnabled) {
                window.enable()
                orderedWindows.add(window)
            } else {
                pendingWindows.add(window)
            }
        }
    }

    private fun enablePendingWindows() {
        for (window in pendingWindows) {
            window.enable()
            orderedWindows.add(window)
        }
        pendingWindows.clear()
    }

    override fun drawContents() {}

    override fun postDrawContents() {
        if (orderedWindows.isEmpty()) return

        val groupPos = position
        var groupSize = size

        // ---- sizing mode size calculation ----
        when (sizingMode) {
            is SizingMode.GridFixed -> {
                groupSize = calculateGridFixedSize(sizingMode as SizingMode.GridFixed)
                size = groupSize
            }
            is SizingMode.GridDynamic -> {
                groupSize = calculateGridDynamicSize()
                size = groupSize
            }
            else -> {}
        }

        val availableWidth = groupSize.x - padding * 2f
        val availableHeight = groupSize.y - padding * 2f

        when (sizingMode) {
            is SizingMode.Grid -> applyGridSizing(availableWidth, availableHeight)
            is SizingMode.GridFixed -> applyGridFixedSizing(sizingMode as SizingMode.GridFixed)
            is SizingMode.GridDynamic -> applyGridDynamicSizing()
            is SizingMode.Custom -> applyCustomSizing(
                availableWidth,
                availableHeight,
                sizingMode as SizingMode.Custom
            )
            else -> {}
        }

        // ---- layout helpers ----
        fun layoutW(w: Window) = w.size.x
        fun layoutH(w: Window) =
            if (w.collapsed) ImGui.getFrameHeight()
            else w.size.y

        // ---- starting cursor ----
        var currentX = when (direction) {
            LayoutDirection.RIGHT_TO_LEFT -> groupPos.x - padding
            else -> groupPos.x + padding
        }

        var currentY = when (direction) {
            LayoutDirection.BOTTOM_TO_TOP -> groupPos.y - padding
            else -> groupPos.y + padding
        }

        // ---- position windows ----
        orderedWindows.forEachIndexed { index, window ->

            val w = layoutW(window)
            val h = layoutH(window)

            val x = when (direction) {
                LayoutDirection.RIGHT_TO_LEFT -> currentX - w
                else -> currentX
            }

            val y = when (direction) {
                LayoutDirection.BOTTOM_TO_TOP -> currentY - h
                else -> currentY
            }

            window.position = ImVec2(x, y)

            val isLast = index == orderedWindows.lastIndex

            when (direction) {
                LayoutDirection.LEFT_TO_RIGHT ->
                    currentX += w + if (!isLast) spacing else 0f

                LayoutDirection.RIGHT_TO_LEFT ->
                    currentX -= w + if (!isLast) spacing else 0f

                LayoutDirection.TOP_TO_BOTTOM ->
                    currentY += h + if (!isLast) spacing else 0f

                LayoutDirection.BOTTOM_TO_TOP ->
                    currentY -= h + if (!isLast) spacing else 0f
            }
        }
    }

    // --------------------------------------------------
    // SIZE CALCULATIONS
    // --------------------------------------------------

    private fun calculateGridDynamicSize(): ImVec2 {
        if (orderedWindows.isEmpty()) return ImVec2()

        var maxW = 0f
        var maxH = 0f

        for (w in orderedWindows) {
            val h = if (w.collapsed) ImGui.getFrameHeight() else w.size.y
            if (w.size.x > maxW) maxW = w.size.x
            if (h > maxH) maxH = h
        }

        val count = orderedWindows.size

        return when (direction) {
            LayoutDirection.LEFT_TO_RIGHT, LayoutDirection.RIGHT_TO_LEFT ->
                ImVec2(
                    (maxW * count) + spacing * (count - 1) + padding * 2,
                    maxH + padding * 2
                )

            else ->
                ImVec2(
                    maxW + padding * 2,
                    (maxH * count) + spacing * (count - 1) + padding * 2
                )
        }
    }

    private fun applyGridDynamicSizing() {
        var maxW = 0f
        var maxH = 0f

        for (w in orderedWindows) {
            val h = if (w.collapsed) ImGui.getFrameHeight() else w.size.y
            if (w.size.x > maxW) maxW = w.size.x
            if (h > maxH) maxH = h
        }

        for (w in orderedWindows) {
            w.size = ImVec2(maxW, maxH)
        }
    }

    private fun calculateGridFixedSize(mode: SizingMode.GridFixed): ImVec2 {
        if (orderedWindows.isEmpty()) return ImVec2()

        val item = mode.itemSizeProvider()
        val count = orderedWindows.size

        return when (direction) {
            LayoutDirection.LEFT_TO_RIGHT, LayoutDirection.RIGHT_TO_LEFT ->
                ImVec2(
                    item.x * count + spacing * (count - 1) + padding * 2,
                    item.y + padding * 2
                )

            else ->
                ImVec2(
                    item.x + padding * 2,
                    item.y * count + spacing * (count - 1) + padding * 2
                )
        }
    }

    private fun applyGridSizing(w: Float, h: Float) {
        val count = orderedWindows.size
        if (count == 0) return

        when (direction) {
            LayoutDirection.LEFT_TO_RIGHT, LayoutDirection.RIGHT_TO_LEFT -> {
                val totalSpacing = spacing * (count - 1)
                val width = (w - totalSpacing) / count
                orderedWindows.forEach { it.size = ImVec2(width, h) }
            }

            else -> {
                val totalSpacing = spacing * (count - 1)
                val height = (h - totalSpacing) / count
                orderedWindows.forEach { it.size = ImVec2(w, height) }
            }
        }
    }

    private fun applyGridFixedSizing(mode: SizingMode.GridFixed) {
        val item = mode.itemSizeProvider()

        for (w in orderedWindows) {
            val width = if (item.x > 0) item.x else w.size.x
            val height = if (item.y > 0) item.y else w.size.y
            w.size = ImVec2(width, height)
        }
    }

    private fun applyCustomSizing(
        w: Float,
        h: Float,
        mode: SizingMode.Custom
    ) {
        val space = ImVec2(w, h)
        orderedWindows.forEachIndexed { i, win ->
            win.size = mode.sizer(win, i, orderedWindows.size, space)
        }
    }

    // --------------------------------------------------

    override fun restore() {
        orderedWindows.forEach { it.restore() }
        super.restore()
    }

    override fun delete() {
        orderedWindows.forEach { it.delete() }
        pendingWindows.forEach { it.delete() }
        super.delete()
    }
}



