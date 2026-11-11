#pragma once
#include <cstddef>
#include <cstdint>
#include <vector>

namespace systemdlc::recovered {
class TextTable {
public:
    TextTable(const std::uint8_t* resource, std::size_t resource_size,
              const std::uint8_t* plaintext, std::size_t plaintext_size);
    const char* find(std::uint64_t key) const noexcept;
    const char* data() const noexcept { return plaintext_.data(); }
    std::size_t count() const noexcept { return records_.size(); }
private:
    struct Record {
        std::uint64_t key;
        std::uint32_t offset;
        std::uint32_t size;
    };
    std::vector<Record> records_;
    std::vector<std::uint32_t> slots_;
    std::vector<char> plaintext_;
};
}
