package com.example.smartphoneagent.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import com.example.smartphoneagent.plugin.ActionExecutor
import com.example.smartphoneagent.task.AppDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LongRunningWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "LongRunningWorker"
        private const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong("task_id", -1)
        if (taskId == -1L) return Result.failure()

        val database = AppDatabase.getDatabase(applicationContext)
        val taskDao = database.taskDao()
        val task = taskDao.getTaskById(taskId) ?: return Result.failure()

        return try {
            ensureNotificationChannel()

            val actionExecutor = ActionExecutor(applicationContext)
            val paramsMap: Map<String, String> = try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                Gson().fromJson(task.actionParams, type)
            } catch (e: Exception) {
                emptyMap()
            }

            Log.d(TAG, "Executing long task: ${task.title}")
            setForeground(ForegroundInfo(
                NOTIFICATION_ID,
                NotificationCompat.Builder(applicationContext, TaskForegroundService.CHANNEL_ID)
                    .setContentTitle("AI 手机助手")
                    .setContentText(task.title)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setOngoing(true)
                    .build()
            ))

            val result = actionExecutor.execute(task.actionType, paramsMap)

            taskDao.updateTask(task.copy(
                status = "completed",
                updatedAt = System.currentTimeMillis(),
                description = "${task.description}\nResult: $result"
            ))

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Long task failed: ${task.title}", e)
            taskDao.updateTask(task.copy(
                status = "failed",
                updatedAt = System.currentTimeMillis(),
                description = "${task.description}\nError: ${e.message}"
            ))
            if (runAttemptCount >= 3) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TaskForegroundService.CHANNEL_ID,
                "任务进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示长时间运行任务的进度"
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
