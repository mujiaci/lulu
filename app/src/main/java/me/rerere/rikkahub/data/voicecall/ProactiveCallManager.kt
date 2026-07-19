package me.rerere.rikkahub.data.voicecall

import android.Manifest
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import me.rerere.rikkahub.INCOMING_VOICE_CALL_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.ProactiveCallSetting
import me.rerere.rikkahub.data.datastore.shouldUseProactiveCallChannel
import java.time.LocalDateTime

object ProactiveCallManager {
    const val ACTION_INCOMING_CALL = "me.rerere.rikkahub.action.PROACTIVE_INCOMING_CALL"
    const val ACTION_DECLINE_CALL = "me.rerere.rikkahub.action.PROACTIVE_DECLINE_CALL"
    const val EXTRA_ASSISTANT_ID = "proactive_call_assistant_id"
    const val EXTRA_ASSISTANT_NAME = "proactive_call_assistant_name"
    const val EXTRA_CONVERSATION_ID = "proactive_call_conversation_id"
    const val EXTRA_REASON = "proactive_call_reason"
    const val EXTRA_AUTO_START = "proactive_call_auto_start"

    private const val PREFS_NAME = "proactive_call_state"
    private const val KEY_LAST_CALL_PREFIX = "last_call:"
    private const val KEY_PENDING_ASSISTANT = "pending_assistant"
    private const val KEY_PENDING_AT = "pending_at"
    private const val KEY_OUTCOME_ASSISTANT = "outcome_assistant"
    private const val KEY_OUTCOME = "outcome"
    private const val KEY_OUTCOME_AT = "outcome_at"
    private const val NOTIFICATION_ID = 2411
    private const val RING_TIMEOUT_MILLIS = 60_000L

