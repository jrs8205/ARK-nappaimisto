// JNI-silta whisper.cpp:hen: mallin lataus, puheen tunnistus ja vapautus.
// Tulos palautetaan UTF-8-tavuina, koska NewStringUTF vaatisi muunnetun
// UTF-8:n eikä kestäisi nelitavuisia merkkejä.

#include <jni.h>
#include <string>
#include "whisper.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_org_jarsi_ark_dictation_WhisperNative_nativeInit(
    JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jbyteArray JNICALL
Java_org_jarsi_ark_dictation_WhisperNative_nativeTranscribe(
    JNIEnv *env, jobject, jlong handle, jfloatArray pcm, jstring prompt,
    jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) return nullptr;

    const jsize n = env->GetArrayLength(pcm);
    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);
    const char *promptChars =
        prompt ? env->GetStringUTFChars(prompt, nullptr) : nullptr;

    whisper_full_params params =
        whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "fi";
    params.translate = false;
    params.no_timestamps = true;
    // Enkooderi käsittelisi aina täyden 30 s ikkunan (1500 askelta,
    // 50/s) puheen pituudesta riippumatta; rajaus pätkän todelliseen
    // pituuteen nopeuttaa lyhyet pätkät moninkertaisesti.
    const int audioCtx = n / (16000 / 50) + 32;
    if (audioCtx < 1500) {
        params.audio_ctx = audioCtx;
    }
    // Jokainen pätkä tunnistetaan itsenäisesti; jatkuvuus annetaan
    // edellisen pätkän tekstinä kehotteessa.
    params.no_context = true;
    params.n_threads = threads;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    if (promptChars != nullptr && promptChars[0] != '\0') {
        params.initial_prompt = promptChars;
    }

    std::string result;
    if (whisper_full(ctx, params, samples, n) == 0) {
        const int segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < segments; i++) {
            result += whisper_full_get_segment_text(ctx, i);
        }
    }

    if (promptChars != nullptr) {
        env->ReleaseStringUTFChars(prompt, promptChars);
    }
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);

    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(result.size()));
    if (bytes != nullptr && !result.empty()) {
        env->SetByteArrayRegion(
            bytes, 0, static_cast<jsize>(result.size()),
            reinterpret_cast<const jbyte *>(result.data()));
    }
    return bytes;
}

JNIEXPORT void JNICALL
Java_org_jarsi_ark_dictation_WhisperNative_nativeFree(
    JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}

} // extern "C"
