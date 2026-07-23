package space.karrarnazim.jackcabletester.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import space.karrarnazim.jackcabletester.data.TestFile
import space.karrarnazim.jackcabletester.data.TestFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

data class ReceiverStats(
    val totalExpected: Int = 0,
    val validReceived: Int = 0,
    val crcErrors: Int = 0,
    val dataMismatches: Int = 0,
    val isListening: Boolean = false
)

class AudioReceiver {
    private var audioRecord: AudioRecord? = null
    var isRecording = false
        private set

    val stats = MutableStateFlow(ReceiverStats())

    private fun goertzel(samples: ShortArray, start: Int, N: Int, k: Int): Float {
        val w = (2.0 * Math.PI * k) / N
        val cosine = Math.cos(w).toFloat()
        val coeff = 2.0f * cosine
        var q1 = 0.0f
        var q2 = 0.0f
        for (i in 0 until N) {
            val sample = samples[start + i] / 32768.0f
            val q0 = coeff * q1 - q2 + sample
            q2 = q1
            q1 = q0
        }
        return (q1 * q1 + q2 * q2 - q1 * q2 * coeff)
    }

    @SuppressLint("MissingPermission")
    suspend fun startListening(testFile: TestFile) {
        isRecording = true
        stats.value = ReceiverStats(totalExpected = testFile.numPackets, isListening = true)
        
        val expectedPacketsMap = testFile.packets.associateBy { it.seq }

        withContext(Dispatchers.IO) {
            val sampleRate = 48000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuffer, sampleRate)
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()

            val processBuffer = ShortArray(96000)
            var processLen = 0

            val STATE_IDLE = 0
            val STATE_READ_BITS = 1
            val STATE_READ_STOP = 2

            var state = STATE_IDLE
            var currentByte = 0
            var bitIndex = 0
            var lastWasIdle = true

            val byteRing = ByteArray(42)
            
            fun onByteReceived(b: Byte) {
                System.arraycopy(byteRing, 1, byteRing, 0, 41)
                byteRing[41] = b
                
                if (byteRing[0] == 0xAA.toByte() && byteRing[1] == 0x55.toByte() &&
                    byteRing[2] == 0xAA.toByte() && byteRing[3] == 0x55.toByte()) {
                    
                    val crcReceived = ByteBuffer.wrap(byteRing, 40, 2).short.toInt() and 0xFFFF
                    val crcCalculated = TestFileHelper.crc16(byteRing, 4, 36)
                    
                    val seq = ByteBuffer.wrap(byteRing, 4, 4).int
                    val payload = byteRing.copyOfRange(8, 40)
                    
                    val currentStats = stats.value
                    
                    if (crcReceived == crcCalculated) {
                        val expected = expectedPacketsMap[seq]
                        if (expected != null && expected.payload.contentEquals(payload)) {
                            stats.value = currentStats.copy(validReceived = currentStats.validReceived + 1)
                        } else {
                            stats.value = currentStats.copy(dataMismatches = currentStats.dataMismatches + 1)
                        }
                    } else {
                        stats.value = currentStats.copy(crcErrors = currentStats.crcErrors + 1)
                    }
                    byteRing[0] = 0 // Consume magic
                }
            }

            val readBuffer = ShortArray(4096)
            while (isRecording) {
                val readCount = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                if (readCount > 0) {
                    if (processLen + readCount > processBuffer.size) {
                        processLen = 0 
                    }
                    System.arraycopy(readBuffer, 0, processBuffer, processLen, readCount)
                    processLen += readCount

                    var i = 0
                    while (i <= processLen - 400) {
                        when (state) {
                            STATE_IDLE -> {
                                val pSpace = goertzel(processBuffer, i, 40, 2)
                                val pMark = goertzel(processBuffer, i, 40, 4)
                                val isSpace = pSpace > pMark * 1.5f && pSpace > 0.1f 

                                if (isSpace && lastWasIdle) {
                                    state = STATE_READ_BITS
                                    i += 60 
                                    currentByte = 0
                                    bitIndex = 0
                                } else {
                                    lastWasIdle = !isSpace
                                    i += 4 
                                }
                            }
                            STATE_READ_BITS -> {
                                val pSpace = goertzel(processBuffer, i, 40, 2)
                                val pMark = goertzel(processBuffer, i, 40, 4)
                                val bit = if (pMark > pSpace) 1 else 0
                                currentByte = currentByte or (bit shl bitIndex)
                                bitIndex++
                                i += 40
                                if (bitIndex == 8) {
                                    state = STATE_READ_STOP
                                }
                            }
                            STATE_READ_STOP -> {
                                val pSpace = goertzel(processBuffer, i, 40, 2)
                                val pMark = goertzel(processBuffer, i, 40, 4)
                                val bit = if (pMark > pSpace) 1 else 0
                                if (bit == 1) {
                                    onByteReceived(currentByte.toByte())
                                }
                                i += 20 
                                state = STATE_IDLE
                                lastWasIdle = true 
                            }
                        }
                    }

                    val remaining = processLen - i
                    if (remaining > 0 && i > 0) {
                        System.arraycopy(processBuffer, i, processBuffer, 0, remaining)
                    }
                    processLen = remaining
                }
            }
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            stats.value = stats.value.copy(isListening = false)
        }
    }

    fun stop() {
        isRecording = false
    }
}
