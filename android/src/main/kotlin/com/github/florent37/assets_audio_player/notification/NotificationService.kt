package com.github.florent37.assets_audio_player.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata // Media3 MediaMetadata
import androidx.media3.common.Player // Import Media3 Player
import androidx.media3.common.util.NotificationUtil // Media3 NotificationUtil for MediaStyle
import androidx.media3.session.MediaSession // Media3 MediaSession
import androidx.media3.session.MediaSessionService // Import MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.github.florent37.assets_audio_player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.media3.session.SessionToken // Import SessionToken
import androidx.media3.session.MediaController // Import MediaController for communicating with MediaSession
import com.github.florent37.assets_audio_player.AssetsAudioPlayerPlugin // Assuming access to the plugin instance

// Add the OptIn annotation for unstable Media3 APIs if needed,
// or mark specific usages with @androidx.media3.common.util.UnstableApi
// @OptIn(androidx.media3.common.util.UnstableApi::class)

// *** CHANGE 1: Inherit from MediaSessionService ***
class NotificationService : MediaSessionService() {

    private var mediaSession: MediaSession? = null // Instance of the Media3 MediaSession
    // *** CHANGE 2: Track the ID of the currently active player for the notification ***
    private var activePlayerId: String? = null

    // *** REMOVED: This direct access is no longer suitable.
    // The service needs a way to get a reference to your Player instance(s) managed by the plugin.
    // The logic for accessing players will be handled in the custom command processing.
    /*
    private val yourPlayerInstance: Player?
        get() = null // Replace with your actual player access logic
     */

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "assets_audio_player"
        const val MEDIA_SESSION_TAG = "assets_audio_player"

        // Keep existing constants for intents and manifest metadata
        const val EXTRA_PLAYER_ID = "playerId"
        const val EXTRA_NOTIFICATION_ACTION = "notificationAction"
        const val TRACK_ID = "trackID";

        const val manifestIcon = "assets.audio.player.notification.icon"
        const val manifestIconPlay = "assets.audio.player.notification.icon.play"
        const val manifestIconPause = "assets.audio.player.notification.icon.pause"
        const val manifestIconPrev = "assets.audio.player.notification.icon.prev"
        const val manifestIconNext = "assets.audio.player.notification.icon.next"
        const val manifestIconStop = "assets.audio.player.notification.icon.stop"

