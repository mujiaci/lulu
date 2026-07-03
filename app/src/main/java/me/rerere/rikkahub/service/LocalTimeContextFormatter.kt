package me.rerere.rikkahub.service

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object LocalTimeContextFormatter {
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun format(
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val period = periodLabel(now.hour)
        return "当前本地时间: ${dateTimeFormatter.format(now)}（$period ${timeFormatter.format(now)}，24小时制，时区 ${zoneId.id}）"
    }

    fun periodLabel(hour: Int): String = when (hour) {
        in 0..4 -> "凌晨"
        in 5..10 -> "早上"
        11, 12 -> "中午"
        in 13..17 -> "下午"
        in 18..22 -> "晚上"
        else -> "深夜"
    }
}
