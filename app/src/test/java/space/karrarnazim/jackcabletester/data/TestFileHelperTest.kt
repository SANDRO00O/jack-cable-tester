package space.karrarnazim.jackcabletester.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TestFileHelperTest {

    @Test
    fun crc16_isDeterministic() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val crc1 = TestFileHelper.crc16(data, 0, data.size)
        val crc2 = TestFileHelper.crc16(data, 0, data.size)
        assertEquals(crc1, crc2)
    }

    @Test
    fun crc16_detectsSingleBitFlip() {
        val original = byteArrayOf(1, 2, 3, 4, 5)
        val corrupted = byteArrayOf(1, 2, 3, 4, 6)
        val crcOriginal = TestFileHelper.crc16(original, 0, original.size)
        val crcCorrupted = TestFileHelper.crc16(corrupted, 0, corrupted.size)
        assertNotEquals(crcOriginal, crcCorrupted)
    }

    @Test
    fun crc16_ofEmptyRangeIsAllOnes() {
        val data = ByteArray(0)
        assertEquals(0xFFFF, TestFileHelper.crc16(data, 0, 0))
    }
}
