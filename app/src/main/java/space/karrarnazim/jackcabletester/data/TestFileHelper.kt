package space.karrarnazim.jackcabletester.data

import android.content.Context
import android.net.Uri
import java.nio.ByteBuffer

object TestFileHelper {
    val MAGIC_HEADER = "JCT1".toByteArray()

    fun generateTestFile(context: Context, uri: Uri, numPackets: Int): Boolean {
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(MAGIC_HEADER)
                val numBuf = ByteBuffer.allocate(4).putInt(numPackets).array()
                out.write(numBuf)
                
                for (i in 0 until numPackets) {
                    val magic = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0xAA.toByte(), 0x55.toByte())
                    val seqBuf = ByteBuffer.allocate(4).putInt(i).array()
                    val payload = ByteArray(32)
                    for (j in 0 until 32) {
                        payload[j] = ((i + j) % 256).toByte()
                    }
                    val crcData = ByteBuffer.allocate(36).put(seqBuf).put(payload).array()
                    val crc = crc16(crcData, 0, 36)
                    val crcBuf = ByteBuffer.allocate(2).putShort(crc.toShort()).array()
                    
                    out.write(magic)
                    out.write(seqBuf)
                    out.write(payload)
                    out.write(crcBuf)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun loadTestFile(context: Context, uri: Uri): TestFile? {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) != 4 || !magic.contentEquals(MAGIC_HEADER)) return null
                
                val numBuf = ByteArray(4)
                if (input.read(numBuf) != 4) return null
                val numPackets = ByteBuffer.wrap(numBuf).int
                
                val packets = mutableListOf<TestPacket>()
                val packetBuf = ByteArray(42)
                for (i in 0 until numPackets) {
                    var read = 0
                    while (read < 42) {
                        val r = input.read(packetBuf, read, 42 - read)
                        if (r == -1) break
                        read += r
                    }
                    if (read == 42) {
                        val seq = ByteBuffer.wrap(packetBuf, 4, 4).int
                        val payload = packetBuf.copyOfRange(8, 40)
                        packets.add(TestPacket(seq, payload))
                    }
                }
                return TestFile(numPackets, packets)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun crc16(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (j in 0 until 8) {
                if ((crc and 1) != 0) {
                    crc = (crc ushr 1) xor 0xA001
                } else {
                    crc = crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }
}
