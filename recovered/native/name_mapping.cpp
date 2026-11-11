#include "name_mapping.hpp"
#include <algorithm>
#include <iterator>

namespace systemdlc::recovered {
namespace {
struct MultiMapping {
    std::uint64_t hash;
    NameCandidates names;
};
struct SingleMapping {
    std::uint64_t hash;
    const char* name;
};
#include "name_mapping_data.inc"

std::uint64_t name_hash(std::string_view input) noexcept {
    std::uint64_t value = 0xcca3d13b6510feabULL;
    for (unsigned char byte : input) value = (value ^ byte) * 0x100000001b3ULL;
    return value;
}

template<class Row, std::size_t N>
const Row* lookup(const Row (&rows)[N], std::uint64_t hash) noexcept {
    const auto* row = std::lower_bound(std::begin(rows), std::end(rows), hash,
        [](const Row& entry, std::uint64_t value) { return entry.hash < value; });
    return row != std::end(rows) && row->hash == hash ? row : nullptr;
}
}

// Client RVAs 0x5c6810, 0x5c69f0, 0x5c6ab0; decoded tables replace TXT1 lookups.
const char* mapped_name(std::string_view input, std::int32_t mode,
                        std::uint32_t name_space) noexcept {
    const auto hash = name_hash(input);
    if (mode < 3) {
        if (mode == 1 && name_space < 5) {
            if (const auto* row = lookup(mode1_names, hash)) return row->names[name_space];
        }
    } else if (name_space == 1) {
        if (mode >= 5) {
            if (const auto* row = lookup(mode5_names, hash)) return row->name;
        }
        if (mode >= 4) {
            if (const auto* row = lookup(mode4_names, hash)) return row->name;
        }
        if (const auto* row = lookup(mode3_names, hash)) return row->name;
    }
    return nullptr;
}

// Client RVA 0x34d70; a non-null empty mapping is a successful result.
const char* select_name(const NameCandidates& candidates, std::int32_t mode,
                       std::uint32_t name_space) noexcept {
    if (mode != 2) {
        if (!candidates[2]) return nullptr;
        if (const auto* mapped = mapped_name(candidates[2], mode, name_space)) return mapped;
    }
    return candidates[name_space < candidates.size() ? name_space : 0];
}
}
