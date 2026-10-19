package uz.jarvis.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Jarvis bulut serveri bilan sinxronlash. Mahalliy bilim/qoidalarni serverga
 * yuboradi (push) va serverdagilarni telefonga oladi (pull) — shunday qilib
 * baza bulutda saqlanadi va telefon xotirasi to'lmaydi.
 */
object Cloud {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class Result(val ok: Boolean, val message: String)

    fun isConfigured(ctx: Context) = Prefs.getServerUrl(ctx).isNotBlank()

    suspend fun sync(ctx: Context): Result = withContext(Dispatchers.IO) {
        val base = Prefs.getServerUrl(ctx).trimEnd('/')
        if (base.isBlank()) return@withContext Result(false, "Server manzili kiritilmagan")
        val token = Prefs.getServerToken(ctx)
        try {
            // 1) PUSH — mahalliy bilim va qoidalarni yuboramiz
            val payload = JSONObject()
            val kArr = JSONArray()
            Knowledge.all(ctx).forEach {
                kArr.put(JSONObject().put("id", it.id).put("text", it.text).put("source", it.source).put("ts", it.ts / 1000))
            }
            payload.put("knowledge", kArr)
            val lArr = JSONArray()
            Memory.lessonsList(ctx).forEach { lArr.put(it) }
            payload.put("lessons", lArr)

            val pushReq = Request.Builder()
                .url("$base/push")
                .apply { if (token.isNotBlank()) addHeader("X-Token", token) }
                .post(payload.toString().toRequestBody(JSON))
                .build()
            http.newCall(pushReq).execute().use { r ->
                if (!r.isSuccessful) return@withContext Result(false, "Server xatosi (push): HTTP ${r.code}")
            }

            // 2) PULL — serverdagi bilim/qoidalarni olib, mahalliyga qo'shamiz
            val pullReq = Request.Builder()
                .url("$base/pull")
                .apply { if (token.isNotBlank()) addHeader("X-Token", token) }
                .get().build()
            http.newCall(pullReq).execute().use { r ->
                val body = r.body?.string() ?: ""
                if (!r.isSuccessful) return@withContext Result(false, "Server xatosi (pull): HTTP ${r.code}")
                val o = JSONObject(body)
                val k = o.optJSONArray("knowledge") ?: JSONArray()
                for (i in 0 until k.length()) {
                    val it = k.getJSONObject(i)
                    Knowledge.addRemote(ctx, it.optString("id"), it.optString("text"),
                        it.optString("source"), it.optLong("ts") * 1000)
                }
                val l = o.optJSONArray("lessons") ?: JSONArray()
                for (i in 0 until l.length()) Memory.addLesson(ctx, l.getString(i))
            }

            Result(true, "Sinxronlandi ✓")
        } catch (e: Exception) {
            Result(false, "Ulanib bo'lmadi: ${e.message}")
        }
    }
}
