package grouzerliu.mirrorgame.adb

import java.io.Closeable
import java.io.DataOutputStream
import java.net.Socket

/**
 * scrcpy control channel.
 * Sends control messages over a dedicated socket connection.
 *
 * Protocol (scrcpy v3.3.4):
 * - TYPE_INJECT_TOUCH_EVENT (2): action(1) + pointerId(8) + x(4) + y(4) + screenW(2) + screenH(2) + pressure(2) + actionButton(4) + buttons(4)
 * - TYPE_BACK_OR_SCREEN_ON (4): action(1)
 *
 * All multi-byte values are big-endian.
 */
class ControlChannel(private val socket: Socket) : Closeable {

    private val dos = DataOutputStream(socket.getOutputStream())

    companion object {
        const val TYPE_INJECT_TOUCH_EVENT = 2
        const val TYPE_BACK_OR_SCREEN_ON = 4

        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP = 6

        private const val PRESSURE_MAX: Short = -1 // 0xFFFF as signed short
        private const val PRESSURE_MIN: Short = 0
    }

    @Synchronized
    fun sendTouchDown(x: Int, y: Int, screenWidth: Int, screenHeight: Int, pointerId: Int = 0) {
        if (socket.isClosed) return
        try {
            writeTouchEvent(ACTION_DOWN, x, y, screenWidth, screenHeight, PRESSURE_MAX, pointerId)
            dos.flush()
        } catch (e: Exception) {
            println("[control] sendTouchDown error: ${e.message}")
        }
    }

    @Synchronized
    fun sendPointerDown(x: Int, y: Int, screenWidth: Int, screenHeight: Int, pointerId: Int) {
        if (socket.isClosed) return
        try {
            writeTouchEvent(ACTION_POINTER_DOWN, x, y, screenWidth, screenHeight, PRESSURE_MAX, pointerId)
            dos.flush()
        } catch (e: Exception) {
            println("[control] sendPointerDown error: ${e.message}")
        }
    }

    @Synchronized
    fun sendTouchMove(x: Int, y: Int, screenWidth: Int, screenHeight: Int, pointerId: Int = 0) {
        if (socket.isClosed) return
        try {
            writeTouchEvent(ACTION_MOVE, x, y, screenWidth, screenHeight, PRESSURE_MAX, pointerId)
            dos.flush()
        } catch (e: Exception) {
            println("[control] sendTouchMove error: ${e.message}")
        }
    }

    @Synchronized
    fun sendTouchUp(x: Int, y: Int, screenWidth: Int, screenHeight: Int, pointerId: Int = 0) {
        if (socket.isClosed) return
        try {
            writeTouchEvent(ACTION_UP, x, y, screenWidth, screenHeight, PRESSURE_MIN, pointerId)
            dos.flush()
        } catch (e: Exception) {
            println("[control] sendTouchUp error: ${e.message}")
        }
    }

    @Synchronized
    fun sendPointerUp(x: Int, y: Int, screenWidth: Int, screenHeight: Int, pointerId: Int) {
        if (socket.isClosed) return
        try {
            writeTouchEvent(ACTION_POINTER_UP, x, y, screenWidth, screenHeight, PRESSURE_MIN, pointerId)
            dos.flush()
        } catch (e: Exception) {
            println("[control] sendPointerUp error: ${e.message}")
        }
    }

    /**
     * Send a tap (touch down then up) at the specified device coordinates.
     */
    @Synchronized
    fun sendTouch(x: Int, y: Int, screenWidth: Int, screenHeight: Int) {
        if (socket.isClosed) return
        try {
            writeTouchEvent(ACTION_DOWN, x, y, screenWidth, screenHeight, PRESSURE_MAX, 0)
            writeTouchEvent(ACTION_UP, x, y, screenWidth, screenHeight, PRESSURE_MIN, 0)
            dos.flush()
        } catch (e: Exception) {
            println("[control] sendTouch error: ${e.message}")
        }
    }

    /**
     * Send a BACK key event (press then release).
     */
    @Synchronized
    fun sendBack() {
        if (socket.isClosed) return
        try {
            dos.writeByte(TYPE_BACK_OR_SCREEN_ON)
            dos.writeByte(ACTION_DOWN)
            dos.writeByte(TYPE_BACK_OR_SCREEN_ON)
            dos.writeByte(ACTION_UP)
            dos.flush()
        } catch (e: Exception) {
            println("[control] sendBack error: ${e.message}")
        }
    }

    private fun writeTouchEvent(
        action: Int, x: Int, y: Int, screenWidth: Int, screenHeight: Int, pressure: Short, pointerId: Int = 0,
    ) {
        dos.writeByte(TYPE_INJECT_TOUCH_EVENT)
        dos.writeByte(action)
        dos.writeLong(pointerId.toLong())
        dos.writeInt(x)
        dos.writeInt(y)
        dos.writeShort(screenWidth)
        dos.writeShort(screenHeight)
        dos.writeShort(pressure.toInt())
        dos.writeInt(0) // actionButton = 0
        dos.writeInt(0) // buttons = 0
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
