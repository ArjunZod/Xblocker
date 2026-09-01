package com.antigravity.shieldx.music

import com.antigravity.shieldx.core.model.RepeatMode
import com.antigravity.shieldx.core.model.Track
import java.util.Collections

class MusicQueueManager {

    private val originalQueue = mutableListOf<Track>()
    private val activeQueue = mutableListOf<Track>()
    private var currentIndex = 0
    private var isShuffle = false
    private var repeatMode = RepeatMode.OFF

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        originalQueue.clear()
        originalQueue.addAll(tracks)
        activeQueue.clear()
        activeQueue.addAll(tracks)

        currentIndex = startIndex.coerceIn(0, (activeQueue.size - 1).coerceAtLeast(0))

        if (isShuffle && activeQueue.isNotEmpty()) {
            val currentTrack = activeQueue.getOrNull(currentIndex)
            activeQueue.shuffle()
            if (currentTrack != null) {
                activeQueue.remove(currentTrack)
                activeQueue.add(0, currentTrack)
                currentIndex = 0
            }
        }
    }

    fun getCurrentTrack(): Track? {
        if (activeQueue.isEmpty() || currentIndex !in activeQueue.indices) return null
        return activeQueue[currentIndex]
    }

    fun next(): Track? {
        if (activeQueue.isEmpty()) return null

        if (repeatMode == RepeatMode.ONE) {
            return getCurrentTrack()
        }

        if (currentIndex < activeQueue.size - 1) {
            currentIndex++
            return activeQueue[currentIndex]
        } else if (repeatMode == RepeatMode.ALL) {
            currentIndex = 0
            return activeQueue[currentIndex]
        }
        return null
    }

    fun previous(): Track? {
        if (activeQueue.isEmpty()) return null

        if (currentIndex > 0) {
            currentIndex--
            return activeQueue[currentIndex]
        } else if (repeatMode == RepeatMode.ALL) {
            currentIndex = activeQueue.size - 1
            return activeQueue[currentIndex]
        }
        return activeQueue.getOrNull(0)
    }

    fun addToQueue(track: Track) {
        originalQueue.add(track)
        activeQueue.add(track)
    }

    fun playNext(track: Track) {
        val insertIndex = (currentIndex + 1).coerceAtMost(activeQueue.size)
        originalQueue.add(insertIndex, track)
        activeQueue.add(insertIndex, track)
    }

    fun toggleShuffle(): Boolean {
        isShuffle = !isShuffle
        val currentTrack = getCurrentTrack()

        if (isShuffle) {
            activeQueue.shuffle()
            if (currentTrack != null) {
                activeQueue.remove(currentTrack)
                activeQueue.add(0, currentTrack)
                currentIndex = 0
            }
        } else {
            activeQueue.clear()
            activeQueue.addAll(originalQueue)
            if (currentTrack != null) {
                val idx = activeQueue.indexOf(currentTrack)
                if (idx != -1) currentIndex = idx
            }
        }
        return isShuffle
    }

    fun toggleRepeat(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        return repeatMode
    }

    /**
     * Remove the track at [index].
     *
     * The index bookkeeping is the whole point: removing an entry before the
     * current one shifts everything down, so [currentIndex] has to follow or the
     * queue silently starts pointing at the wrong song.
     *
     * Returns true when something was removed.
     */
    fun removeAt(index: Int): Boolean {
        if (index !in activeQueue.indices) return false

        val removed = activeQueue.removeAt(index)
        originalQueue.remove(removed)

        when {
            // Removing something earlier in the list pulls the current track back.
            index < currentIndex -> currentIndex--

            // Removing the current track leaves the index pointing at whatever
            // slid into its place; clamp so it stays inside the list.
            index == currentIndex ->
                currentIndex = currentIndex.coerceAtMost((activeQueue.size - 1).coerceAtLeast(0))
        }
        return true
    }

    /**
     * Move a track from one position to another, keeping [currentIndex] pointing
     * at the same song rather than the same slot.
     */
    fun move(from: Int, to: Int): Boolean {
        if (from !in activeQueue.indices) return false
        val target = to.coerceIn(0, activeQueue.size - 1)
        if (from == target) return false

        val playing = getCurrentTrack()
        val track = activeQueue.removeAt(from)
        activeQueue.add(target, track)

        // Re-find the playing track instead of trying to derive the new index.
        if (playing != null) {
            val found = activeQueue.indexOf(playing)
            if (found != -1) currentIndex = found
        }
        return true
    }

    /** Drop everything after the current track, keeping what is playing. */
    fun clearUpcoming() {
        if (activeQueue.isEmpty()) return
        val keepTo = currentIndex.coerceAtMost(activeQueue.size - 1)
        while (activeQueue.size > keepTo + 1) {
            val removed = activeQueue.removeAt(activeQueue.size - 1)
            originalQueue.remove(removed)
        }
    }

    /** Jump directly to a queue position, for tapping an upcoming track. */
    fun jumpTo(index: Int): Track? {
        if (index !in activeQueue.indices) return null
        currentIndex = index
        return activeQueue[index]
    }

    fun getQueue(): List<Track> = activeQueue.toList()
    fun getCurrentIndex(): Int = currentIndex
    fun isShuffleEnabled(): Boolean = isShuffle
    fun getRepeatMode(): RepeatMode = repeatMode
}
