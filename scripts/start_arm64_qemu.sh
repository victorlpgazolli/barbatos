#!/bin/bash

ARCH=arm64
echo "Starting QEMU for architecture: $ARCH"

# qemu-img create -f qcow2 debian-hd.qcow2 20G
if [ ! -f "debian-hd.qcow2" ]; then
    echo "Creating debian-hd.qcow2 (20GB)..."
    qemu-img create -f qcow2 debian-hd.qcow2 20G
else
    echo "debian-hd.qcow2 already exists, skipping creation."
fi

ISO=debian-13.4.0-$ARCH-netinst.iso

if [ ! -f "$ISO" ]; then
    echo "Downloading $ISO..."
    curl -L -o "$ISO" "https://cdimage.debian.org/debian-cd/current/$ARCH/iso-cd/$ISO"
else
    echo "$ISO already exists, skipping download."
fi

qemu-system-aarch64 \
  -machine virt,accel=hvf \
  -cpu host \
  -smp 4 \
  -m 2048 \
  -bios /opt/homebrew/opt/qemu/share/qemu/edk2-aarch64-code.fd \
  -device virtio-net-pci,netdev=net0 \
  -netdev user,id=net0,hostfwd=tcp::2222-:22 \
  -drive if=virtio,format=qcow2,file=debian-hd.qcow2 \
  -cdrom $(pwd)/$ISO \
  -nographic