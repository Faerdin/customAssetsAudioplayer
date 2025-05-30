package com.github.florent37.assets_audio_player.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.florent37.assets_audio_player.AssetsAudioPlayerPlugin
import com.github.florent37.assets_audio_player.MyMediaSessionService // Replace with your service name
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors // You might need to add this dependency

class NotificationManager(private val context: Context) {

    var closed = false

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    init {
        // Connect to the MediaSessionService when the NotificationManager is created.
        // This should be done efficiently, perhaps only when the first player
        // requests notification display.
        connectMediaController()
    }

    private fun connectMediaController() {
        if (mediaController == null && mediaControllerFuture == null) {
            // Replace MyMediaSessionService with the actual name of your service class
            val sessionToken = SessionToken(context, ComponentName(context, MyMediaSessionService::class.java))
            mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            mediaControllerFuture?.addListener({
                mediaController = mediaControllerFuture?.get()
                // Once connected, you can send initial state or commands if needed.
                // For example, if there was a player active before the plugin was hot-reloaded,
                // you might want to restore its state.
            }, MoreExecutors.directExecutor()) // Use a suitable executor, e.g., a background thread pool
        }
    }

    private fun releaseMediaController() {
        mediaControllerFuture?.let { future ->
            MediaController.releaseFuture(future)
            mediaControllerFuture = null
        }
        mediaController = null
    }

    fun showNotification(playerId: String, audioMetas: AudioMetas, isPlaying: Boolean, notificationSettings: NotificationSettings, stop: Boolean, durationMs: Long) {
        if (closed) {
            return
        }

        // Ensure the MediaSessionService is running.
        // The MediaSessionService will manage the notification and the MediaSession.
        val serviceIntent = Intent(context, MyMediaSessionService::class.java)
        // You might need to pass some initial data to the service here
        // if it needs it to initialize the session or player.
        // If this is the first time starting the service for this playback session,
        // you might include the playerId and initial audioMetas.
        ContextCompat.startForegroundService(context, serviceIntent)

        // Ensure the MediaController is connected before sending commands
        connectMediaController()

        if (stop) {
            // Inform the MediaSessionService to stop playback for this player ID
            // and potentially hide the notification if no other players are active.
            // The MediaSessionService would handle the actual stopping of the player
            // and updating the MediaSession state and notification.
            mediaController?.sendCustomCommand(Command.COMMAND_STOP_PLAYER, Bundle().apply {
                putString(Command.EXTRA_PLAYER_ID, playerId)
            })
            //stopNotification()
        } else {
            // Inform the MediaSessionService about the playback state and metadata update
            // for the specified player ID.
            // The MediaSessionService would update the MediaSession and notification accordingly.
            // You need to make AudioMetas and NotificationSettings Parcelable to pass them in a Bundle.
            mediaController?.sendCustomCommand(Command.COMMAND_UPDATE_PLAYER_STATE, Bundle().apply {
                putString(Command.EXTRA_PLAYER_ID, playerId)
                putBoolean(Command.EXTRA_IS_PLAYING, isPlaying)
                // putParcelable(Command.EXTRA_AUDIO_METAS, audioMetas) // Requires AudioMetas to be Parcelable
                // putParcelable(Command.EXTRA_NOTIFICATION_SETTINGS, notificationSettings) // Requires NotificationSettings to be Parcelable
                putLong(Command.EXTRA_DURATION_MS, durationMs)
                // You might also need to send the current position if relevant for the notification/MediaSession
                // putLong(Command.EXTRA_CURRENT_POSITION_MS, currentPositionMs)
            })

            // Tell the MediaSessionService which player is currently controlling the notification.
            // This custom command would be received by the MediaSessionService's callback.
            // The MediaSessionService will then update its internal state to track the active player
            // and ensure its MediaSession is the primary one managing the notification and handling commands.
            mediaController?.sendCustomCommand(Command.COMMAND_SET_ACTIVE_PLAYER, Bundle().apply {
                putString(Command.EXTRA_PLAYER_ID, playerId)
            })
        }

        // The MediaSessionService will handle registering the active player with the plugin instance
        // based on the COMMAND_SET_ACTIVE_PLAYER command received via its MediaSession.Callback.
        // You should remove this line from here as the service is responsible for this.
        // AssetsAudioPlayerPlugin.instance?.assetsAudioPlayer?.registerLastPlayerWithNotif(playerId)
    }

