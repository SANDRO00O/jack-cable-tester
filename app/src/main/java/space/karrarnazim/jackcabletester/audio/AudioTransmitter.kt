package space.karrarnazim.jackcabletester.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import space.karrarnazim.jackcabletester.data.TestFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class AudioTransmitter {
    private var audioTrack: AudioTrack? = null
    var isPlaying = false
        private set

    val sampleRate = 48000
    val baudRate = 1200
    val samplesPerBit = sampleRate / baudRate
    val freqSpace = 2400.0 // 0 / Start
    val freqMark = 4800.0  // 1 / Stop / Idle

    suspend fun transmit(uri: Uri, context: Context, onProgress: (Int, Int) -> Unit) {
        val testFile = TestFileHelper.loadTestFile(context, uri) ?: return
        
        isPlaying = true
        withContext(Dispatchers.IO) {
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuffer, sampleRate * 2) 
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            var phase = 0.0

            fun writeSamples(freq: Double, numSamples: Int): ShortArray {
                val array = ShortArray(numSamples)
                val phaseInc = 2.0 * Math.PI * freq / sampleRate
                for (i in 0 until numSamples) {
                    array[i] = (Math.sin(phase) * 32767).toInt().toShort()
                    phase += phaseInc
                    if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
                }
                return array
            }

            // Write 500ms of Idle (Mark)
            val idleSamples = writeSamples(freqMark, sampleRate / 2)
            audioTrack?.write(idleSamples, 0, idleSamples.size)

            val total = testFile.packets.size
            for ((index, packet) in testFile.packets.withIndex()) {
                if (!isPlaying) break
                
                val magic = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0xAA.toByte(), 0x55.toByte())
                val seqBuf = ByteBuffer.allocate(4).putInt(packet.seq).array()
                val crcData = ByteBuffer.allocate(36).put(seqBuf).put(packet.payload).array()
                val crc = TestFileHelper.crc16(crcData, 0, 36)
                val crcBuf = ByteBuffer.allocate(2).putShort(crc.toShort()).array()
                
                val packetBytes = ByteBuffer.allocate(42)
                    .put(magic).put(seqBuf).put(packet.payload).put(crcBuf).array()

                val packetSamples = ShortArray(420 * samplesPerBit)
                var offset = 0
                
                for (b in packetBytes) {
                    val byteVal = b.toInt() and 0xFF
                    // Start bit (0 / Space)
                    val startWave = writeSamples(freqSpace, samplesPerBit)
                    System.arraycopy(startWave, 0, packetSamples, offset, samplesPerBit)
                    offset += samplesPerBit
                    
                    // 8 Data bits (LSB first)
                    for (i in 0..7) {
                        val bit = (byteVal shr i) and 1
                        val freq = if (bit == 1) freqMark else freqSpace
                        val bitWave = writeSamples(freq, samplesPerBit)
                        System.arraycopy(bitWave, 0, packetSamples, offset, samplesPerBit)
                        offset += samplesPerBit
                    }
                    
                    // Stop bit (1 / Mark)
                    val stopWave = writeSamples(freqMark, samplesPerBit)
                    System.arraycopy(stopWave, 0, packetSamples, offset, samplesPerBit)
                    offset += samplesPerBit
                }
                
                audioTrack?.write(packetSamples, 0, packetSamples.size)
                
                val gapSamples = writeSamples(freqMark, sampleRate / 50)
                audioTrack?.write(gapSamples, 0, gapSamples.size)
                
                withContext(Dispatchers.Main) {
                    onProgress(index + 1, total)
                }
            }
            
            val endIdle = writeSamples(freqMark, sampleRate / 2)
            audioTrack?.write(endIdle, 0, endIdle.size)
            
            delay(1000)
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            
            withContext(Dispatchers.Main) {
                isPlaying = false
            }
        }
    }

    fun stop() {
        isPlaying = false
    }
}
