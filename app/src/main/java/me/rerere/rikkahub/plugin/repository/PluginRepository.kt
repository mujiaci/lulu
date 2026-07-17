package me.rerere.rikkahub.plugin.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * 插件数据仓库
 * 使用DataStore存储插件配置和启用状态
 */
class PluginRepository(
    private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "plugin_settings")

    /**
     * 获取插件配置
     */
    suspend fun getPluginConfig(pluginId: String): Map<String, JsonElement> {
        return context.dataStore.data.map { prefs ->
            val configKey = stringPreferencesKey("plugin_config_$pluginId")
            val configJson = prefs[configKey] ?: "{}"
            try {
                json.decodeFromString<Map<String, JsonElement>>(configJson)
            } catch (e: Exception) {
                emptyMap()
            }
        }.first()
    }

    /**
     * 保存插件配置
     */
    suspend fun savePluginConfig(pluginId: String, config: Map<String, JsonElement>) {
        context.dataStore.edit { prefs ->
            val configKey = stringPreferencesKey("plugin_config_$pluginId")
            prefs[configKey] = json.encodeToString(config)
        }
    }

    /**
     * 检查插件是否启用
     */
    suspend fun isPluginEnabled(pluginId: String): Boolean {
        return context.dataStore.data.map { prefs ->
            val enabledKey = booleanPreferencesKey("plugin_enabled_$pluginId")
            prefs[enabledKey] ?: true // 默认启用
        }.first()
    }

    /**
     * 设置插件启用状态
     */
    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val enabledKey = booleanPreferencesKey("plugin_enabled_$pluginId")
            prefs[enabledKey] = enabled
        }
    }

    /**
     * 保存插件信息（初始化时调用）
     */
    suspend fun savePlugin(pluginId: String) {
        // 确保插件有启用状态记录
        context.dataStore.edit { prefs ->
            val enabledKey = booleanPreferencesKey("plugin_enabled_$pluginId")
            if (prefs[enabledKey] == null) {
                prefs[enabledKey] = true
            }
        }
    }

    /**
     * 保存插件信息
     */
    suspend fun savePlugin(pluginInfo: me.rerere.rikkahub.plugin.model.PluginInfo) {
        context.dataStore.edit { prefs ->
            val enabledKey = booleanPreferencesKey("plugin_enabled_${pluginInfo.manifest.id}")
            if (prefs[enabledKey] == null) {
                prefs[enabledKey] = pluginInfo.isEnabled
            }
            
            // 保存配置
            if (pluginInfo.config.isNotEmpty()) {
                val configKey = stringPreferencesKey("plugin_config_${pluginInfo.manifest.id}")
                prefs[configKey] = json.encodeToString(pluginInfo.config)
            }
        }
    }

    /**
     * 移除插件
     */
    suspend fun removePlugin(pluginId: String) {
        context.dataStore.edit { prefs ->
            prefs.remove(booleanPreferencesKey("plugin_enabled_$pluginId"))
            prefs.remove(stringPreferencesKey("plugin_config_$pluginId"))
        }
    }

    /**
     * 清除所有插件数据
     */
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}