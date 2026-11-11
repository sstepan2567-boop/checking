#include "config_validation.hpp"
extern "C" {
bool recovered_bounded_string(const systemdlc::recovered::JsonStringView* value,
                              std::size_t minimum, std::size_t maximum) {
    return systemdlc::recovered::bounded_json_string(value, minimum, maximum);
}
bool recovered_config_name(const systemdlc::recovered::JsonStringView* value) {
    return systemdlc::recovered::valid_config_name(value);
}
}
