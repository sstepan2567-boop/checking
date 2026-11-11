#pragma once
#include <cstddef>
#include <cstdint>

namespace systemdlc::recovered {
struct JsonStringView {
    std::int32_t type;
    std::uint32_t reserved;
    std::uint64_t member_name;
    const std::uint8_t* data;
    std::size_t size;
};
bool bounded_json_string(const JsonStringView* value, std::size_t minimum, std::size_t maximum);
bool valid_config_name(const JsonStringView* value);
}
