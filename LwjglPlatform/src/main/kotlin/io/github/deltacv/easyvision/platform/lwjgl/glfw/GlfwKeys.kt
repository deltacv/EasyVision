package io.github.deltacv.easyvision.platform.lwjgl.glfw

import io.github.deltacv.easyvision.platform.PlatformKeys
import org.lwjgl.glfw.GLFW.*

object GlfwKeys : PlatformKeys {

    override val ArrowUp = glfwGetKeyScancode(GLFW_KEY_UP) //111
    override val ArrowDown = glfwGetKeyScancode(GLFW_KEY_DOWN)// 116
    override val ArrowLeft = glfwGetKeyScancode(GLFW_KEY_LEFT) // 113
    override val ArrowRight = glfwGetKeyScancode(GLFW_KEY_RIGHT) //114

    override val Escape =  glfwGetKeyScancode(GLFW_KEY_ESCAPE) //9
    override val Spacebar = glfwGetKeyScancode(GLFW_KEY_SPACE) //65
    override val Delete =  glfwGetKeyScancode(GLFW_KEY_DELETE) //119

    override val LeftShift =  glfwGetKeyScancode(GLFW_KEY_LEFT_SHIFT) //50
    override val RightShift = glfwGetKeyScancode(GLFW_KEY_RIGHT_SHIFT) //62

    override val LeftControl = glfwGetKeyScancode(GLFW_KEY_LEFT_CONTROL) //37
    override val RightControl = glfwGetKeyScancode(GLFW_KEY_RIGHT_SHIFT) //105

}