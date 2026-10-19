package uz.jarvis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Jarvis'ning "o'sib boradigan" xotirasi. Ikki qism:
 *  1) Oxirgi suhbatlar (kontekst) — javob berishda esga olinadi.
 *  2) O'rganilgan qoidalar (SABOQ'lar) — bosh miya har safar yangi saboq
 *     qaytarsa shu yerga qo'shiladi va keyingi javoblarga ta'sir qiladi.
 * Shunday qilib "yosh miya" vaqt o'tgani sari yaxshilanib boradi.
 */
object Memory {
    private const val NAME = "jarvis_memory"
    private const val KEY_TURNS = "turns"
    private const val KEY_LESSONS = "lessons"
    private const val MAX_TURNS = 20          // kontekst uchun saqlanadigan suhbatlar
    private const val MAX_LESSONS = 50         // o'rganilgan qoidalar chegarasi

    private fun p(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---- suhbatlar ----
    fun addTurn(ctx: Context, user: String, assistant: String) {
        val arr = JSONArray(p(ctx).getString(KEY_TURNS, "[]"))
        arr.put(JSONObject().put("u", user).put("a", assistant))
        // oxirgi MAX_TURNS tasini qoldiramiz
        val trimmed = JSONArray()
        val start = maxOf(0, arr.length() - MAX_TURNS)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        p(ctx).edit().putString(KEY_TURNS, trimmed.toString()).apply()
    }

    private fun recentTurns(ctx: Context, n: Int): String {
        val arr = JSONArray(p(ctx).getString(KEY_TURNS, "[]"))
        if (arr.length() == 0) return ""
        val sb = StringBuilder()
        val start = maxOf(0, arr.length() - n)
        for (i in start until arr.length()) {
            val o = arr.getJSONObject(i)
            sb.append("Foydalanuvchi: ").append(o.getString("u")).append('\n')
            sb.append("Jarvis: ").append(o.getString("a")).append('\n')
        }
        return sb.toString().trim()
    }

    // ---- o'rganilgan qoidalar ----
    fun addLesson(ctx: Context, lesson: String) {
        val clean = lesson.trim()
        if (clean.isBlank() || clean.length < 4) return
        val arr = JSONArray(p(ctx).getString(KEY_LESSONS, "[]"))
        // takrorlanmasin
        for (i in 0 until arr.length()) {
            if (arr.getString(i).equals(clean, ignoreCase = true)) return
        }
        arr.put(clean)
        val trimmed = JSONArray()
        val start = maxOf(0, arr.length() - MAX_LESSONS)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        p(ctx).edit().putString(KEY_LESSONS, trimmed.toString()).apply()
    }

    private fun lessons(ctx: Context): String {
        val arr = JSONArray(p(ctx).getString(KEY_LESSONS, "[]"))
        if (arr.length() == 0) return ""
        val sb = StringBuilder()
        for (i in 0 until arr.length()) sb.append("- ").append(arr.getString(i)).append('\n')
        return sb.toString().trim()
    }

    fun lessonCount(ctx: Context): Int = JSONArray(p(ctx).getString(KEY_LESSONS, "[]")).length()

    /** Javob berishdan oldin bosh miyaga beriladigan to'liq xotira konteksti. */
    fun buildContext(ctx: Context): String {
        val sb = StringBuilder()
        val l = lessons(ctx)
        if (l.isNotBlank()) {
            sb.append("Avval o'rgangan qoidalaring:\n").append(l).append("\n\n")
        }
        val t = recentTurns(ctx, 6)
        if (t.isNotBlank()) {
            sb.append("Oxirgi suhbat:\n").append(t)
        }
        return sb.toString().trim()
    }

    fun clear(ctx: Context) {
        p(ctx).edit().clear().apply()
    }
}
