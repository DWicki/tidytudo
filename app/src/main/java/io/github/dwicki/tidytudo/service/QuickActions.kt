package io.github.dwicki.tidytudo.service

import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.RobotSession
import io.github.dwicki.tidytudo.api.ValetudoClient
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.api.model.StatusValue
import io.github.dwicki.tidytudo.data.CleaningPreset

/**
 * Commands shared by the home screen, notification actions, the widget, the tile and launcher
 * shortcuts, so all of them behave the same.
 */
object QuickActions {

    /** Pause while busy, otherwise start (Valetudo resumes a paused job on `start`). */
    suspend fun toggle(client: ValetudoClient, state: RobotState?): ApiResult<Unit> {
        val busy = state?.status?.value?.isActive == true
        return client.basicControl(if (busy) "pause" else "start")
    }

    suspend fun dock(client: ValetudoClient): ApiResult<Unit> = client.basicControl("home")

    /** Applies the preset's fan/water/mode first, then starts rooms or a full cleanup. */
    suspend fun runPreset(session: RobotSession, preset: CleaningPreset): ApiResult<Unit> {
        val client = session.client
        val capabilities = session.live.value.capabilities.ifEmpty {
            (client.capabilities() as? ApiResult.Success)?.value?.toSet().orEmpty()
        }
        val presetSettings = listOf(
            Capabilities.OPERATION_MODE to preset.operationMode,
            Capabilities.FAN_SPEED to preset.fanSpeed,
            Capabilities.WATER_USAGE to preset.waterUsage,
        )
        for ((capability, value) in presetSettings) {
            if (value == null || capability !in capabilities) continue
            val result = client.setPreset(capability, value)
            if (result is ApiResult.Failure) return result
        }
        return if (preset.segmentIds.isEmpty()) {
            client.basicControl("start")
        } else {
            client.cleanSegments(preset.segmentIds, preset.iterations, customOrder = false)
        }
    }

    fun isCleaning(state: RobotState?): Boolean {
        val status = state?.status?.value ?: return false
        return status == StatusValue.CLEANING || status == StatusValue.RETURNING || status == StatusValue.PAUSED
    }
}
