package com.pixeloffice.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.pixeloffice.rendering.WindowContentSize
import com.pixeloffice.rendering.WindowSizing
import org.lwjgl.glfw.GLFW.GLFW_DONT_CARE
import org.lwjgl.glfw.GLFW.GLFW_MAXIMIZED
import org.lwjgl.glfw.GLFW.GLFW_TRUE
import org.lwjgl.glfw.GLFW.glfwGetMonitorWorkarea
import org.lwjgl.glfw.GLFW.glfwGetMonitors
import org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor
import org.lwjgl.glfw.GLFW.glfwGetWindowAttrib
import org.lwjgl.glfw.GLFW.glfwGetWindowFrameSize
import org.lwjgl.glfw.GLFW.glfwGetWindowMonitor
import org.lwjgl.glfw.GLFW.glfwGetWindowPos
import org.lwjgl.glfw.GLFW.glfwGetWindowSize
import org.lwjgl.glfw.GLFW.glfwSetWindowAspectRatio
import org.lwjgl.glfw.GLFW.glfwSetWindowPos
import org.lwjgl.glfw.GLFW.glfwSetWindowSize
import kotlin.math.max
import kotlin.math.min

private const val PREFERRED_PIXEL_SCALE = 4f

private data class NativeRect(val x: Int, val y: Int, val width: Int, val height: Int)
private data class WindowFrame(val left: Int, val top: Int, val right: Int, val bottom: Int)

private var applyingDesktopResize = false
private var lastAcceptedContentSize: WindowContentSize? = null

fun applyDesktopWindowLayout(worldWidth: Int, worldHeight: Int, fixedSky: Boolean) {
    if (worldWidth <= 0 || worldHeight <= 0) return

    val graphics = Gdx.graphics as? Lwjgl3Graphics ?: return
    val window = graphics.window.windowHandle
    if (isExternallySized(window)) return

    val workArea = currentMonitorWorkArea(window) ?: return
    val frame = getWindowFrame(window)
    val maxContentWidth = (workArea.width - frame.left - frame.right).coerceAtLeast(1)
    val maxContentHeight = (workArea.height - frame.top - frame.bottom).coerceAtLeast(1)
    val target = if (fixedSky) {
        WindowSizing.calculate(
            worldWidth,
            worldHeight,
            maxContentWidth,
            maxContentHeight,
            PREFERRED_PIXEL_SCALE
        )
    } else {
        WindowSizing.calculateUniform(
            worldWidth,
            worldHeight,
            maxContentWidth,
            maxContentHeight,
            PREFERRED_PIXEL_SCALE
        )
    }
    if (target.width <= 0 || target.height <= 0) return

    // A fixed-height sky plus a scalable office is not one constant aspect
    // ratio, so native GLFW aspect locking cannot model it.
    glfwSetWindowAspectRatio(window, GLFW_DONT_CARE, GLFW_DONT_CARE)
    setWindowContentSize(window, target)

    val outerWidth = target.width + frame.left + frame.right
    val outerHeight = target.height + frame.top + frame.bottom
    val contentX = workArea.x + (workArea.width - outerWidth) / 2 + frame.left
    val contentY = workArea.y + (workArea.height - outerHeight) / 2 + frame.top
    glfwSetWindowPos(window, contentX, contentY)
}

fun constrainDesktopWindowResize(
    worldWidth: Int,
    worldHeight: Int,
    fixedSky: Boolean,
    requestedWidth: Int,
    requestedHeight: Int
) {
    if (worldWidth <= 0 || worldHeight <= 0 ||
        requestedWidth <= 0 || requestedHeight <= 0 || applyingDesktopResize
    ) {
        return
    }

    val graphics = Gdx.graphics as? Lwjgl3Graphics ?: return
    val window = graphics.window.windowHandle
    if (isExternallySized(window)) return

    val workArea = currentMonitorWorkArea(window) ?: return
    val frame = getWindowFrame(window)
    val previous = lastAcceptedContentSize ?: currentWindowContentSize(window)
    val target = WindowSizing.constrainResize(
        worldWidth = worldWidth,
        worldHeight = worldHeight,
        previousWidth = previous.width,
        previousHeight = previous.height,
        requestedWidth = requestedWidth,
        requestedHeight = requestedHeight,
        maxContentWidth = (workArea.width - frame.left - frame.right).coerceAtLeast(1),
        maxContentHeight = (workArea.height - frame.top - frame.bottom).coerceAtLeast(1),
        fixedSky = fixedSky,
        preferredScale = PREFERRED_PIXEL_SCALE
    )
    if (target.width <= 0 || target.height <= 0) return

    if (target.width == requestedWidth && target.height == requestedHeight) {
        lastAcceptedContentSize = target
        return
    }
    setWindowContentSize(window, target)
}

private fun setWindowContentSize(window: Long, target: WindowContentSize) {
    lastAcceptedContentSize = target
    applyingDesktopResize = true
    try {
        glfwSetWindowSize(window, target.width, target.height)
    } finally {
        applyingDesktopResize = false
    }
}

private fun currentWindowContentSize(window: Long): WindowContentSize {
    val width = IntArray(1)
    val height = IntArray(1)
    glfwGetWindowSize(window, width, height)
    return WindowContentSize(width[0], height[0])
}

private fun isExternallySized(window: Long): Boolean =
    glfwGetWindowMonitor(window) != 0L ||
        glfwGetWindowAttrib(window, GLFW_MAXIMIZED) == GLFW_TRUE

private fun currentMonitorWorkArea(window: Long): NativeRect? {
    val windowX = IntArray(1)
    val windowY = IntArray(1)
    val windowWidth = IntArray(1)
    val windowHeight = IntArray(1)
    glfwGetWindowPos(window, windowX, windowY)
    glfwGetWindowSize(window, windowWidth, windowHeight)
    val windowRect = NativeRect(windowX[0], windowY[0], windowWidth[0], windowHeight[0])

    val monitors = glfwGetMonitors()
    var bestArea: NativeRect? = null
    var bestOverlap = -1L
    if (monitors != null) {
        for (index in monitors.position() until monitors.limit()) {
            val area = monitorWorkArea(monitors.get(index)) ?: continue
            val overlap = intersectionArea(windowRect, area)
            if (overlap > bestOverlap) {
                bestArea = area
                bestOverlap = overlap
            }
        }
    }

    if (bestArea != null) return bestArea
    val primary = glfwGetPrimaryMonitor()
    return if (primary == 0L) null else monitorWorkArea(primary)
}

private fun monitorWorkArea(monitor: Long): NativeRect? {
    if (monitor == 0L) return null
    val x = IntArray(1)
    val y = IntArray(1)
    val width = IntArray(1)
    val height = IntArray(1)
    glfwGetMonitorWorkarea(monitor, x, y, width, height)
    if (width[0] <= 0 || height[0] <= 0) return null
    return NativeRect(x[0], y[0], width[0], height[0])
}

private fun getWindowFrame(window: Long): WindowFrame {
    val left = IntArray(1)
    val top = IntArray(1)
    val right = IntArray(1)
    val bottom = IntArray(1)
    glfwGetWindowFrameSize(window, left, top, right, bottom)
    return WindowFrame(left[0], top[0], right[0], bottom[0])
}

private fun intersectionArea(first: NativeRect, second: NativeRect): Long {
    val width = max(0, min(first.x + first.width, second.x + second.width) - max(first.x, second.x))
    val height = max(0, min(first.y + first.height, second.y + second.height) - max(first.y, second.y))
    return width.toLong() * height
}
