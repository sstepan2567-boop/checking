#include "name_mapping.hpp"
#include "config_validation.hpp"
#include "text_table.hpp"
#include <exception>
extern "C" {
bool recovered_bounded_string(const systemdlc::recovered::JsonStringView* value,
                              std::size_t minimum, std::size_t maximum) {
    return systemdlc::recovered::bounded_json_string(value, minimum, maximum);
}
bool recovered_config_name(const systemdlc::recovered::JsonStringView* value) {
    return systemdlc::recovered::valid_config_name(value);
}

void* recovered_text_create(const std::uint8_t* resource, std::size_t resource_size,
                            const std::uint8_t* plaintext, std::size_t plaintext_size) {
    try {
        return new systemdlc::recovered::TextTable(resource, resource_size, plaintext, plaintext_size);
    } catch (const std::exception&) {
        return nullptr;
    }
}
void recovered_text_destroy(void* table) {
    delete static_cast<systemdlc::recovered::TextTable*>(table);
}
std::int64_t recovered_text_offset(void* table, std::uint64_t key) {
    auto* text = static_cast<systemdlc::recovered::TextTable*>(table);
    const auto* found = text->find(key);
    return found ? found - text->data() : -1;
}
}

extern "C" const char* recovered_mapped_name(const char* input, std::size_t size,
                                             std::int32_t mode, std::uint32_t name_space) {
    return systemdlc::recovered::mapped_name(std::string_view(input, size), mode, name_space);
}
