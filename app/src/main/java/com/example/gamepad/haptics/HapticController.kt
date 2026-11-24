package com.example.gamepad.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticController(context: Context) {

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun tick(heavy: Boolean = false) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effectId = if (heavy) {
                    VibrationEffect.EFFECT_HEAVY_CLICK
                } else {
                    VibrationEffect.EFFECT_CLICK
                }
                vib.vibrate(VibrationEffect.createPredefined(effectId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createOneShot(
                        if (heavy) 18L else 10L,
                        if (heavy) 180 else 110
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(if (heavy) 16L else 9L)
            }
        } catch (_: Exception) {
        }
    }
}
