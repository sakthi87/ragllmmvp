#!/bin/bash
# Install Python dependencies from wheel files (offline installation)
# Run this on the restricted MacBook Pro after transferring python-packages/
# Optionally creates and uses a virtual environment

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PACKAGES_DIR="$SCRIPT_DIR/python-packages"
VENV_DIR="$SCRIPT_DIR/venv"

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

echo "=========================================="
echo "Installing Python Dependencies (Offline)"
echo "=========================================="
echo ""

# Check Python version
PYTHON_VERSION=$(python3 --version 2>&1)
echo "Python: $PYTHON_VERSION"
echo "Packages directory: $PACKAGES_DIR"
echo ""

# Ask if user wants to use venv
if [ ! -d "$VENV_DIR" ]; then
    echo "Virtual environment not found."
    read -p "Create virtual environment? (y/n) [y]: " CREATE_VENV
    CREATE_VENV=${CREATE_VENV:-y}
    
    if [ "$CREATE_VENV" = "y" ] || [ "$CREATE_VENV" = "Y" ]; then
        echo "Creating virtual environment..."
        python3 -m venv "$VENV_DIR"
        echo "✅ Virtual environment created at: $VENV_DIR"
        echo ""
    fi
fi

# Activate venv if it exists
if [ -d "$VENV_DIR" ]; then
    echo "Activating virtual environment..."
    source "$VENV_DIR/bin/activate"
    echo "✅ Virtual environment activated"
    echo ""
fi

# Count wheel files
WHEEL_COUNT=$(find "$PACKAGES_DIR" -name "*.whl" | wc -l | tr -d ' ')
echo "Found $WHEEL_COUNT wheel file(s)"
echo ""

# Install from local wheel files
echo "Installing packages..."
pip install --no-index --find-links "$PACKAGES_DIR" -r "$SCRIPT_DIR/requirements.txt"

echo ""
echo "=========================================="
echo "✅ Installation complete!"
echo "=========================================="
echo ""

if [ -d "$VENV_DIR" ]; then
    echo "Virtual environment: $VENV_DIR"
    echo ""
    echo "To activate venv in future sessions:"
    echo "  source venv/bin/activate"
    echo ""
fi

echo "Verify installation:"
python -c "import requests; print('✅ requests:', requests.__version__)"
python -c "import psycopg2; print('✅ psycopg2:', psycopg2.__version__)"
echo ""

