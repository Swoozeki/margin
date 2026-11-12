package app.margin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

val BIBLE_CATEGORIES = listOf("Characters", "Places", "Threads", "Questions")

data class Entry(val id: Long, val category: String, val text: String, val createdAt: Long)

class BibleStore(context: Context) {
    private val file = File(context.filesDir, "bible.json")

    fun load(): List<Entry> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val list = ArrayList<Entry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Entry(o.getLong("id"), o.getString("category"), o.getString("text"), o.getLong("createdAt"))
                )
            }
            list.sortedBy { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(category: String, text: String): List<Entry> {
        val now = System.currentTimeMillis()
        val updated = load() + Entry(now, category, text, now)
        save(updated)
        return updated
    }

    fun remove(id: Long): List<Entry> {
        val updated = load().filter { it.id != id }
        save(updated)
        return updated
    }

    private fun save(list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("category", e.category)
            o.put("text", e.text)
            o.put("createdAt", e.createdAt)
            arr.put(o)
        }
        val tmp = File(file.parentFile, "bible.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }
}
