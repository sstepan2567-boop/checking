#include "text_table.hpp"
#include <cstring>
#include <limits>
#include <stdexcept>

namespace systemdlc::recovered {
namespace {
std::uint32_t u32(const std::uint8_t* p) {
    return std::uint32_t(p[0]) | (std::uint32_t(p[1]) << 8) |
        (std::uint32_t(p[2]) << 16) | (std::uint32_t(p[3]) << 24);
}
std::uint64_t u64(const std::uint8_t* p) {
    return u32(p) | (std::uint64_t(u32(p + 4)) << 32);
}
}

// Reconstructs the offline wrapper's predecoded, immutable state, not the lazy cipher.
TextTable::TextTable(const std::uint8_t* resource, std::size_t resource_size,
                     const std::uint8_t* plaintext, std::size_t plaintext_size) {
    if (!resource || resource_size < 12 || std::memcmp(resource, "TXT1", 4) != 0)
        throw std::invalid_argument("invalid TXT1 header");
    const auto count = u32(resource + 4);
    const auto bytes = u32(resource + 8);
    if (std::uint64_t(count) * 24 + bytes + 12 != resource_size || plaintext_size != bytes ||
        (!plaintext && plaintext_size != 0))
        throw std::invalid_argument("inconsistent TXT1 sizes");
    std::size_t capacity = 16;
    while (capacity < std::uint64_t(count) * 2) {
        if (capacity > std::numeric_limits<std::size_t>::max() / 2)
            throw std::invalid_argument("TXT1 index overflow");
        capacity *= 2;
    }
    records_.reserve(count);
    slots_.resize(capacity);
    if (plaintext_size) plaintext_.assign(plaintext, plaintext + plaintext_size);
    for (std::uint32_t index = 0; index < count; ++index) {
        const auto* descriptor = resource + 12 + std::size_t(index) * 24;
        Record record{u64(descriptor), u32(descriptor + 8), u32(descriptor + 12)};
        if (record.size == 0 || record.offset > bytes || record.size > bytes - record.offset)
            throw std::invalid_argument("TXT1 record outside cache");
        if (plaintext_[std::size_t(record.offset) + record.size - 1] != 0)
            throw std::invalid_argument("plaintext record is not terminated");
        auto slot = std::uint32_t(record.key) & (capacity - 1);
        while (slots_[slot]) {
            if (records_[slots_[slot] - 1].key == record.key)
                throw std::invalid_argument("duplicate TXT1 key");
            slot = (slot + 1) & (capacity - 1);
        }
        records_.push_back(record);
        slots_[slot] = index + 1;
    }
}

// Client RVA 0x62310, ready-cache path. All 64 key bits participate in equality.
const char* TextTable::find(std::uint64_t key) const noexcept {
    auto slot = std::uint32_t(key) & (slots_.size() - 1);
    while (slots_[slot]) {
        const auto& record = records_[slots_[slot] - 1];
        if (record.key == key) return plaintext_.data() + record.offset;
        slot = (slot + 1) & (slots_.size() - 1);
    }
    return nullptr;
}
}