        // *** REMOVED: updateNotifMetaData is no longer needed here ***
        // The MediaSession automatically handles metadata updates based on the connected Player.
        /*
        fun updateNotifMetaData(context: Context,
                                durationMs: Long,
                                title: String? = null,
                                artist: String? = null,
                                album: String? = null
        ) {
           // ... (removed logic)
        }
        */

    }

    // *** CHANGE 3: Implement onGetSession() ***
    // This is required by MediaSessionService to provide the MediaSession to clients.
    override fun onGetSession(controllerInfo: androidx.media3.session.MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Initialize the MediaSession here. It will be linked to a player later.
        initializeMediaSession()
    }

    private fun initializeMediaSession() {
        // The MediaSession is created here. We don't link a player immediately
        // because the active player can change. The player will be set on the
        // MediaSession when a COMMAND_SET_ACTIVE_PLAYER is received.
        mediaSession = MediaSession.Builder(this, DummyPlayer()) // *** CHANGE 4: Use a DummyPlayer initially ***
            .setSessionActivity(createContentIntent()) // Optional: Set a PendingIntent to launch an Activity
            .setCallback(MediaSessionCallback()) // Implement the MediaSession.Callback
            .build()

        // The MediaSession is now created. The MediaSessionService automatically makes the session active.
    }

    // *** CHANGE 5: Refactor MediaSession.Callback to handle custom commands and delegate to the active player ***
    private inner class MediaSessionCallback : MediaSession.Callback {

        // Handle standard Media3 commands (play, pause, seek, skip)
        // These commands are typically sent from controllers (like the notification or media buttons)
        // and should be delegated to the currently active player.
        override fun onPlay(session: MediaSession, controller: androidx.media3.session.MediaSession.ControllerInfo): Int {
            getActivePlayer()?.play()
            return SessionResult.RESULT_SUCCESS
        }

        override fun onPause(session: MediaSession, controller: androidx.media3.session.MediaSession.ControllerInfo): Int {
            getActivePlayer()?.pause()
            return SessionResult.RESULT_SUCCESS
        }

        override fun onStop(session: MediaSession, controller: androidx.media3.session.MediaSession.ControllerInfo): Int {
            getActivePlayer()?.stop()
            // Consider stopping the service when playback stops for all players
            // This logic will be handled by the service managing multiple players.
            return SessionResult.RESULT_SUCCESS
        }

        override fun onSeekTo(session: MediaSession, controller: androidx.media3.session.MediaSession.ControllerInfo, position: Long): Int {
             getActivePlayer()?.seekTo(position)
            return SessionResult.RESULT_SUCCESS
        }

        override fun onSkipToNext(session: MediaSession, controller: androidx.media3.session.MediaSession.ControllerInfo): Int {
            // Delegate next command to your active player
            // getActivePlayer()?.seekToNext() // Assuming your player has a seekToNext method
            // Or implement your logic to move to the next track for the active player
            return SessionResult.RESULT_SUCCESS
        }

        override fun onSkipToPrevious(session: MediaSession, controller: androidx.media3.session.MediaSession.ControllerInfo): Int {
            // Delegate previous command to your active player
            // getActivePlayer()?.seekToPrevious() // Assuming your player has a seekToPrevious method
            // Or implement your logic to move to the previous track for the active player
            return SessionResult.RESULT_SUCCESS
        }

        // *** CHANGE 6: Implement onCustomCommand to handle commands from NotificationManager ***
        override fun onCustomCommand(session: MediaSession,
                                     controller: androidx.media3.session.MediaSession.ControllerInfo,
                                     customCommand: SessionCommand, args: Bundle?): SessionResult {
            when (customCommand.customAction) {
                Command.COMMAND_SET_ACTIVE_PLAYER -> {
                    val playerId = args?.getString(Command.EXTRA_PLAYER_ID)
                    if (playerId != null) {
                        activePlayerId = playerId
                        // *** CHANGE 7: Link the active player to the MediaSession ***
                        // Get the actual Player instance based on playerId and set it on the MediaSession.
                        // This requires a way to access your player instances from within the service.
                        // Example (assuming you have a function to get a player by ID):
                        val playerToLink = getPlayerById(playerId) // You need to implement getPlayerById
                        if (playerToLink != null) {
                            session.player = playerToLink
                            // Update MediaMetadata and PlaybackState immediately if needed
                            // (though setting the player should handle this automatically)
                            // updateMediaSessionState(playerToLink) // Optional, might be handled by setting the player
                        } else {
                            // Handle error: player not found
                        }

// *** CHANGE 8: Inform the plugin instance about the active notification player ***
                        // This replaces the line removed in the previous version of the code.
                        // The plugin instance needs to know which player is now controlling the notification.
                        AssetsAudioPlayerPlugin.instance?.onNotificationPlayerChanged(playerId)

                        return SessionResult.RESULT_SUCCESS // Indicate success
                    }
                    return SessionResult.RESULT_ERROR_BAD_VALUE // Indicate missing playerId
                }

                Command.COMMAND_UPDATE_PLAYER_STATE -> {
                    // *** CHANGE 11: Handle updating the state of the active player and notification ***
                    // This command is sent from the NotificationManager to update the UI/notification.
                    // We just need to ensure the MediaSession's linked player's state and metadata
                    // are current. The MediaSession and MediaStyle notification will automatically
                    // update when the underlying player's state changes.
                    // You might not need to do anything explicit here if your players
                    // are correctly updating the linked MediaSession.
                    // However, if you need to trigger a notification update explicitly,
                    // you could do it here.
                    val playerId = args?.getString(Command.EXTRA_PLAYER_ID)
                    val playerToUpdate = if (playerId == activePlayerId) session.player else getPlayerById(playerId ?: "")
                    if (playerToUpdate != null) {
                        // No need to manually update the notification here.
                        // The MediaSession will observe the player's state.
                        // Ensure your players are correctly reporting state changes.
                        // You might consider updating the MediaMetadata on the player
                        // if it has changed.
                    }
                    return SessionResult.RESULT_SUCCESS
                }

                Command.COMMAND_STOP_PLAYER -> {
                    // *** CHANGE 12: Handle stopping a specific player ***
                    val playerId = args?.getString(Command.EXTRA_PLAYER_ID)
                    if (playerId != null) {
                        val playerToStop = getPlayerById(playerId) // Get the player by ID
                        playerToStop?.stop() // Stop the specific player
                        playerToStop?.release() // Release resources if needed

                        // If the stopped player was the active one, unlink it from the MediaSession
                        if (playerId == activePlayerId) {
                            mediaSession?.player = DummyPlayer() // Unlink the player
                            activePlayerId = null // Clear the active player ID
                            stopForeground(true) // Stop foreground service and remove notification
                            stopSelf() // Stop the service if no players are active
                        }
                        // You might also need to remove the player from your internal tracking
                        // in the plugin instance.
                    }
                    return SessionResult.RESULT_SUCCESS
                }
                Command.COMMAND_HIDE_NOTIFICATION -> {
                    // *** CHANGE 13: Handle hiding the notification ***
                    hideNotif()
                    // You might also want to stop the service here if this command
                    // implies stopping playback altogether.
                    // stopSelf() // Consider stopping the service
                    return SessionResult.RESULT_SUCCESS
                }
                Command.COMMAND_STOP_SERVICE -> {
                    // *** CHANGE 14: Handle stopping the service entirely ***
                    stopSelf()
                    return SessionResult.RESULT_SUCCESS
                }
                else -> {
                    // Handle other custom commands if any
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            }
        }
    }

    // *** CHANGE 15: Implement a Player.Listener to observe the active player's state ***
    private val playerEventListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            // Handle playback state changes (playing, paused, buffered, etc.)
            // The MediaSession automatically reflects this.
            // You might add custom logic here if needed, e.g., updating UI in the plugin.
             when (playbackState) {
                 Player.STATE_IDLE -> {
                     // Player is idle, might consider stopping the service/notification
                     // hideNotif()
                     // stopSelf() // Consider stopping the service
                 }
                 Player.STATE_ENDED -> {
                     // Playback ended, consider stopping the service/notification
                      hideNotif()
                     stopSelf() // Stop the service
                 }
                 Player.STATE_BUFFERING -> {
                     // Buffering
                 }
                 Player.STATE_READY -> {
                     // Ready to play
                 }
             }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Handle playWhenReady changes
            // This reflects whether the player is supposed to play when ready.
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Handle isPlaying changes (true when actually playing audio)
            // This is a good place to update the notification if needed, though
            // the MediaSession linked to the player should handle this automatically.
            // You can still call displayNotification here if you need more control
            // over the notification appearance based on isPlaying state.
             if (isPlaying) {
                 // Player is playing, ensure the service is in the foreground
                 // The MediaSessionService should manage this, but you can reinforce it.
                 // startForeground(NOTIFICATION_ID, buildNotification(...)) // Rebuild and start foreground if needed
             } else {
                 // Player is paused or stopped, update the notification
                 // The MediaSessionService should manage this.
                 // If you need to stop the foreground service when paused:
                 // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                 //     stopForeground(STOP_FOREGROUND_DETACH) // Keep notification but stop foreground
                 // } else {
                 //    stopForeground(false) // Keep notification but stop foreground
                 // }
             }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // Handle metadata changes
            // The MediaSession automatically reflects this, updating the notification.
            // You might update UI in the plugin based on new metadata.
        }

         override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
             // Handle playback speed or pitch changes
         }

         override fun onPlayerError(error: PlaybackException) {
             // Handle player errors
             // You might want to stop playback, update the notification, or log the error.
         }

        // Implement other relevant Player.Listener methods as needed.
    }


    // *** CHANGE 16: Remove the manual onStartCommand processing of notification actions ***
    // The MediaSession and its Callback will handle media button intents.
    // You will still receive Intents to start the service, but the playback
    // control commands will go through the MediaSession.
    /*
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // Continue to handle your existing notification actions
        when (val notificationAction = intent.getSerializableExtra(EXTRA_NOTIFICATION_ACTION)) {
            is NotificationAction.Show -> {
                // The displayNotification logic needs to be updated to work with MediaSession
                // displayNotification(notificationAction)
            }
            is NotificationAction.Hide -> {
                hideNotif()
            }
        }
        // The MediaSession will now handle media button intents automatically.
        // You might not need to process them directly here anymore.

        return START_NOT_STICKY
    }
    */

    // *** CHANGE 17: Implement getActivePlayer() to retrieve the currently active Player instance ***
    // This is a placeholder. You need to implement the actual logic to get the Player
    // instance based on the activePlayerId. This will likely involve accessing the
    // player instances managed by your AssetsAudioPlayerPlugin.
    private fun getActivePlayer(): Player? {
        // TODO: Implement actual logic to get the active Player from the plugin
        // Example: return AssetsAudioPlayerPlugin.instance?.getPlayer(activePlayerId)
        return null // Replace with your implementation
    }

    // *** CHANGE 18: Implement getPlayerById() to retrieve a Player instance by ID ***
    // This is a placeholder. You need to implement the actual logic to retrieve
    // the correct Player instance from your plugin's management based on the provided ID.
    private fun getPlayerById(playerId: String): Player? {
        // Access the static instance of the plugin
        val pluginInstance = AssetsAudioPlayerPlugin.instance
        // Access the AssetsAudioPlayer instance from the plugin
        val assetsAudioPlayer = pluginInstance?.assetsAudioPlayer
        // Access the players map from the AssetsAudioPlayer instance and get the player by ID
        return assetsAudioPlayer?.getPlayers()?.get(playerId)
    }

