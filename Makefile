CXX ?= c++
AR ?= ar
PYTHON ?= .analysis-tools/venv/bin/python
JAVA_HOME ?= $(shell mise where java@temurin-25.0.5+8.0.LTS)
BUILD := analysis/build
CPPFLAGS += -Irecovered/native -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux
CXXFLAGS += -std=c++17 -O2 -Wall -Wextra -Werror -pedantic -fPIC -MMD -MP
SOURCES := $(wildcard recovered/native/*.cpp)
OBJECTS := $(patsubst recovered/native/%.cpp,$(BUILD)/%.o,$(SOURCES))

RE_CAPS ?= case/AGENT_CAPS.json
RE_JOURNAL ?= analysis/exec_journal.jsonl

.PHONY: all test verify-text verify-mappings dynamic clean
all: $(BUILD)/libsystemdc_recovered.a

$(BUILD):
	mkdir -p $@

$(BUILD)/%.o: recovered/native/%.cpp | $(BUILD)
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -c $< -o $@

$(BUILD)/libsystemdc_recovered.a: $(OBJECTS)
	$(AR) rcs $@ $^

$(BUILD)/test_jvm_attachment: tests/test_jvm_attachment.cpp $(BUILD)/libsystemdc_recovered.a
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $^ -o $@

$(BUILD)/librecovered_test.so: tests/dynamic/native_exports.cpp $(BUILD)/libsystemdc_recovered.a
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -shared $^ -o $@

$(BUILD)/test_text_table: tests/test_text_table.cpp $(BUILD)/libsystemdc_recovered.a
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $^ -o $@

$(BUILD)/test_name_mapping: tests/test_name_mapping.cpp $(BUILD)/libsystemdc_recovered.a
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $^ -o $@

$(BUILD)/test_jvm_attachment_real: tests/test_jvm_attachment_real.cpp $(BUILD)/libsystemdc_recovered.a
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) $^ -L$(JAVA_HOME)/lib/server -Wl,-rpath,$(JAVA_HOME)/lib/server -ljvm -pthread -o $@

test: $(BUILD)/test_jvm_attachment $(BUILD)/test_text_table $(BUILD)/test_name_mapping $(BUILD)/test_jvm_attachment_real
	$(BUILD)/test_jvm_attachment
	$(BUILD)/test_text_table
	$(BUILD)/test_name_mapping
	timeout 30 $(BUILD)/test_jvm_attachment_real
	$(PYTHON) -m unittest discover -s tests -v

verify-text: $(BUILD)/librecovered_test.so
	@test -n "$(ARCHIVE)" || { echo 'Set ARCHIVE to the supplied complete systemdc.zip'; exit 2; }
	$(PYTHON) -m tools.verify_text_table "$(ARCHIVE)" $(BUILD)/librecovered_test.so --report evidence/text-table-validation.json

verify-mappings: $(BUILD)/librecovered_test.so
	$(PYTHON) -m tools.recover_name_mappings analysis/images/client/image.bin recovered/native/name_mapping_data.inc --check --library $(BUILD)/librecovered_test.so --report evidence/name-mappings.json

dynamic: $(BUILD)/librecovered_test.so
	@test -n "$(ARCHIVE)" || { echo 'Set ARCHIVE to the supplied complete systemdc.zip'; exit 2; }
	@test -f "$(RE_SKILL)/scripts/guard.py" || { echo 'Set RE_SKILL to the supplied re-java-native toolkit; isolated preflight and guard required'; exit 2; }
	RE_CAPS="$(RE_CAPS)" RE_JOURNAL="$(RE_JOURNAL)" $(PYTHON) "$(RE_SKILL)/scripts/guard.py" --sample --timeout 120 -- $(PYTHON) -m tools.emulation.verify_config "$(ARCHIVE)" $(BUILD)/librecovered_test.so --report evidence/dynamic-config.json

clean:
	rm -f $(BUILD)/*.o $(BUILD)/*.d $(BUILD)/libsystemdc_recovered.a $(BUILD)/librecovered_test.so $(BUILD)/test_jvm_attachment $(BUILD)/test_text_table $(BUILD)/test_name_mapping $(BUILD)/test_jvm_attachment_real

-include $(OBJECTS:.o=.d)
