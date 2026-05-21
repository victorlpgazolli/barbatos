OS   := $(shell uname -s)
ARCH := $(shell uname -m)

ifeq ($(OS),Darwin)
  ifeq ($(ARCH),arm64)
    GRADLE_TARGET := linkReleaseExecutableMacosArm64
    BIN_PATH      := build/bin/macosArm64/releaseExecutable/barbatos.kexe
  else
    GRADLE_TARGET := linkReleaseExecutableMacosX64
    BIN_PATH      := build/bin/macosX64/releaseExecutable/barbatos.kexe
  endif
else ifeq ($(OS),Linux)
  ifeq ($(ARCH),x86_64)
    GRADLE_TARGET := linkReleaseExecutableLinuxX64
    BIN_PATH      := build/bin/linuxX64/releaseExecutable/barbatos.kexe
  else ifeq ($(ARCH),aarch64)
    GRADLE_TARGET := linkReleaseExecutableLinuxArm64
    BIN_PATH      := build/bin/linuxArm64/releaseExecutable/barbatos.kexe
  else
    $(error Unsupported Linux architecture: $(ARCH))
  endif
else
  $(error Unsupported OS: $(OS))
endif

.PHONY: install_dependencies setup_devkit compile_binary prepare_release release run run_arm64_qemu clean

install_dependencies: setup_devkit

setup_devkit:
	chmod +x scripts/download_frida_devkit.sh
	./scripts/download_frida_devkit.sh

compile_binary:
	./gradlew $(GRADLE_TARGET)

release: compile_binary prepare_release

run_arm64_qemu:
	./scripts/start_arm64_qemu.sh

run:
	./dist/barbatos

prepare_release:
	mkdir -p dist
	cp $(BIN_PATH) dist/barbatos
	chmod +x dist/barbatos
	@echo "Binary ready in dist/barbatos"

clean:
	./gradlew clean
	rm -rf dist/ src/nativeInterop/cinterop/*.a src/nativeInterop/cinterop/*.h
