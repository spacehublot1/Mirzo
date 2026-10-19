package uz.jarvis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * "Miyalar" ro'yxati va sozlamalar. Har bir miya = provayder turi + kalit +
 * ixtiyoriy model + nom. Bir turdan bir nechta kalit (miya) qo'shsa bo'ladi.
 * Kalitlar faqat shu telefonda, ilovaning shaxsiy xotirasida saqlanadi.
 */
object Prefs {
    private const val NAME = "jarvis_prefs"
    private const val KEY_BRAINS = "brains"
    private const val KEY_AGG = "aggregator_id"
    private const val KEY_VOICE = "voice_reply"

    private fun p(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    data class Brain(
        val id: String,
        val type: String,     // Providers.Type.id
        val label: String,    // foydalanuvchi bergan nom (masalan "ChatGPT-1")
        val key: String,
        val model: String     // bo'sh bo'lsa provayderning standart modeli
    ) {
        fun modelOrDefault(): String = model.ifBlank { Providers.byId(type).defaultModel }
        fun displayName(): String = label.ifBlank { Providers.byId(type).label }
    }

    fun brains(ctx: Context): List<Brain> {
        val arr = JSONArray(p(ctx).getString(KEY_BRAINS, "[]"))
        val list = mutableListOf<Brain>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Brain(
                    id = o.optString("id"),
                    type = o.optString("type"),
                    label = o.optString("label"),
                    key = o.optString("key"),
                    model = o.optString("model")
                )
            )
        }
        return list.filter { it.key.isNotBlank() }
    }

    private fun save(ctx: Context, list: List<Brain>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("type", it.type)
                    .put("label", it.label)
                    .put("key", it.key)
                    .put("model", it.model)
            )
        }
        p(ctx).edit().putString(KEY_BRAINS, arr.toString()).apply()
    }

    fun addBrain(ctx: Context, type: String, label: String, key: String, model: String) {
        val list = brains(ctx).toMutableList()
        list.add(Brain(UUID.randomUUID().toString(), type, label.trim(), key.trim(), model.trim()))
        save(ctx, list)
    }

    fun removeBrain(ctx: Context, id: String) {
        save(ctx, brains(ctx).filterNot { it.id == id })
    }

    fun getAggregator(ctx: Context): Brain? {
        val list = brains(ctx)
        if (list.isEmpty()) return null
        val saved = p(ctx).getString(KEY_AGG, "") ?: ""
        return list.firstOrNull { it.id == saved } ?: list.first()
    }

    fun setAggregator(ctx: Context, id: String) {
        p(ctx).edit().putString(KEY_AGG, id).apply()
    }

    fun isVoiceReply(ctx: Context): Boolean = p(ctx).getBoolean(KEY_VOICE, true)
    fun setVoiceReply(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean(KEY_VOICE, on).apply() }

    // TTS gapirish tezligi (0.5x - 2.0x), standart 1.0
    fun getSpeechRate(ctx: Context): Float = p(ctx).getFloat("speech_rate", 1.0f)
    fun setSpeechRate(ctx: Context, r: Float) { p(ctx).edit().putFloat("speech_rate", r).apply() }

    // Internetdan foydalanish (Gemini Google-qidiruv grounding)
    fun isWebEnabled(ctx: Context): Boolean = p(ctx).getBoolean("web_enabled", true)
    fun setWebEnabled(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("web_enabled", on).apply() }

    // Bulut server (xotira/bilimni saqlash uchun)
    fun getServerUrl(ctx: Context): String = p(ctx).getString("server_url", "")?.trim() ?: ""
    fun getServerToken(ctx: Context): String = p(ctx).getString("server_token", "")?.trim() ?: ""
    fun setServer(ctx: Context, url: String, token: String) {
        p(ctx).edit().putString("server_url", url.trim().trimEnd('/')).putString("server_token", token.trim()).apply()
    }

    fun isConfigured(ctx: Context): Boolean = brains(ctx).isNotEmpty()
}
