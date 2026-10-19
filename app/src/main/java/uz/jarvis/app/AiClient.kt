package uz.jarvis.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * "Bosh miya" qatlami. Bitta savolni hamma miyaga (Prefs.brains) parallel
 * yuboradi, keyin aggregator (bosh miya) javoblarni birlashtirib yagona eng
 * yaxshi javob va bitta "saboq" qaytaradi. Saboq xotiraga yozilib boradi.
 */
object AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class AiAnswer(val brainName: String, val text: String, val ok: Boolean, val error: String? = null)
    data class Council(val finalAnswer: String, val raw: List<AiAnswer>, val lesson: String)

    private data class Raw(val code: Int, val body: String)

    /** So'rovni yuboradi; 429/503 (limit/band) bo'lsa biroz kutib qayta uradi. */
    private fun send(req: Request, maxRetries: Int = 2): Raw {
        var attempt = 0
        while (true) {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if ((resp.code == 429 || resp.code == 503) && attempt < maxRetries) {
                    attempt++
                    try { Thread.sleep(if (attempt == 1) 3000L else 7000L) } catch (_: InterruptedException) {}
                    return@use  // qayta urinamiz
                }
                return Raw(resp.code, body)
            }
        }
    }

    private fun callBrain(brain: Prefs.Brain, system: String, user: String): AiAnswer {
        val type = Providers.byId(brain.type)
        return try {
            when (type.style) {
                Providers.Style.GEMINI -> geminiCall(brain, system, user)
                Providers.Style.CLAUDE -> claudeCall(brain, system, user)
                Providers.Style.OPENAI -> openAiCompatCall(brain, type.baseUrl, system, user)
            }
        } catch (e: Exception) {
            AiAnswer(brain.displayName(), "", false, e.message ?: e.toString())
        }
    }

    private fun openAiCompatCall(brain: Prefs.Brain, url: String, system: String, user: String): AiAnswer {
        val messages = JSONArray()
        if (system.isNotBlank()) messages.put(JSONObject().put("role", "system").put("content", system))
        messages.put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject().put("model", brain.modelOrDefault()).put("messages", messages).toString()

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${brain.key}")
            .post(body.toRequestBody(JSON))
            .build()

        val raw = send(req)
        if (raw.code !in 200..299) return AiAnswer(brain.displayName(), "", false, "HTTP ${raw.code}: ${briefErr(raw.body)}")
        val content = JSONObject(raw.body).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        return AiAnswer(brain.displayName(), content.trim(), true)
    }

    private fun geminiCall(brain: Prefs.Brain, system: String, user: String): AiAnswer {
        // Avval tanlangan model; agar 429 (limit) bo'lsa — yengilroq flash-lite'ga o'tamiz
        val models = buildList {
            add(brain.modelOrDefault())
            if (!brain.modelOrDefault().contains("lite")) add("gemini-3.5-flash-lite")
        }.distinct()

        var lastErr = "noma'lum"
        for (model in models) {
            val url = "${Providers.byId("gemini").baseUrl}/$model:generateContent?key=${brain.key}"
            val parts = JSONArray().put(JSONObject().put("text", user))
            val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
            val body = JSONObject().put("contents", contents)
            if (system.isNotBlank()) {
                body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            }
            val req = Request.Builder().url(url).post(body.toString().toRequestBody(JSON)).build()
            val raw = send(req)
            if (raw.code in 200..299) {
                val out = JSONObject(raw.body).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                return AiAnswer(brain.displayName(), out.trim(), true)
            }
            lastErr = "HTTP ${raw.code}: ${briefErr(raw.body)}"
            if (raw.code != 429 && raw.code != 503) break   // boshqa xato bo'lsa — qayta urinmaymiz
        }
        return AiAnswer(brain.displayName(), "", false, lastErr)
    }

    private fun claudeCall(brain: Prefs.Brain, system: String, user: String): AiAnswer {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject()
            .put("model", brain.modelOrDefault())
            .put("max_tokens", 2048)
            .put("messages", messages)
        if (system.isNotBlank()) body.put("system", system)

        val req = Request.Builder()
            .url(Providers.byId("claude").baseUrl)
            .addHeader("x-api-key", brain.key)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val raw = send(req)
        if (raw.code !in 200..299) return AiAnswer(brain.displayName(), "", false, "HTTP ${raw.code}: ${briefErr(raw.body)}")
        val out = JSONObject(raw.body).getJSONArray("content").getJSONObject(0).getString("text")
        return AiAnswer(brain.displayName(), out.trim(), true)
    }

    private fun briefErr(body: String): String = try {
        val o = JSONObject(body)
        when {
            o.has("error") && o.get("error") is JSONObject -> o.getJSONObject("error").optString("message").take(160)
            o.has("error") -> o.optString("error").take(160)
            else -> body.take(160)
        }
    } catch (e: Exception) { body.take(160) }

    suspend fun council(ctx: Context, userPrompt: String, memory: String): Council = coroutineScope {
        val brains = Prefs.brains(ctx)
        if (brains.isEmpty()) {
            return@coroutineScope Council("Miya qo'shilmagan. Sozlamalardan API kalit qo'shing.", emptyList(), "")
        }

        val baseSystem = buildString {
            append("Sen 'Jarvis' — aqlli, bilimdon va ishonchli shaxsiy yordamchisan. ")
            append("Qoidalar:\n")
            append("1) Foydalanuvchi yozgan tilda javob ber (asosan o'zbekcha, u rus/ingliz yozsa — o'sha tilda).\n")
            append("2) Javobing aniq, to'liq va foydali bo'lsin. Savolni diqqat bilan o'qib, mohiyatiga javob ber.\n")
            append("3) Kerak bo'lsa bosqichma-bosqich tushuntir, misol keltir. Bilmasang — taxmin qilma, bilmasligingni ayt.\n")
            append("4) Qisqa savolga qisqa, murakkab savolga batafsil javob ber. Ortiqcha so'z ishlatma.\n")
            append("5) Xotirangdagi ma'lumotlardan foydalan, lekin faqat tegishli bo'lsa.")
            if (memory.isNotBlank()) append("\n\nXotira (foydalanuvchi haqida va o'rgangan narsalaring):\n").append(memory)
        }

        // 1) Parallel so'rov — har bir miya (bir xil provayderning turli kalitlari ham)
        val answers = withContext(Dispatchers.IO) {
            brains.map { b -> async { callBrain(b, baseSystem, userPrompt) } }.awaitAll()
        }

        val good = answers.filter { it.ok && it.text.isNotBlank() }
        if (good.isEmpty()) {
            val why = answers.joinToString("; ") { "${it.brainName}: ${it.error}" }
            return@coroutineScope Council("Hech bir miya javob bermadi. ($why)", answers, "")
        }
        if (good.size == 1) {
            return@coroutineScope Council(good[0].text, answers, "")
        }

        // 2) Bosh miya sintez qiladi
        val aggregator = Prefs.getAggregator(ctx) ?: brains.first()
        val synthPrompt = buildString {
            append("Foydalanuvchi savoli:\n").append(userPrompt).append("\n\n")
            append("Quyida turli AI'larning javoblari. Ularni tahlil qil, xatolarini tuzat, ")
            append("eng to'g'ri va to'liq YAGONA javobni o'zbek tilida yoz. ")
            append("Javob oxirida alohida qatorda 'SABOQ: ...' deb, kelajakda yaxshiroq javob ")
            append("berish uchun bitta qisqa qoida yoz (bo'lmasa 'SABOQ: yo'q').\n\n")
            good.forEach { a -> append("=== ${a.brainName} ===\n").append(a.text).append("\n\n") }
        }
        val synth = withContext(Dispatchers.IO) { callBrain(aggregator, baseSystem, synthPrompt) }

        if (!synth.ok || synth.text.isBlank()) {
            val best = good.maxByOrNull { it.text.length }!!
            return@coroutineScope Council(best.text, answers, "")
        }
        val (finalAns, lesson) = splitLesson(synth.text)
        Council(finalAns, answers, lesson)
    }

    private fun splitLesson(text: String): Pair<String, String> {
        val idx = text.lastIndexOf("SABOQ:")
        if (idx < 0) return text.trim() to ""
        val answer = text.substring(0, idx).trim()
        val lesson = text.substring(idx + 6).trim()
        val clean = if (lesson.equals("yo'q", true) || lesson.isBlank()) "" else lesson
        return answer to clean
    }
}
