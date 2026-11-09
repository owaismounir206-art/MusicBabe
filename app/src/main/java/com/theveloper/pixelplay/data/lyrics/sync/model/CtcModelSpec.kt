package com.theveloper.pixelplay.data.lyrics.sync.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The manifest written by scripts/models/export_ctc_model.py next to each ONNX model. */
@Serializable
data class CtcModelSpec(
    val id: String,
    val language: String,
    val source: String = "",
    val license: String = "",
    val sampleRate: Int,
    val frameStride: Int,
    val receptiveField: Int,
    val normalizeInput: Boolean,
    val input: String,
    val output: String,
    val outputIsLogProbs: Boolean,
    val blankId: Int,
    val wordDelimiterId: Int? = null,
    val vocab: List<String>,
    val bytes: Long = 0,
    val sha256: String = "",
) {
    val msPerFrame: Double get() = frameStride * 1000.0 / sampleRate

    /** Model frames produced for [samples] input samples (convolutional encoder, no padding). */
    fun framesFor(samples: Int): Int = if (samples < receptiveField) 0 else (samples - receptiveField) / frameStride + 1

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        fun parse(text: String): CtcModelSpec = JSON.decodeFromString(serializer(), text)
    }
}
