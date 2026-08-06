#!/bin/bash

# Barbatos - Interactive Debug Kit Installer
# Supports: macOS (ARM64), Linux (x64, ARM64/WSL)

set -e

GITHUB_REPO="victorlpgazolli/barbatos"
INSTALL_DIR="$HOME/.barbatos"
BIN_DIR="$INSTALL_DIR/bin"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}==>${NC} Installing Barbatos: Interactive Debug Kit..."

# 1. Detect OS and Architecture
OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS" in
    Darwin)
        if [ "$ARCH" = "arm64" ]; then
            PLATFORM="macos-arm64"
        else
            echo -e "${RED}Error:${NC} Only Apple Silicon (ARM64) is currently supported on macOS."
            exit 1
        fi
        ;;
    Linux)
        if [ "$ARCH" = "x86_64" ]; then
            PLATFORM="linux-x64"
        elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
            PLATFORM="linux-arm64"
        else
            echo -e "${RED}Error:${NC} Unsupported Linux architecture: $ARCH"
            exit 1
        fi
        ;;
    *)
        echo -e "${RED}Error:${NC} Unsupported operating system: $OS"
        exit 1
        ;;
esac

echo "please install the following dependencies: unzip tmux curl adb jq"
# 2. Get Latest Release Version from GitHub
LATEST_RELEASE=$(curl -s "https://api.github.com/repos/$GITHUB_REPO/releases" |
  jq -r '.[] | select(.prerelease == false) | .tag_name' |
  head -n 1)

if [ -z "$LATEST_RELEASE" ]; then
    echo -e "${RED}Error:${NC} Could not find a valid release on GitHub."
    exit 1
fi

echo -e "${BLUE}==>${NC} Downloading Barbatos $LATEST_RELEASE for $PLATFORM..."
DOWNLOAD_URL="https://github.com/$GITHUB_REPO/releases/download/$LATEST_RELEASE/barbatos-$PLATFORM.zip"

# 3. Create install directory
mkdir -p $HOME/.cache/barbatos
mkdir -p "$BIN_DIR"

# 4. Download and Extract
TEMP_ZIP=$(mktemp)
curl -L -o "$TEMP_ZIP" "$DOWNLOAD_URL"

echo -e "${BLUE}==>${NC} Extracting to $INSTALL_DIR..."
unzip -o "$TEMP_ZIP" -d "$INSTALL_DIR/tmp_extract"
mv "$INSTALL_DIR/tmp_extract/dist/"* "$BIN_DIR/"
rm -rf "$INSTALL_DIR/tmp_extract"
rm "$TEMP_ZIP"

# 5. Handle macOS Quarantine
if [ "$OS" = "Darwin" ]; then
    echo -e "${BLUE}==>${NC} Since the project is not signed, removing macOS quarantine attributes..."
    xattr -dr com.apple.quarantine "$BIN_DIR" || true
fi

# 6. Ensure executability
chmod +x "$BIN_DIR/barbatos" "$BIN_DIR/barbatos-bridge"

# 7. Configure PATH or create symbolic link in ~/.local/bin
LOCAL_BIN="$HOME/.local/bin"
if [[ ":$PATH:" != *":$LOCAL_BIN:"* ]]; then
    echo -e "\n${RED}Warning:${NC} $LOCAL_BIN is not in your PATH. Configuring for you..."

    SHELL_TYPE=$(basename "$SHELL")
    RC_FILE=""

    case "$SHELL_TYPE" in
        zsh) RC_FILE="$HOME/.zshrc" ;;
        bash) RC_FILE="$HOME/.bashrc" ;;
    esac
    if [ -z "$RC_FILE" ]; then
        echo -e "${RED}Error:${NC} Unsupported shell: $SHELL_TYPE. Please add $LOCAL_BIN to your PATH manually."
        exit 1
    fi

    echo -e "export PATH=\"\$PATH:$LOCAL_BIN\"" >> "$RC_FILE"
    echo -e "${GREEN}Success:${NC} Added $LOCAL_BIN to PATH in $RC_FILE."

fi
mkdir -p "$LOCAL_BIN"
ln -sf "$BIN_DIR/barbatos" "$LOCAL_BIN/barbatos"
ln -sf "$BIN_DIR/barbatos-bridge" "$LOCAL_BIN/barbatos-bridge"
echo -e "\n${GREEN}Success:${NC} Created symbolic links in $LOCAL_BIN."

echo -e "\n${BLUE}==>${NC} Run 'barbatos' to start debugging!"
