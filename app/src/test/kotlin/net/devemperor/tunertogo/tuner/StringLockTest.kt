package net.devemperor.tunertogo.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StringLockTest {
    @Test
    fun `crown forward goes from auto through every string back to auto`() {
        var lock: Int? = null
        val seen = mutableListOf<Int?>()
        repeat(7) {
            lock = steppedStringLock(lock, 1, 6)
            seen += lock
        }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, null), seen)
    }

    @Test
    fun `crown backwards wraps from auto to the last string`() {
        assertEquals(5, steppedStringLock(null, -1, 6))
        assertNull(steppedStringLock(0, -1, 6))
    }

    @Test
    fun `chromatic mode has nothing to lock`() {
        assertNull(steppedStringLock(null, 1, 0))
    }
}
