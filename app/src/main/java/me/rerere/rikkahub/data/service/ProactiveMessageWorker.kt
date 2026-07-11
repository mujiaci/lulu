package me.rerere.rikkahub.data.service

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.getProactiveMessageSetting
import me.rerere.rikkahub.data.datastore.SettingsStore
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.uuid.Uuid

internal data class TargetedProactiveWorkSpec(
    val uniqueWorkName: String,
    val delayMillis: Long,
    val assistantId: String,
    val commitmentId: String,
) {
    val isTargeted: Boolean
        get() = assistantId.isNotBlank() && commitmentId.isNotBlank()
}

internal fun buildTargetedProactiveWorkSpec(
    triggerAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    assistantId: String,
    commitmentId: String,
): TargetedProactiveWorkSpec = TargetedProactiveWorkSpec(
    uniqueWorkName = "targeted_proactive_message_work",
    delayMillis = (triggerAtMillis - nowMillis).coerceAtLeast(0L),
    assistantId = assistantId.trim(),
    commitmentId = commitmentId.trim(),
)

/**
 * WorkManager-based fallback for proactive message scheduling.
 * More reliable than AlarmManager on devices with aggressive battery optimization.
 */
class ProactiveMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ProactiveMessageWorker"
        private const val UNIQUE_WORK_NAME = "proactive_message_work"
        private const val TARGETED_UNIQUE_WORK_NAME = "targeted_proactive_message_work"

        fun scheduleNext(context: Context, setting: me.rerere.rikkahub.data.datastore.ProactiveMessageSetting) {
            if (!setting.enabled) {
                cancel(context)
                return
            }

            val minMinutes = setting.minIntervalMinutes.coerceAtLeast(1)
            val maxMinutes = setting.maxIntervalMinutes.coerceAtLeast(minMinutes)
            val delayMinutes = Random.nextInt(minMinutes, maxMinutes + 1)
            scheduleNext(context, setting, delayMinutes)
        }

        fun scheduleNext(
            context: Context,
            setting: me.rerere.rikkahub.data.datastore.ProactiveMessageSetting,
            delayMinutes: Int,
        ) {
            if (!setting.enabled) {
                cancel(context)
                return
            }
            val safeDelayMinutes = delayMinutes.coerceAtLeast(1)

            val workRequest = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
                .setInitialDelay(safeDelayMinutes.toLong(), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            // Also save trigger time to SharedPreferences for UI display
            val triggerTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(safeDelayMinutes.toLong())
            context.getSharedPreferences("proactive_message_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("next_trigger_time", triggerTime)
                .putString(ProactiveMessageService.EXTRA_ASSISTANT_ID, setting.assistantId)
                .apply()

            Log.d(TAG, "Scheduled WorkManager proactive message in $safeDelayMinutes minutes")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.d(TAG, "Cancelled WorkManager proactive message")
        }

        fun scheduleTargeted(
            context: Context,
            triggerAtMillis: Long,
            assistantId: String,
            commitmentId: String,
        ) {
            val spec = buildTargetedProactiveWorkSpec(
                triggerAtMillis = triggerAtMillis,
                assistantId = assistantId,
                commitmentId = commitmentId,
            )
            if (!spec.isTargeted) return

            val inputData = Data.Builder()
                .putString(ProactiveMessageService.EXTRA_ASSISTANT_ID, spec.assistantId)
                .putString(ProactiveMessageService.EXTRA_COMMITMENT_ID, spec.commitmentId)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
                .setInputData(inputData)
                .setInitialDelay(spec.delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                TARGETED_UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
            Log.d(TAG, "Scheduled targeted WorkManager fallback commitment=${spec.commitmentId}")
        }

        fun cancelTargeted(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TARGETED_UNIQUE_WORK_NAME)
            Log.d(TAG, "Cancelled targeted WorkManager fallback")
        }

        /**
         * Check if exact alarm permission is granted (Android 12+)
         */
        fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return true
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }

        /**
         * Check if app is ignoring battery optimizations
         */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "ProactiveMessageWorker triggered")

        val settingsStore = org.koin.core.context.GlobalContext.get().get<SettingsStore>()
        val settings = settingsStore.settingsFlow.first()
        val targetedAssistantId = inputData
            .getString(ProactiveMessageService.EXTRA_ASSISTANT_ID)
            ?.takeIf(String::isNotBlank)
        val targetedCommitmentId = inputData
            .getString(ProactiveMessageService.EXTRA_COMMITMENT_ID)
            ?.takeIf(String::isNotBlank)
        val isTargeted = targetedAssistantId != null && targetedCommitmentId != null
        val targetedAssistantUuid = if (isTargeted) {
            runCatching { Uuid.parse(targetedAssistantId.orEmpty()) }.getOrNull() ?: return Result.failure()
        } else {
            null
        }
        val proactiveSetting = settings.getProactiveMessageSetting(targetedAssistantUuid)

        if (!proactiveSetting.enabled && !isTargeted) {
            Log.d(TAG, "Proactive message disabled, skipping")
            return Result.success()
        }

        // Acquire a wake lock for the duration of the work
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ProactiveMessage::WorkerWakeLock"
        )
        wakeLock.acquire(5 * 60 * 1000L) // 5 minutes max

        try {
            // Delegate to the existing trigger service logic
            // Start the foreground service which handles the actual AI generation
            val serviceIntent = android.content.Intent(applicationContext, ProactiveMessageTriggerService::class.java).apply {
                putExtra(ProactiveMessageService.EXTRA_ASSISTANT_ID, proactiveSetting.assistantId)
                targetedCommitmentId?.let { putExtra(ProactiveMessageService.EXTRA_COMMITMENT_ID, it) }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }

            if (!isTargeted) {
                scheduleNext(applicationContext, proactiveSetting)
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ProactiveMessageWorker failed", e)
            if (!isTargeted) {
                scheduleNext(applicationContext, proactiveSetting)
            }
            return Result.retry()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}
