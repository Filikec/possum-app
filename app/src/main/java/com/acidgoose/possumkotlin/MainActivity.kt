package com.acidgoose.possumkotlin

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.security.Permission
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.jar.Manifest


const val PREF_LOCK = "updateLock"
const val PREF_SYS = "updateSys"
const val PREF_ACTIVE = "active"
const val PREF_UPDATE_TIME = "lastUpdate"
const val PREF_ALERTED = "alerted"
const val PREF_SCALE = "scale"
const val PREF = "preferences"


class MainActivity : AppCompatActivity() {

    private val createImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
        if (result.resultCode == Activity.RESULT_OK) {

            CoroutineScope(Dispatchers.IO).launch{
                val uri = (result.data?.data)!!
                val out: OutputStream? = contentResolver.openOutputStream(uri)

                val bitmap = BitmapFactory.decodeFile(File(filesDir, LAST_POSSUM).absolutePath)

                bitmap.compress(Bitmap.CompressFormat.JPEG,100,out)

                withContext(Dispatchers.IO) {
                    out?.close()
                }
            }
        }
    }

    val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {

            } else {
            }
        }


    private lateinit var switchLock : SwitchCompat
    private lateinit var switchSys : SwitchCompat
    private lateinit var switchActive : SwitchCompat
    private lateinit var switchScale : SwitchCompat
    private lateinit var help : Button
    private lateinit var updated : TextView

    private var active = false

    private lateinit var pref : SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val actionBar: ActionBar? = supportActionBar
        actionBar?.hide()
        window.statusBarColor = ContextCompat.getColor(this, R.color.PossumBeige)

        findViewById<Button>(R.id.update).setOnClickListener{
            updateWallpaper(this)
        }

        loadProperties()
        setOnClickListeners()

        switchActive.isChecked = active
        switchLock.isChecked = pref.getBoolean(PREF_LOCK,false)
        switchSys.isChecked = pref.getBoolean(PREF_SYS,false)
        switchScale.isChecked = pref.getBoolean(PREF_SCALE,false)

        updateTime()
        keepUpdatingUI()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }


    private fun loadProperties(){
        updated = findViewById(R.id.updateTime)
        switchActive = findViewById(R.id.active)
        switchSys = findViewById(R.id.updateSys)
        switchLock = findViewById(R.id.updateLock)
        switchScale = findViewById(R.id.scaleImg)
        help = findViewById(R.id.helpButton)

        active = (PendingIntent.getForegroundService(this, START_ALARM_REQUEST,
            Intent(this,WallpaperService::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) != null)

        pref = getSharedPreferences(PREF,MODE_PRIVATE)
    }

    private fun setOnClickListeners(){
        switchLock.setOnCheckedChangeListener{_,b->
            pref.edit().apply{
                putBoolean(PREF_LOCK,b)
            }.apply()
        }

        help.setOnClickListener{
            val lock = findViewById<TextView>(R.id.updateLock)
            val home = findViewById<TextView>(R.id.updateSys)
            val scale = findViewById<TextView>(R.id.scaleImg)
            val active = findViewById<TextView>(R.id.active)

            if (lock.text == getString(R.string.update_lock_wallpaper)){
                lock.text = getString(R.string.info_lockscreen)
                home.text = getString(R.string.info_homescreen)
                scale.text = getString(R.string.info_scale)
                active.text = getString(R.string.info_active)
            }else{
                lock.text = getString(R.string.update_lock_wallpaper)
                home.text = getString(R.string.update_system_wallpaper)
                scale.text = getString(R.string.scale_imgs)
                active.text = getString(R.string.active)
            }
        }

        switchSys.setOnCheckedChangeListener{_,b->
            pref.edit().apply{
                putBoolean(PREF_SYS,b)
            }.apply()
        }

        switchScale.setOnCheckedChangeListener{_,b->
            pref.edit().apply{
                putBoolean(PREF_SCALE,b)
            }.apply()
        }

        switchActive.setOnCheckedChangeListener{_,b->
            pref.edit().apply{
                putBoolean(PREF_ACTIVE,b)
            }.apply()

            if (b && !active){
                pref.edit().putBoolean(PREF_ALERTED,false).apply()

                startForegroundService(Intent(this,WallpaperService::class.java))
            }else if (!b){
                val alarmManager =
                    this.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

                val alarmDownloadIntent = PendingIntent.getForegroundService(this, START_ALARM_REQUEST,
                    Intent(this,WallpaperService::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)

                alarmManager?.cancel(alarmDownloadIntent)
                alarmDownloadIntent.cancel()

            }
            active = b
        }


        findViewById<Button>(R.id.save).setOnClickListener{

            if (File(filesDir, LAST_POSSUM).exists()){
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_TITLE, "possum.jpg")
                }
                createImage.launch(intent)
            }else{
                showAlert(this,"No image", "No image downloaded yet")
            }

        }


    }

    private fun showAlert(context: Context?, title: String?, message: String?) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { b, _ ->
                b.dismiss()
            }.show()
    }

    /**
     * Spawn a new thread that each seconds updates the time since last download
     */
    private fun keepUpdatingUI(){
        Thread{
            while (true){
                Thread.sleep(1000)
                runOnUiThread {
                    updateTime()
                }
            }
        }.start()
    }

    private fun updateTime(){
        val lastTime = pref.getLong(PREF_UPDATE_TIME,-1)

        if (lastTime == -1L){
            updated.text = getString(R.string.updated," never")
        }else{
            val date = Instant.ofEpochMilli(lastTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            val dateString = date.format(formatter)
            updated.text = getString(R.string.updated, " $dateString")
        }
    }

}