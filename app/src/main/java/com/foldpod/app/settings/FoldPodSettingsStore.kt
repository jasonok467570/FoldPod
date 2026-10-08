package com.foldpod.app.settings

import android.content.Context

class FoldPodSettingsStore(
    context: Context,
) {

    private val prefs =
        context.getSharedPreferences(
            "foldpod_settings",
            Context.MODE_PRIVATE,
        )

    val lastSelectedPlayerPackage: String?
        get() = prefs.getString(KEY_LAST_SELECTED_PLAYER, null)

    fun setLastSelectedPlayerPackage(packageName: String) {
        require(packageName.isNotBlank())
        prefs.edit().putString(KEY_LAST_SELECTED_PLAYER, packageName).apply()
    }


    val wheelSensitivity: Int
        get() =
            prefs.getInt(
                KEY_WHEEL_SENSITIVITY,
                DEFAULT_WHEEL_SENSITIVITY,
            ).coerceIn(1, 10)


    fun setWheelSensitivity(
        value: Int,
    ) {
        prefs.edit()
            .putInt(
                KEY_WHEEL_SENSITIVITY,
                value.coerceIn(1, 10),
            )
            .apply()
    }

    val seekStepSeconds: Int
        get() = prefs.getInt(KEY_SEEK_STEP_SECONDS, DEFAULT_SEEK_STEP_SECONDS)
            .coerceIn(MIN_SEEK_STEP_SECONDS, MAX_SEEK_STEP_SECONDS)

    fun setSeekStepSeconds(value: Int) {
        prefs.edit().putInt(KEY_SEEK_STEP_SECONDS,
            value.coerceIn(MIN_SEEK_STEP_SECONDS, MAX_SEEK_STEP_SECONDS)).apply()
    }


    val wheelTickHapticsEnabled: Boolean
        get() =
            prefs.getBoolean(
                KEY_WHEEL_TICK_HAPTICS,
                true,
            )


    fun setWheelTickHapticsEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_WHEEL_TICK_HAPTICS,
                enabled,
            )
            .apply()
    }


    val buttonHapticsEnabled: Boolean
        get() =
            prefs.getBoolean(
                KEY_BUTTON_HAPTICS,
                true,
            )


    fun setButtonHapticsEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_BUTTON_HAPTICS,
                enabled,
            )
            .apply()
    }


    val idleModeEnabled: Boolean
        get() =
            prefs.getBoolean(
                KEY_IDLE_MODE_ENABLED,
                true,
            )


    fun setIdleModeEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_IDLE_MODE_ENABLED,
                enabled,
            )
            .apply()
    }


    val idleDelaySeconds: Int
        get() =
            prefs.getInt(
                KEY_IDLE_DELAY_SECONDS,
                30,
            ).let { saved ->
                if (saved in IDLE_DELAY_OPTIONS) saved else 30
            }


    fun cycleIdleDelaySeconds(): Int {
        val current = idleDelaySeconds
        val index = IDLE_DELAY_OPTIONS.indexOf(current)
        val next = IDLE_DELAY_OPTIONS[(index + 1) % IDLE_DELAY_OPTIONS.size]

        prefs.edit()
            .putInt(
                KEY_IDLE_DELAY_SECONDS,
                next,
            )
            .apply()

        return next
    }


    val pixelShiftEnabled: Boolean
        get() =
            prefs.getBoolean(
                KEY_PIXEL_SHIFT_ENABLED,
                true,
            )


    fun setPixelShiftEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_PIXEL_SHIFT_ENABLED,
                enabled,
            )
            .apply()
    }


    companion object {

        const val DEFAULT_WHEEL_SENSITIVITY = 6
        const val DEFAULT_SEEK_STEP_SECONDS = 5
        const val MIN_SEEK_STEP_SECONDS = 1
        const val MAX_SEEK_STEP_SECONDS = 60

        val IDLE_DELAY_OPTIONS =
            listOf(
                15,
                30,
                60,
            )


        /*
         * Slider 1 -> 약 22° / step
         * Slider 6 -> 약 13° / step
         * Slider 10 -> 약 6° / step
         *
         * 값이 커질수록 더 민감하다.
         */
        fun wheelStepDegrees(
            sensitivity: Int,
        ): Float {
            val value =
                sensitivity
                    .coerceIn(1, 10)

            val fraction =
                (value - 1) / 9f

            return (
                22f -
                    16f * fraction
                )
                .coerceIn(6f, 22f)
        }


        fun wheelSensitivityLabel(
            sensitivity: Int,
        ): String {
            val value =
                sensitivity
                    .coerceIn(1, 10)

            return when {
                value <= 3 -> "Low · $value/10"
                value <= 7 -> "Normal · $value/10"
                else -> "High · $value/10"
            }
        }


        private const val KEY_WHEEL_SENSITIVITY =
            "wheel_sensitivity_v2"

        private const val KEY_SEEK_STEP_SECONDS = "seek_step_seconds"

        private const val KEY_LAST_SELECTED_PLAYER = "last_selected_player_package"

        private const val KEY_WHEEL_TICK_HAPTICS =
            "wheel_tick_haptics"

        private const val KEY_BUTTON_HAPTICS =
            "button_haptics"

        private const val KEY_IDLE_MODE_ENABLED =
            "idle_mode_enabled"

        private const val KEY_IDLE_DELAY_SECONDS =
            "idle_delay_seconds"

        private const val KEY_PIXEL_SHIFT_ENABLED =
            "pixel_shift_enabled"
    }
}
