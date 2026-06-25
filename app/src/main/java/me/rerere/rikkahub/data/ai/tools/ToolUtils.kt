package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.Tool

fun List<Tool>.deduplicateByToolName(): List<Tool> = distinctBy { it.name }
