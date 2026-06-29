package com.houseofyeti.howler.audio

import android.content.Context

/**
 * Persists the tier-2 manual calibration for this install (one device profile, v1).
 * See docs/howler-calibration-design.md.
 */
class CalibrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("howler_calibration", Context.MODE_PRIVATE)

    fun loadManual(): Calibration.Manual? {
        if (!prefs.contains(KEY_OFFSET)) return null
        val offset = prefs.getFloat(KEY_OFFSET, 0f).toDouble()
        val meter = prefs.getString(KEY_METER, "") ?: ""
        return Calibration.Manual(offset, meter)
    }

    fun saveManual(manual: Calibration.Manual) {
        prefs.edit()
            .putFloat(KEY_OFFSET, manual.offsetDb.toFloat())
            .putString(KEY_METER, manual.referenceMeterClass)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_OFFSET = "offset_db"
        const val KEY_METER = "meter_class"
    }
}
