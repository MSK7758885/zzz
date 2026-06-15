package com.example.smartphoneagent.plugin

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.util.Log
import com.example.smartphoneagent.service.AgentAccessibilityService

class ActionExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ActionExecutor"
    }

    private val pluginManager = PluginManager(context)
    private val accessibilityService: AgentAccessibilityService?
        get() = AgentAccessibilityService.instance

    fun execute(action: String, params: Map<String, String> = emptyMap()): String {
        Log.d(TAG, "执行操作: $action 参数: $params")

        val plugins = pluginManager.getAllPlugins()
        if (plugins.none { it.actionType == action && it.enabled }) {
            return "插件 '$action' 未启用，请在设置中开启"
        }

        return try {
            when (action) {
                "open_app" -> openApp(params["packageName"] ?: "")
                "set_volume" -> setVolume(params["level"]?.toIntOrNull() ?: 50)
                "screenshot" -> takeScreenshot()
                "get_sensor" -> getSensorData()
                "get_ui_tree" -> getUiTree()
                "click_on_text" -> clickOnText(params["text"] ?: "")
                "click_position" -> clickAt(
                    params["x"]?.toFloatOrNull() ?: 0f,
                    params["y"]?.toFloatOrNull() ?: 0f
                )
                "swipe" -> performSwipe(
                    params["startX"]?.toFloatOrNull() ?: 0f,
                    params["startY"]?.toFloatOrNull() ?: 0f,
                    params["endX"]?.toFloatOrNull() ?: 0f,
                    params["endY"]?.toFloatOrNull() ?: 0f
                )
                "input_text" -> inputText(params["text"] ?: "")
                else -> "未知操作: $action"
            }
        } catch (e: Exception) {
            Log.e(TAG, "操作执行失败: $action", e)
            "执行 $action 出错: ${e.message}"
        }
    }

    private fun openApp(packageName: String): String {
        if (packageName.isEmpty()) return "包名为空"
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "成功打开 $packageName"
            } else {
                "未找到应用 $packageName"
            }
        } catch (e: Exception) {
            "打开 $packageName 失败: ${e.message}"
        }
    }

    private fun setVolume(level: Int): String {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (level * maxVolume / 100).coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            "音量已设置为 $level% (${targetVolume}/${maxVolume})"
        } catch (e: Exception) {
            "设置音量失败: ${e.message}"
        }
    }

    private fun takeScreenshot(): String {
        return try {
            val service = accessibilityService
            if (service != null) {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                "已通过无障碍服务截取屏幕截图"
            } else {
                "截图需要先开启无障碍服务"
            }
        } catch (e: Exception) {
            "截图失败: ${e.message}"
        }
    }

    private fun getSensorData(): String {
        return try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                ?: return "设备无加速度传感器"
            val listener = SensorListener()
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            Thread.sleep(200)
            sensorManager.unregisterListener(listener)
            val data = listener.lastData
            if (data != null) {
                val (x, y, z) = data
                "加速度传感器: x=${"%.2f".format(x)}, y=${"%.2f".format(y)}, z=${"%.2f".format(z)}"
            } else {
                "未能读取加速度传感器数据"
            }
        } catch (e: Exception) {
            "获取传感器数据失败: ${e.message}"
        }
    }

    private class SensorListener : SensorEventListener {
        var lastData: Triple<Float, Float, Float>? = null
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                lastData = Triple(it.values[0], it.values[1], it.values[2])
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun getUiTree(): String {
        return accessibilityService?.getUiTree() ?: "无障碍服务未开启，无法获取UI树"
    }

    private fun clickOnText(text: String): String {
        val service = accessibilityService ?: return "无障碍服务未开启"
        val success = service.clickOnText(text)
        return if (success) "已点击 '$text'" else "未在屏幕上找到 '$text'"
    }

    private fun clickAt(x: Float, y: Float): String {
        val service = accessibilityService ?: return "无障碍服务未开启"
        val success = service.performClick(x, y)
        return if (success) "已点击坐标 ($x, $y)" else "点击失败 ($x, $y)"
    }

    private fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float): String {
        val service = accessibilityService ?: return "无障碍服务未开启"
        val success = service.performSwipe(startX, startY, endX, endY)
        return if (success) "已从 ($startX, $startY) 滑动到 ($endX, $endY)" else "滑动失败"
    }

    private fun inputText(text: String): String {
        val service = accessibilityService ?: return "无障碍服务未开启"
        val success = service.inputText(text)
        return if (success) "文本输入成功" else "文本输入失败 - 无聚焦的输入框"
    }
}
