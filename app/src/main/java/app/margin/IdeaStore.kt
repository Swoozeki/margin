package app.margin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Idea(val id: Long, val text: String, val createdAt: Long)

class IdeaStore(context: Context) {
    private val file = File(context.filesDir, "ideas.json")

    fun load(): List<Idea> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val list = ArrayList<Idea>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Idea(o.getLong("id"), o.getString("text"), o.getLong("createdAt")))
            }
            list.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(text: String): List<Idea> {
        val now = System.currentTimeMillis()
        val updated = listOf(Idea(now, text, now)) + load()
        save(updated)
        return updated
    }

    fun remove(id: Long): List<Idea> {
        val updated = load().filter { it.id != id }
        save(updated)
        return updated
    }

    private fun save(list: List<Idea>) {
        val arr = JSONArray()
        for (idea in list) {
            val o = JSONObject()
            o.put("id", idea.id)
            o.put("text", idea.text)
            o.put("createdAt", idea.createdAt)
            arr.put(o)
        }
        val tmp = File(file.parentFile, "ideas.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }
}
