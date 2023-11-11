package com.acidgoose.possumkotlin

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

const val CHANNEL_ID = "POSSUM"
const val NOTIFICATION_ID = 1
const val START_ALARM_REQUEST = 1312631287
const val LAST_POSSUM = "lastPossumImage.jpg"

/**
 * Update the current wallpaper to the latest one
 */
fun updateWallpaper(context : Context){
    //AppLog.write("Updated function started",context)

    CoroutineScope(Dispatchers.IO).launch {
        context.getSharedPreferences(PREF, Service.MODE_PRIVATE).edit().putLong(PREF_UPDATE_TIME,System.currentTimeMillis()).apply()

        val wakeLock: PowerManager.WakeLock =
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Possum::WallpaperUpdate").apply {
                    acquire()
                }
            }

        try{
            // what url finder to use
            val urlFinder : UrlFinder = MastodonUrl()

            val pref = context.getSharedPreferences(PREF, Service.MODE_PRIVATE)

            val imageUri = urlFinder.getImageUrl(context)

            val image = downloadWallpaper(imageUri)

            val wallpaperManager = WallpaperManager.getInstance(context)

            var scaledImage = image!!

            if (pref.getBoolean(PREF_SCALE,false)){

                val screenWidth = Resources.getSystem().displayMetrics.widthPixels
                val screenHeight = Resources.getSystem().displayMetrics.heightPixels

                val ratio = image.height.toFloat()/image.width.toFloat()

                Log.d("Resize","Ration: " + ratio)
                val scaledHeight = (ratio*screenWidth.toFloat()).toInt()
                Log.d("Resize","Image: " + screenWidth + " " + scaledHeight)
                scaledImage = image.scale(screenWidth,scaledHeight)

                val newBitmap = Bitmap.createBitmap(screenWidth,screenHeight,Bitmap.Config.ARGB_8888)
                val canvas = Canvas(newBitmap)
                canvas.drawColor(Color.BLACK)

                val y = ((screenHeight - scaledHeight) / 2.0).toInt()

                val paint = Paint().apply {
                    isAntiAlias = true
                    isDither = true
                    isFilterBitmap = true
                }

                canvas.drawBitmap(scaledImage, null, Rect(0,y,screenWidth,y+scaledHeight), paint)

                scaledImage = newBitmap
            }
            Log.d("Resize","Image Size: " + scaledImage.width + " " + scaledImage.height)

            if (pref.getBoolean(PREF_LOCK,false)){
                wallpaperManager.setBitmap(scaledImage,null,false,WallpaperManager.FLAG_LOCK)
            }
            if (pref.getBoolean(PREF_SYS,false)){
                wallpaperManager.setBitmap(scaledImage,null,false,WallpaperManager.FLAG_SYSTEM)
            }

            storeImage(context,image)

            //AppLog.write("Updated function success",context)
        }catch (_: Exception){
            //Log.d("twitter","couldn't update wallpaper")
            //AppLog.write("Updated function failed",context)
        }finally {
            wakeLock.release()
        }

    }
}


/**
 * Get the time for the next alarm
 * This time is equal to the first minute after the start of the next hour
 */
private fun getNextAlarmTime() : Long {
    val curTime = System.currentTimeMillis()/1000*1000 // disregard milliseconds
    val curMinute = curTime / 60000 % 60
    val waitTime = 61 - curMinute

    return curTime + waitTime * 60000
}


/**
 * Download the wallpaper from the link provided
 */
suspend fun downloadWallpaper(url: String) : Bitmap? {
    val newUrl = URL(url)

    val conn: HttpsURLConnection =
        withContext(Dispatchers.IO) {
            newUrl.openConnection()
        } as HttpsURLConnection

    conn.doInput = true
    withContext(Dispatchers.IO) {
        conn.connect()
    }
    val `is`: InputStream = conn.inputStream
    val options = BitmapFactory.Options()
    options.inPreferredConfig = Bitmap.Config.RGB_565

    return BitmapFactory.decodeStream(`is`, null, options)
}

suspend fun storeImage(context: Context, image : Bitmap?){
    val file = File(context.filesDir, LAST_POSSUM)
    val out = file.outputStream()
    image?.compress(Bitmap.CompressFormat.JPEG,100,out)
    withContext(Dispatchers.IO) {
        out.close()
    }
}

/**
 * Foreground service that sets the wallpaper on start command
 * This is done periodically every hour
 */
class TwitterService : Service() {

    override fun onCreate() {
        super.onCreate()

        createChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Possums are being downloaded!")
            .setPriority(NotificationCompat.PRIORITY_LOW).build()

        (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)?.notify(NOTIFICATION_ID,notification)
        startForeground(NOTIFICATION_ID,notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setAlarm()
        updateWallpaper(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Don't allow binding to this service
     */
    override fun onBind(intent: Intent): IBinder {
        return Binder()
    }

    /**
     * Create the notification channel
     */
    private fun createChannel(){
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        notificationManager.createNotificationChannel(channel)
    }


    /**
     * Set the alarm for the next invocation of this service
     */
    private fun setAlarm(){
        //AppLog.write("Setting alarm",this)

        val alarmDownloadIntent = PendingIntent.getForegroundService(this, START_ALARM_REQUEST,
            Intent(this,TwitterService::class.java),PendingIntent.FLAG_IMMUTABLE)

        val alarmManager =
            this.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        alarmManager?.setAlarmClock(AlarmManager.AlarmClockInfo(getNextAlarmTime(),null),alarmDownloadIntent)
        //AppLog.write("Setting alarm finished",this)
        //Log.d("alarm manager", alarmManager?.nextAlarmClock.toString())
    }
}