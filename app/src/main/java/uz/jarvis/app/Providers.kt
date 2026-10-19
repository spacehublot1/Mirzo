package uz.jarvis.app

/**
 * Qo'llab-quvvatlanadigan AI provayderlari ro'yxati.
 * Har bir "miya" shu turlardan biriga tegishli bo'ladi; bir turdan bir nechta
 * kalit (bir nechta miya) qo'shsa bo'ladi.
 *
 * style — so'rov formati:
 *   OPENAI — OpenAI-mos (/chat/completions, Bearer kalit)
 *   GEMINI — Google Gemini (kalit URL'da)
 *   CLAUDE — Anthropic (x-api-key + anthropic-version)
 */
object Providers {

    enum class Style { OPENAI, GEMINI, CLAUDE }

    data class Type(
        val id: String,
        val label: String,
        val baseUrl: String,
        val defaultModel: String,
        val style: Style,
        val keyHint: String
    )

    val ALL: List<Type> = listOf(
        Type("openai", "ChatGPT (OpenAI)", "https://api.openai.com/v1/chat/completions", "gpt-4o", Style.OPENAI, "sk-..."),
        Type("gemini", "Gemini (Google)", "https://generativelanguage.googleapis.com/v1beta/models", "gemini-3.6-flash", Style.GEMINI, "AIza..."),
        Type("deepseek", "DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-chat", Style.OPENAI, "sk-..."),
        Type("claude", "Claude (Anthropic)", "https://api.anthropic.com/v1/messages", "claude-3-5-sonnet-latest", Style.CLAUDE, "sk-ant-..."),
        Type("groq", "Groq", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile", Style.OPENAI, "gsk_..."),
        Type("mistral", "Mistral", "https://api.mistral.ai/v1/chat/completions", "mistral-large-latest", Style.OPENAI, "..."),
        Type("openrouter", "OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "openai/gpt-4o-mini", Style.OPENAI, "sk-or-...")
    )

    fun byId(id: String): Type = ALL.firstOrNull { it.id == id } ?: ALL[0]

    val labels: List<String> get() = ALL.map { it.label }
    val ids: List<String> get() = ALL.map { it.id }
}
