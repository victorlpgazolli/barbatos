#!/bin/bash
set -e

FRIDA_VERSION="17.9.1" # Use a specific version
PLATFORM=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=${1:-$(uname -m)}

echo "Setting up Frida Devkit for $PLATFORM/$ARCH..."

if [ "$PLATFORM" == "darwin" ]; then
    if [[ "$ARCH" == "arm64" || "$ARCH" == "aarch64" ]]; then
        DEVKIT_NAME="frida-core-devkit-${FRIDA_VERSION}-macos-arm64.tar.xz"
    else
        DEVKIT_NAME="frida-core-devkit-${FRIDA_VERSION}-macos-x86_64.tar.xz"
    fi
elif [ "$PLATFORM" == "linux" ]; then
    if [[ "$ARCH" == "arm64" || "$ARCH" == "aarch64" ]]; then
        DEVKIT_NAME="frida-core-devkit-${FRIDA_VERSION}-linux-arm64.tar.xz"
    else
        DEVKIT_NAME="frida-core-devkit-${FRIDA_VERSION}-linux-x86_64.tar.xz"
    fi
else
    echo "Unsupported platform: $PLATFORM"
    exit 1
fi

URL="https://github.com/frida/frida/releases/download/${FRIDA_VERSION}/${DEVKIT_NAME}"
TARGET_DIR="src/nativeInterop/cinterop"

echo "Downloading Frida Devkit from $URL..."
curl -L "$URL" -o "${DEVKIT_NAME}"

echo "Extracting to $TARGET_DIR..."
mkdir -p "$TARGET_DIR"
tar -xf "${DEVKIT_NAME}" -C "$TARGET_DIR"

# Rename headers to match our .def file if necessary
mv "$TARGET_DIR/frida-core.h" "$TARGET_DIR/frida-core.h.tmp" 2>/dev/null || true
mv "$TARGET_DIR/frida-core.h.tmp" "$TARGET_DIR/frida-core.h" 2>/dev/null || true

clang -E -P -v "$TARGET_DIR/frida-core.h"

rm "${DEVKIT_NAME}"
echo "Frida Devkit setup complete."
