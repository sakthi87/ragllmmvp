#!/bin/bash
# Download Python wheel files for macOS (for offline installation)
# Run this on a machine WITH internet, then transfer python-packages/ to restricted environment

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PACKAGES_DIR="$SCRIPT_DIR/python-packages"

echo "=========================================="
echo "Downloading Python Dependencies for macOS"
echo "=========================================="
echo ""

# Detect Python version
PYTHON_VERSION=$(python3 --version 2>&1 | awk '{print $2}' | cut -d. -f1,2)
PYTHON_PLATFORM=$(python3 -c "import platform; print(platform.machine())" 2>&1)

echo "Python version: $PYTHON_VERSION"
echo "Platform: $PYTHON_PLATFORM"
echo ""

# Create packages directory
mkdir -p "$PACKAGES_DIR"

# Download wheel files for macOS
echo "Downloading wheel files..."
pip3 download \
    --platform macosx_10_9_universal2 \
    --platform macosx_11_0_arm64 \
    --platform macosx_11_0_x86_64 \
    --only-binary=:all: \
    -r "$SCRIPT_DIR/requirements.txt" \
    -d "$PACKAGES_DIR"

echo ""
echo "=========================================="
echo "✅ Download complete!"
echo "=========================================="
echo "Packages downloaded to: $PACKAGES_DIR"
echo ""
echo "Next steps:"
echo "1. Transfer the 'python-packages/' directory to your restricted MacBook Pro"
echo "2. On restricted MacBook Pro, run:"
echo "   pip3 install --no-index --find-links python-packages/ -r requirements.txt"
echo ""

