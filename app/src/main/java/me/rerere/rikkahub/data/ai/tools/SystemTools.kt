package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.service.AmapService
import me.rerere.rikkahub.data.service.LocationService
import me.rerere.rikkahub.data.service.RikkaNotificationListenerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
sealed class SystemToolOption {
    @Serializable @SerialName("location") data object Location : SystemToolOption()
    @Serializable @SerialName("notifications") data object Notifications : SystemToolOption()
    @Serializable @SerialName("app_usage") data object AppUsage : SystemToolOption()
    @Serializable @SerialName("camera") data object Camera : SystemToolOption()
    @Serializable @SerialName("explore_nearby") data object ExploreNearby : SystemToolOption()
    @Serializable @SerialName("gadgetbridge") data object Gadgetbridge : SystemToolOption()
    @Serializable @SerialName("alarm") data object Alarm : SystemToolOption()
    @Serializable @SerialName("battery") data object Battery : SystemToolOption()
    @Serializable @SerialName("music") data object Music : SystemToolOption()
    @Serializable @SerialName("sms") data object Sms : SystemToolOption()
}

class SystemTools(private val context: Context, private val settings: Settings) {

    companion object {
        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun hasNotificationPermission(context: Context): Boolean =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true

        fun hasAppUsagePermission(context: Context): Boolean =
            (context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager)
                .checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    // ==================== 位置工具 ====================

    val locationTool: Tool by lazy {
        Tool(
            name = "get_location",
            description = "Get the current device location with coordinates and address. Uses Amap API for reverse geocoding if API key is configured.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        putJsonObject("include_address") {
                            put("type", "boolean")
                            put("description", "Whether to include address info (reverse geocoding)")
                        }
                    }
                )
            },
            execute = { _ ->
                if (!hasLocationPermission(context)) {
                    return@Tool listOf(UIMessagePart.Text(
                        buildJsonObject { put("success", false); put("error", "Location permission not granted") }.toString()
                    ))
                }
                try {
                    val apiKey = settings.systemToolsSetting.amapApiKey
                    val locationService = LocationService(context, AmapService(apiKey))
                    val locationResult = if (apiKey.isNotBlank()) {
                        runBlocking { locationService.getCurrentLocation(apiKey) }
                    } else {
                        runBlocking { locationService.getCoordinatesOnly() }
                    }
                    val loc = locationResult.getOrNull()
                    if (loc == null) {
                        return@Tool listOf(UIMessagePart.Text(
                            buildJsonObject {
                                put("success", false)
                                put("error", locationResult.exceptionOrNull()?.message ?: "Unable to get fresh location")
                            }.toString()
                        ))
                    }

                    val result = buildJsonObject {
                        put("success", true)
                        put("latitude", loc.latitude)
                        put("longitude", loc.longitude)
                        put("altitude", loc.altitude)
                        put("accuracy", loc.accuracy.toDouble())
                        put("timestamp", loc.timestamp)
                        put("time", dateFormat.format(Date(loc.timestamp)))
                        put("fresh_within_minutes", 10)
                        put("address", loc.address.ifBlank { "Unknown address" })
                        put("city", loc.city)
                        put("district", loc.district)
                        put("street", loc.street)
                    }
                    listOf(UIMessagePart.Text(result.toString()))
                } catch (e: Exception) {
                    listOf(UIMessagePart.Text(buildJsonObject { put("success", false); put("error", e.message ?: "Unknown error") }.toString()))
                }
            }
        )
    }

    // ==================== 通知工具 ====================

    val notificationsTool: Tool by lazy {
        Tool(
            name = "get_notifications",
            description = "Get today's notifications from the device. Returns notification titles, content, app names, and timestamps.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        putJsonObject("limit") {
                            put("type", "integer")
                            put("description", "Maximum number of notifications to return (default 20)")
                        }
                    }
                )
            },
            execute = { args ->
                val params = args.jsonObject
                try {
                    val limit = params["limit"]?.jsonPrimitive?.intOrNull ?: 20
                    val notifications = RikkaNotificationListenerService.getTodayNotifications().take(limit)

                    if (notifications.isEmpty()) {
                        return@Tool listOf(UIMessagePart.Text(
                            buildJsonObject { put("success", true); put("count", 0); put("message", "No notifications found for today") }.toString()
                        ))
                    }

                    val arr = kotlinx.serialization.json.buildJsonArray {
                        notifications.forEach { notif ->
                            add(buildJsonObject {
                                put("app_name", notif.appName)
                                put("package_name", notif.packageName)
                                put("title", notif.title)
                                put("content", notif.content)
                                put("time", dateFormat.format(Date(notif.timestamp)))
                                put("category", notif.category ?: "")
                            })
                        }
                    }

                    listOf(UIMessagePart.Text(buildJsonObject { put("success", true); put("count", notifications.size); put("notifications", arr) }.toString()))
                } catch (e: Exception) {
                    listOf(UIMessagePart.Text(buildJsonObject { put("success", false); put("error", e.message ?: "Unknown error") }.toString()))
                }
            }
        )
    }

    // ==================== 外部工具实例 ====================

    private val appUsageTool by lazy { createAppUsageTool(context) }
    private val exploreNearbyTool by lazy { createExploreNearbyTool(context, settings) }
    private val cameraTool by lazy { createCameraTool(context) }
    private val gadgetbridgeTool by lazy { createGadgetbridgeTool(settings.systemToolsSetting.gadgetbridgeDbPath) }
    private val alarmTool by lazy { createAlarmTool(context) }
    private val batteryTool by lazy { createBatteryTool(context) }
    private val musicTool by lazy { createMusicTool(context) }
    private val smsTool by lazy { createSmsTool(context) }

    // ==================== 获取工具列表 ====================

    fun getTools(enabledTools: Set<SystemToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (SystemToolOption.Location in enabledTools) tools.add(locationTool)
        if (SystemToolOption.Notifications in enabledTools) tools.add(notificationsTool)
        if (SystemToolOption.AppUsage in enabledTools) tools.add(appUsageTool)
        if (SystemToolOption.ExploreNearby in enabledTools) tools.add(exploreNearbyTool)
        if (SystemToolOption.Camera in enabledTools) tools.add(cameraTool)
        if (SystemToolOption.Gadgetbridge in enabledTools) tools.add(gadgetbridgeTool)
        if (SystemToolOption.Alarm in enabledTools) tools.add(alarmTool)
        if (SystemToolOption.Battery in enabledTools) tools.add(batteryTool)
        if (SystemToolOption.Music in enabledTools) tools.add(musicTool)
        if (SystemToolOption.Sms in enabledTools) tools.add(smsTool)
        return tools
    }
}
