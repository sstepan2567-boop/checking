#include "jvm_attachment.hpp"
#include <cstdlib>
#include <iostream>
#include <thread>

namespace {
void require(bool condition, const char* message) {
    if (!condition) {
        std::cerr << message << '\n';
        std::abort();
    }
}
void no_exception(JNIEnv* env) {
    require(!env->ExceptionCheck(), "unexpected Java exception");
}
}

int main() {
    using namespace systemdlc::recovered;
    JvmAttachment absent;
    require(!attach_existing_vm(JNI_GetCreatedJavaVMs, absent), "unexpected preexisting JVM");
    JavaVM* vm = nullptr;
    JNIEnv* env = nullptr;
    char check_jni[] = "-Xcheck:jni";
    JavaVMOption option{check_jni, nullptr};
    JavaVMInitArgs options{JNI_VERSION_1_6, 1, &option, JNI_FALSE};
    require(JNI_CreateJavaVM(&vm, reinterpret_cast<void**>(&env), &options) == JNI_OK,
            "cannot create test JVM");
    JvmAttachment main_thread;
    require(attach_existing_vm(JNI_GetCreatedJavaVMs, main_thread), "main-thread attach failed");
    require(main_thread.vm == vm && main_thread.env == env, "main-thread environment changed");

    std::thread worker([vm] {
        void* before = nullptr;
        require(vm->GetEnv(&before, JNI_VERSION_1_6) == JNI_EDETACHED, "worker already attached");
        JvmAttachment attached;
        require(attach_existing_vm(JNI_GetCreatedJavaVMs, attached), "worker attach failed");
        require(attached.vm == vm && attached.env, "missing worker environment");
        void* current = nullptr;
        require(vm->GetEnv(&current, JNI_VERSION_1_6) == JNI_OK && current == attached.env,
                "worker environment mismatch");
        JNIEnv* e = attached.env;
        jclass type = e->FindClass("java/lang/Thread");
        no_exception(e);
        require(type != nullptr, "Thread class missing");
        jmethodID current_thread = e->GetStaticMethodID(type, "currentThread", "()Ljava/lang/Thread;");
        no_exception(e);
        require(current_thread != nullptr, "currentThread method missing");
        jmethodID is_daemon = e->GetMethodID(type, "isDaemon", "()Z");
        no_exception(e);
        require(is_daemon != nullptr, "isDaemon method missing");
        jobject thread = e->CallStaticObjectMethod(type, current_thread);
        no_exception(e);
        require(thread != nullptr, "current thread missing");
        jboolean daemon = e->CallBooleanMethod(thread, is_daemon);
        no_exception(e);
        require(daemon == JNI_TRUE, "worker was not attached as daemon");
        JvmAttachment repeated;
        require(attach_existing_vm(JNI_GetCreatedJavaVMs, repeated), "repeat attach failed");
        require(repeated.env == e, "repeat attach changed environment");
        e->DeleteLocalRef(thread);
        e->DeleteLocalRef(type);
        require(vm->DetachCurrentThread() == JNI_OK, "detach failed");
        require(vm->GetEnv(&current, JNI_VERSION_1_6) == JNI_EDETACHED, "worker remained attached");
    });
    worker.join();
    require(vm->DestroyJavaVM() == JNI_OK, "cannot destroy test JVM");
    std::cout << "Real JVM attachment: existing thread, daemon worker, repeat attach and detach pass.\n";
}
