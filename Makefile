BRIDGE_DIR := $(abspath bridge)
BRIDGE_BIN := bridge/dist/barbatos-bridge
VENV       := bridge/venv
PIP        := $(VENV)/bin/pip
PYTHON     := $(abspath $(VENV)/bin/python)

.PHONY: install_dependencies compile_all compile_bridge_agent compile_bridge prepare_release release

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

compile_all: compile_bridge_agent compile_bridge

release: compile_all prepare_release

prepare_release:
	mkdir -p dist
	cp $(BRIDGE_BIN) dist/barbatos
	chmod +x dist/barbatos
	@echo "Binaries ready in dist/"
