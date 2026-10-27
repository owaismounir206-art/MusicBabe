package com.theveloper.pixelplay.data.ai.provider

/** Why a key check failed, in terms the user can act on. */
enum class AiKeyProblem { INVALID_KEY, QUOTA, NETWORK, SERVER, OTHER }

/** Generation presets offered instead of raw sampling numbers. */
enum class AiGenerationPreset(val temperature: Float, val topP: Float) {
    PRECISE(0.3f, 0.8f),
    BALANCED(0.7f, 0.95f),
    CREATIVE(1.1f, 1.0f);

    companion object {
        /** The preset matching the saved values, or null when they were set by hand. */
        fun matching(temperature: Float, topP: Float): AiGenerationPreset? = entries.firstOrNull {
            kotlin.math.abs(it.temperature - temperature) < 0.01f && kotlin.math.abs(it.topP - topP) < 0.01f
        }
    }
}

object AiKeyGuide {

    /** Where to create a key for [provider]; null when the provider has no single console. */
    fun consoleUrl(provider: AiProvider): String? = when (provider) {
        AiProvider.GEMINI -> "https://aistudio.google.com/app/apikey"
        AiProvider.DEEPSEEK -> "https://platform.deepseek.com/api_keys"
        AiProvider.GROQ -> "https://console.groq.com/keys"
        AiProvider.MISTRAL -> "https://console.mistral.ai/api-keys"
        AiProvider.NVIDIA -> "https://build.nvidia.com/"
        AiProvider.KIMI -> "https://platform.moonshot.ai/console/api-keys"
        AiProvider.GLM -> "https://open.bigmodel.cn/usercenter/apikeys"
        AiProvider.OPENAI -> "https://platform.openai.com/api-keys"
        AiProvider.OPENROUTER -> "https://openrouter.ai/keys"
        AiProvider.OLLAMA -> "https://ollama.com/settings/keys"
        AiProvider.CUSTOM -> null
    }

    private val INVALID = listOf(
        "401", "403", "api key not valid", "invalid api key", "invalid_api_key", "incorrect api key",
        "unauthorized", "unauthenticated", "permission_denied", "authentication"
    )
    private val QUOTA = listOf(
        "429", "quota", "rate limit", "rate_limit", "resource_exhausted", "insufficient", "billing",
        "credit", "too many requests"
    )
    private val NETWORK = listOf(
        "unable to resolve host", "unknownhost", "timeout", "timed out", "failed to connect",
        "connection", "network", "no address associated"
    )
    private val SERVER = listOf("500", "502", "503", "504", "unavailable", "overloaded", "internal error")

    /** Reads the error returned by a provider (HTTP code and body vary) into an [AiKeyProblem]. */
    fun classify(message: String?): AiKeyProblem {
        val text = message?.lowercase().orEmpty()
        return when {
            text.isBlank() -> AiKeyProblem.OTHER
            INVALID.any { it in text } -> AiKeyProblem.INVALID_KEY
            QUOTA.any { it in text } -> AiKeyProblem.QUOTA
            NETWORK.any { it in text } -> AiKeyProblem.NETWORK
            SERVER.any { it in text } -> AiKeyProblem.SERVER
            else -> AiKeyProblem.OTHER
        }
    }
}