/*
    private fun createReturnIntent(forAction: String, forPlayer: String, audioMetas: AudioMetas): Intent {
        return Intent(this, NotificationActionReceiver::class.java)
                .setAction(forAction)
                .putExtra(EXTRA_PLAYER_ID, forPlayer)
                .putExtra(TRACK_ID, audioMetas.trackID)
    }
*/
// *** CHANGE 19: Update displayNotification to build a notification compatible with MediaSession ***
    // You still need to build the notification here, but it will be linked to the MediaSession.
    // Media3's MediaStyleNotificationHelper is useful, or you can use NotificationCompat.MediaStyle.
    private fun displayNotification(notificationAction: NotificationAction.Show) {
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID).apply {
            // Set notification icon
            setSmallIcon(getResourceId(manifestIcon))

            // Set content title and text from notificationAction
            setContentTitle(notificationAction.title)
            setContentText(notificationAction.artist)
            setSubText(notificationAction.album)

            // Set large icon if available (handle bitmap loading asynchronously if needed)
            notificationAction.imageUri?.let { uri ->
                GlobalScope.launch(Dispatchers.IO) {
                    val bitmap = try {
                        // Assuming a helper function to load bitmap from URI
                        BitmapLoader.loadBitmapFromUri(this@NotificationService, uri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                    bitmap?.let {
                        // Update the notification with the bitmap
                        notificationBuilder.setLargeIcon(it)
                        NotificationManagerCompat.from(this@NotificationService)
                            .notify(NOTIFICATION_ID, notificationBuilder.build())
                    }
                }
/*
            val image = ImageDownloader.loadBitmap(context = applicationContext, imageMetas = action.audioMetas.image)
            if(image != null){
                displayNotification(action, image) //display with image
                return@launch
            }
            val imageOnLoadError = ImageDownloader.loadBitmap(context = applicationContext, imageMetas = action.audioMetas.imageOnLoadError)
            if(imageOnLoadError != null){
                displayNotification(action, imageOnLoadError) //display with error image
                return@launch
            }

            val imageFromManifest = ImageDownloader.loadHolderBitmapFromManifest(context = applicationContext)
            if(imageFromManifest != null){
                displayNotification(action, imageFromManifest) //display with manifest image
                return@launch
            }

            displayNotification(action, null) //display without image
 */

        }
    }
/*
    // Icon retrieval methods remain largely the same, assuming your drawable resources exist
    private fun getSmallIcon(context: Context, resourceName: String?): Int {
        // Keep your existing logic for getting the small icon
        return getCustomIconOrDefault(context, manifestIcon, context.resources.getIdentifier(
            "exo_icon_small", "drawable", "androidx.media3.ui"), null)
    }

    private fun getPlayIcon(context: Context, resourceName: String?): Int {
        // Keep your existing logic for getting the play icon
        return getCustomIconOrDefault(context, manifestIconPlay, context.resources.getIdentifier(
            "exo_icon_play", "drawable", "androidx.media3.ui"), resourceName)
    }

    private fun getPauseIcon(context: Context, resourceName: String?): Int {
        // Keep your existing logic for getting the pause icon
        return getCustomIconOrDefault(context, manifestIconPause, context.resources.getIdentifier(
            "exo_icon_pause", "drawable", "androidx.media3.ui"), resourceName)
    }

    private fun getNextIcon(context: Context, resourceName: String?): Int {
        // Keep your existing logic for getting the next icon
        return getCustomIconOrDefault(context, manifestIconNext, context.resources.getIdentifier(
            "exo_icon_next", "drawable", "androidx.media3.ui"), resourceName)
    }

    private fun getPrevIcon(context: Context, resourceName: String?): Int {
        // Keep your existing logic for getting the previous icon
        return getCustomIconOrDefault(context, manifestIconPrev, context.resources.getIdentifier(
            "exo_icon_previous", "drawable", "androidx.media3.ui"), resourceName)
    }

    private fun getStopIcon(context: Context, resourceName: String?): Int {
        // Keep your existing logic for getting the stop icon
        return getCustomIconOrDefault(context, manifestIconStop, context.resources.getIdentifier(
            "exo_icon_stop", "drawable", "androidx.media3.ui"), resourceName)
    }
*/
            // *** CHANGE 20: Use MediaStyle for playback controls and metadata display ***
            // Link the notification to the MediaSession
            setStyle(
                androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession!!)
                // Or using NotificationCompat.MediaStyle directly:
                // NotificationCompat.MediaStyle().setMediaSession(mediaSession!!.sessionCompatToken) // If using Support Library MediaSession
                    .setShowActionsInCompactView(0, 1, 2) // Indices of actions to show in compact view (e.g., Prev, Play/Pause, Next)
            )
/*
    private fun getCustomIconOrDefault(context: Context, manifestName: String, defaultIcon: Int, resourceName: String? = null): Int {
        try {
            // by resource name
            if (!resourceName.isNullOrEmpty()){
                val customIconFromName = context.resources.getIdentifier(resourceName, "drawable", context.packageName)

                if (customIconFromName != 0) {
                    return customIconFromName
                }
            }

            //by manifest
            val appInfos = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            val customIconFromManifest = appInfos.metaData.get(manifestName) as? Int
            if (customIconFromManifest != null) {
                return customIconFromManifest
            }
        } catch (t: Throwable) {
            //print(t)
        }

        //if customIconFromName is null or customIconFromManifest is null
        return defaultIcon
    }
*/

            // *** CHANGE 21: Add actions for playback control (linked to MediaSession) ***
            // Media3 automatically handles play/pause, next, previous actions if your Player supports them.
            // You might still need custom actions for stop or other commands.

            // Example of adding a custom Stop action:
            addAction(
                getIconResourceId(manifestIconStop),
                "Stop",
                getStopPendingIntent() // You need to create this PendingIntent
            )
            // Media3 will automatically add default play/pause, next, previous actions
            // based on the Player capabilities linked to the MediaSession.

            // Set click intent for the notification
            setContentIntent(createContentIntent())

            // Allow the notification to be dismissed
            setOngoing(false)

            // Set visibility for lock screen controls
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            // *** CHANGE 22: Set MediaMetadata on the MediaSession's Player ***
            // The notification automatically picks up metadata from the linked Player.
            // Update the Player's metadata when it changes.
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(notificationAction.title)
                .setArtist(notificationAction.artist)
                .setAlbumTitle(notificationAction.album)
                .setArtworkUri(notificationAction.imageUri)
                .setDurationMs(notificationAction.durationMs ?: C.TIME_UNSET)
                .build()
            getActivePlayer()?.setMediaMetadata(mediaMetadata)

            // *** CHANGE 23: Set PlaybackState on the MediaSession's Player ***
            // The notification automatically picks up playback state from the linked Player.
            // Ensure your Player correctly reports its state.
            // The Player.Listener onPlaybackStateChanged and onIsPlayingChanged will help with this.

        }

        // Start the service in the foreground with the notification
        // *** CHANGE 24: Use startForeground() with the MediaSessionService's API ***
        // MediaSessionService has a built-in way to handle foreground service.
        // You call setForegroundNotification() and the service manages the rest.
        // You might still need to call startService() initially to start the service.
        setNotification(NOTIFICATION_ID, notificationBuilder.build())

        // If you are calling this from a place where the service might not be running foreground
        // you might need to explicitly start it:
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             startForeground(NOTIFICATION_ID, notificationBuilder.build(), Notification.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
         } else {
             startForeground(NOTIFICATION_ID, notificationBuilder.build())
         }

        // *** REMOVED: Manual notification display ***
        /*
        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, notificationBuilder.build())
        */
    }

    // *** CHANGE 25: Implement updateMediaSessionState() to explicitly update MediaSession ***
    // While setting the player usually triggers updates, you might need this
    // after linking a player or when other relevant state changes occur.
    private fun updateMediaSessionState(player: Player) {
        // The MediaSession will automatically observe the Player.
        // Explicitly updating metadata or playback state on the Player
        // will reflect in the MediaSession and subsequently the notification.
        // For example, if you need to update the position:
        // mediaSession?.setPlaybackState(
        //     PlaybackStateCompat.Builder()
        //         .setState(player.playbackState, player.currentPosition, player.playbackSpeed)
        //         .setActions(...) // Define available actions
        //         .build()
        // )
        // Or update metadata if it has changed outside of a Player event:
        // mediaSession?.setMetadata(
        //     MediaMetadataCompat.Builder()
        //         .setTitle(...)
        //         .setArtist(...)
        //         .build()
        // )
    }


    // *** CHANGE 26: Create a PendingIntent to launch your main Activity ***
    // This is typically what happens when the user clicks on the notification.
    private fun createContentIntent(): PendingIntent {
        //val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        //return if (openAppIntent != null) {
        //    PendingIntent.getActivity(
        return PendingIntent.getActivity(
            this,
            0,
            //openAppIntent,
            intent,
            PendingIntent.FLAG_IMMUTABLE // Use FLAG_IMMUTABLE for security
        )
    }

    // *** CHANGE 27: Create PendingIntents for custom actions (e.g., Stop) ***
    private fun getStopPendingIntent(): PendingIntent {
        val intent = Intent(this, NotificationService::class.java).apply {
            action = Command.COMMAND_STOP_SERVICE // Or a specific stop player command
            // You might include the activePlayerId here if stopping a specific player
            putExtra(Command.EXTRA_PLAYER_ID, activePlayerId)
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

        //val notification = notificationBuilder.build()

    // Utility function to get resource ID from string
    private fun getResourceId(name: String): Int {
        return try {
            resources.getIdentifier(name, "drawable", packageName)
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

/*
        startForeground(NOTIFICATION_ID, notification)

        // The stopForeground logic might need adjustment based on how you manage playback lifecycle
        // with the Media3 Player. You might stop the service when the player is idle or stopped.
        // This specific check might still be valid depending on your desired behavior.
        //fix for https://github.com/florent37/Flutter-AssetsAudioPlayer/issues/139
        if (!action.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // Using Build.VERSION_CODES.N for stopForeground(flags)
           stopForeground(STOP_FOREGROUND_DETACH or STOP_FOREGROUND_REMOVE) // Using flags for modern Android
        } else if (action.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Keep the service in the foreground when playing
            startForeground(NOTIFICATION_ID, notification)
        } else if (!action.isPlaying && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
             // For older versions, stop the foreground service without flags
            stopForeground(true) // This version is deprecated but used for older APIs
        }
    }
 */

    // Utility function to get drawable resource ID for icons
    private fun getIconResourceId(name: String): Int {
        return try {
            resources.getIdentifier(name, "drawable", packageName)
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

/*
    // Helper function to determine which action indices to show in the compact view
    private fun getCompactViewActionIndices(notificationSettings: NotificationSettings): IntArray {
        val indices = mutableListOf<Int>()
        var currentIndex = 0

        if (notificationSettings.prevEnabled) {
            // If previous is enabled, it's the first action added
            indices.add(currentIndex++)
        }
        if (notificationSettings.playPauseEnabled) {
            // Play/Pause is the next action
            indices.add(currentIndex++)
        }
        if (notificationSettings.nextEnabled) {
            // Next is the next action
            indices.add(currentIndex++)
        }
         if (notificationSettings.stopEnabled) {
             // Stop is the last action
             indices.add(currentIndex++)
         }

        return indices.toIntArray()
    }
*/

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Assets Audio Player", // User-visible name
                NotificationManagerCompat.IMPORTANCE_LOW // Low importance to minimize disruption
            ).apply {
                description = "Notification channel for Assets Audio Player"
            }
            val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(this)
            notificationManager.createNotificationChannel(channel)
        }
    }

    // *** CHANGE 28: Implement hideNotif() to remove the notification ***
    private fun hideNotif() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }

    // *** CHANGE 29: Implement onDestroy() to release the MediaSession ***
    override fun onDestroy() {
        mediaSession?.run {
            player.removeListener(playerEventListener) // Remove the listener
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

// *** CHANGE 30: Implement a DummyPlayer for initial MediaSession creation ***
    // This player doesn't actually play audio but satisfies the MediaSession's requirement
    // for a Player instance when initially creating the session before a real player
    // is linked.
    private class DummyPlayer : Player {
        override fun getPlaybackState(): Int = STATE_IDLE
        override fun getPlayWhenReady(): Boolean = false
        override fun getCurrentPosition(): Long = 0
        override fun getDuration(): Long = C.TIME_UNSET
        override fun getBufferedPosition(): Long = 0
        override fun getBufferedPercentage(): Int = 0
        override fun getTotalBufferedDuration(): Long = 0
        override fun isPlayingAd(): Boolean = false
        override fun isLoading(): Boolean = false
        override fun getCurrentMediaItemIndex(): Int = 0
        override fun getCurrentMediaItem(): androidx.media3.common.MediaItem? = null
        override fun getPlaybackSpeed(): Float = 1.0f
        override fun getPlayerError(): PlaybackException? = null
        override fun getVolume(): Float = 1.0f
        override fun getAudioSessionId(): Int = C.AUDIO_SESSION_ID_UNSET
        override fun getMediaMetadata(): MediaMetadata = MediaMetadata.EMPTY
        override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY

        // Listener management (dummy implementation)
        private val listeners = mutableSetOf<Player.Listener>()
        override fun addListener(listener: Player.Listener) { listeners.add(listener) }
        override fun removeListener(listener: Player.Listener) { listeners.remove(listener) }
        override fun clearVideoEffects() {}
        override fun setVideoEffects(videoEffects: List<androidx.media3.common.Effect>) {}
        override fun clearAuxEffectInfo() {}
        override fun setAuxEffectInfo(auxEffectInfo: androidx.media3.common.audio.AuxEffectInfo) {}
        override fun createMessage(target: androidx.media3.common.util.MediaPeriodQueueNavigator): androidx.media3.common.util.ExoPlaybackException? {
             return null // Not implemented
        }
        override fun setWakeMode(wakeMode: Int) {}
        override fun setHandleAudioBecomingNoisy(handleAudioBecomingNoisy: Boolean) {}
        override fun setPriorityTaskManager(priorityTaskManager: androidx.media3.common.util.PriorityTaskManager?) {}
        override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {}
        override fun getSkipSilenceEnabled(): Boolean { return false }

        // Playback control methods (dummy implementations)
        override fun prepare() {}
        override fun play() {}
        override fun pause() {}
        override fun stop() {}
        override fun release() {}
        override fun seekToDefaultPosition() {}
        override fun seekToDefaultPosition(mediaItemIndex: Int) {}
        override fun seekTo(positionMs: Long) {}
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {}
        override fun setPlayWhenReady(playWhenReady: Boolean) {}
        override fun setRepeatMode(repeatMode: Int) {}
        override fun getRepeatMode(): Int = Player.REPEAT_MODE_OFF
        override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {}
        override fun getShuffleModeEnabled(): Boolean = false
        override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}
        override fun setMediaItems(mediaItems: List<androidx.media3.common.MediaItem>) {}
        override fun addMediaItems(mediaItems: List<androidx.media3.common.MediaItem>) {}
        override fun removeMediaItems(fromIndex: Int, toIndex: Int) {}
        override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {}
        override fun setMediaItem(mediaItem: androidx.media3.common.MediaItem) {}
        override fun addMediaItem(mediaItem: androidx.media3.common.MediaItem) {}
        override fun setVolume(volume: Float) {}
        override fun getDeviceVolume(): Int = 0
        override fun isDeviceMuted(): Boolean = true
        override fun setDeviceVolume(volume: Int) {}
        override fun increaseDeviceVolume() {}
        override fun decreaseDeviceVolume() {}
        override fun setDeviceMuted(muted: Boolean) {}
        override fun seekBack() {}
        override fun seekForward() {}
        override fun setPlaylistMetadata(playlistMetadata: MediaMetadata) {}
        override fun setMediaMetadata(mediaMetadata: MediaMetadata) {}
        override fun getApplicationRepeatMode(): Int = REPEAT_MODE_OFF
        override fun setApplicationRepeatMode(applicationRepeatMode: Int) {}
        override fun getCurrentLiveConfiguration(): androidx.media3.common.LiveConfiguration? = null
        override fun isPlaying(): Boolean = false // Dummy always reports not playing
        override fun getActionForMask(mask: Long): Int = 0 // Dummy implementation
        override fun isCommandAvailable(command: @Player.Command Int): Boolean = false // Dummy implementation
        override fun canAdvertiseSession(): Boolean = false // Dummy implementation

        // You might need to override other methods from the Player interface
        // depending on what methods your MediaSession callback uses or expects
        // the Player to support. For a dummy player, minimal implementation is usually sufficient.
    }
}

// Helper object for custom commands
object Command {
    const val COMMAND_SET_ACTIVE_PLAYER = "SET_ACTIVE_PLAYER"
    const val COMMAND_UPDATE_PLAYER_STATE = "UPDATE_PLAYER_STATE"
    const val COMMAND_STOP_PLAYER = "STOP_PLAYER"
    const val COMMAND_HIDE_NOTIFICATION = "HIDE_NOTIFICATION"
    const val COMMAND_STOP_SERVICE = "STOP_SERVICE"

    // Extra for playerId
    const val EXTRA_PLAYER_ID = "playerId"
}

// Helper class for loading bitmaps (replace with your actual implementation)
object BitmapLoader {
    suspend fun loadBitmapFromUri(context: Context, uri: String): Bitmap? {
        // Implement your bitmap loading logic here
        // This could involve using Coil, Glide, or manually loading from a URI
        return null // Placeholder
    }
}

// Data class to hold notification action information (replace with your actual class)
data class NotificationAction(
    val title: String?,
    val artist: String?,
    val album: String?,
    val imageUri: String?,
    val durationMs: Long?
) : java.io.Serializable // Make it Serializable if you pass it via Intent Extras

// Assuming your plugin has a static instance and methods to access players
// Replace with your actual plugin structure
class AssetsAudioPlayerPlugin {
    companion object {
        var instance: AssetsAudioPlayerPlugin? = null
    }

    fun getPlayer(playerId: String): Player? {
        // Implement logic to retrieve the Player instance by ID
        return null // Placeholder
    }

    fun onNotificationPlayerChanged(playerId: String?) {
        // Implement logic to handle the active notification player change in your plugin
    }
}
