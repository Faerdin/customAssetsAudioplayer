package com.github.florent37.assets_audio_player.playerimplem

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.github.florent37.assets_audio_player.AssetAudioPlayerThrowable
import com.github.florent37.assets_audio_player.AssetsAudioPlayerPlugin
import com.github.florent37.assets_audio_player.Player
//Eirik 20.03.25: The new androidx.media3 libraries, specified those to use instead of ".*"
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi //Added to not have an error
import androidx.media3.datasource.AssetDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ExoPlayer.Builder
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.SimpleExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.AdtsExtractor

// Eirik 20.03.25: The constants
import androidx.media3.common.C.AUDIO_SESSION_ID_UNSET
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF

import io.flutter.embedding.engine.plugins.FlutterPlugin
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class IncompatibleException(val audioType: String, val type: PlayerImplemTesterExoPlayer.Type) : Throwable()

class PlayerImplemTesterExoPlayer(private val type: Type) : PlayerImplemTester {

    enum class Type {
        Default,
        HLS,
        DASH,
        SmoothStreaming
    }

    override suspend fun open(configuration: PlayerFinderConfiguration) : PlayerFinder.PlayerWithDuration {
        if(AssetsAudioPlayerPlugin.displayLogs) {
            Log.d("PlayerImplem", "trying to open with exoplayer($type)")
        }
        //some type are only for web
        //if(configuration.audioType != Player.AUDIO_TYPE_LIVESTREAM && configuration.audioType != Player.AUDIO_TYPE_LIVESTREAM){
        if(configuration.audioType != Player.AUDIO_TYPE_NETWORK && configuration.audioType != Player.AUDIO_TYPE_LIVESTREAM){
            if(type == Type.HLS || type == Type.DASH || type == Type.SmoothStreaming) {
                throw IncompatibleException(configuration.audioType, type)
            }
        }

        val mediaPlayer = PlayerImplemExoPlayer(
                onFinished = {
                    configuration.onFinished?.invoke()
                    //stop(pingListener = false)
                },
                onBuffering = {
                    configuration.onBuffering?.invoke(it)
                },
                onError = { t ->
                    configuration.onError?.invoke(t)
                },
                type = this.type
        )

        try {
            val durationMS = mediaPlayer.open( // Call open on the PlayerImplemExoPlayer instance
                    context = configuration.context,
                    assetAudioPath = configuration.assetAudioPath,
                    audioType = configuration.audioType,
                    assetAudioPackage = configuration.assetAudioPackage,
                    networkHeaders = configuration.networkHeaders,
                    flutterAssets = configuration.flutterAssets,
                    drmConfiguration = configuration.drmConfiguration
            )
            return PlayerFinder.PlayerWithDuration(
                    player = mediaPlayer, // Return the PlayerImplemExoPlayer instance
                    duration = durationMS
            )
        } catch (t: Throwable) {
            if(AssetsAudioPlayerPlugin.displayLogs) {
                Log.d("PlayerImplem", "failed to open with exoplayer($type)")
            }
            mediaPlayer.release() // Call release on the PlayerImplemExoPlayer instance
            throw  t
        }
    }
}

