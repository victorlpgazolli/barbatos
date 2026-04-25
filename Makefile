OS   := $(shell uname -s)
ARCH := $(shell uname -m)

ifeq ($(OS),Darwin)
  ifeq ($(ARCH),arm64)
    GRADLE_TARGET := linkReleaseExecutableMacosArm64
    TUI_BIN       := build/bin/macosArm64/releaseExecutable/barbatos.kexe
  else
    GRADLE_TARGET := linkReleaseExecutableMacosX64
    TUI_BIN       := build/bin/macosX64/releaseExecutable/barbatos.kexe
  endif
else ifeq ($(OS),Linux)
  ifeq ($(ARCH),x86_64)
    GRADLE_TARGET := linkReleaseExecutableLinuxX64
    TUI_BIN       := build/bin/linuxX64/releaseExecutable/barbatos.kexe
  else ifeq ($(ARCH),aarch64)
    GRADLE_TARGET := linkReleaseExecutableLinuxArm64
    TUI_BIN       := build/bin/linuxArm64/releaseExecutable/barbatos.kexe
  else
    $(error Unsupported Linux architecture: $(ARCH))
  endif
else
  $(error Unsupported OS: $(OS))
endif

BRIDGE_DIR := $(abspath bridge)
BRIDGE_BIN := bridge/dist/barbatos-bridge
VENV       := bridge/venv
PIP        := $(VENV)/bin/pip
PYTHON     := $(abspath $(VENV)/bin/python)

.PHONY: install_dependencies compile_all compile_bridge_agent compile_bridge compile_binary prepare_release release run run_arm64_qemu

install_dependencies:
	rm -rf $(VENV)
	python3 -m venv $(VENV)
	$(PIP) install --upgrade pip
	$(PIP) install -r bridge/requirements.txt
	cd bridge && npm ci

compile_bridge_agent:
	cd bridge && npm ci
	cd bridge && npx frida-compile agent.js -o agent.bundle.js -c
	cd bridge && npx frida-compile agent.objc.js -o agent.objc.bundle.js -c

compile_bridge: compile_bridge_agent
	cd bridge && $(PYTHON) -m PyInstaller bridge.spec

compile_binary:
	./gradlew $(GRADLE_TARGET)

compile_all: compile_binary compile_bridge_agent compile_bridge

release: compile_all prepare_release

run_arm64_qemu:
	./scripts/start_arm64_qemu.sh

run:
	./dist/barbatos

prepare_release:
	mkdir -p dist
	cp $(TUI_BIN) dist/barbatos
	cp $(BRIDGE_BIN) dist/barbatos-bridge
	chmod +x dist/barbatos dist/barbatos-bridge
	@echo "Binaries ready in dist/"
