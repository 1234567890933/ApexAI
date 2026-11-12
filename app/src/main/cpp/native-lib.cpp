#include <jni.h>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

/**
 * Apex native core.
 *
 * These are the two operations the engine performs on large byte buffers —
 * Shannon entropy and printable-string extraction. Both are O(n) over the whole
 * file, and on a 40 MB dex the JVM version spent most of its time on bounds
 * checks and object allocation. Doing them here keeps the buffer in one pass
 * with no allocation per byte.
 */

extern "C" JNIEXPORT jstring JNICALL
Java_com_apex_ai_core_NativeBridge_engineVersion(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF("Apex native core 1.0 (C++17)");
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_apex_ai_core_NativeBridge_entropy(JNIEnv *env, jobject /* this */, jbyteArray data) {
    if (data == nullptr) return 0.0;
    const jsize len = env->GetArrayLength(data);
    if (len <= 0) return 0.0;

    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    if (bytes == nullptr) return 0.0;

    int64_t counts[256];
    std::memset(counts, 0, sizeof(counts));
    for (jsize i = 0; i < len; ++i) {
        counts[static_cast<uint8_t>(bytes[i])]++;
    }
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    const double total = static_cast<double>(len);
    double entropy = 0.0;
    for (int i = 0; i < 256; ++i) {
        if (counts[i] == 0) continue;
        const double p = static_cast<double>(counts[i]) / total;
        entropy -= p * (std::log(p) / std::log(2.0));
    }
    return entropy;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_apex_ai_core_NativeBridge_extractStrings(
        JNIEnv *env, jobject /* this */,
        jbyteArray data, jint minLength, jint limit) {

    if (data == nullptr) return nullptr;
    const jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    if (bytes == nullptr) return nullptr;

    std::vector<std::string> found;
    found.reserve(static_cast<size_t>(limit > 0 ? limit : 32));

    std::string current;
    current.reserve(256);

    for (jsize i = 0; i < len && static_cast<jint>(found.size()) < limit; ++i) {
        const uint8_t b = static_cast<uint8_t>(bytes[i]);
        if (b >= 32 && b <= 126) {
            current.push_back(static_cast<char>(b));
            if (current.size() > 512) current.clear();
        } else {
            if (static_cast<jint>(current.size()) >= minLength) {
                found.push_back(current);
            }
            current.clear();
        }
    }
    if (static_cast<jint>(found.size()) < limit &&
        static_cast<jint>(current.size()) >= minLength) {
        found.push_back(current);
    }
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(found.size()), stringClass, nullptr);
    for (size_t i = 0; i < found.size(); ++i) {
        jstring item = env->NewStringUTF(found[i].c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i), item);
        env->DeleteLocalRef(item);
    }
    return result;
}
