package com.r3gate.app

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo

object UploadNotifications {
    private const val CHANNEL="workmeter_photos"
    private fun manager(context:Context):NotificationManager {
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Zdjęcia i Google Disk",NotificationManager.IMPORTANCE_LOW))
        return manager
    }
    private fun activity(context:Context,reference:String="",photos:Boolean=false):PendingIntent {
        val intent=Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if(photos) putExtra("widget_action","photos")
            else if(reference.isNotBlank()) putExtra("upload_retry",reference)
        }
        return PendingIntent.getActivity(context,if(photos) 211 else 212+reference.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun builder(context:Context,reference:String,message:String,retry:Boolean):NotificationCompat.Builder {
        manager(context)
        return NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_upload)
            .setContentTitle("WorkMeter · Google Disk").setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setColor(0xFF37474F.toInt()).setOnlyAlertOnce(true).setContentIntent(activity(context,if(retry) reference else ""))
            .addAction(R.drawable.ic_upload,"Dodaj zdjęcia",activity(context,photos=true))
            .apply { if(retry) addAction(R.drawable.ic_upload,"Ponów",activity(context,reference)) }
    }
    fun foreground(context:Context,reference:String,message:String,done:Int,total:Int):ForegroundInfo {
        val notification=builder(context,reference,message,false).setOngoing(true).setProgress(total,done,total==0).build()
        val id=3100+(reference.hashCode() and 0xFFFF)
        return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(id,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id,notification)
    }
    fun result(context:Context,reference:String,message:String,retry:Boolean) {
        manager(context)
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(7100+(reference.hashCode() and 0xFFFF),builder(context,reference,message,retry).setAutoCancel(true).build())
    }
    fun shortcut(context:Context) {
        manager(context)
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(2100,builder(context,"","Dodaj zdjęcia do obiektu i wyślij na Google Disk",false).setContentIntent(activity(context,photos=true)).build())
    }
}
