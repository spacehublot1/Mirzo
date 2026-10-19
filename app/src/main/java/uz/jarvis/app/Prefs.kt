package uz.jarvis.app

import android.content.Context

/**
 * Telefonda saqlanadigan sozlamalar: har bir AI provayderining API kaliti,
 * "bosh miya" (javoblarni sintez qiluvchi) tanlovi va ovoz sozlamasi.
 * Kalitlar faqat shu telefonda, ilovaning shaxsiy xotirasida saqlanadi.
 */
object Prefs {
    private const val NAME = "jarvis_prefs"

    const val OPENAI = "openai"
    const val GEMINI = "gemini"
    const val DEEPSEEK = "deepseek"

    private fun p(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getKey(ctx: Context, provider: String): String =
        p(ctx).getString("key_$provider", "")?.trim() ?: ""

    fun setKey(ctx: Context, provider: String, value: String) {
        p(ctx).edit().putString("key_$provider", value.trim()).apply()
    }

    /** Kaliti kiritilgan (yoqilgan) provayderlar ro'yxati. */
    fun activeProviders(ctx: Context): List<String> =
        listOf(OPENAI, GEMINI, DEEPSEEK).filter { getKey(ctx, it).isNotBlank() }

    /** Javoblarni birlashtiruvchi "bosh miya". Standart: OpenAI (bo'lmasa birinchi mavjud). */
    fun getAggregator(ctx: Context): String {
        val saved = p(ctx).getString("aggregator", "") ?: ""
        val active = activeProviders(ctx)
        return when {
            saved.isNotBlank() && getKey(ctx, saved).isNotBlank() -> saved
            active.isNotEmpty() -> active.first()
            else -> OPENAI
        }
    }

    fun setAggregator(ctx: Context, provider: String) {
        p(ctx).edit().putString("aggregator", provider).apply()
    }

    fun isVoiceReply(ctx: Context): Boolean = p(ctx).getBoolean("voice_reply", true)
    fun setVoiceReply(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("voice_reply", on).apply() }

    fun isConfigured(ctx: Context): Boolean = activeProviders(ctx).isNotEmpty()

    fun label(provider: String): String = when (provider) {
        OPENAI -> "ChatGPT (OpenAI)"
        GEMINI -> "Gemini (Google)"
        DEEPSEEK -> "DeepSeek"
        else -> provider
    }
}
