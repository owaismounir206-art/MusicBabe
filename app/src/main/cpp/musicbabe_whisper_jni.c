// JNI bridge between MusicBabe (WhisperNative.kt) and whisper.cpp.
// Transcribes 16 kHz mono float PCM into word-sized segments with timestamps, used to align
// known lyrics to the audio. Only one transcription runs at a time (the Kotlin side serialises).
#include <jni.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include "whisper.h"

#define TAG "MusicBabeWhisper"

static atomic_int g_abort = 0;
static atomic_int g_progress = 0;

static bool abort_cb(void * user_data) {
    (void) user_data;
    return atomic_load(&g_abort) != 0;
}

static void progress_cb(struct whisper_context * ctx, struct whisper_state * state, int progress, void * user_data) {
    (void) ctx; (void) state; (void) user_data;
    atomic_store(&g_progress, progress);
}

JNIEXPORT jlong JNICALL
Java_com_theveloper_pixelplay_data_lyrics_align_WhisperNative_nativeInit(JNIEnv * env, jclass cls, jstring path) {
    (void) cls;
    const char * model_path = (*env)->GetStringUTFChars(env, path, NULL);
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context * ctx = whisper_init_from_file_with_params(model_path, cparams);
    (*env)->ReleaseStringUTFChars(env, path, model_path);
    if (ctx == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to load model");
    }
    return (jlong) (intptr_t) ctx;
}

JNIEXPORT void JNICALL
Java_com_theveloper_pixelplay_data_lyrics_align_WhisperNative_nativeFree(JNIEnv * env, jclass cls, jlong ptr) {
    (void) env; (void) cls;
    struct whisper_context * ctx = (struct whisper_context *) (intptr_t) ptr;
    if (ctx != NULL) whisper_free(ctx);
}

JNIEXPORT void JNICALL
Java_com_theveloper_pixelplay_data_lyrics_align_WhisperNative_nativeAbort(JNIEnv * env, jclass cls) {
    (void) env; (void) cls;
    atomic_store(&g_abort, 1);
}

JNIEXPORT jint JNICALL
Java_com_theveloper_pixelplay_data_lyrics_align_WhisperNative_nativeProgress(JNIEnv * env, jclass cls) {
    (void) env; (void) cls;
    return atomic_load(&g_progress);
}

// Returns UTF-8 bytes of "t0_ms\tt1_ms\ttext\n" per word-sized segment, or NULL on failure/abort.
// Bytes, not a jstring: whisper may emit broken UTF-8 that NewStringUTF would abort on.
JNIEXPORT jbyteArray JNICALL
Java_com_theveloper_pixelplay_data_lyrics_align_WhisperNative_nativeTranscribe(
        JNIEnv * env, jclass cls, jlong ptr, jfloatArray samples, jstring language, jstring prompt, jint threads) {
    (void) cls;
    struct whisper_context * ctx = (struct whisper_context *) (intptr_t) ptr;
    if (ctx == NULL || samples == NULL) return NULL;

    atomic_store(&g_abort, 0);
    atomic_store(&g_progress, 0);

    const char * lang = language != NULL ? (*env)->GetStringUTFChars(env, language, NULL) : NULL;
    const char * prm = prompt != NULL ? (*env)->GetStringUTFChars(env, prompt, NULL) : NULL;
    jsize n = (*env)->GetArrayLength(env, samples);
    jfloat * pcm = (*env)->GetFloatArrayElements(env, samples, NULL);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads > 0 ? threads : 4;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.token_timestamps = true;
    params.max_len = 1;             // one word per segment
    params.split_on_word = true;
    params.suppress_nst = true;
    params.language = (lang != NULL && lang[0] != '\0') ? lang : "auto";
    params.initial_prompt = prm;    // the known lyrics bias recognition towards the real words
    params.abort_callback = abort_cb;
    params.abort_callback_user_data = NULL;
    params.progress_callback = progress_cb;
    params.progress_callback_user_data = NULL;

    int rc = whisper_full(ctx, params, pcm, n);
    (*env)->ReleaseFloatArrayElements(env, samples, pcm, JNI_ABORT);

    jbyteArray out = NULL;
    if (rc == 0 && atomic_load(&g_abort) == 0) {
        int segments = whisper_full_n_segments(ctx);
        size_t cap = 4096, len = 0;
        char * buf = malloc(cap);
        if (buf != NULL) {
            buf[0] = '\0';
            for (int i = 0; i < segments; i++) {
                const char * text = whisper_full_get_segment_text(ctx, i);
                long long t0 = (long long) whisper_full_get_segment_t0(ctx, i) * 10; // centiseconds -> ms
                long long t1 = (long long) whisper_full_get_segment_t1(ctx, i) * 10;
                size_t need = strlen(text) + 64;
                if (len + need >= cap) {
                    while (len + need >= cap) cap *= 2;
                    char * grown = realloc(buf, cap);
                    if (grown == NULL) break;
                    buf = grown;
                }
                int w = snprintf(buf + len, cap - len, "%lld\t%lld\t", t0, t1);
                if (w < 0) break;
                len += (size_t) w;
                // Tabs/newlines inside the text would break the format.
                for (const char * p = text; *p; p++) {
                    buf[len++] = (*p == '\t' || *p == '\n' || *p == '\r') ? ' ' : *p;
                }
                buf[len++] = '\n';
                buf[len] = '\0';
            }
            out = (*env)->NewByteArray(env, (jsize) len);
            if (out != NULL) (*env)->SetByteArrayRegion(env, out, 0, (jsize) len, (const jbyte *) buf);
            free(buf);
        }
    }

    if (lang != NULL) (*env)->ReleaseStringUTFChars(env, language, lang);
    if (prm != NULL) (*env)->ReleaseStringUTFChars(env, prompt, prm);
    return out;
}
