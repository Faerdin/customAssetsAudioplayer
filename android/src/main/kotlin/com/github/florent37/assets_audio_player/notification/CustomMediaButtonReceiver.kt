package com.github.florent37.assets_audio_player.notification

import android.content.Context
import android.content.Intent
import android.util.Log
//import androidx.media3.session.MediaSession
import androidx.media3.session.MediaButtonReceiver
import androidx.annotation.Keep;

@Keep

class CustomMediaButtonReceiver : MediaButtonReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            // Call the superclass's onReceive, which should handle the media button event
            // and route it correctly within the Media3 framework if a MediaSession is active.
            super.onReceive(context, intent)
        } catch (e: Exception) {
            Log.e(javaClass.name, e.message ?: "unknown error")
        }
    }

}
/*
    override fun onReceive(controller: MediaSession.ControllerInfo, intent: Intent) {
        try {
            super.onReceive(controller, intent)
        } catch (e: Exception) {
            Log.e(javaClass.name, e.message ?: "unknown error")
        }
    }
}
*/
