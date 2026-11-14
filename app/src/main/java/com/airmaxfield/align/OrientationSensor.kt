package com.airmaxfield.align

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Куда смотрит камера телефона (телефон стоит вертикально на антенне, экраном к человеку).
 *
 * Берём GAME_ROTATION_VECTOR (гироскоп + акселерометр): металл мачты и антенны не сбивает его,
 * как магнитный компас. Север у него условный — но для «повернуть на 7°» нужны только разницы углов.
 * Если такого датчика нет — обычный ROTATION_VECTOR; нет и его — работаем без направления.
 */
class OrientationSensor(ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val available: Boolean get() = sensor != null

    private val _orientation = MutableStateFlow<Orientation?>(null)
    val orientation: StateFlow<Orientation?> = _orientation.asStateFlow()

    private val r = FloatArray(9)
    private val remapped = FloatArray(9)
    private val angles = FloatArray(3)

    fun start() {
        val s = sensor ?: return
        sm?.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm?.unregisterListener(this)
        _orientation.value = null
    }

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(r, e.values)
        // Ось камеры (задняя сторона телефона) становится осью «вперёд» — как в AR-приложениях.
        SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
        SensorManager.getOrientation(remapped, angles)
        var az = Math.toDegrees(angles[0].toDouble()).toFloat()
        if (az < 0) az += 360f
        // getOrientation: pitch > 0, когда ось камеры смотрит вниз; нам удобнее «вверх — плюс».
        val tiltUp = -Math.toDegrees(angles[1].toDouble()).toFloat()
        val prev = _orientation.value
        _orientation.value = if (prev == null) Orientation(az, tiltUp) else Orientation(
            // Сглаживание: датчик шумит на ±0.5°, а рука на антенне дрожит.
            (prev.azimuthDeg + AlignmentAssistant.angleDiff(prev.azimuthDeg, az) * SMOOTH + 360f) % 360f,
            prev.tiltUpDeg + (tiltUp - prev.tiltUpDeg) * SMOOTH,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object { const val SMOOTH = 0.15f }
}
