package com.tap.zoot.runtime.host

import io.github.charlietap.chasm.memory.ByteArrayLinearMemory
import io.github.charlietap.chasm.runtime.memory.LinearMemory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GuestMemoryTransferTest {
    private val memory = ByteArrayLinearMemory(LinearMemory.Pages(1u))
    private val transfer = GuestMemoryTransfer()

    @Test
    fun readsDoNotExposeGuestMemory() {
        memory.writeI8(24, 7)
        transfer.read(memory, 24, 4) { bytes ->
            assertEquals(7, bytes[0].toInt())
            bytes[0] = 9
        }
        assertEquals(7, memory.readI8(24).toInt())
    }

    @Test
    fun readsCopyOnlyTheRequestedRangeAndReuseTheBuffer() {
        memory.write(24, byteArrayOf(1, 2, 3, 4))
        val first =
            transfer.read(memory, 24, 4) { bytes ->
                assertArrayEquals(byteArrayOf(1, 2, 3, 4), bytes.copyOf(4))
                bytes
            }
        transfer.read(memory, 26, 2) { bytes ->
            assertSame(first, bytes)
            assertArrayEquals(byteArrayOf(3, 4, 3, 4), bytes)
        }
    }

    @Test
    fun writesCopyBackOnlyTheWrittenBytes() {
        memory.fill(20, 9, 10)
        assertEquals(
            2,
            transfer.write(memory, 24, 4) { bytes ->
                bytes[0] = 1
                bytes[1] = 2
                bytes[2] = 3
                assertEquals(9, memory.readI8(24).toInt())
                2
            },
        )
        assertArrayEquals(byteArrayOf(9, 1, 2, 9), memory.read(ByteArray(4), 23, 4))
    }

    @Test
    fun failedResourceReadsDoNotWriteBack() {
        memory.writeI8(24, 9)
        assertEquals(
            -1,
            transfer.write(memory, 24, 4) { bytes ->
                bytes[0] = 1
                -1
            },
        )
        assertEquals(9, memory.readI8(24).toInt())
    }

    @Test
    fun readsAndWritesReuseTheSameBuffer() {
        val first = transfer.read(memory, 24, 4) { it }
        transfer.write(memory, 24, 4) { bytes ->
            assertSame(first, bytes)
            bytes[0] = 7
            1
        }
        transfer.read(memory, 24, 4) { bytes ->
            assertSame(first, bytes)
            assertEquals(7, bytes[0].toInt())
        }
    }
}
