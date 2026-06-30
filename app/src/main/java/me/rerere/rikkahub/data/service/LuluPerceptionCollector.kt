package me.rerere.rikkahub.data.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.ai.tools.SystemTools
import me.rerere.rikkahub.data.ai.tools.createMusicTool
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.gadgetbridge.GadgetbridgeReader
import me.rerere.rikkahub.data.model.LuluActionCue
import me.rerere.rikkahub.data.model.LuluAppUsageState
import me.rerere.rikkahub.data.model.LuluDeviceState
import me.rerere.rikkahub.data.model.LuluHealthState
import me.rerere.rikkahub.data.model.LuluLocationState
import me.rerere.rikkahub.data.model.LuluMusicState
import me.rerere.rikkahub.data.model.LuluPerceptionInput
import me.rerere.ai.ui.UIMessagePart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class LuluPerceptionCollector(
    private val context: Context,
) {
    suspend fun collect(
        userText: String,
        settings: Settings,
        hourOfDay: Int = java.time.LocalDateTime.now().hour,
    ): LuluPerceptionInput = withContext(Dispatchers.IO) {
        val systemTools = settings.systemToolsSetting
        LuluPerceptionInput(
            userText = userText,
            hourOfDay = hourOfDay,
            deviceState = if (systemTools.batteryEnabled) collectDeviceState() else null,
            healthState = if (systemTools.gadgetbridgeEnabled) collectHealthState(settings) else null,
            appUsageState = if (
                systemTools.appUsageAccess || systemTools.appUsageEnabled
            ) {
                collectAppUsageState()
            } else {
                null
            },
            locationState = if (
                (systemTools.locationAccess || systemTools.locationExploreEnabled) &&
                SystemTools.hasLocationPermission(context)
            ) {
                collectLocationState(settings, userText)
            } else {
                null
            },
            musicState = if (systemTools.musicEnabled) collectMusicState() else null,
            actionCues = inferActionCues(userText),
        )
    }

    private fun collectDeviceState(): LuluDeviceState? = runCatching {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        LuluDeviceState(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            networkType = currentNetworkType(),
        )
    }.getOrNull()

    private fun currentNetworkType(): String? = runCatching {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return@runCatching null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@runCatching null
        when {
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }.getOrNull()

    private fun collectHealthState(settings: Settings): LuluHealthState? = runCatching {
        val path = settings.systemToolsSetting.gadgetbridgeDbPath
        if (!GadgetbridgeReader.dbFileExists(path)) return@runCatching null
        val latestActivity = GadgetbridgeReader.readLatestActivitySample(path)
        val latestDailySummary = GadgetbridgeReader.readDailySummaries(1, path).lastOrNull()
        val latestSleep = GadgetbridgeReader.readSleepSummaries(1, path).lastOrNull()

        LuluHealthState(
            sleepMinutes = latestSleep?.totalDuration,
            heartRate = latestActivity?.heartRate ?: latestDailySummary?.hrAvg,
            stepCount = latestDailySummary?.steps ?: latestActivity?.steps,
        )
    }.getOrNull()

    private suspend fun collectAppUsageState(): LuluAppUsageState? {
        if (!SystemTools.hasAppUsagePermission(context)) return null
        return runCatching {
            val usage = AppUsageService(context).getTodayUsageStats().getOrNull().orEmpty()
            LuluAppUsageState(
                topApps = usage.take(3).map { it.appName },
                screenMinutesToday = (usage.sumOf { it.totalTimeInForeground } / 60_000L).toInt(),
            )
        }.getOrNull()
    }

    private suspend fun collectLocationState(settings: Settings, userText: String): LuluLocationState? = runCatching {
        val apiKey = settings.systemToolsSetting.amapApiKey
        val locationService = LocationService(context, AmapService(apiKey))
        val forceRefresh = userText.hasLocationIntent()
        val location = if (apiKey.isNotBlank()) {
            locationService.getCurrentLocation(apiKey, forceRefresh = forceRefresh).getOrNull()
        } else {
            locationService.getCoordinatesOnly(forceRefresh = forceRefresh).getOrNull()
        } ?: return@runCatching null

        LuluLocationState(
            address = location.address.takeIf { it.isNotBlank() },
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }.getOrNull()

    private fun String.hasLocationIntent(): Boolean =
        lowercase().containsAny("在哪", "位置", "附近", "周边", "出门", "出去", "外面", "到没到")

    private suspend fun collectMusicState(): LuluMusicState? = runCatching {
        val output = createMusicTool(context).execute(
            buildJsonObject {
                put("action", "get_now_playing")
            }
        )
            .filterIsInstance<UIMessagePart.Text>()
            .firstOrNull()
            ?.text
            ?: return@runCatching null
        val json = Json.parseToJsonElement(output).jsonObject
        if (json["success"]?.jsonPrimitive?.contentOrNull != "true") return@runCatching null
        val title = json["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "Unknown" }
        val artist = json["artist"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "Unknown" }
        if (title.isNullOrBlank() && artist.isNullOrBlank()) return@runCatching null
        LuluMusicState(
            title = title,
            artist = artist,
            isPlaying = json["is_playing"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull(),
            appName = json["app_name"]?.jsonPrimitive?.contentOrNull,
        )
    }.getOrNull()

    private fun inferActionCues(userText: String): Set<LuluActionCue> {
        val text = userText.lowercase()
        val cues = mutableSetOf<LuluActionCue>()
        if (text.containsAny("在哪", "位置", "附近", "周边", "出门", "出去")) cues += LuluActionCue.LOCATION_CONTEXT
        if (text.containsAny("附近", "周边", "推荐", "吃什么", "去哪")) cues += LuluActionCue.NEARBY_CONTEXT
        if (text.containsAny("消息", "通知", "短信", "未读", "微信", "qq")) cues += LuluActionCue.MESSAGE_CONTEXT
        if (text.containsAny("闹钟", "叫我", "提醒我", "记得叫", "起床")) cues += LuluActionCue.ALARM_CANDIDATE
        if (text.containsAny("日程", "日历", "上课", "有课", "会议", "开会")) cues += LuluActionCue.CALENDAR_CANDIDATE
        if (text.containsAny("音乐", "听歌", "播放", "暂停", "切歌")) cues += LuluActionCue.MUSIC_CONTEXT
        if (text.containsAny("拍照", "摄像头", "看一下", "看看桌面", "看看周围")) cues += LuluActionCue.CAMERA_CANDIDATE
        if (text.containsAny("日志", "日记", "记录下来", "记下来", "写下来")) cues += LuluActionCue.JOURNAL_CANDIDATE
        return cues
    }

    private fun String.containsAny(vararg words: String): Boolean =
        words.any { it in this }
}
