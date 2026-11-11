#pragma once
#include <array>
#include <cstdint>
#include <string_view>

namespace systemdlc::recovered {
using NameCandidates = std::array<const char*, 5>;

// Numeric modes are preserved; their Minecraft-version labels are not established.
const char* mapped_name(std::string_view input, std::int32_t mode,
                        std::uint32_t name_space) noexcept;
const char* select_name(const NameCandidates& candidates, std::int32_t mode,
                       std::uint32_t name_space) noexcept;
}
