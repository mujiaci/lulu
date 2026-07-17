package me.rerere.rikkahub.plugin.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.plugin.loader.LoadedPlugin
import me.rerere.rikkahub.plugin.loader.PluginLoader
import me.rerere.rikkahub.plugin.model.PluginToolDefinition

/**
 * 插件工具提供者
 * 将插件工具转换为AI可用的Tool对象
 */
class PluginToolProvider(
    private val pluginLoader: PluginLoader
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 获取所有插件提供的工具
     */
    fun getTools(): List<Tool> {
        return pluginLoader.getAllLoadedPlugins().flatMap { plugin ->
            plugin.info.manifest.tools.map { toolDef ->
                createTool(plugin, toolDef)
            }
        }
    }

    /**
     * 获取指定插件的工具
     */
    fun getPluginTools(pluginId: String): List<Tool> {
        val plugin = pluginLoader.getLoadedPlugin(pluginId) ?: return emptyList()
        return plugin.info.manifest.tools.map { toolDef ->
            createTool(plugin, toolDef)
        }
    }

    /**
     * 创建Tool对象
     */
    private fun createTool(plugin: LoadedPlugin, toolDef: PluginToolDefinition): Tool {
        return Tool(
            name = toolDef.name,
            description = buildDescription(plugin, toolDef),
            parameters = {
                InputSchema.Obj(
                    properties = buildParameters(toolDef),
                    required = toolDef.parameters.filter { it.required }.map { it.name }
                )
            },
            execute = { params ->
                executeTool(plugin, toolDef, params)
            }
        )
    }

    /**
     * 构建工具描述
     */
    private fun buildDescription(plugin: LoadedPlugin, toolDef: PluginToolDefinition): String {
        val sb = StringBuilder()
        sb.appendLine(toolDef.description)
        sb.appendLine()
        sb.appendLine("Provided by plugin: ${plugin.info.manifest.name} (${plugin.info.manifest.id})")
        return sb.toString().trim()
    }

    /**
     * 构建参数定义
     */
    private fun buildParameters(toolDef: PluginToolDefinition): JsonObject {
        return buildJsonObject {
            toolDef.parameters.forEach { param ->
                put(param.name, buildJsonObject {
                    put("type", param.type)
                    if (param.description != null) {
                        put("description", param.description)
                    }
                    // 根据类型添加额外信息
                    when (param.type) {
                        "array" -> {
                            put("items", buildJsonObject {
                                put("type", "string")
                            })
                        }
                        "object" -> {
                            // 可以在这里添加properties定义
                        }
                    }
                })
            }
        }
    }

    /**
     * 执行工具
     */
    private suspend fun executeTool(
        plugin: LoadedPlugin,
        toolDef: PluginToolDefinition,
        params: JsonElement
    ): List<UIMessagePart> {
        val result = pluginLoader.callTool(
            pluginId = plugin.id,
            toolName = toolDef.name,
            params = params
        )

        return result.fold(
            onSuccess = { jsonElement ->
                val resultStr = json.encodeToString(JsonElement.serializer(), jsonElement)
                listOf(UIMessagePart.Text(resultStr))
            },
            onFailure = { error ->
                val errorObj = buildJsonObject {
                    put("success", false)
                    put("error", error.message ?: "Unknown error")
                }
                listOf(UIMessagePart.Text(errorObj.toString()))
            }
        )
    }

    /**
     * 获取所有开启了 inject_as_prompt 的插件的提示词模板
     * 用于注入到系统提示词中，让AI主动调用插件工具
     */
    fun getPluginPromptInjections(): List<String> {
        return pluginLoader.getAllLoadedPlugins().mapNotNull { plugin ->
            val manifest = plugin.info.manifest
            val promptTemplate = manifest.promptTemplate ?: return@mapNotNull null
            // 检查 inject_as_prompt 配置是否开启
            val injectConfig = plugin.info.config["inject_as_prompt"]
            val shouldInject = when (injectConfig) {
                is kotlinx.serialization.json.JsonPrimitive -> {
                    injectConfig.content == "true"
                }
                else -> false
            }
            if (shouldInject) promptTemplate else null
        }
    }

    /**
     * 获取工具统计信息
     */
    fun getToolStats(): ToolStats {
        val plugins = pluginLoader.getAllLoadedPlugins()
        val totalTools = plugins.sumOf { it.info.manifest.tools.size }
        
        return ToolStats(
            totalPlugins = plugins.size,
            totalTools = totalTools,
            pluginDetails = plugins.map { plugin ->
                PluginToolDetail(
                    pluginId = plugin.id,
                    pluginName = plugin.info.manifest.name,
                    toolCount = plugin.info.manifest.tools.size,
                    toolNames = plugin.info.manifest.tools.map { it.name }
                )
            }
        )
    }

    /**
     * 工具统计信息
     */
    data class ToolStats(
        val totalPlugins: Int,
        val totalTools: Int,
        val pluginDetails: List<PluginToolDetail>
    )

    /**
     * 插件工具详情
     */
    data class PluginToolDetail(
        val pluginId: String,
        val pluginName: String,
        val toolCount: Int,
        val toolNames: List<String>
    )
}
