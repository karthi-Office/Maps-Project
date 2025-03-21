package com.example.mapsproject.services

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.provider.Settings

class MapServices : Service(){
    private lateinit var musicPlayer : MediaPlayer

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        musicPlayer = MediaPlayer.create(this, Settings.System.DEFAULT_RINGTONE_URI)
        musicPlayer.isLooping = true
        musicPlayer.start()

        return START_STICKY

    }

    override fun onDestroy() {
        musicPlayer.stop()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}