class PlayerImplemExoPlayer(
        onFinished: (() -> Unit),
        onBuffering: ((Boolean) -> Unit),
        onError: ((AssetAudioPlayerThrowable) -> Unit),
        val type: PlayerImplemTesterExoPlayer.Type
) : PlayerImplem(
        onFinished = onFinished,
        onBuffering = onBuffering,
        onError = onError
) {

    // Make the ExoPlayer instance public
    var exoPlayerInstance: ExoPlayer? = null
        private set // Allow assignment only within this class
    //private var mediaPlayer: ExoPlayer? = null

    override var loopSingleAudio: Boolean
        //get() = mediaPlayer?.repeatMode == REPEAT_MODE_ALL
        get() = exoPlayerInstance?.repeatMode == REPEAT_MODE_ALL
        set(value) {
            //mediaPlayer?.repeatMode = if (value) REPEAT_MODE_ALL else REPEAT_MODE_OFF
            exoPlayerInstance?.repeatMode = if (value) REPEAT_MODE_ALL else REPEAT_MODE_OFF
        }

    override val isPlaying: Boolean
        //get() = mediaPlayer?.isPlaying ?: false
        get() = exoPlayerInstance?.isPlaying ?: false
    override val currentPositionMs: Long
        //get() = mediaPlayer?.currentPosition ?: 0
        get() = exoPlayerInstance?.currentPosition ?: 0

    override fun stop() {
        //mediaPlayer?.stop()
        exoPlayerInstance?.stop()
    }

    override fun play() {
        //mediaPlayer?.playWhenReady = true
        exoPlayerInstance?.playWhenReady = true
    }

    override fun pause() {
        //mediaPlayer?.playWhenReady = false
        exoPlayerInstance?.playWhenReady = false
    }

    @UnstableApi
    private fun getDataSource(context: Context,
                      flutterAssets: FlutterPlugin.FlutterAssets,
                      assetAudioPath: String?,
                      audioType: String,
                      networkHeaders: Map<*, *>?,
                      assetAudioPackage: String?,
                      drmConfiguration: Map<*, *>?
    ): MediaSource {
        try {
            // Use exoPlayerInstance here as well
            //mediaPlayer?.stop()
            exoPlayerInstance?.stop()
            if (audioType == Player.AUDIO_TYPE_NETWORK || audioType == Player.AUDIO_TYPE_LIVESTREAM) {
                val uri = Uri.parse(assetAudioPath)
                val userAgent = "assets_audio_player"

                val factory = DataSource.Factory {
                    val allowCrossProtocol = true

                    // Use DefaultHttpDataSource.Factory (Builder) instead of direct constructor
                    //val dataSource = DefaultHttpDataSource(userAgent, DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS, DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, allowCrossProtocol, null)

                    val dataSourceFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent(userAgent)
                        .setAllowCrossProtocolRedirects(allowCrossProtocol)
                        // You might need to set connect/read timeouts on the factory if needed
                        // .setConnectTimeoutMs(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS)
                        // .setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS)

                    // Add headers using the setRequestProperty method on the created DataSource
                    val dataSource = dataSourceFactory.createDataSource()
                    networkHeaders?.forEach {
                        it.key?.let { key ->
                            it.value?.let { value ->
                                dataSource.setRequestProperty(key.toString(), value.toString())
                            }
                        }
                    }
                    dataSource
                }

                return when(type){
                    PlayerImplemTesterExoPlayer.Type.HLS -> HlsMediaSource.Factory(factory).setAllowChunklessPreparation(true)
                    PlayerImplemTesterExoPlayer.Type.DASH -> DashMediaSource.Factory(factory)
                    PlayerImplemTesterExoPlayer.Type.SmoothStreaming -> SsMediaSource.Factory(factory)
                    else -> ProgressiveMediaSource.Factory(factory, DefaultExtractorsFactory().setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING))
                //}.createMediaSource(uri)
                }.createMediaSource(MediaItem.fromUri(uri))
            } else if (audioType == Player.AUDIO_TYPE_FILE) {

                val factory = ProgressiveMediaSource
                        .Factory(DefaultDataSourceFactory(context, "assets_audio_player"), DefaultExtractorsFactory())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    val key = drmConfiguration?.get("clearKey")?.toString()

                    if (key != null) {
                        val sessionManager: DrmSessionManager =
                            DefaultDrmSessionManager.Builder().setUuidAndExoMediaDrmProvider(
                            //C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER).build(LocalMediaDrmCallback(key.toByteArray()))
                            C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER).build(LocalMediaDrmCallback(key.toByteArray()))
                        //factory.setDrmSessionManager(sessionManager)
                        factory.setDrmSessionManagerProvider { sessionManager }
                    }
                }

                //return factory.createMediaSource(Uri.fromFile(File(assetAudioPath)))
                return factory.createMediaSource(MediaItem.fromUri(Uri.fromFile(File(assetAudioPath))))
            } else { //asset$
                val p = assetAudioPath!!.replace(" ", "%20")
                val path = if (assetAudioPackage.isNullOrBlank()) {
                    flutterAssets.getAssetFilePathByName(p)
                } else {
                    flutterAssets.getAssetFilePathByName(p, assetAudioPackage)
                }
                val assetDataSource = AssetDataSource(context)
                val fileUri = "file://$path".toUri()
                assetDataSource.open(DataSpec(fileUri))
                //assetDataSource.open(DataSpec(Uri.fromFile(File(path))))

                val factory = DataSource.Factory { assetDataSource }
                return ProgressiveMediaSource
                        .Factory(factory, DefaultExtractorsFactory())
                        .createMediaSource(MediaItem.fromUri(assetDataSource.uri!!))
            }
        } catch (e: Exception) {
            throw e
        }
    }

    //private fun SimpleExoPlayer.Builder.incrementBufferSize(audioType: String): SimpleExoPlayer.Builder {
    @UnstableApi
    private fun ExoPlayer.Builder.incrementBufferSize(audioType: String): ExoPlayer.Builder {
        if (audioType == Player.AUDIO_TYPE_NETWORK || audioType == Player.AUDIO_TYPE_LIVESTREAM) {
            /* Instantiate a DefaultLoadControl.Builder. */
            val loadControlBuilder = DefaultLoadControl.Builder()

/*How many milliseconds of media data to buffer at any time. */
            val loadControlBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS /* This is 50000 milliseconds in ExoPlayer 2.9.6 */

/* Configure the DefaultLoadControl to use the same value for */
            loadControlBuilder.setBufferDurationsMs(
                    loadControlBufferMs,
                    loadControlBufferMs,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)

            //return this.setLoadControl(loadControlBuilder.createDefaultLoadControl())
            return this.setLoadControl(loadControlBuilder.build())
        }
        return this
    }

    fun mapError(t: Throwable) : AssetAudioPlayerThrowable {
        return when {
            t is ExoPlaybackException -> {
                (t.cause as? HttpDataSource.InvalidResponseCodeException)?.takeIf { it.responseCode >= 400 }?.let {
                    AssetAudioPlayerThrowable.UnreachableException(t)
                } ?: let {
                    AssetAudioPlayerThrowable.NetworkError(t)
                }
            }
            t.message?.contains("unable to connect",true) == true -> {
                AssetAudioPlayerThrowable.NetworkError(t)
            }
            else -> {
                AssetAudioPlayerThrowable.PlayerError(t)
            }
        }
    }

    @UnstableApi
    override suspend fun open(
            context: Context,
            flutterAssets: FlutterPlugin.FlutterAssets,
            assetAudioPath: String?,
            audioType: String,
            networkHeaders: Map<*, *>?,
            assetAudioPackage: String?,
            drmConfiguration: Map<*, *>?
    ) = suspendCoroutine<DurationMS> { continuation ->
        var onThisMediaReady = false

        try {
            // Use the public exoPlayerInstance property
            //exoPlayerInstance = SimpleExoPlayer.Builder(context)
            exoPlayerInstance = ExoPlayer.Builder(context)
                .incrementBufferSize(audioType)
                .build()

            //mediaPlayer = SimpleExoPlayer.Builder(context)
            //        .incrementBufferSize(audioType)
            //        .build()

            val mediaSource = getDataSource(
                    context = context,
                    flutterAssets = flutterAssets,
                    assetAudioPath = assetAudioPath,
                    audioType = audioType,
                    networkHeaders = networkHeaders,
                    assetAudioPackage = assetAudioPackage,
                    drmConfiguration = drmConfiguration
            )

            var lastState: Int? = null

            // Use the public exoPlayerInstance property for adding listeners
            //this.mediaPlayer?.addListener(object : androidx.media3.common.Player.Listener {
            this.exoPlayerInstance?.addListener(object : androidx.media3.common.Player.Listener {

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val errorMapped = mapError(error)
                    if (!onThisMediaReady) {
                        continuation.resumeWithException(errorMapped)
                    } else {
                        onError(errorMapped)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (lastState != playbackState) {
                        when (playbackState) {
                            ExoPlayer.STATE_ENDED -> {
                                pause()
                                onFinished.invoke()
                                onBuffering.invoke(false)
                            }
                            ExoPlayer.STATE_BUFFERING -> {
                                onBuffering.invoke(true)
                            }
                            ExoPlayer.STATE_READY -> {
                                onBuffering.invoke(false)
                                if (!onThisMediaReady) {
                                    onThisMediaReady = true
                                    //retrieve duration in seconds
                                    if (audioType == Player.AUDIO_TYPE_LIVESTREAM) {
                                        continuation.resume(0) //no duration for livestream
                                    } else {
                                        //val duration = mediaPlayer?.duration ?: 0
                                        val duration = exoPlayerInstance?.duration ?: 0 // Use exoPlayerInstance
                                        val totalDurationMs = (duration.toLong())

                                        continuation.resume(totalDurationMs)
                                    }
                                }
                            }
                            else -> {
                            }
                        }
                    }
                    lastState = playbackState
                }
            })

            // Use the public exoPlayerInstance property for preparing
            //mediaPlayer?.prepare(mediaSource)
            exoPlayerInstance?.prepare(mediaSource)
        } catch (error: Throwable) {
            if (!onThisMediaReady) {
                continuation.resumeWithException(error)
            } else {
                onBuffering.invoke(false)
                onError(mapError(error))
            }
        }
    }

    override fun release() {
        //mediaPlayer?.release()
        exoPlayerInstance?.release()
        exoPlayerInstance = null // Set to null after releasing
    }

    override fun seekTo(to: Long) {
        //mediaPlayer?.seekTo(to)
        exoPlayerInstance?.seekTo(to) // Use exoPlayerInstance
    }

    override fun setVolume(volume: Float) {
        //mediaPlayer?.audioComponent?.volume = volume
        exoPlayerInstance?.volume = volume // Use exoPlayerInstance
    }

    override fun setPlaySpeed(playSpeed: Float) {
        //val params: PlaybackParameters? = mediaPlayer?.getPlaybackParameters()
        val params: PlaybackParameters? = exoPlayerInstance?.getPlaybackParameters() // Use exoPlayerInstance
        if (params != null) {
            //mediaPlayer?.setPlaybackParameters(PlaybackParameters(playSpeed, params.pitch))
            exoPlayerInstance?.setPlaybackParameters(PlaybackParameters(playSpeed, params.pitch)) // Use exoPlayerInstance
        }
    }

    override fun setPitch(pitch: Float) {
        //val params: PlaybackParameters? = mediaPlayer?.getPlaybackParameters()
        val params: PlaybackParameters? = exoPlayerInstance?.getPlaybackParameters() // Use exoPlayerInstance
        if (params != null) {
            //mediaPlayer?.setPlaybackParameters(PlaybackParameters(params.speed, pitch))
            exoPlayerInstance?.setPlaybackParameters(PlaybackParameters(params.speed, pitch)) // Use exoPlayerInstance
        }
    }

    //Eirik 20.03.25: Replacement using androidx.media3
    @UnstableApi
    override fun getSessionId(listener: (Int) -> Unit) {
        //val id = mediaPlayer?.audioSessionId?.takeIf { it != AUDIO_SESSION_ID_UNSET }
        val id = exoPlayerInstance?.audioSessionId?.takeIf { it != AUDIO_SESSION_ID_UNSET } // Use exoPlayerInstance
        if (id != null) {
            listener(id)
        } else {
            val playerListener = object : androidx.media3.common.Player.Listener {
                override fun onAudioAttributesChanged(audioAttributes: androidx.media3.common.AudioAttributes) {
                    //val id = mediaPlayer?.audioSessionId
                    val id = exoPlayerInstance?.audioSessionId // Use exoPlayerInstance
                    if (id != null && id != AUDIO_SESSION_ID_UNSET) {
                        listener(id)
                        //mediaPlayer?.removeListener(this)
                        exoPlayerInstance?.removeListener(this) // Use exoPlayerInstance
                    }
                }
            }
            //mediaPlayer?.addListener(playerListener)
            exoPlayerInstance?.addListener(playerListener) // Use exoPlayerInstance
        }
    }
}