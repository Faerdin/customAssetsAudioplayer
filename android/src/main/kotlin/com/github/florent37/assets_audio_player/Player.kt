package com.github.florent37.assets_audio_player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Message
import com.github.florent37.assets_audio_player.headset.HeadsetStrategy
import com.github.florent37.assets_audio_player.notification.AudioMetas
import com.github.florent37.assets_audio_player.notification.NotificationManager
import com.github.florent37.assets_audio_player.notification.NotificationService
import com.github.florent37.assets_audio_player.notification.NotificationSettings
import com.github.florent37.assets_audio_player.playerimplem.*
import com.github.florent37.assets_audio_player.stopwhencall.AudioFocusStrategy
import com.github.florent37.assets_audio_player.stopwhencall.StopWhenCall
//import io.flutter.embedding.engine.plugins.FlutterPlugin
//import io.flutter.plugin.common.MethodChannel
import androidx.media3.common.Player // Import the Media3 Player interface
import androidx.media3.common.Player.Listener
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi // Import UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Does not depend on Flutter, feel free to use it in all your projects
 */
class Player(
        val id: String,
        private val context: Context,
        private val stopWhenCall: StopWhenCall,
        private val notificationManager: NotificationManager,
        private val flutterAssets: FlutterPlugin.FlutterAssets
) : Player // Implement the Media3 Player interface (unqualified name is fine due to import)
{
    // {

    companion object {
        const val VOLUME_WHEN_REDUCED = 0.3

        const val AUDIO_TYPE_NETWORK = "network"
        const val AUDIO_TYPE_LIVESTREAM = "liveStream"
        const val AUDIO_TYPE_FILE = "file"
        const val AUDIO_TYPE_ASSET = "asset"
    }

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // To handle position updates.
    private val handler = Handler()

    //private var mediaPlayer: PlayerImplem? = null
    private var mediaPlayer: ExoPlayer? = null

    //region outputs
    var onVolumeChanged: ((Double) -> Unit)? = null
    var onPlaySpeedChanged: ((Double) -> Unit)? = null
    var onPitchChanged: ((Double) -> Unit)? = null
    var onForwardRewind: ((Double) -> Unit)? = null
    var onReadyToPlay: ((DurationMS) -> Unit)? = null
    var onSessionIdFound: ((Int) -> Unit)? = null
    var onPositionMSChanged: ((Long) -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onPlaying: ((Boolean) -> Unit)? = null
    var onBuffering: ((Boolean) -> Unit)? = null
    var onError: ((AssetAudioPlayerThrowable) -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onNotificationPlayOrPause: (() -> Unit)? = null
    var onNotificationStop: (() -> Unit)? = null
    //endregion

    private var respectSilentMode: Boolean = false
    private var headsetStrategy: HeadsetStrategy = HeadsetStrategy.none
    private var audioFocusStrategy: AudioFocusStrategy = AudioFocusStrategy.None
    private var volume: Double = 1.0
    private var playSpeed: Double = 1.0
    private var pitch: Double = 1.0

    private var isEnabledToPlayPause: Boolean = true
    private var isEnabledToChangeVolume: Boolean = true

    val isPlaying: Boolean
        get() = mediaPlayer != null && mediaPlayer!!.isPlaying

    private var lastRingerMode: Int? = null //see https://developer.android.com/reference/android/media/AudioManager.html?hl=fr#getRingerMode()

    private var displayNotification = false

    private var _playingPath : String? = null
    private var _durationMs : DurationMS = 0
    private var _positionMs : DurationMS = 0
    private var _lastOpenedPath : String? = null
    private var audioMetas: AudioMetas? = null
    private var notificationSettings: NotificationSettings? = null

    private var _lastPositionMs: Long? = null
    private val updatePosition = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mediaPlayer ->
                try {
                    if (!mediaPlayer.isPlaying) {
                        handler.removeCallbacks(this)
                    }

                    //Probably more correct to use contentPosition instead of currentPosition. Needs to be tested.
                    //val positionMs : Long = mediaPlayer.currentPosition
                    val positionMs : Long = mediaPlayer.contentPosition

                    if(_lastPositionMs != positionMs) {
                        // Send position (milliseconds) to the application.
                        onPositionMSChanged?.invoke(positionMs)
                        _lastPositionMs = positionMs
                    }

                    if (respectSilentMode) {
                        val ringerMode = am.ringerMode
                        if (lastRingerMode != ringerMode) { //if changed
                            lastRingerMode = ringerMode
                            setVolume(volume) //re-apply volume if changed
                        }
                    }

                    _positionMs = if(_durationMs != 0L) {
                        min(positionMs, _durationMs)
                    } else {
                        positionMs
                    }
                    updateNotifPosition()

                    // Update every 300ms.
                    handler.postDelayed(this, 300)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Deprecated(
        "Use seekToNextMediaItem() instead.", // Provide a message
        ReplaceWith("seekToNextMediaItem()") // Suggest the replacement
    )
    @UnstableApi
    override fun next() {
        mediaPlayer?.seekToNextMediaItem()
        this.onNext?.invoke()
    }

    fun prev() {
        this.onPrev?.invoke()
    }

    fun onAudioUpdated(path: String, audioMetas: AudioMetas) {
        if(_playingPath == path || (_playingPath == null && _lastOpenedPath == path)){
            this.audioMetas = audioMetas
            updateNotif()
        }
    }

    fun open(assetAudioPath: String?,
             assetAudioPackage: String?,
             audioType: String,
             autoStart: Boolean,
             volume: Double,
             seek: Int?,
             respectSilentMode: Boolean,
             displayNotification: Boolean,
             notificationSettings: NotificationSettings,
             audioMetas: AudioMetas,
             playSpeed: Double,
             pitch: Double,
             headsetStrategy: HeadsetStrategy,
             audioFocusStrategy: AudioFocusStrategy,
             networkHeaders: Map<*, *>?,
             result: MethodChannel.Result,
             context: Context,
             drmConfiguration: Map<*, *>?
    ) {
        try {
            stop(pingListener = false)
        } catch (t: Throwable){
            print(t)
        }

        this.displayNotification = displayNotification
        this.audioMetas = audioMetas
        this.notificationSettings = notificationSettings
        this.respectSilentMode = respectSilentMode
        this.headsetStrategy = headsetStrategy
        this.audioFocusStrategy = audioFocusStrategy

        _lastOpenedPath = assetAudioPath
      
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val playerWithDuration = PlayerFinder.findWorkingPlayer(
                        PlayerFinderConfiguration(
                        assetAudioPath = assetAudioPath,
                        flutterAssets = flutterAssets,
                        assetAudioPackage = assetAudioPackage,
                        audioType = audioType,
                        networkHeaders = networkHeaders,
                        context = context,
                        onFinished = {
                            stopWhenCall.stop()
                            onFinished?.invoke()
                        },
                        onPlaying = onPlaying,
                        onBuffering = onBuffering,
                        onError= onError,
                        drmConfiguration = drmConfiguration
                        )PlayerImplem
                )

                val durationMs = playerWithDuration.duration
                mediaPlayer = playerWithDuration.player

                //here one open succeed
                onReadyToPlay?.invoke(durationMs)
                mediaPlayer?.getSessionId(listener = {
                    onSessionIdFound?.invoke(it)
                })

                _playingPath = assetAudioPath
                _durationMs = durationMs

                setVolume(volume)
                setPlaySpeed(playSpeed)
                setPitch(pitch)

                seek?.let {
                    this@Player.seek(milliseconds = seek * 1L)
                }

                if (autoStart) {
                    play() //display notif inside
                } else {
                    updateNotif() //if pause, we need to display the notif
                }
                result.success(null)
            } catch (error: Throwable) {
                error.printStackTrace()
                if(error is PlayerFinder.NoPlayerFoundException && error.why != null){
                    result.error("OPEN", error.why.message, mapOf(
                            "type" to error.why.type,
                            "message" to error.why.message
                    ))
                } else {
                    result.error("OPEN", error.message, null)
                }
            }
        }
    }

    fun stop(pingListener: Boolean = true, removeNotification: Boolean = true) {
        mediaPlayer?.apply {
            // Reset duration and position.
            // handler.removeCallbacks(updatePosition);
            // channel.invokeMethod("player.duration", 0);
            onPositionMSChanged?.invoke(0)

            mediaPlayer?.stop()
            mediaPlayer?.release()
            onPlaying?.invoke(false)
            handler.removeCallbacks(updatePosition)
        }
        if (forwardHandler != null) {
            forwardHandler!!.stop()
            forwardHandler = null
        }
        mediaPlayer = null
        onForwardRewind?.invoke(0.0)
        if (pingListener) { //action from user
            onStop?.invoke()
            updateNotif(removeNotificationOnStop= removeNotification)
        }
    }

    // Implement the abstract stop() method from androidx.media3.common.Player
    override fun stop() {
        // Call your existing custom stop method with default values
        this.stop(pingListener = true, removeNotification = true)
    }


    fun toggle() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    private fun stopForward() {
        forwardHandler?.takeIf { h -> h.isActive }?.let { h ->
            h.stop()
            setPlaySpeed(this.playSpeed)
        }
        onForwardRewind?.invoke(0.0)
    }

    private fun updateNotifPosition() {
        this.audioMetas
                ?.takeIf { this.displayNotification }
                ?.takeIf { notificationSettings?.seekBarEnabled ?: true }
                ?.let { audioMetas ->
                    NotificationService.updatePosition(
                            context = context,
                            isPlaying = isPlaying,
                            speed = this.playSpeed.toFloat(),
                            currentPositionMs = _positionMs
                    )
        }
    }

    fun forceNotificationForGroup(
            audioMetas: AudioMetas,
            isPlaying: Boolean,
            display: Boolean,
            notificationSettings: NotificationSettings
    ) {
        notificationManager.showNotification(
                playerId = id,
                audioMetas = audioMetas,
                isPlaying = isPlaying,
                notificationSettings = notificationSettings,
                stop = !display,
                durationMs = 0
        )
    }

    fun showNotification(show: Boolean){
        val oldValue = this.displayNotification
        this.displayNotification = show
        if(oldValue) { //if was showing a notification
            notificationManager.stopNotification()
            //hide it
        } else {
            updateNotif()
        }
    }
    
    private fun updateNotif(removeNotificationOnStop: Boolean = true) {
        this.audioMetas?.takeIf { this.displayNotification }?.let { audioMetas ->
            this.notificationSettings?.let { notificationSettings ->
                updateNotifPosition()
                notificationManager.showNotification(
                        playerId = id,
                        audioMetas = audioMetas,
                        isPlaying = this.isPlaying,
                        notificationSettings = notificationSettings,
                        stop = removeNotificationOnStop && mediaPlayer == null,
                        durationMs = this._durationMs
                )
            }
        }
    }

    fun play() {
        if(audioFocusStrategy is AudioFocusStrategy.None){
            this.isEnabledToPlayPause = true //this one must be called before play/pause()
            this.isEnabledToChangeVolume = true //this one must be called before play/pause()
            playerPlay()
        } else {
            val audioState = this.stopWhenCall.requestAudioFocus(audioFocusStrategy)
            if (audioState == StopWhenCall.AudioState.AUTHORIZED_TO_PLAY) {
                this.isEnabledToPlayPause = true //this one must be called before play/pause()
                this.isEnabledToChangeVolume = true //this one must be called before play/pause()
                playerPlay()
            } //else will wait until focus is enabled
        }
    }

    private fun playerPlay() { //the play
        if (isEnabledToPlayPause) { //can be disabled while recieving phone call
            mediaPlayer?.let { player ->
                stopForward()
                player.play()
                _lastPositionMs = null
                handler.post(updatePosition)
                onPlaying?.invoke(true)
                updateNotif()
            }
        } else {
            this.stopWhenCall.requestAudioFocus(audioFocusStrategy)
        }
    }

    fun pause() {
        if (isEnabledToPlayPause) {
            mediaPlayer?.let {
                it.pause()
                handler.removeCallbacks(updatePosition)

                stopForward()
                onPlaying?.invoke(false)
                updateNotif()
            }
        }
    }

    fun loopSingleAudio(loop: Boolean){
        mediaPlayer?.loopSingleAudio = loop
    }

    fun seek(milliseconds: Long) {
        mediaPlayer?.apply {
            val to = max(milliseconds, 0L)
            seekTo(to)
            onPositionMSChanged?.invoke(currentPositionMs)
        }
    }

    fun seekBy(milliseconds: Long) {
        mediaPlayer?.let {
            val to = it.currentPositionMs + milliseconds;
            seek(to)
        }
    }

    fun setVolume(volume: Double) {
        if (isEnabledToChangeVolume) {
            this.volume = volume
            mediaPlayer?.let {
                var v = volume
                if (this.respectSilentMode) {
                    v = when (am.ringerMode) {
                        AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE -> 0.toDouble()
                        else -> volume //AudioManager.RINGER_MODE_NORMAL
                    }
                }

                it.setVolume(v.toFloat())

                onVolumeChanged?.invoke(this.volume) //only notify the setted volume, not the silent mode one
            }
        }
    }

    override fun setVolume(volume: Float) {
        this.setVolume(volume.toDouble())
    }

    private var forwardHandler: ForwardHandler? = null;

    fun setPlaySpeed(playSpeed: Double) {
        if (playSpeed >= 0) { //android only take positive play speed
            if (forwardHandler != null) {
                forwardHandler!!.stop()
                forwardHandler = null
            }
            this.playSpeed = playSpeed
            mediaPlayer?.let {
                it.setPlaySpeed(playSpeed.toFloat())
                onPlaySpeedChanged?.invoke(this.playSpeed)
            }
        }
    }

    fun setPitch(pitch: Double) {
        if (pitch >= 0) { //android only take positive pitch
            if (forwardHandler != null) {
                forwardHandler!!.stop()
                forwardHandler = null
            }
            this.pitch = pitch
            mediaPlayer?.let {
                it.setPitch(pitch.toFloat())
                onPitchChanged?.invoke(this.pitch)
            }
        }
    }

    fun forwardRewind(speed: Double) {
        if (forwardHandler == null) {
            forwardHandler = ForwardHandler()
        }

        mediaPlayer?.pause()
        //handler.removeCallbacks(updatePosition)
        //onPlaying?.invoke(false)

        onForwardRewind?.invoke(speed)
        forwardHandler!!.start(this, speed)
    }

    private var volumeBeforePhoneStateChanged: Double? = null
    private var wasPlayingBeforeEnablePlayChange: Boolean? = null
    fun updateEnableToPlay(audioState: StopWhenCall.AudioState) {
        (audioFocusStrategy as? AudioFocusStrategy.Request)?.let { audioFocusStrategy ->
            when (audioState) {
                StopWhenCall.AudioState.AUTHORIZED_TO_PLAY -> {
                    this.isEnabledToPlayPause = true //this one must be called before play/pause()
                    this.isEnabledToChangeVolume = true //this one must be called before play/pause()
                    if(audioFocusStrategy.resumeAfterInterruption) {
                        wasPlayingBeforeEnablePlayChange?.let {
                            //phone call ended
                            if (it) {
                                playerPlay()
                            } else {
                                pause()
                            }
                        }
                    }
                    volumeBeforePhoneStateChanged?.let {
                        setVolume(it)
                    }
                    wasPlayingBeforeEnablePlayChange = null
                    volumeBeforePhoneStateChanged = null
                }
                StopWhenCall.AudioState.REDUCE_VOLUME -> {
                    volumeBeforePhoneStateChanged = this.volume
                    setVolume(VOLUME_WHEN_REDUCED)
                    this.isEnabledToChangeVolume = false //this one must be called after setVolume()
                }
                StopWhenCall.AudioState.FORBIDDEN -> {
                    wasPlayingBeforeEnablePlayChange = this.isPlaying
                    pause()
                    this.isEnabledToPlayPause = false //this one must be called after pause()
                }
            }
        }
    }

    fun askPlayOrPause() {
        this.onNotificationPlayOrPause?.invoke()
    }

    fun askStop() {
        this.onNotificationStop?.invoke()
    }

    fun onHeadsetPlugged(plugged: Boolean) {
        if(plugged){
            when(this.headsetStrategy){
                HeadsetStrategy.pauseOnUnplug -> { /* do nothing */}
                HeadsetStrategy.pauseOnUnplugPlayOnPlug -> {
                    if(!isPlaying) {
                        this.onNotificationPlayOrPause?.invoke()
                    }
                }
                else -> { /* do nothing */ }
            }
        } else {
            when(this.headsetStrategy){
                HeadsetStrategy.pauseOnUnplug, HeadsetStrategy.pauseOnUnplugPlayOnPlug  -> {
                    if(isPlaying) {
                        this.onNotificationPlayOrPause?.invoke()
                    }
                }
                else -> { /* do nothing */ }
            }
        }
    }

    // Implement the abstract getApplicationLooper() method from androidx.media3.common.Player
    override fun getApplicationLooper(): Looper {
        // Since your player logic seems to run on the main thread,
        // return the looper of the main thread.
        return Looper.getMainLooper()
    }

    // Maintain a list of registered Media3 Player.Listener instances
    private val media3Listeners = mutableListOf<Player.Listener>()

    // Implement the abstract addListener method from androidx.media3.common.Player
    override fun addListener(listener: Player.Listener) {
        // Add the listener to your list
        media3Listeners.add(listener)
    }

    // Implement the abstract removeListener method from androidx.media3.common.Player
    override fun removeListener(listener: Player.Listener) {
        // Remove the listener from your list
        media3Listeners.remove(listener)
    }

    // Example of how to notify listeners when playback state changes:
    // You would call this from your internal playback logic whenever the state changes
    fun notifyPlaybackStateChanged(playbackState: Int) {
        for (listener in media3Listeners) {
            listener.onPlaybackStateChanged(playbackState)
        }
    }

    // Example of how to notify listeners when there's a player error:
    // You would call this from your internal error handling logic
    fun notifyPlayerError(error: PlaybackException) {
        for (listener in media3Listeners) {
            listener.onPlayerError(error)
        }
    }

    // Implement the abstract setMediaItem method from androidx.media3.common.Player
    override fun setMediaItem(mediaItem: MediaItem) {
        // Create a list with the single media item and call setMediaItems
        setMediaItems(mutableListOf(mediaItem))
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare() // Or call prepare in the setMediaItems method you call
    }

    // This one takes a single MediaItem and a startPositionMs
    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        // Create a list with the single media item and call setMediaItems
        // Use startIndex 0 for a single item
        setMediaItems(mutableListOf(mediaItem), 0, startPositionMs)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare() // Or call prepare in the setMediaItems method you call
    }

    // This one takes a single MediaItem and a resetPosition
    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        // Create a list with the single media item and call setMediaItems
        setMediaItems(mutableListOf(mediaItem), resetPosition)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare() // Or call prepare in the setMediaItems method you call
    }

    // Implement the abstract setMediaItems method from androidx.media3.common.Player
    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        // Pass the list of MediaItems to the underlying ExoPlayer
        mediaPlayer?.setMediaItems(mediaItems)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    // This one takes the list of MediaItems AND the resetPosition boolean
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        mediaPlayer?.setMediaItems(mediaItems, resetPosition)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    // This one takes the list of MediaItems, startIndex, and startPositionMs
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        // Pass the list of MediaItems, startIndex, and startPositionMs to the underlying ExoPlayer
        mediaPlayer?.setMediaItems(mediaItems, startIndex, startPositionMs)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        // Create a list with the single media item and call addMediaItems
        mediaPlayer?.addMediaItem(mediaItem)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare() // Or call prepare in the setMediaItems method you call
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        // Create a list with the single media item and call addMediaItems
        mediaPlayer?.addMediaItem(index, mediaItem)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare() // Or call prepare in the setMediaItems method you call
    }

    // Implement the abstract addMediaItems method from androidx.media3.common.Player
    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        // Pass the list of MediaItems to the underlying ExoPlayer
        mediaPlayer?.addMediaItems(mediaItems)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    // This one takes the list of MediaItems AND the resetPosition boolean
    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        mediaPlayer?.addMediaItems(index, mediaItems)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        mediaPlayer?.moveMediaItem(currentIndex, newIndex)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        mediaPlayer?.moveMediaItems(fromIndex, toIndex, newIndex)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        mediaPlayer?.replaceMediaItem(index, mediaItem)
        // Optionally call prepare() here if you want the player to prepare immediately
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        mediaPlayer?.replaceMediaItems(fromIndex, toIndex, mediaItems)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun removeMediaItem(index: Int) {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        mediaPlayer?.removeMediaItem(index)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        mediaPlayer?.removeMediaItems(fromIndex, toIndex)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun clearMediaItems() {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        mediaPlayer?.clearMediaItems()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getMediaItemCount(): Int {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        return mediaPlayer?.mediaItemCount ?: 0
    }

    override fun isCommandAvailable(command: Int): Boolean {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        return mediaPlayer?.isCommandAvailable(command) ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun canAdvertiseSession(): Boolean {
        return false
    }

    override fun getAvailableCommands(): Player.Commands {
        return mediaPlayer?.getAvailableCommands(Player.Commands) ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    //Class 'Player' is not abstract and does not implement abstract member public
    //abstract fun prepare(): Unit defined in androidx. media3.common. Player
    override fun prepare() {
        // Pass the list of MediaItems and the resetPosition to the underlying ExoPlayer
        mediaPlayer?.prepare()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getPlaybackState(): Int {
        return mediaPlayer?.playbackState ?: Player.STATE_IDLE
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getPlaybackSuppressionReason(): Int {
        return mediaPlayer?.playbackSuppressionReason ?: Player.PLAYBACK_SUPPRESSION_REASON_NONE
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun  isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getPlayerError(): PlaybackException? {
        return mediaPlayer?.playerError
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean): Unit {
        mediaPlayer?.setPlayWhenReady(playWhenReady)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override  fun getPlayWhenReady(): Boolean {
        return mediaPlayer?.playWhenReady ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setRepeatMode(repeatMode: Int): Unit {
        mediaPlayer?.setRepeatMode(repeatMode)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getRepeatMode(): Int {
        return mediaPlayer?.repeatMode ?: Player.REPEAT_MODE_OFF
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean): Unit {
        mediaPlayer?.setShuffleModeEnabled(shuffleModeEnabled)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getShuffleModeEnabled(): Boolean {
        return mediaPlayer?.shuffleModeEnabled ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun isLoading(): Boolean {
        return mediaPlayer?.isLoading ?: false
        // Optionally call prepare() here if you want the player to prepare immediately

    }

    override fun seekToDefaultPosition(): Unit {
        mediaPlayer?.seekToDefaultPosition()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int): Unit {
        mediaPlayer?.seekToDefaultPosition(mediaItemIndex)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun seekTo(positionMs: Long): Unit {
        mediaPlayer?.seekTo(positionMs)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long): Unit {
        mediaPlayer?.seekTo(mediaItemIndex, positionMs)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override  fun getSeekBackIncrement(): Long {
        return mediaPlayer?.seekBackIncrement ?: 0L
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun seekBack(): Unit {
        mediaPlayer?.seekBack()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getSeekForwardIncrement(): Long {
        return mediaPlayer?.seekForwardIncrement ?: 0L
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun seekForward(): Unit {
        mediaPlayer?.seekForward()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun hasPreviousMediaItem(): Boolean {
        return mediaPlayer?.hasPreviousMediaItem() ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }


    override fun seekToPreviousWindow(): Unit {
        mediaPlayer?.seekToPreviousWindow()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun seekToNextWindow(): Unit {
        mediaPlayer?.seekToNextWindow()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override  fun seekToPreviousMediaItem(): Unit {
        mediaPlayer?.seekToPreviousMediaItem()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getMaxSeekToPreviousPosition(): Long {
        return mediaPlayer?.maxSeekToPreviousPosition ?: 0L
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun seekToPrevious(): Unit {
        mediaPlayer?.seekToPrevious()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun hasNext(): Boolean {
        return mediaPlayer?.hasNext() ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

   override fun hasNextWindow(): Boolean {
        return mediaPlayer?.hasNextWindow() ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
   }

    override fun hasNextMediaItem(): Boolean {
        return mediaPlayer?.hasNextMediaItem() ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun seekToNextMediaItem(): Unit {
        mediaPlayer?.seekToNextMediaItem()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun seekToNext(): Unit {
        mediaPlayer?.seekToNext()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters): Unit {
        mediaPlayer?.setPlaybackParameters(playbackParameters)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setPlaybackSpeed(speed: Float): Unit {
        mediaPlayer?.setPlaybackSpeed(speed)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return mediaPlayer?.playbackParameters ?: PlaybackParameters()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun release(): Unit {
        mediaPlayer?.release()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentTracks(): Tracks {
        return mediaPlayer?.currentTracks ?: Tracks()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        return mediaPlayer?.trackSelectionParameters ?: TrackSelectionParameters()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters): Unit {
        mediaPlayer?.setTrackSelectionParameters(parameters)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getMediaMetadata(): MediaMetadata {
        return mediaPlayer?.mediaMetadata ?: MediaMetadata()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        return mediaPlayer?.playlistMetadata ?: MediaMetadata()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata): Unit {
        mediaPlayer?.setPlaylistMetadata(mediaMetadata)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentManifest(): Any? {
        return mediaPlayer?.currentManifest
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentTimeline(): Timeline {
        return mediaPlayer?.currentTimeline ?: Timeline()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentPeriodIndex(): Int {
        return mediaPlayer?.currentPeriodIndex ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentWindowIndex(): Int {
        return mediaPlayer?.currentWindowIndex ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentMediaItemIndex(): Int {
        return mediaPlayer?.currentMediaItemIndex ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getNextWindowIndex(): Int {
        return mediaPlayer?.nextWindowIndex ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getNextMediaItemIndex(): Int {
        return mediaPlayer?.nextMediaItemIndex ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getPreviousWindowIndex(): Int {
        return mediaPlayer?.previousWindowIndex ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getPreviousMediaItemIndex(): Int {
        return mediaPlayer?.previousMediaItemIndex ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentMediaItem(): MediaItem? {
        return mediaPlayer?.currentMediaItem
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        return mediaPlayer?.getMediaItemAt(index) ?: MediaItem()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getDuration(): Long {
        return mediaPlayer?.duration ?: 0L
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentPosition(): Long {
        return mediaPlayer?.currentPosition ?: 0L
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getBufferedPosition(): Long {
        return mediaPlayer?.bufferedPosition ?: 0L
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getBufferedPercentage(): Int {
        return mediaPlayer?.bufferedPercentage ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getTotalBufferedDuration(): Long {
        return mediaPlayer?.totalBufferedDuration ?: 0L
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun isCurrentWindowDynamic(): Boolean {
        return mediaPlayer?.isCurrentWindowDynamic ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun isCurrentMediaItemDynamic(): Boolean {
        return mediaPlayer?.isCurrentMediaItemDynamic ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun isCurrentWindowLive(): Boolean {
        return mediaPlayer?.isCurrentWindowLive ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun isCurrentMediaItemLive(): Boolean {
        return mediaPlayer?.isCurrentMediaItemLive ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentLiveOffset(): Long {
        return mediaPlayer?.currentLiveOffset ?: 0L
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun isCurrentWindowSeekable(): Boolean {
        return mediaPlayer?.isCurrentWindowSeekable ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun isCurrentMediaItemSeekable(): Boolean {
        return mediaPlayer?.isCurrentMediaItemSeekable ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun isPlayingAd(): Boolean {
        return mediaPlayer?.isPlayingAd ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentAdGroupIndex(): Int {
        return mediaPlayer?.currentAdGroupIndex ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        return mediaPlayer?.currentAdIndexInAdGroup ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getContentDuration(): Long {
        return mediaPlayer?.contentDuration ?: 0L
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getContentPosition(): Long {
        return mediaPlayer?.contentPosition ?: 0L
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getContentBufferedPosition(): Long {
        return mediaPlayer?.contentBufferedPosition ?: 0L
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getAudioAttributes(): AudioAttributes {
        return mediaPlayer?.audioAttributes ?: AudioAttributes()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getVolume(): Float {
        return mediaPlayer?.volume ?: 0f
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun clearVideoSurface(): Unit {
        mediaPlayer?.clearVideoSurface()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun clearVideoSurface(surface: Surface?): Unit {
        mediaPlayer?.clearVideoSurface(surface)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setVideoSurface(surface: Surface?): Unit {
        mediaPlayer?.setVideoSurface(surface)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?): Unit {
        mediaPlayer?.setVideoSurfaceHolder(surfaceHolder)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?): Unit {
        mediaPlayer?.clearVideoSurfaceHolder(surfaceHolder)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?): Unit {
        mediaPlayer?.setVideoSurfaceView(surfaceView)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?): Unit {
        mediaPlayer?.clearVideoSurfaceView(surfaceView)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setVideoTextureView(textureView: TextureView?): Unit {
        mediaPlayer?.setVideoTextureView(textureView)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun clearVideoTextureView(textureView: TextureView?): Unit {
        mediaPlayer?.clearVideoTextureView(textureView)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getVideoSize(): VideoSize {
        return mediaPlayer?.videoSize ?: VideoSize()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getSurfaceSize(): Size {
        return mediaPlayer?.surfaceSize ?: Size()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getCurrentCues(): CueGroup {
        return mediaPlayer?.currentCues ?: CueGroup()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getDeviceInfo(): DeviceInfo {
        return mediaPlayer?.deviceInfo ?: DeviceInfo()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun getDeviceVolume(): Int {
        return mediaPlayer?.deviceVolume ?: 0
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun isDeviceMuted(): Boolean {
        return mediaPlayer?.isDeviceMuted ?: false
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setDeviceVolume(volume: Int): Unit {
        mediaPlayer?.setDeviceVolume(volume)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setDeviceVolume(volume: Int, flags: Int): Unit {
        mediaPlayer?.setDeviceVolume(volume, flags)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override  fun increaseDeviceVolume(): Unit {
        mediaPlayer?.increaseDeviceVolume()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun increaseDeviceVolume(flags: Int): Unit {
        mediaPlayer?.increaseDeviceVolume(flags)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun decreaseDeviceVolume(): Unit {
        mediaPlayer?.decreaseDeviceVolume()
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun decreaseDeviceVolume(flags: Int): Unit {
        mediaPlayer?.decreaseDeviceVolume(flags)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setDeviceMuted(muted: Boolean): Unit {
        mediaPlayer?.setDeviceMuted(muted)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int): Unit {
        mediaPlayer?.setDeviceMuted(muted, flags)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean): Unit {
        mediaPlayer?.setAudioAttributes(audioAttributes, handleAudioFocus)
        // Optionally call prepare() here if you want the player to prepare immediately
        // mediaPlayer?.prepare()
    }
}

class ForwardHandler : Handler() {

    companion object {
        const val MESSAGE_FORWARD = 1
        const val DELAY = 300L
    }

    private var player: com.github.florent37.assets_audio_player.Player? = null
    private var speed: Double = 1.0

    val isActive: Boolean
        get() = hasMessages(MESSAGE_FORWARD)

    fun start(player: com.github.florent37.assets_audio_player.Player, speed: Double) {
        this.player = player
        this.speed = speed
        removeMessages(MESSAGE_FORWARD)
        sendEmptyMessage(MESSAGE_FORWARD)
    }

    fun stop() {
        removeMessages(MESSAGE_FORWARD)
        this.player = null
    }

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        if (msg.what == MESSAGE_FORWARD) {
            this.player?.let {
                it.seekBy((DELAY * speed).toLong())
                sendEmptyMessageDelayed(MESSAGE_FORWARD, DELAY)
            }
        }
    }
}










