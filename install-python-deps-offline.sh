#!/bin/bash
# Install Python dependencies from wheel files (offline installation)
# Run this on the restricted MacBook Pro after transferring python-packages/

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PACKAGES_DIR="$SCRIPT_DIR/python-packages"

echo "=========================================="
echo "Installing Python Dependencies (Offline)"
echo "=========================================="
echo ""

# Check if packages directory exists
if [ ! -d "$PACKAGES_DIR" ]; then
    echo "❌ Error: python-packages/ directory not found!"
    echo ""
    echo "Please:"
    echo "1. Download packages on a machine with internet using:"
    echo "   ./download-python-deps.sh"
    echo "2. Transfer the python-packages/ directory to this machine"
    echo "3. Run this script again"
    exit 1
fi

# Check Python version
PYTHON_VERSION=$(python3 --version 2>&1)
echo "Python: $PYTHON_VERSION"
echo "Packages directory: $PACKAGES_DIR"
echo ""

# Count wheel files
WHEEL_COUNT=$(find "$PACKAGES_DIR" -name "*.whl" | wc -l | tr -d ' ')
echo "Found $WHEEL_COUNT wheel file(s)"
echo ""

# Install from local wheel files
echo "Installing packages..."
pip3 install --no-index --find-links "$PACKAGES_DIR" -r "$SCRIPT_DIR/requirements.txt"

echo ""
echo "=========================================="
echo "✅ Installation complete!"
echo "=========================================="
echo ""
echo "Verify installation:"
python3 -c "import requests; print('✅ requests:', requests.__version__)"
python3 -c "import psycopg2; print('✅ psycopg2:', psycopg2.__version__)"
echo ""

