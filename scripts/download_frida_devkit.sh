#!/bin/bash
set -e

FRIDA_VERSION="17.9.1" # Use a specific version
PLATFORM=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=${1:-$(uname -m)}

TARGET_OS=${1:-$(uname -s | tr '[:upper:]' '[:lower:]')}
TARGET_ARCH=${2:-$(uname -m)}
TARGET_DIR=${3:-"src/nativeInterop/cinterop"}

if [ "$TARGET_OS" == "darwin" ] || [ "$TARGET_OS" == "macos" ]; then
    PLATFORM="macos"
elif [ "$TARGET_OS" == "linux" ]; then
    PLATFORM="linux"
else
    echo "OS not supported: $TARGET_OS"
    exit 1
fi
if [[ "$TARGET_ARCH" == "arm64" || "$TARGET_ARCH" == "aarch64" ]]; then
    ARCH="arm64"
elif [[ "$TARGET_ARCH" == "x86_64" || "$TARGET_ARCH" == "amd64" ]]; then
    ARCH="x86_64"
else
    echo "Architecture not supported: $TARGET_ARCH"
    exit 1
fi

echo "Setting up Frida Devkit for $PLATFORM/$ARCH..."
DEVKIT_NAME="frida-core-devkit-${FRIDA_VERSION}-${PLATFORM}-${ARCH}.tar.xz"
URL="https://github.com/frida/frida/releases/download/${FRIDA_VERSION}/${DEVKIT_NAME}"

if [ -f "$TARGET_DIR/libfrida-core.a" ]; then
    echo "Frida Devkit already exists in $TARGET_DIR. Skipping download."
    exit 0
fi
echo "Downloading Frida Devkit from $URL..."
mkdir -p "$TARGET_DIR"
curl -L "$URL" -o "${DEVKIT_NAME}"

echo "Extracting to $TARGET_DIR..."
tar -xf "${DEVKIT_NAME}" -C "$TARGET_DIR"

rm "${DEVKIT_NAME}"
echo "Frida Devkit setup complete."