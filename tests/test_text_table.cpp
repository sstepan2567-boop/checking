#include "text_table.hpp"
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <stdexcept>
#include <vector>

namespace {
using systemdlc::recovered::TextTable;

struct Entry {
    std::uint64_t key;
    std::uint32_t offset;
    std::uint32_t size;
};

void write_u32(std::vector<std::uint8_t>& bytes, std::size_t offset, std::uint32_t value) {
    for (unsigned int i = 0; i < 4; ++i)
        bytes[offset + i] = static_cast<std::uint8_t>(value >> (i * 8));
}

void write_u64(std::vector<std::uint8_t>& bytes, std::size_t offset, std::uint64_t value) {
    for (unsigned int i = 0; i < 8; ++i)
        bytes[offset + i] = static_cast<std::uint8_t>(value >> (i * 8));
}

std::vector<std::uint8_t> make_resource(const std::vector<Entry>& entries,
                                        std::uint32_t plaintext_size) {
    std::vector<std::uint8_t> resource(12 + entries.size() * 24 + plaintext_size);
    std::memcpy(resource.data(), "TXT1", 4);
    write_u32(resource, 4, static_cast<std::uint32_t>(entries.size()));
    write_u32(resource, 8, plaintext_size);
    for (std::size_t i = 0; i < entries.size(); ++i) {
        const auto offset = 12 + i * 24;
        write_u64(resource, offset, entries[i].key);
        write_u32(resource, offset + 8, entries[i].offset);
        write_u32(resource, offset + 12, entries[i].size);
    }
    return resource;
}

void expect_invalid(const std::uint8_t* resource, std::size_t resource_size,
                    const std::uint8_t* plaintext, std::size_t plaintext_size) {
    bool rejected = false;
    try {
        TextTable table(resource, resource_size, plaintext, plaintext_size);
    } catch (const std::invalid_argument&) {
        rejected = true;
    }
    assert(rejected);
}

void test_empty_table() {
    const auto resource = make_resource({}, 0);
    TextTable table(resource.data(), resource.size(), nullptr, 0);
    assert(table.count() == 0);
    assert(table.find(0) == nullptr);
}

void test_valid_keys_and_low32_collisions() {
    constexpr std::uint64_t first_key = 0x0000000100000005ULL;
    constexpr std::uint64_t second_key = 0xABCDEF0100000005ULL;
    const auto resource = make_resource({{first_key, 0, 4}, {second_key, 4, 4}}, 8);
    const std::vector<std::uint8_t> plaintext{'o', 'n', 'e', 0, 't', 'w', 'o', 0};
    TextTable table(resource.data(), resource.size(), plaintext.data(), plaintext.size());

    assert(table.count() == 2);
    assert(std::strcmp(table.find(first_key), "one") == 0);
    assert(std::strcmp(table.find(second_key), "two") == 0);
    assert(table.find(0xFEDCBA9800000005ULL) == nullptr);
    assert(table.find(7) == nullptr);
}

void test_wraparound_probing() {
    const auto resource = make_resource({{15, 0, 2}, {31, 2, 2}, {47, 4, 2}}, 6);
    const std::vector<std::uint8_t> plaintext{'a', 0, 'b', 0, 'c', 0};
    TextTable table(resource.data(), resource.size(), plaintext.data(), plaintext.size());

    assert(std::strcmp(table.find(15), "a") == 0);
    assert(std::strcmp(table.find(31), "b") == 0);
    assert(std::strcmp(table.find(47), "c") == 0);
    assert(table.find(63) == nullptr);
}

void test_duplicate_keys_rejected() {
    const auto resource = make_resource({{42, 0, 2}, {42, 2, 2}}, 4);
    const std::vector<std::uint8_t> plaintext{'a', 0, 'b', 0};
    expect_invalid(resource.data(), resource.size(), plaintext.data(), plaintext.size());
}

void test_malformed_header_sizes_and_pointers() {
    expect_invalid(nullptr, 0, nullptr, 0);

    auto resource = make_resource({}, 0);
    expect_invalid(resource.data(), 11, nullptr, 0);
    resource[0] = 'X';
    expect_invalid(resource.data(), resource.size(), nullptr, 0);

    resource = make_resource({{1, 0, 2}}, 2);
    resource.pop_back();
    const std::vector<std::uint8_t> plaintext{'x', 0};
    expect_invalid(resource.data(), resource.size(), plaintext.data(), plaintext.size());

    resource = make_resource({{1, 0, 2}}, 2);
    write_u32(resource, 8, 1);
    expect_invalid(resource.data(), resource.size(), plaintext.data(), plaintext.size());

    resource = make_resource({{1, 0, 2}}, 2);
    expect_invalid(resource.data(), resource.size(), plaintext.data(), 1);
    expect_invalid(resource.data(), resource.size(), nullptr, plaintext.size());
}

void test_invalid_records_rejected() {
    const std::vector<std::uint8_t> plaintext{'x', 0};
    auto resource = make_resource({{1, 3, 1}}, 2);
    expect_invalid(resource.data(), resource.size(), plaintext.data(), plaintext.size());

    resource = make_resource({{1, 1, 2}}, 2);
    expect_invalid(resource.data(), resource.size(), plaintext.data(), plaintext.size());

    resource = make_resource({{1, 0, 0}}, 2);
    expect_invalid(resource.data(), resource.size(), plaintext.data(), plaintext.size());

    resource = make_resource({{1, 0, 2}}, 2);
    const std::vector<std::uint8_t> unterminated{'x', 'y'};
    expect_invalid(resource.data(), resource.size(), unterminated.data(), unterminated.size());
}

void test_source_buffers_are_not_retained() {
    constexpr std::uint64_t key = 0x123456789ABCDEF0ULL;
    auto resource = make_resource({{key, 0, 6}}, 6);
    std::vector<std::uint8_t> plaintext{'o', 'w', 'n', 'e', 'd', 0};
    TextTable table(resource.data(), resource.size(), plaintext.data(), plaintext.size());

    write_u64(resource, 12, key + 1);
    plaintext[0] = 'X';
    assert(std::strcmp(table.find(key), "owned") == 0);
    assert(table.find(key + 1) == nullptr);
}
}

int main() {
    test_empty_table();
    test_valid_keys_and_low32_collisions();
    test_wraparound_probing();
    test_duplicate_keys_rejected();
    test_malformed_header_sizes_and_pointers();
    test_invalid_records_rejected();
    test_source_buffers_are_not_retained();
}
