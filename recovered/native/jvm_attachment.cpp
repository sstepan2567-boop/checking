#include "jvm_attachment.hpp"

namespace systemdlc::recovered {
// Reconstructed excerpt of client RVA 0x3e3aa0; excludes TLS and later initialization.
bool attach_existing_vm(EnumerateVms enumerate, JvmAttachment& result) {
    result = {};
    jsize count = 0;
    if (enumerate(&result.vm, 1, &count) != JNI_OK || count == 0 || result.vm == nullptr) {
        return false;
    }
    void* environment = nullptr;
    jint status = result.vm->GetEnv(&environment, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        JavaVMAttachArgs args{JNI_VERSION_1_6, nullptr, nullptr};
        status = result.vm->AttachCurrentThreadAsDaemon(&environment, &args);
    }
    result.env = static_cast<JNIEnv*>(environment);
    return status == JNI_OK;
}
}
