#pragma once
#include <jni.h>

namespace systemdlc::recovered {
using EnumerateVms = jint (JNICALL *)(JavaVM**, jsize, jsize*);
struct JvmAttachment {
    JavaVM* vm = nullptr;
    JNIEnv* env = nullptr;
};
bool attach_existing_vm(EnumerateVms enumerate, JvmAttachment& result);
}
