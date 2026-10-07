package com.capivaraec.smwc.core.patch

import java.security.MessageDigest

/** SNES ROM helpers, following the rules RetroAchievements (rcheevos) uses to hash SNES games. */
object SnesRom {
    const val COPIER_HEADER_SIZE = 512

    /** Super Mario World (USA), without a copier header: the base ROM of SMW hacks. */
    const val SMW_USA_MD5 = "cdd3c8c37322978ca8669b34bc89c804"
    const val SMW_USA_CRC32 = 0xB19ED489L

    /** A 512-byte copier header makes the size 512 more than a multiple of 1 KiB. */
    fun hasCopierHeader(rom: ByteArray): Boolean = rom.size % 1024 == COPIER_HEADER_SIZE

    fun withoutCopierHeader(rom: ByteArray): ByteArray =
        if (hasCopierHeader(rom)) rom.copyOfRange(COPIER_HEADER_SIZE, rom.size) else rom

    /** The hash RetroAchievements uses: MD5 of the ROM without its copier header, lowercase hex. */
    fun raHash(rom: ByteArray): String = md5(withoutCopierHeader(rom))

    /**
     * Checks that [rom] is a clean Super Mario World (USA), with or without a copier header, and
     * returns it without the header. Throws [PatchException] otherwise.
     */
    fun requireCleanSmw(rom: ByteArray): ByteArray {
        val bare = withoutCopierHeader(rom)
        if (md5(bare) != SMW_USA_MD5) {
            throw PatchException("This is not a clean Super Mario World (USA) ROM")
        }
        return bare
    }

    /**
     * True if the checksum in the LoROM internal header matches the ROM's contents. Lunar Magic
     * recomputes it on save, so it tells whether an IPS patch was applied at the right offset
     * (a misplaced patch leaves the base ROM's old checksum behind).
     */
    fun hasValidLoRomChecksum(rom: ByteArray): Boolean {
        val bare = withoutCopierHeader(rom)
        if (bare.size < 0x8000) return false
        fun word(at: Int) = (bare[at].toInt() and 0xFF) or ((bare[at + 1].toInt() and 0xFF) shl 8)
        val complement = word(0x7FDC)
        val checksum = word(0x7FDE)
        return complement + checksum == 0xFFFF && checksum == computeChecksum(bare)
    }

    /** The SNES checksum: the 16-bit byte sum, with a non-power-of-two tail mirrored to fill the ROM. */
    fun computeChecksum(rom: ByteArray): Int {
        fun sum(from: Int, to: Int): Long { var s = 0L; for (i in from until to) s += rom[i].toInt() and 0xFF; return s }
        val base = Integer.highestOneBit(rom.size)
        var total = sum(0, base)
        val rest = rom.size - base
        if (rest > 0) total += sum(base, rom.size) * (base / rest)
        return (total and 0xFFFF).toInt()
    }

    fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
}
