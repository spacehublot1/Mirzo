package uz.jarvis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Jarvis'ning bilim bazasi — foydalanuvchi o'zi "o'rgatgan" ma'lumotlar.
 * Hujjat, matn, fakt yoki tuzatish qo'shiladi; javob berishdan oldin savolga
 * eng mos bo'laklar tanlab, bosh miyaga beriladi (soddalashtirilgan RAG).
 *
 * Hozircha telefon xotirasida saqlanadi. Keyingi bosqichda bu baza bulutga
 * (online serverga) ko'chiriladi — telefon xotirasi to'lmasligi uchun.
 */
object Knowledge {
    private const val NAME = "jarvis_knowledge"
    private const val KEY = "items"
    private const val MAX_ITEMS = 500
    private const val TOP_K = 6                 // javobga qo'shiladigan bo'laklar soni
    private const val PER_ITEM_CHARS = 700      // har bo'lakdan olinadigan belgi
    private const val TOTAL_CHARS = 3500        // umumiy chegara (prompt shishib ketmasin)

    private fun p(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    data class Item(val id: String, val text: String, val source: String, val ts: Long)

    fun all(ctx: Context): List<Item> {
        val arr = JSONArray(p(ctx).getString(KEY, "[]"))
        val list = mutableListOf<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(Item(o.optString("id"), o.optString("text"), o.optString("source"), o.optLong("ts")))
        }
        return list.sortedByDescending { it.ts }
    }

    private fun save(ctx: Context, list: List<Item>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("text", it.text).put("source", it.source).put("ts", it.ts))
        }
        p(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(ctx: Context, text: String, source: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val list = all(ctx).toMutableList()
        list.add(0, Item(UUID.randomUUID().toString(), clean, source.trim().ifBlank { "qo'lda" }, System.currentTimeMillis()))
        save(ctx, list.take(MAX_ITEMS))
    }

    fun remove(ctx: Context, id: String) = save(ctx, all(ctx).filterNot { it.id == id })

    /** Serverdan kelgan elementni id bo'yicha (takrorlamay) qo'shadi. */
    fun addRemote(ctx: Context, id: String, text: String, source: String, ts: Long) {
        if (id.isBlank() || text.isBlank()) return
        val list = all(ctx)
        if (list.any { it.id == id }) return
        val m = list.toMutableList()
        m.add(Item(id, text, source.ifBlank { "bulut" }, if (ts > 0) ts else System.currentTimeMillis()))
        save(ctx, m.take(MAX_ITEMS))
    }

    fun count(ctx: Context): Int = all(ctx).size

    /** Savolga eng mos bilimlarni tanlab, prompt uchun matn qaytaradi. */
    fun retrieve(ctx: Context, query: String): String {
        val items = all(ctx)
        if (items.isEmpty()) return ""
        val words = query.lowercase()
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length >= 3 }
            .toSet()

        val ranked = if (words.isEmpty()) {
            items.take(TOP_K)
        } else {
            items.map { it to score(it.text, words) }
                .sortedByDescending { it.second }
                .filter { it.second > 0 }
                .take(TOP_K)
                .map { it.first }
                .ifEmpty { items.take(2) }   // mos kelmasa — eng yangi 2 tasi
        }

        val sb = StringBuilder("Bilim bazangdan tegishli ma'lumot:\n")
        var total = 0
        for (it in ranked) {
            val chunk = it.text.take(PER_ITEM_CHARS)
            if (total + chunk.length > TOTAL_CHARS) break
            sb.append("- ").append(chunk)
            if (it.source.isNotBlank()) sb.append("  (manba: ").append(it.source).append(")")
            sb.append('\n')
            total += chunk.length
        }
        return sb.toString().trim()
    }

    private fun score(text: String, words: Set<String>): Int {
        val lower = text.lowercase()
        return words.count { lower.contains(it) }
    }

    fun clear(ctx: Context) = p(ctx).edit().clear().apply()
}