    fun stopNotification() {
        if (closed) {
            return
        }
        // Inform the MediaSessionService to hide the notification.
        // This might be used when all players are stopped or the user dismisses the notification.
        // The MediaSessionService would then stop itself if there are no active players
        // or reasons to remain in the foreground.
        mediaController?.sendCustomCommand(Command.COMMAND_HIDE_NOTIFICATION, null)
    }

    fun hideNotificationService(definitively: Boolean = false) {
        if (closed) {
            return
        }
        // This might correspond to completely stopping the MediaSessionService
        // and releasing all resources.
        mediaController?.sendCustomCommand(Command.COMMAND_STOP_SERVICE, null)
        releaseMediaController()
        closed = definitively
    }

    // You might need to release the MediaController when the plugin is unregistered.
    // For example, in the dispose method of your Flutter plugin's Android implementation.
    fun dispose() {
        releaseMediaController()
        closed = true
    }
}

// Define custom commands to communicate with the MediaSessionService
object Command {
    const val COMMAND_SET_ACTIVE_PLAYER = "set_active_player"
    const val COMMAND_UPDATE_PLAYER_STATE = "update_player_state"
    const val COMMAND_STOP_PLAYER = "stop_player" // Command to specifically stop a player via service
    const val COMMAND_HIDE_NOTIFICATION = "hide_notification"
    const val COMMAND_STOP_SERVICE = "stop_service"

    const val EXTRA_PLAYER_ID = "player_id"
    const val EXTRA_IS_PLAYING = "is_playing"
    const val EXTRA_AUDIO_METAS = "audio_metas"
    const val EXTRA_NOTIFICATION_SETTINGS = "notification_settings"
    const val EXTRA_DURATION_MS = "duration_ms"
    // Add other extras as needed, e.g., current position
    // const val EXTRA_CURRENT_POSITION_MS = "current_position_ms"
}

// You will need to make AudioMetas and NotificationSettings Parcelable
// so they can be passed through Bundles.
// Example:
/*
import android.os.Parcel
import android.os.Parcelable

data class AudioMetas(
    val title: String?,
    val artist: String?,
    val album: String?,
    val imageUri: String?, // Or whatever type your image representation is
    val notificationIcon: String?,
    val artDownloader: String?
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeString(album)
        parcel.writeString(imageUri)
        parcel.writeString(notificationIcon)
        parcel.writeString(artDownloader)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AudioMetas> {
        override fun createFromParcel(parcel: Parcel): AudioMetas {
            return AudioMetas(parcel)
        }

        override fun newArray(size: Int): Array<AudioMetas?> {
            return arrayOfNulls(size)
        }
    }
}

data class NotificationSettings(
    val nextEnabled: Boolean,
    val prevEnabled: Boolean,
    val stopEnabled: Boolean,
    val customStopAction: Boolean,
    val playPauseEnabled: Boolean,
    val seekBarEnabled: Boolean,
    val customPlayPauseAction: Boolean,
    val customNextAction: Boolean,
    val customPrevAction: Boolean,
    val msNotification: Int
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (nextEnabled) 1.toByte() else 0.toByte())
        parcel.writeByte(if (prevEnabled) 1.toByte() else 0.toByte())
        parcel.writeByte(if (stopEnabled) 1.toByte() else 0.toByte())
        parcel.writeByte(if (customStopAction) 1.toByte() else 0.toByte())
        parcel.writeByte(if (playPauseEnabled) 1.toByte() else 0.toByte())
        parcel.writeByte(if (seekBarEnabled) 1.toByte() else 0.toByte())
        parcel.writeByte(if (customPlayPauseAction) 1.toByte() else 0.toByte())
        parcel.writeByte(if (customNextAction) 1.toByte() else 0.toByte())
        parcel.writeByte(if (customPrevAction) 1.toByte() else 0.toByte())
        parcel.writeInt(msNotification)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<NotificationSettings> {
        override fun createFromParcel(parcel: Parcel): NotificationSettings {
            return NotificationSettings(parcel)
        }

        override fun newArray(size: Int): Array<NotificationSettings?> {
            return arrayOfNulls(size)
        }
    }
}
*/
