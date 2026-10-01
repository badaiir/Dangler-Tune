package com.dangler.tune.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Гиро-пайплайн по мотивам GyroParallaxBackground из GloryholeVPN:
 * GAME_ROTATION_VECTOR → ROTATION_VECTOR → ACCELEROMETER fallback,
 * автокалибровка первой точки, EMA alpha=0.10, deadzone 0.18, clamp ±25.
 *
 * В тюнере наклон управляет поверхностью "крови" внутри ноты
 * (как настоящая жидкость) + пузырьковым уровнем.
 */
class TiltSensor(context: Context) {

    data class Tilt(val rollDeg: Float = 0f, val pitchDeg: Float = 0f)

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _tilt = MutableStateFlow(Tilt())
    val tilt: StateFlow<Tilt> = _tilt.asStateFlow()

    private var calibratedRoll: Float? = null
    private var calibratedPitch: Float? = null
    private var filteredRoll = 0f
    private var filteredPitch = 0f
    private var targetRoll = 0f
    private var targetPitch = 0f
    private var started = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (rawRoll, rawPitch) = when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]
                    val roll = Math.toDegrees(atan2(ax.toDouble(), az.toDouble())).toFloat()
                    val pitch = Math.toDegrees(atan2(-ay.toDouble(), az.toDouble())).toFloat()
                    roll to pitch
                }
                else -> {
                    val rot = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rot, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rot, orientation)
                    val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                    val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                    roll.coerceIn(-40f, 40f) to pitch.coerceIn(-40f, 40f)
                }
            }

            // автокалибровка первой точки — центр независимо от стартового угла
            if (calibratedRoll == null) {
                calibratedRoll = rawRoll
                calibratedPitch = rawPitch
                return
            }

            val relRoll = (rawRoll - calibratedRoll!!).coerceIn(-25f, 25f)
            val relPitch = (rawPitch - calibratedPitch!!).coerceIn(-25f, 25f)

            // EMA
            filteredRoll += (relRoll - filteredRoll) * 0.10f
            filteredPitch += (relPitch - filteredPitch) * 0.10f

            // deadzone против тремора рук
            if (abs(filteredRoll - targetRoll) > 0.18f) targetRoll = filteredRoll
            if (abs(filteredPitch - targetPitch) > 0.18f) targetPitch = filteredPitch

            _tilt.value = Tilt(targetRoll, targetPitch)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (started) return
        val s = sensor ?: return
        sensorManager.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
        started = true
    }

    fun stop() {
        if (!started) return
        sensorManager.unregisterListener(listener)
        started = false
        calibratedRoll = null
        calibratedPitch = null
        filteredRoll = 0f
        filteredPitch = 0f
        targetRoll = 0f
        targetPitch = 0f
        _tilt.value = Tilt()
    }
}
