#include "config_validation.hpp"
#include <cstring>

namespace systemdlc::recovered {
static_assert(offsetof(JsonStringView, data) == 0x10);
static_assert(offsetof(JsonStringView, size) == 0x18);

// Wrapper RVA 0x7b40. This byte predicate does not validate UTF-8.
bool bounded_json_string(const JsonStringView* value, std::size_t minimum, std::size_t maximum) {
    return value != nullptr && value->type == 3 && minimum <= value->size &&
        value->size <= maximum && std::memchr(value->data, 0, value->size) == nullptr;
}

// Wrapper RVA 0x7b80. Names must have a non-space byte and no ASCII controls.
bool valid_config_name(const JsonStringView* value) {
    if (!bounded_json_string(value, 1, 512)) return false;
    bool non_space = false;
    for (std::size_t index = 0; index < value->size; ++index) {
        const auto byte = value->data[index];
        if (byte < 0x20 || byte == 0x7f) return false;
        non_space = non_space || byte > 0x20;
    }
    return non_space;
}
}
