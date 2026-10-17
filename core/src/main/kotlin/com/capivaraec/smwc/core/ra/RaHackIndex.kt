package com.capivaraec.smwc.core.ra

import java.text.Normalizer

enum class RaMatchKind {
    /** The MD5 of the patched ROM is one of the set's supported hashes: the same hack and version. */
    HASH,

    /** Only the names match, so it may be another version of the hack (or, rarely, another hack). */
    NAME,
}

data class RaMatch(val game: RaGame, val kind: RaMatchKind)

/** Finds the RetroAchievements set of a hack, by ROM hash when known, else by name. */
class RaHackIndex(games: List<RaGame>) {
    private val byHash: Map<String, RaGame> = buildMap {
        for (game in games) for (hash in game.hashes) putIfAbsent(hash.lowercase(), game)
    }

    // On a name clash, the bigger set comes first (the other is usually a bonus or old version).
    private val byName: Map<String, List<RaGame>> = buildMap<String, MutableList<RaGame>> {
        for (game in games.sortedByDescending { it.achievements }) {
            for (key in nameKeys(game.title)) getOrPut(key) { mutableListOf() }.add(game)
        }
    }

    val size: Int = games.size

    /**
     * [romMd5s]: hashes of the ROMs the hack's patch produces (see `RomHashRepository`).
     * [unlinkedGameIds]: sets the user said are not this hack; skipped when matching by name.
     */
    fun find(hackName: String, romMd5s: Collection<String> = emptyList(), unlinkedGameIds: Collection<Long> = emptySet()): RaMatch? {
        romMd5s.firstNotNullOfOrNull { byHash[it.lowercase()] }?.let { return RaMatch(it, RaMatchKind.HASH) }
        return nameKeys(hackName)
            .firstNotNullOfOrNull { key -> byName[key]?.firstOrNull { it.id !in unlinkedGameIds } }
            ?.let { RaMatch(it, RaMatchKind.NAME) }
    }

    companion object {
        private val tag = Regex("~[^~]*~")
        private val nonAlphanumeric = Regex("[^a-z0-9]+")
        private val marks = Regex("\\p{M}+")
        private val entities = mapOf("&amp;" to "&", "&#039;" to "'", "&#39;" to "'", "&quot;" to "\"", "&apos;" to "'")
        private const val BASE_GAME = "supermarioworld"

        /**
         * Comparable forms of a name: lowercase letters and digits only, without RA's `~Hack~`
         * tag, and also without a leading "Super Mario World" (RA often titles hacks
         * "Super Mario World: <name>" while SMW Central uses just "<name>").
         */
        fun nameKeys(name: String): List<String> {
            var s = entities.entries.fold(name) { acc, (entity, char) -> acc.replace(entity, char, ignoreCase = true) }
            s = Normalizer.normalize(s.replace(tag, " "), Normalizer.Form.NFD).replace(marks, "")
            val key = s.lowercase().replace("&", "and").replace(nonAlphanumeric, "")
            if (key.isEmpty()) return emptyList()
            val withoutBase = key.removePrefix(BASE_GAME)
            return if (withoutBase != key && withoutBase.isNotEmpty()) listOf(key, withoutBase) else listOf(key)
        }
    }
}
