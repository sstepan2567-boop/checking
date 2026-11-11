CXX ?= c++
AR ?= ar
PYTHON ?= .analysis-tools/venv/bin/python
JAVA_HOME ?= $(shell mise where java@temurin-25.0.5+8.0.LTS)
BUILD := analysis/build
CPPFLAGS += -Irecovered/native -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux
CXXFLAGS += -std=c++17 -O2 -Wall -Wextra -Werror -pedantic -fPIC -MMD -MP
SOURCES := $(wildcard recovered/native/*.cpp)
OBJECTS := $(patsubst recovered/native/%.cpp,$(BUILD)/%.o,$(SOURCES))

.PHONY: all test dynamic clean
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

test: $(BUILD)/test_jvm_attachment
	$(BUILD)/test_jvm_attachment
	$(PYTHON) -m unittest discover -s tests -v

dynamic: $(BUILD)/librecovered_test.so
	@test -n "$(ARCHIVE)" || { echo 'Set ARCHIVE to the supplied complete systemdc.zip'; exit 2; }
	$(PYTHON) -m tools.emulation.verify_config "$(ARCHIVE)" $(BUILD)/librecovered_test.so --report evidence/dynamic-config.json

clean:
	rm -f $(BUILD)/*.o $(BUILD)/*.d $(BUILD)/libsystemdc_recovered.a $(BUILD)/librecovered_test.so $(BUILD)/test_jvm_attachment

-include $(OBJECTS:.o=.d)
