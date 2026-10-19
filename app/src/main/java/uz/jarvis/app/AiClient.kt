package uz.jarvis.app

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
 * "Bosh miya" qatlami. Bitta savolni hamma faol AI'ga parallel yuboradi,
 * keyin tanlangan aggregator (bosh miya) ularning javoblarini ko'rib,
 * yagona eng yaxshi javobni tuzadi va bir "saboq" (o'rganilgan qoida)
 * qaytaradi — bu saboq xotiraga yozilib, keyingi javoblarni yaxshilaydi.
 */
object AiClient {

    // Modellar — kerak bo'lsa shu yerdan o'zgartiriladi
    private const val MODEL_OPENAI = "gpt-4o"
    private const val MODEL_DEEPSEEK = "deepseek-chat"
    private const val MODEL_GEMINI = "gemini-1.5-flash"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class AiAnswer(val provider: String, val text: String, val ok: Boolean, val error: String? = null)
    data class Council(val finalAnswer: String, val raw: List<AiAnswer>, val lesson: String)

    /** Bitta provayderga so'rov. system — xotira/qoidalar, user — savol. */
    private fun callProvider(
        ctx: android.content.Context,
        provider: String,
        system: String,
        user: String
    ): AiAnswer {
        val key = Prefs.getKey(ctx, provider)
        if (key.isBlank()) return AiAnswer(provider, "", false, "kalit yo'q")
        return try {
            when (provider) {
                Prefs.GEMINI -> geminiCall(key, system, user)
                else -> openAiCompatCall(provider, key, system, user)
            }
        } catch (e: Exception) {
            AiAnswer(provider, "", false, e.message ?: e.toString())
        }
    }

    /** OpenAI va DeepSeek bir xil (OpenAI-mos) format ishlatadi. */
    private fun openAiCompatCall(provider: String, key: String, system: String, user: String): AiAnswer {
        val url = if (provider == Prefs.DEEPSEEK)
            "https://api.deepseek.com/chat/completions"
        else
            "https://api.openai.com/v1/chat/completions"
        val model = if (provider == Prefs.DEEPSEEK) MODEL_DEEPSEEK else MODEL_OPENAI

        val messages = JSONArray()
        if (system.isNotBlank()) messages.put(JSONObject().put("role", "system").put("content", system))
        messages.put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .toString()

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .post(body.toRequestBody(JSON))
            .build()

        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return AiAnswer(provider, "", false, "HTTP ${resp.code}: ${briefErr(text)}")
            val content = JSONObject(text)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            return AiAnswer(provider, content.trim(), true)
        }
    }

    private fun geminiCall(key: String, system: String, user: String): AiAnswer {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_GEMINI:generateContent?key=$key"
        val parts = JSONArray().put(JSONObject().put("text", user))
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
        val body = JSONObject().put("contents", contents)
        if (system.isNotBlank()) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }

        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON))
            .build()

        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return AiAnswer(Prefs.GEMINI, "", false, "HTTP ${resp.code}: ${briefErr(text)}")
            val cand = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
            val out = cand.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            return AiAnswer(Prefs.GEMINI, out.trim(), true)
        }
    }

    private fun briefErr(body: String): String = try {
        JSONObject(body).getJSONObject("error").optString("message").take(160)
    } catch (e: Exception) { body.take(160) }

    /**
     * To'liq "kengash" jarayoni: parallel so'rov -> sintez -> saboq.
     * memory — xotiradan kelgan kontekst (foydalanuvchi faktlari + o'rganilgan qoidalar).
     */
    suspend fun council(
        ctx: android.content.Context,
        userPrompt: String,
        memory: String
    ): Council = coroutineScope {
        val providers = Prefs.activeProviders(ctx)
        if (providers.isEmpty()) {
            return@coroutineScope Council("Kalit kiritilmagan. Sozlamalardan API kalit qo'shing.", emptyList(), "")
        }

        val baseSystem = buildString {
            append("Sen 'Jarvis' nomli yordamchining miyalaridan birisan. ")
            append("Foydalanuvchiga u yozgan tilda (asosan o'zbekcha) aniq va foydali javob ber.")
            if (memory.isNotBlank()) {
                append("\n\nXotira (foydalanuvchi haqida va o'rganilgan qoidalar):\n")
                append(memory)
            }
        }

        // 1) Parallel so'rov
        val answers = withContext(Dispatchers.IO) {
            providers.map { p ->
                async { callProvider(ctx, p, baseSystem, userPrompt) }
            }.awaitAll()
        }

        val good = answers.filter { it.ok && it.text.isNotBlank() }
        if (good.isEmpty()) {
            val why = answers.joinToString("; ") { "${Prefs.label(it.provider)}: ${it.error}" }
            return@coroutineScope Council("Hech bir AI javob bermadi. ($why)", answers, "")
        }
        // Agar faqat bitta miya javob bergan bo'lsa — sintezga hojat yo'q
        if (good.size == 1 && providers.size == 1) {
            return@coroutineScope Council(good[0].text, answers, "")
        }

        // 2) Bosh miya sintez qiladi
        val aggregator = Prefs.getAggregator(ctx).let {
            if (Prefs.getKey(ctx, it).isNotBlank()) it else providers.first()
        }
        val synthPrompt = buildString {
            append("Foydalanuvchi savoli:\n").append(userPrompt).append("\n\n")
            append("Quyida turli AI'larning javoblari. Ularni tahlil qil, xatolarini tuzat, ")
            append("eng to'g'ri va to'liq YAGONA javobni o'zbek tilida yoz. ")
            append("Javob oxirida alohida qatorda 'SABOQ: ...' deb, kelajakda shunga o'xshash ")
            append("savollarga yaxshiroq javob berish uchun bitta qisqa qoida yoz (bo'lmasa 'SABOQ: yo'q').\n\n")
            good.forEach { a ->
                append("=== ${Prefs.label(a.provider)} ===\n").append(a.text).append("\n\n")
            }
        }
        val synth = withContext(Dispatchers.IO) {
            callProvider(ctx, aggregator, baseSystem, synthPrompt)
        }

        if (!synth.ok || synth.text.isBlank()) {
            // Sintez bo'lmasa — eng uzun javobni qaytaramiz
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
        val cleanLesson = if (lesson.equals("yo'q", true) || lesson.isBlank()) "" else lesson
        return answer to cleanLesson
    }
}
