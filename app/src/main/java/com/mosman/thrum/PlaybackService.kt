package com.mosman.thrum

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps a song, and its vibration, going when Thrum is closed or the screen
 * is off, and puts its controls in the notification shade and on the lock
 * screen. Moved into v1 on 2026-10-04 at Mutalib's request (PROFILE §4).
 *
 * It holds no player of its own: [Player] owns the one player in the app,
 * and this only lends it to Android. Media3 draws the notification, keeps
 * the service in the foreground while the song plays, and lets go when it
 * stops. A media notification needs no notification permission on Android
 * 13+, so this asks the user nothing new.
 */
class PlaybackService : MediaSessionService() {

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_stat_thrum)
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = Player.session(this)

    /**
     * Swiped away from recent apps: a song still playing carries on, like any
     * music player; a paused or finished one ends with the app.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!Player.isPlaying()) stopSelf()
    }

    override fun onDestroy() {
        Player.releaseSession()
        super.onDestroy()
    }
}
