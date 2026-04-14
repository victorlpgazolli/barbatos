#!/bin/bash
set -e

# Default to release, allow override with --debug
BUILD_TYPE="Release"
if [ "$1" == "--debug" ]; then
    BUILD_TYPE="Debug"
fi

echo "Building Linux x64 ($BUILD_TYPE)..."

# 1. Install dependencies (creates venv and installs pip/npm deps)
echo "Installing dependencies..."
make install_dependencies

# 2. Build Components
echo "Compiling all components..."
if [ "$BUILD_TYPE" == "Debug" ]; then
    # Manually override for Debug if needed, as Makefile defaults to Release
    ./gradlew linkDebugExecutableLinuxX64 --no-daemon
    make compile_bridge
    make compile_mcp
else
    make compile_all
fi

# 3. Prepare Output
echo "Preparing dist/ directory..."
# If it's a Debug build, we manually copy since prepare_release expects Release paths
if [ "$BUILD_TYPE" == "Debug" ]; then
    mkdir -p dist
    cp build/bin/linuxX64/debugExecutable/barbatos.kexe dist/barbatos
    cp bridge/dist/barbatos-bridge dist/barbatos-bridge
    cp mcp_server/dist/barbatos-mcp dist/barbatos-mcp
    chmod +x dist/barbatos dist/barbatos-bridge dist/barbatos-mcp
else
    make prepare_release
fi

echo "Build complete. Artifacts in dist/"
