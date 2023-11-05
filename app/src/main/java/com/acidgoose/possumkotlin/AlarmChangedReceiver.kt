package com.acidgoose.possumkotlin

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat

/**
 * Receive changes when there's no alarm scheduled or the phone restarted
 * Creates a notification to notify that possums aren't being downloaded :{
 */
class AlarmChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        //AppLog.write("Received broadcast",context)

        if (intent.action != "android.app.action.NEXT_ALARM_CLOCK_CHANGED" &&
                intent.action != "android.intent.action.BOOT_COMPLETED") return

        val active = isAlarmActive(context)
        val pref = context.getSharedPreferences(PREF, AppCompatActivity.MODE_PRIVATE)
        val alerted = pref.getBoolean(PREF_ALERTED,false)

        //AppLog.write("Is alarm active: $active",context)

        if (active || alerted) return

        pref.edit().putBoolean(PREF_ALERTED,true).apply()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.alarm_changed_text))
            .setContentIntent(PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()

        (context.getSystemService(Service.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(NOTIFICATION_ID,notification)

        //AppLog.write("Created notification",context)
    }

    /**
     * checks if alarm is active
     */
    private fun isAlarmActive(context : Context) : Boolean {
        return (PendingIntent.getForegroundService(
            context,
            START_ALARM_REQUEST,
            Intent(context, TwitterService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) != null)
    }
}