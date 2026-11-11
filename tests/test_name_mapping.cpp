#include "name_mapping.hpp"
#include <cassert>
#include <cstring>
#include <limits>

using namespace systemdlc::recovered;

int main() {
    const NameCandidates candidates{"obfuscated", "named", "unmapped/Class", "yarn", "legacy"};
    for (std::uint32_t i = 0; i < 5; ++i) {
        assert(select_name(candidates, 2, i) == candidates[i]);
        assert(select_name(candidates, 0, i) == candidates[i]);
        assert(select_name(candidates, -1, i) == candidates[i]);
    }
    assert(select_name(candidates, 2, 5) == candidates[0]);
    assert(select_name(candidates, 1, std::numeric_limits<std::uint32_t>::max()) == candidates[0]);
    const NameCandidates missing{"fallback", nullptr, nullptr, "other", ""};
    assert(select_name(missing, 1, 0) == nullptr);
    assert(select_name(missing, 2, 0) == missing[0]);
    assert(select_name(missing, 2, 1) == nullptr);
    assert(select_name(missing, 2, 4) != nullptr);
    assert(std::strcmp(select_name(missing, 2, 4), "") == 0);

    const NameCandidates visitor{"fallback", "fallback", "net/minecraft/class_5348$class_5245",
                                 "fallback", "fallback"};
    assert(std::strcmp(select_name(visitor, 1, 0), "wu$a") == 0);
    assert(std::strcmp(select_name(visitor, 1, 1),
                       "net/minecraft/network/chat/FormattedText$ContentConsumer") == 0);
    assert(std::strcmp(select_name(visitor, 1, 2), visitor[2]) == 0);
    assert(std::strcmp(select_name(visitor, 1, 3),
                       "net/minecraft/text/StringVisitable$Visitor") == 0);
    assert(select_name(visitor, 1, 4) != nullptr);
    assert(std::strcmp(select_name(visitor, 1, 4), "") == 0);
    assert(select_name(visitor, 2, 0) == visitor[0]);
    assert(mapped_name("unmapped/Class", 1, 1) == nullptr);
    assert(mapped_name("method_19334", 4, 1) == nullptr);
    assert(std::strcmp(mapped_name("method_19334", 5, 1), "getFogType") == 0);
    assert(mapped_name("method_19334", 5, 2) == nullptr);
    assert(mapped_name(std::string_view("method_19334\0x", 14), 5, 1) == nullptr);
}
