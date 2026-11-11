#include "jvm_attachment.hpp"
#include <cassert>
#include <cstddef>

static_assert(sizeof(void*) == 8);
static_assert(offsetof(JNIInvokeInterface_, GetEnv) == 0x30);
static_assert(offsetof(JNIInvokeInterface_, AttachCurrentThreadAsDaemon) == 0x38);

namespace {
JNIEnv environment{};
JavaVM vm{};
jint enumeration_status = JNI_OK, environment_status = JNI_OK, attach_status = JNI_OK;
jsize count = 1;
bool null_vm = false;
int get_calls = 0, attach_calls = 0;

jint JNICALL enumerate(JavaVM** out, jsize capacity, jsize* found) {
    assert(capacity == 1);
    *out = null_vm ? nullptr : &vm;
    *found = count;
    return enumeration_status;
}
jint JNICALL get_env(JavaVM* current, void** out, jint version) {
    assert(current == &vm && version == JNI_VERSION_1_6);
    ++get_calls;
    if (environment_status == JNI_OK) *out = &environment;
    return environment_status;
}
jint JNICALL attach(JavaVM* current, void** out, void* args) {
    assert(current == &vm);
    auto* options = static_cast<JavaVMAttachArgs*>(args);
    assert(options->version == JNI_VERSION_1_6);
    assert(options->name == nullptr && options->group == nullptr);
    ++attach_calls;
    if (attach_status == JNI_OK) *out = &environment;
    return attach_status;
}
}

int main() {
    JNIInvokeInterface_ functions{};
    functions.GetEnv = get_env;
    functions.AttachCurrentThreadAsDaemon = attach;
    vm.functions = &functions;
    using namespace systemdlc::recovered;
    JvmAttachment result;
    assert(attach_existing_vm(enumerate, result));
    assert(result.vm == &vm && result.env == &environment && attach_calls == 0);
    environment_status = JNI_EDETACHED;
    assert(attach_existing_vm(enumerate, result));
    assert(attach_calls == 1 && result.env == &environment);
    attach_status = JNI_ERR;
    assert(!attach_existing_vm(enumerate, result));
    environment_status = JNI_EVERSION;
    assert(!attach_existing_vm(enumerate, result));
    assert(attach_calls == 2);
    const int calls = get_calls;
    enumeration_status = JNI_ERR;
    assert(!attach_existing_vm(enumerate, result));
    enumeration_status = JNI_OK;
    count = 0;
    assert(!attach_existing_vm(enumerate, result));
    count = 1;
    null_vm = true;
    assert(!attach_existing_vm(enumerate, result));
    assert(get_calls == calls);
}
