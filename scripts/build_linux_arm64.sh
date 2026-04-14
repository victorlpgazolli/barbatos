#!/bin/bash
set -e

# Default to release, allow override with --debug
BUILD_TYPE="Release"
if [ "$1" == "--debug" ]; then
    BUILD_TYPE="Debug"
fi

echo "Building Linux ARM64 ($BUILD_TYPE)..."

HOST_ARCH=$(uname -m)

if [ "$HOST_ARCH" = "aarch64" ]; then
    echo "Running natively on ARM64. Using Makefile..."
    make install_dependencies
    if [ "$BUILD_TYPE" == "Debug" ]; then
        ./gradlew linkDebugExecutableLinuxArm64 --no-daemon
        make compile_bridge
        make compile_mcp
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
    
    # 3 & 4. Build Python components inside ARM64 Docker
    docker run --rm --platform linux/arm64 \
        -v "$(pwd):/app" -w /app \
        python:3.11-slim \
        bash -c "apt-get update && apt-get install -y binutils && \
                 pip install -r bridge/requirements.txt && cd bridge && python3 -m PyInstaller bridge.spec && \
                 cd ../mcp_server && pip install -r requirements.txt && cd mcp_server && python3 -m PyInstaller mcp.spec"
fi

# 5. Prepare Output
echo "Preparing dist/ directory..."
if [ "$BUILD_TYPE" == "Debug" ]; then
    mkdir -p dist
    cp build/bin/linuxArm64/debugExecutable/barbatos.kexe dist/barbatos
    cp bridge/dist/barbatos-bridge dist/barbatos-bridge
    cp mcp_server/dist/barbatos-mcp dist/barbatos-mcp
    chmod +x dist/barbatos dist/barbatos-bridge dist/barbatos-mcp
elif [ "$HOST_ARCH" = "aarch64" ]; then
    make prepare_release
else
    # Manual prepare if we cross-compiled
    mkdir -p dist
    cp build/bin/linuxArm64/releaseExecutable/barbatos.kexe dist/barbatos
    cp bridge/dist/barbatos-bridge dist/barbatos-bridge
    cp mcp_server/dist/barbatos-mcp dist/barbatos-mcp
    chmod +x dist/barbatos dist/barbatos-bridge dist/barbatos-mcp
fi

echo "Build complete. Artifacts in dist/"
