package space.karrarnazim.jackcabletester.data

data class TestPacket(
    val seq: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TestPacket
        if (seq != other.seq) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = seq
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

data class TestFile(
    val numPackets: Int,
    val packets: List<TestPacket>
)
