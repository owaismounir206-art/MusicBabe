package com.theveloper.pixelplay.data.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiKeyGuideTest {

    @Test
    fun classifiesTypicalProviderErrors() {
        assertEquals(AiKeyProblem.INVALID_KEY, AiKeyGuide.classify("HTTP 400: API key not valid. Please pass a valid API key."))
        assertEquals(AiKeyProblem.INVALID_KEY, AiKeyGuide.classify("401 Unauthorized"))
        assertEquals(AiKeyProblem.QUOTA, AiKeyGuide.classify("429 Too Many Requests: RESOURCE_EXHAUSTED"))
        assertEquals(AiKeyProblem.QUOTA, AiKeyGuide.classify("Insufficient Balance"))
        assertEquals(AiKeyProblem.NETWORK, AiKeyGuide.classify("Unable to resolve host \"api.groq.com\""))
        assertEquals(AiKeyProblem.SERVER, AiKeyGuide.classify("503 Service Unavailable"))
        assertEquals(AiKeyProblem.OTHER, AiKeyGuide.classify(null))
        assertEquals(AiKeyProblem.OTHER, AiKeyGuide.classify("something odd"))
    }

    @Test
    fun everyProviderButCustomHasAConsole() {
        AiProvider.entries.filter { it != AiProvider.CUSTOM }.forEach { assertNotNull(it.name, AiKeyGuide.consoleUrl(it)) }
        assertNull(AiKeyGuide.consoleUrl(AiProvider.CUSTOM))
    }

    @Test
    fun presetsAreRecognisedFromSavedValues() {
        assertEquals(AiGenerationPreset.BALANCED, AiGenerationPreset.matching(0.7f, 0.95f))
        assertNull(AiGenerationPreset.matching(0.42f, 0.5f))
    }
}