    fun shouldOffer(
        context: Context,
        assistantId: String,
        setting: ProactiveCallSetting,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!setting.allowMobileData && !isOnWifi(context)) return false
        val lastCall = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CALL_PREFIX + assistantId, 0L)
        val selector = (((nowMillis / 60_000L).hashCode() xor assistantId.hashCode()) and Int.MAX_VALUE) % 100
        return shouldUseProactiveCallChannel(
            setting = setting,
            localHour = LocalDateTime.now().hour,
            millisSinceLastCall = nowMillis - lastCall,
            selector = selector,
        )
    }

    fun offerIncomingCall(
        context: Context,
        assistantId: String,
        assistantName: String,
        conversationId: String,
        reason: String,
        setting: ProactiveCallSetting,
        force: Boolean = false,
    ): Boolean {
        if (!force && !shouldOffer(context, assistantId, setting)) return false
        val appContext = context.applicationContext
        val ringingIntent = routeIntent(
            context = appContext,
            assistantId = assistantId,
            assistantName = assistantName,
            conversationId = conversationId,
            reason = reason,
            autoStart = false,
        )
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            markCallOffered(appContext, assistantId, countForCooldown = !force)
            appContext.startActivity(ringingIntent)
            return true
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val person = Person.Builder()
            .setName(assistantName)
            .setImportant(true)
            .build()
        val declineIntent = Intent(appContext, ProactiveCallActionReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra(EXTRA_ASSISTANT_ID, assistantId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            appContext,
            2412,
            declineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val answerPendingIntent = PendingIntent.getActivity(
            appContext,
            2413,
            routeIntent(
                context = appContext,
                assistantId = assistantId,
                assistantName = assistantName,
                conversationId = conversationId,
                reason = reason,
                autoStart = true,
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val ringingPendingIntent = PendingIntent.getActivity(
            appContext,
            2414,
            ringingIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val ringtoneUri = setting.ringtoneUri
            ?.takeIf(String::isNotBlank)
            ?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val channelId = ensureIncomingCallChannel(appContext, ringtoneUri)
        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(assistantName)
            .setContentText("语音来电")
            .setSound(ringtoneUri)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(RING_TIMEOUT_MILLIS)
            .setContentIntent(ringingPendingIntent)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declinePendingIntent, answerPendingIntent))
            .addPerson(person)
        if (setting.fullScreenWhenAllowed) {
            builder.setFullScreenIntent(ringingPendingIntent, true)
        }
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build())
        markCallOffered(appContext, assistantId, countForCooldown = !force)
        return true
    }

    fun dismissIncomingCall(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    fun markAnswered(context: Context, assistantId: String) {
        dismissIncomingCall(context)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_ASSISTANT)
            .remove(KEY_PENDING_AT)
            .remove(KEY_OUTCOME_ASSISTANT)
            .remove(KEY_OUTCOME)
            .remove(KEY_OUTCOME_AT)
            .apply()
    }

    fun markDeclined(context: Context, assistantId: String) {
        dismissIncomingCall(context)
        val now = System.currentTimeMillis()
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_ASSISTANT)
            .remove(KEY_PENDING_AT)
            .putString(KEY_OUTCOME_ASSISTANT, assistantId)
            .putString(KEY_OUTCOME, "用户拒绝了上一次主动来电")
            .putLong(KEY_OUTCOME_AT, now)
            .apply()
    }

    fun recentOutcomeContext(
        context: Context,
        assistantId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pendingAssistant = prefs.getString(KEY_PENDING_ASSISTANT, null)
        val pendingAt = prefs.getLong(KEY_PENDING_AT, 0L)
        if (
            pendingAssistant == assistantId &&
            pendingAt > 0L &&
            nowMillis - pendingAt > RING_TIMEOUT_MILLIS
        ) {
            prefs.edit()
                .remove(KEY_PENDING_ASSISTANT)
                .remove(KEY_PENDING_AT)
                .putString(KEY_OUTCOME_ASSISTANT, assistantId)
                .putString(KEY_OUTCOME, "用户未接上一次主动来电")
                .putLong(KEY_OUTCOME_AT, pendingAt + RING_TIMEOUT_MILLIS)
                .apply()
        }
        val outcomeAssistant = prefs.getString(KEY_OUTCOME_ASSISTANT, null)
        val outcomeAt = prefs.getLong(KEY_OUTCOME_AT, 0L)
        return prefs.getString(KEY_OUTCOME, null)
            ?.takeIf {
                outcomeAssistant == assistantId &&
                    outcomeAt > 0L &&
                    nowMillis - outcomeAt <= 7L * 24L * 60L * 60L * 1_000L
            }
    }

    private fun routeIntent(
        context: Context,
        assistantId: String,
        assistantName: String,
        conversationId: String,
        reason: String,
        autoStart: Boolean,
    ) = Intent(context, RouteActivity::class.java).apply {
        action = ACTION_INCOMING_CALL
        putExtra(EXTRA_ASSISTANT_ID, assistantId)
        putExtra(EXTRA_ASSISTANT_NAME, assistantName)
        putExtra(EXTRA_CONVERSATION_ID, conversationId)
        putExtra(EXTRA_REASON, reason)
        putExtra(EXTRA_AUTO_START, autoStart)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private fun markCallOffered(
        context: Context,
        assistantId: String,
        countForCooldown: Boolean,
    ) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (countForCooldown) putLong(KEY_LAST_CALL_PREFIX + assistantId, now)
            }
            .putString(KEY_PENDING_ASSISTANT, assistantId)
            .putLong(KEY_PENDING_AT, now)
            .remove(KEY_OUTCOME_ASSISTANT)
            .remove(KEY_OUTCOME)
            .remove(KEY_OUTCOME_AT)
            .apply()
    }

    private fun ensureIncomingCallChannel(context: Context, ringtoneUri: Uri): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return INCOMING_VOICE_CALL_NOTIFICATION_CHANNEL_ID
        val channelId = "${INCOMING_VOICE_CALL_NOTIFICATION_CHANNEL_ID}_${ringtoneUri.toString().hashCode().toString().replace("-", "n")}"
        val manager = context.getSystemService(NotificationManager::class.java)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(channelId, "角色来电", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "角色主动来电通知"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                setSound(ringtoneUri, audioAttributes)
                enableVibration(true)
            },
        )
        return channelId
    }

    private fun isOnWifi(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}

class ProactiveCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ProactiveCallManager.ACTION_DECLINE_CALL) {
            intent.getStringExtra(ProactiveCallManager.EXTRA_ASSISTANT_ID)
                ?.takeIf(String::isNotBlank)
                ?.let { assistantId -> ProactiveCallManager.markDeclined(context, assistantId) }
                ?: ProactiveCallManager.dismissIncomingCall(context)
        }
    }
}
