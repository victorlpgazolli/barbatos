#!/bin/bash
set -e

# Run as root in Docker, or with sudo in CI
SUDO=""
if [ "$(id -u)" != "0" ]; then
    SUDO="sudo"
fi

$SUDO dpkg --add-architecture arm64

# Pin existing sources to amd64 only (DEB822 format)
if ls /etc/apt/sources.list.d/*.sources 2>/dev/null | grep -q .; then
    for f in /etc/apt/sources.list.d/*.sources; do
        $SUDO sed -i '/^Architectures:/d' "$f"
        $SUDO sed -i '/^Types:/a Architectures: amd64' "$f"
    done
fi

# Pin existing sources to amd64 only (legacy format)
$SUDO sed -i 's/^deb http/deb [arch=amd64] http/g' /etc/apt/sources.list 2>/dev/null || true

# Add ARM64 package sources from ports.ubuntu.com
echo "deb [arch=arm64] http://ports.ubuntu.com/ubuntu-ports noble main restricted universe multiverse" | $SUDO tee /etc/apt/sources.list.d/arm64-ports.list
echo "deb [arch=arm64] http://ports.ubuntu.com/ubuntu-ports noble-updates main restricted universe multiverse" | $SUDO tee -a /etc/apt/sources.list.d/arm64-ports.list
echo "deb [arch=arm64] http://ports.ubuntu.com/ubuntu-ports noble-security main restricted universe multiverse" | $SUDO tee -a /etc/apt/sources.list.d/arm64-ports.list

$SUDO apt-get update
$SUDO apt-get install -y --no-install-recommends \
    gcc-aarch64-linux-gnu \
    libcurl4-openssl-dev:arm64 \
    libssl-dev:arm64 \
    libssh-dev:arm64 \
    libbrotli-dev:arm64 \
    libkrb5-dev:arm64 \
    libidn2-dev:arm64 \
    libldap-dev:arm64 \
    libnghttp2-dev:arm64 \
    libpsl-dev:arm64 \
    librtmp-dev:arm64 \
    libzstd-dev:arm64 \
    zlib1g-dev:arm64
