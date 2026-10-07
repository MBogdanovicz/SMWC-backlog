package com.capivaraec.smwc.core.patch

import java.util.zip.CRC32

/** The patch cannot be applied (corrupt, or made for another base ROM). */
class PatchException(message: String) : Exception(message)

enum class PatchFormat(val extension: String) {
    BPS("bps"),
    IPS("ips"),
    ;

    companion object {
        /** By the file's magic bytes; null if it is not a patch. */
        fun detect(bytes: ByteArray): PatchFormat? = when {
            bytes.startsWith("BPS1") -> BPS
            bytes.startsWith("PATCH") -> IPS
            else -> null
        }

        private fun ByteArray.startsWith(magic: String) =
            size >= magic.length && magic.indices.all { this[it] == magic[it].code.toByte() }
    }
}

/**
 * Applies a BPS patch (https://www.romhacking.net/documents/746/). The source CRC32 in the
 * footer must match [source], so a patch for another base ROM fails instead of making garbage.
 */
object BpsPatcher {
    fun sourceCrc32(patch: ByteArray): Long = readUInt32(patch, patch.size - 12)

    fun apply(source: ByteArray, patch: ByteArray): ByteArray {
        if (PatchFormat.detect(patch) != PatchFormat.BPS || patch.size < 4 + 12) throw PatchException("Not a BPS patch")
        if (crc32(patch, 0, patch.size - 4) != readUInt32(patch, patch.size - 4)) throw PatchException("The BPS patch is corrupted")
        if (crc32(source) != sourceCrc32(patch)) throw PatchException("The patch was made for a different base ROM")

        val reader = Reader(patch, 4)
        val sourceSize = reader.number().toInt()
        if (sourceSize != source.size) throw PatchException("The patch expects a ${sourceSize}-byte ROM")
        val target = ByteArray(reader.number().toInt())
        reader.skip(reader.number().toInt()) // metadata

        val end = patch.size - 12
        var out = 0
        var sourceRelative = 0
        var targetRelative = 0
        while (reader.position < end) {
            val data = reader.number()
            val length = ((data shr 2) + 1).toInt()
            when ((data and 3).toInt()) {
                0 -> repeat(length) { target[out] = source[out]; out++ } // SourceRead
                1 -> repeat(length) { target[out++] = reader.byte() } // TargetRead
                2 -> { // SourceCopy
                    sourceRelative += reader.signedOffset()
                    repeat(length) { target[out++] = source[sourceRelative++] }
                }
                else -> { // TargetCopy: may overlap the bytes being written, so copy one at a time
                    targetRelative += reader.signedOffset()
                    repeat(length) { target[out++] = target[targetRelative++] }
                }
            }
        }
        if (crc32(target) != readUInt32(patch, patch.size - 8)) throw PatchException("The patched ROM failed its checksum")
        return target
    }

    private class Reader(private val bytes: ByteArray, var position: Int) {
        fun byte(): Byte = bytes.getOrNull(position++) ?: throw PatchException("The BPS patch is truncated")
        fun skip(count: Int) { position += count }

        /** BPS variable-length number. */
        fun number(): Long {
            var data = 0L
            var shift = 1L
            while (true) {
                val x = byte().toInt() and 0xFF
                data += (x and 0x7F) * shift
                if (x and 0x80 != 0) return data
                shift = shift shl 7
                data += shift
            }
        }

        fun signedOffset(): Int {
            val n = number()
            val magnitude = (n shr 1).toInt()
            return if (n and 1L != 0L) -magnitude else magnitude
        }
    }

    private fun readUInt32(bytes: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { acc, i -> acc or ((bytes[at + i].toLong() and 0xFF) shl (8 * i)) }
}

/** Applies an IPS patch. IPS has no checksums, so nothing guarantees the right base ROM. */
object IpsPatcher {
    fun apply(source: ByteArray, patch: ByteArray): ByteArray {
        if (PatchFormat.detect(patch) != PatchFormat.IPS) throw PatchException("Not an IPS patch")
        var target = source.copyOf()
        var size = target.size
        fun ensure(length: Int) {
            if (length > target.size) target = target.copyOf(maxOf(length, target.size * 2))
            if (length > size) size = length
        }
        fun at(i: Int): Int = (patch.getOrNull(i) ?: throw PatchException("The IPS patch is truncated")).toInt() and 0xFF

        var p = 5
        while (true) {
            val offset = (at(p) shl 16) or (at(p + 1) shl 8) or at(p + 2)
            if (offset == EOF) {
                // Optional truncation length after the EOF marker.
                if (patch.size >= p + 6) size = (at(p + 3) shl 16) or (at(p + 4) shl 8) or at(p + 5)
                break
            }
            val length = (at(p + 3) shl 8) or at(p + 4)
            p += 5
            if (length > 0) {
                if (p + length > patch.size) throw PatchException("The IPS patch is truncated")
                ensure(offset + length)
                patch.copyInto(target, destinationOffset = offset, startIndex = p, endIndex = p + length)
                p += length
            } else { // run-length record
                val count = (at(p) shl 8) or at(p + 1)
                val value = patch[p + 2]
                ensure(offset + count)
                target.fill(value, offset, offset + count)
                p += 3
            }
        }
        return target.copyOf(size)
    }

    private const val EOF = 0x454F46 // "EOF"
}

internal fun crc32(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): Long =
    CRC32().apply { update(bytes, from, to - from) }.value
