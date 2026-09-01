package com.antigravity.shieldx.music

import com.antigravity.shieldx.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Queue editing is where off-by-one errors quietly corrupt playback: remove an
 * item above the current one and, unless the index follows, the queue starts
 * pointing at a different song than the one playing.
 */
class MusicQueueManagerTest {

    private lateinit var queue: MusicQueueManager

    private fun track(id: String) = Track(id = id, title = "Song $id", artist = "Artist")

    @Before
    fun setUp() {
        queue = MusicQueueManager()
        queue.setQueue(listOf("a", "b", "c", "d", "e").map(::track), startIndex = 2)
    }

    @Test
    fun `starts on the requested track`() {
        assertEquals("c", queue.getCurrentTrack()?.id)
        assertEquals(2, queue.getCurrentIndex())
    }

    @Test
    fun `removing an earlier track keeps the same song playing`() {
        queue.removeAt(0)
        assertEquals("c", queue.getCurrentTrack()?.id)
        assertEquals(1, queue.getCurrentIndex())
    }

    @Test
    fun `removing a later track does not move the current index`() {
        queue.removeAt(4)
        assertEquals("c", queue.getCurrentTrack()?.id)
        assertEquals(2, queue.getCurrentIndex())
    }

    @Test
    fun `removing the current track lands on the one that took its place`() {
        queue.removeAt(2)
        assertEquals("d", queue.getCurrentTrack()?.id)
        assertEquals(2, queue.getCurrentIndex())
    }

    @Test
    fun `removing the last remaining track leaves an empty queue`() {
        queue.setQueue(listOf(track("only")))
        queue.removeAt(0)
        assertEquals(0, queue.getQueue().size)
        assertNull(queue.getCurrentTrack())
    }

    @Test
    fun `removing out of range is rejected`() {
        assertFalse(queue.removeAt(99))
        assertFalse(queue.removeAt(-1))
        assertEquals(5, queue.getQueue().size)
    }

    @Test
    fun `moving a track keeps the same song playing`() {
        // Move "a" from the top down past the current track.
        queue.move(0, 4)
        assertEquals("c", queue.getCurrentTrack()?.id)
        assertEquals(1, queue.getCurrentIndex())
        assertEquals(listOf("b", "c", "d", "e", "a"), queue.getQueue().map { it.id })
    }

    @Test
    fun `moving the current track follows it to the new position`() {
        queue.move(2, 0)
        assertEquals("c", queue.getCurrentTrack()?.id)
        assertEquals(0, queue.getCurrentIndex())
    }

    @Test
    fun `moving to the same slot is a no-op`() {
        assertFalse(queue.move(2, 2))
    }

    @Test
    fun `play next inserts directly after the current track`() {
        queue.playNext(track("z"))
        assertEquals(listOf("a", "b", "c", "z", "d", "e"), queue.getQueue().map { it.id })
        assertEquals("c", queue.getCurrentTrack()?.id)
    }

    @Test
    fun `add to queue appends to the end`() {
        queue.addToQueue(track("z"))
        assertEquals("z", queue.getQueue().last().id)
    }

    @Test
    fun `clearing upcoming keeps the current track and drops the rest`() {
        queue.clearUpcoming()
        assertEquals(listOf("a", "b", "c"), queue.getQueue().map { it.id })
        assertEquals("c", queue.getCurrentTrack()?.id)
    }

    @Test
    fun `jumping selects that track`() {
        val jumped = queue.jumpTo(4)
        assertEquals("e", jumped?.id)
        assertEquals("e", queue.getCurrentTrack()?.id)
    }

    @Test
    fun `jumping out of range returns null and changes nothing`() {
        assertNull(queue.jumpTo(99))
        assertEquals("c", queue.getCurrentTrack()?.id)
    }

    @Test
    fun `repeated removals stay consistent`() {
        queue.removeAt(0)
        queue.removeAt(0)
        // "a" and "b" gone; "c" should still be the one playing.
        assertEquals("c", queue.getCurrentTrack()?.id)
        assertEquals(0, queue.getCurrentIndex())
        assertTrue(queue.getQueue().map { it.id } == listOf("c", "d", "e"))
    }
}
