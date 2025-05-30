package com.github.florent37.assets_audio_player.notification

import android.content.Context
import android.content.Intent
import android.os.Bundle
//import androidx.media3.session.MediaController
//import androidx.media3.session.MediaSession
//import androidx.media3.common.PlaybackState
import android.view.KeyEvent
//import androidx.concurrent.futures.ResolvableFuture
import androidx.concurrent.futures.CallbackToFutureAdapter
//import androidx.media3.common.Player
//import androidx.media3.common.Player.Listener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
//import androidx.media3.common.util.ListenableFuture
import androidx.media3.session.SessionResult
//import androidx.privacysandbox.tools.core.generator.build
import com.google.common.util.concurrent.ListenableFuture

@androidx.media3.common.util.UnstableApi

class MediaButtonsReceiver(
    context: Context,
    private val onAction: (MediaButtonAction) -> Unit,
    private val onNotifSeek: (Long) -> Unit
) {

    companion object {
        var instance: MediaButtonsReceiver? = null

        private var mediaSession: MediaSession? = null

        fun getMediaSession(context: Context): MediaSession {
            if (mediaSession == null) {
                val player = ExoPlayer.Builder(context).build()
               //mediaSession = androidx.media3.session.MediaSession.Builder(context,
               //     androidx.media3.common.Player.Listener { /* empty */ }).build()
/*
                mediaSession = MediaSession.Builder(context, player).build()
            }
            return mediaSession!!
        }
    }



    init {
        instance = this
        getMediaSession(context).setCallback(mediaSessionCallback)
    }

                private val mediaSessionCallback = object : MediaSession.Callback {*/

                val mediaSessionCallback = object : MediaSession.Callback {
                    override fun onMediaButtonEvent(
                        mediaSession: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        intent: Intent
                    ): Boolean {
                        //onIntentReceive(intent)
                        instance?.onIntentReceive(intent)
                        return true
                    }

                    override fun onCustomCommand(
                        mediaSession: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        customCommand: SessionCommand,
                        args: Bundle
//        ): ListenableFuture<SessionResult> {
//            //return super.onCustomCommand(controller, customCommand, args)
//            return androidx.media3.common.util.Futures.immediateFuture
//            (androidx.media3.session.SessionResult(
//                androidx.media3.session.SessionResult.RESULT_SUCCESS))

                    ): ListenableFuture<SessionResult> {
//                        val future = ResolvableFuture.create<SessionResult>()
//                        future.set(SessionResult(SessionResult.RESULT_SUCCESS))
//                        return future
                        return CallbackToFutureAdapter.getFuture { completer ->
                            completer.set(SessionResult(SessionResult.RESULT_SUCCESS))
                            "CustomCommandHandled"
                        }
                    }
                }

                mediaSession = MediaSession.Builder(context, player)
                    .setCallback(mediaSessionCallback).build()
            }
            return mediaSession!!
        }
    }

    enum class MediaButtonAction {
        play,
        pause,
        playOrPause,
        next,
        prev,
        stop
    }

//    init {
//        getMediaSession(context).setCallback(mediaSessionCallback)
//    }

    init {
        instance = this
        getMediaSession(context) // Ensure media session is initialized
    }

    private fun getAdjustedKeyCode(keyEvent: KeyEvent): Int {
        val keyCode = keyEvent.keyCode
        return if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode ==
            KeyEvent.KEYCODE_MEDIA_PAUSE) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        } else keyCode
    }

    private fun mapAction(keyCode: Int): MediaButtonAction? {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> MediaButtonAction.play
            KeyEvent.KEYCODE_MEDIA_PAUSE -> MediaButtonAction.pause
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> MediaButtonAction.playOrPause
            KeyEvent.KEYCODE_MEDIA_STOP -> MediaButtonAction.stop
            KeyEvent.KEYCODE_MEDIA_NEXT -> MediaButtonAction.next
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MediaButtonAction.prev
            else -> null
        }
    }

    /*
    fun onIntentReceive(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        (intent.extras?.get(Intent.EXTRA_KEY_EVENT) as? KeyEvent)
            ?.takeIf { it.action == KeyEvent.ACTION_DOWN }
            ?.let { getAdjustedKeyCode(it) }
            ?.let { mapAction(it) }
            ?.let { action -> handleMediaButton(action) }
    }


     */
    fun onIntentReceive(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        // Use getParcelable for type-safe retrieval
        //@Suppress("DEPRECATION") // Suppress the deprecation warning for the parameter if needed, although getParcelable itself isn't deprecated
        val keyEvent: KeyEvent? = intent.extras?.getParcelable(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)

        keyEvent
            ?.takeIf { it.action == KeyEvent.ACTION_DOWN }
            ?.let { getAdjustedKeyCode(it) }
            ?.let { mapAction(it) }
            ?.let { action -> handleMediaButton(action) }
    }

/*
    fun onIntentReceive(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val keyEvent = intent.getParcelableExtraCompat<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        keyEvent?.takeIf { it.action == KeyEvent.ACTION_DOWN }
            ?.let { getAdjustedKeyCode(it) }
            ?.let { mapAction(it) }
            ?.let { action -> handleMediaButton(action) }
    }
*/
    private fun seekPlayerTo(pos: Long) {
        this.onNotifSeek(pos)
    }

    private fun handleMediaButton(action: MediaButtonAction) {
        this.onAction(action)
    }
}
