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

# Download wheel files for macOS - multiple Python versions for compatibility
echo "Downloading wheel files for multiple Python versions..."
echo "This ensures compatibility with Python 3.8, 3.9, 3.10, 3.11, 3.12, 3.13, 3.14..."

# Download for multiple Python versions (including 3.14)
for PY_VER in cp38 cp39 cp310 cp311 cp312 cp313 cp314; do
    PY_NUM=${PY_VER:2}
    echo "Downloading for Python $PY_NUM..."
    pip3 download \
        --python-version $PY_NUM \
        --platform macosx_10_9_universal2 \
        --platform macosx_11_0_arm64 \
        --platform macosx_11_0_x86_64 \
        --only-binary=:all: \
        -r "$SCRIPT_DIR/requirements.txt" \
        -d "$PACKAGES_DIR" 2>&1 | grep -E "(Saved|Collecting|ERROR|WARNING)" || true
done

# Also download universal wheels (py3-none-any) which work for all Python versions
echo ""
echo "Downloading universal wheels (compatible with all Python versions)..."
pip3 download \
    --only-binary=:all: \
    -r "$SCRIPT_DIR/requirements.txt" \
    -d "$PACKAGES_DIR" 2>&1 | grep -E "(Saved|Collecting|ERROR)" || true

# For Python 3.14, if binary wheels aren't available, try to get source distributions as fallback
echo ""
echo "Checking for Python 3.14 specific packages..."
pip3 download \
    --python-version 14 \
    --platform macosx_10_9_universal2 \
    --platform macosx_11_0_arm64 \
    --platform macosx_11_0_x86_64 \
    -r "$SCRIPT_DIR/requirements.txt" \
    -d "$PACKAGES_DIR" 2>&1 | grep -E "(Saved|Collecting|ERROR)" || true

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

