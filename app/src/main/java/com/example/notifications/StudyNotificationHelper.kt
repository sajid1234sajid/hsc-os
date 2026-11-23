package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppLanguage
import com.example.data.StringsProvider
import java.util.Calendar

object StudyNotificationHelper {
    const val CHANNEL_TIMER = "hsc_timer_alerts"
    const val CHANNEL_REMINDER = "hsc_daily_reminders"
    private const val REMINDER_REQUEST_CODE = 2028

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val timerChannel = NotificationChannel(
                CHANNEL_TIMER,
                "সেশন টাইমার অ্যালার্ট",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "৭৫ মিনিটের পড়ার ধাপ ও সেশন শেষ হলে নোটিফিকেশন"
            }
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER,
                "দৈনিক পড়ার রিমাইন্ডার",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "প্রতিদিন নির্ধারিত সময়ে পড়ার রিমাইন্ডার"
            }
            manager.createNotificationChannel(timerChannel)
            manager.createNotificationChannel(reminderChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showSessionNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showDailyReminderNotification(context: Context, lang: AppLanguage) {
        val title = StringsProvider.text(
            lang,
            "আজকের ৪টি সেশন শুরু করার সময় হয়েছে!",
            "Time for today's 4 study sessions!"
        )
        val body = StringsProvider.text(
            lang,
            "আজকের রুটিনের বিষয়গুলো ৭৫ মিনিট (৫০+২০+৫) নিয়মে শেষ করুন।",
            "Complete today's 4 subjects using the 50/20/5 phase timer."
        )
        showSessionNotification(context, title, body, 9001)
    }

    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int, enabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, StudyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!enabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        ensureChannels(context)
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            trigger.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
}
