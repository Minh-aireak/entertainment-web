package com.MyProject.room_service.enums;

/** Host-only playback controls. HEARTBEAT is a periodic resync ping (no state transition) that
 *  just refreshes positionSeconds/lastActionAt so late joiners and connected viewers can correct
 *  drift without the host needing to pause/seek. */
public enum PlaybackAction {
    PLAY,
    PAUSE,
    SEEK,
    CHANGE_EPISODE,
    HEARTBEAT
}
