#!/bin/bash
set -e

# Default to release, allow override with --debug
BUILD_TYPE="Release"
if [ "$1" == "--debug" ]; then
    BUILD_TYPE="Debug"
fi

echo "Building Linux ARM64 ($BUILD_TYPE)..."

HOST_ARCH=$(uname -m)
HOST_OS=$(uname -s)

if [ "$HOST_ARCH" = "aarch64" ] && [ "$HOST_OS" = "Linux" ]; then
    echo "Running natively on Linux ARM64. Using Makefile..."
    make install_dependencies
    if [ "$BUILD_TYPE" == "Debug" ]; then
        ./gradlew linkDebugExecutableLinuxArm64 --no-daemon
        make compile_bridge
    else
        make compile_all
    fi
else
    echo "Cross-compiling from $HOST_ARCH to ARM64 using Docker..."
    # 1. Compile Frida JS Agent (Architecture agnostic)
    make compile_bridge_agent
    
    # 2. Build Kotlin Native TUI
    echo "Building Kotlin Native TUI..."
    ./gradlew "link${BUILD_TYPE}ExecutableLinuxArm64" --no-daemon
    
    # 3. Build Python bridge component inside ARM64 Docker
    docker run --rm --platform linux/arm64 \
        -v "$(pwd):/app" -w /app \
        python:3.11-slim \
        bash -c "apt-get update && apt-get install -y binutils && \
                 pip install -r bridge/requirements.txt && cd bridge && python3 -m PyInstaller bridge.spec"
fi

# 4. Prepare Output
echo "Preparing dist/ directory..."
if [ "$BUILD_TYPE" == "Debug" ]; then
    mkdir -p dist
    cp build/bin/linuxArm64/debugExecutable/barbatos.kexe dist/barbatos
    cp bridge/dist/barbatos-bridge dist/barbatos-bridge
    chmod +x dist/barbatos dist/barbatos-bridge
elif [ "$HOST_ARCH" = "aarch64" ]; then
    make prepare_release
else
    # Manual prepare if we cross-compiled
    mkdir -p dist
    cp build/bin/linuxArm64/releaseExecutable/barbatos.kexe dist/barbatos
    cp bridge/dist/barbatos-bridge dist/barbatos-bridge
    chmod +x dist/barbatos dist/barbatos-bridge
fi

echo "Build complete. Artifacts in dist/"
