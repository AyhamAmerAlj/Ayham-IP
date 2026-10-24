package com.ayham.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local storage: account, language, favorites / watch later / watched lists, resume positions. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ayham", Context.MODE_PRIVATE)

    fun account(): Account? {
        val s = sp.getString("server", null) ?: return null
        return Account(s, sp.getString("user", "") ?: "", sp.getString("pass", "") ?: "")
    }

    fun saveAccount(a: Account?) {
        val e = sp.edit()
        if (a == null) {
            e.remove("server").remove("user").remove("pass")
        } else {
            e.putString("server", a.server).putString("user", a.user).putString("pass", a.pass)
        }
        e.apply()
    }

    var arabic: Boolean
        get() = sp.getBoolean("ar", true)
        set(v) {
            sp.edit().putBoolean("ar", v).apply()
        }

    var liveExt: String
        get() = sp.getString("liveExt", "m3u8") ?: "m3u8"
        set(v) {
            sp.edit().putString("liveExt", v).apply()
        }

    fun list(name: String): List<Item> {
        val s = sp.getString("list_$name", null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            val out = ArrayList<Item>(a.length())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val nm = o.optString("name")
                out.add(
                    Item(
                        type = o.optString("type"),
                        id = o.optString("id"),
                        name = nm,
                        poster = o.optString("poster"),
                        ext = o.optString("ext", "mp4"),
                        categoryId = o.optString("cat"),
                        rating = o.optString("rating"),
                        epgId = o.optString("epg"),
                        norm = Norm.n(nm)
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveList(name: String, items: List<Item>) {
        val a = JSONArray()
        for (it in items) {
            a.put(
                JSONObject()
                    .put("type", it.type).put("id", it.id).put("name", it.name)
                    .put("poster", it.poster).put("ext", it.ext).put("cat", it.categoryId)
                    .put("rating", it.rating).put("epg", it.epgId)
            )
        }
        sp.edit().putString("list_$name", a.toString()).apply()
    }

    fun progress(key: String): Long = sp.getLong("p_$key", 0L)
    fun setProgress(key: String, posMs: Long) {
        sp.edit().putLong("p_$key", posMs).apply()
    }

    fun isDone(key: String): Boolean = sp.getBoolean("d_$key", false)
    fun setDone(key: String, done: Boolean) {
        sp.edit().putBoolean("d_$key", done).apply()
    }
}
