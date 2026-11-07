package com.example.securityguard

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * 封装加速度传感器，检测"摇一摇"手势。
 * 原理：计算三轴加速度的合成向量，减去重力加速度，
 * 当瞬时加速度变化超过阈值时，判定为一次有效摇动；
 * 在时间窗口内累计到足够次数的摇动，触发回调。
 */
class ShakeDetector(
    context: Context,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // 可根据实际测试调整的参数
    private val shakeThreshold = 15f       // 加速度阈值 (m/s^2)，越小越灵敏
    private val shakeCountRequired = 2     // 时间窗口内需要达到的摇动次数
    private val shakeWindowMillis = 1000L  // 时间窗口，单位毫秒

    private var shakeCount = 0
    private var windowStartTime = 0L

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // 合成加速度，减去重力加速度 (约 9.8 m/s^2)
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = magnitude - SensorManager.GRAVITY_EARTH

        if (delta > shakeThreshold) {
            val now = System.currentTimeMillis()

            if (now - windowStartTime > shakeWindowMillis) {
                // 超出时间窗口，重新计数
                windowStartTime = now
                shakeCount = 1
            } else {
                shakeCount++
                if (shakeCount >= shakeCountRequired) {
                    shakeCount = 0
                    onShakeDetected()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不需要处理
    }
}
