#!/bin/bash
set -e

echo "Creating virtual environment for build..."
python3 -m venv venv
source venv/bin/activate

echo "Installing build dependencies..."

python3 -m pip install --upgrade pip
python3 -m pip install pyinstaller frida
npm ci

echo "Creating bundle of agent.js..."
npx frida-compile agent.js -o agent.bundle.js -c

echo "Running PyInstaller..."
python3 -m PyInstaller bridge.spec

echo "Build complete. Binary available at dist/barbatos-bridge"
deactivate
