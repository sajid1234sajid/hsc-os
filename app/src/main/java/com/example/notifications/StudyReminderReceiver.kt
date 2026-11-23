package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppLanguage

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        StudyNotificationHelper.showDailyReminderNotification(context, AppLanguage.BN)
    }
}
