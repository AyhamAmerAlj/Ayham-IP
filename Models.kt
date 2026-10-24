package com.ayham.player.data

data class Item(
    val type: String, // "live" | "movie" | "series"
    val id: String,
    val name: String,
    val poster: String = "",
    val ext: String = "mp4",
    val categoryId: String = "",
    val rating: String = "",
    val added: Long = 0L,
    val epgId: String = "",
    val norm: String = ""
) {
    val key: String get() = "$type:$id"
}

data class Category(val id: String, val name: String)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val num: Int,
    val ext: String,
    val plot: String,
    val image: String,
    val durationSecs: Int
)

data class Details(
    val plot: String = "",
    val year: String = "",
    val genre: String = "",
    val cast: String = "",
    val backdrop: String = "",
    val duration: String = "",
    val rating: String = "",
    val episodes: List<Episode> = emptyList()
)

data class Account(val server: String, val user: String, val pass: String)

object Norm {
    /** Normalizes Arabic/English text so search works regardless of diacritics, hamza forms, etc. */
    fun n(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) {
            when {
                ch in 'ً'..'ٟ' || ch == 'ـ' -> {}
                ch == 'أ' || ch == 'إ' || ch == 'آ' -> sb.append('ا')
                ch == 'ى' -> sb.append('ي')
                ch == 'ة' -> sb.append('ه')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
