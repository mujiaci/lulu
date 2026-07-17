package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.service.AmapService
import me.rerere.rikkahub.data.service.LocationService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.context.GlobalContext

fun createWeatherTool(context: Context, settings: Settings): Tool = Tool(
    name = "get_weather",
    description = "Get current weather and temperature for the user's current location. Use this when the user asks about weather, temperature, rain, heat, cold, or whether they should bring an umbrella.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("force_refresh_location") {
                    put("type", "boolean")
                    put("description", "Request a fresh Android location before querying weather")
                }
            }
        )
    },
    execute = { args ->
        if (!SystemTools.hasLocationPermission(context)) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", "Location permission not granted")
                    }.toString()
                )
            )
        }
        try {
            val forceRefresh = args.jsonObject["force_refresh_location"]?.jsonPrimitive?.booleanOrNull ?: false
            val apiKey = settings.systemToolsSetting.amapApiKey
            val locationService = LocationService(context, AmapService(apiKey))
            val locationResult = if (apiKey.isNotBlank()) {
                locationService.getCurrentLocation(apiKey, forceRefresh = forceRefresh)
            } else {
                locationService.getCoordinatesOnly(forceRefresh = forceRefresh)
            }
            val location = locationResult.getOrNull()
                ?: return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", false)
                            put("error", locationResult.exceptionOrNull()?.message ?: "Unable to get location")
                        }.toString()
                    )
                )

            val client = GlobalContext.get().get<OkHttpClient>()
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${location.latitude}" +
                "&longitude=${location.longitude}" +
                "&current=temperature_2m,apparent_temperature,precipitation,rain,weather_code,wind_speed_10m" +
                "&timezone=auto"
            val body = client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Weather request failed: HTTP ${response.code}")
                }
                response.body.string()
            }
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("provider", "Open-Meteo")
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("address", location.address.ifBlank { "${location.latitude}, ${location.longitude}" })
                        put("raw", body)
                    }.toString()
                )
            )
        } catch (e: Exception) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", e.message ?: "Unknown weather error")
                    }.toString()
                )
            )
        }
    }
)
