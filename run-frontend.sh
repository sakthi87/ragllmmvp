#!/bin/bash
# Run frontend from static build - NO npm/internet required
# Usage: ./run-frontend.sh

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
BUILD_DIR="$FRONTEND_DIR/build"
JAVA_SERVER="$SCRIPT_DIR/SimpleHttpServer.class"

# Check if build directory exists
if [ ! -d "$BUILD_DIR" ]; then
    echo "❌ Build directory not found: $BUILD_DIR"
    echo "Please build the frontend first (requires internet):"
    echo "  cd frontend && npm install && npm run build"
    exit 1
fi

# Compile Java HTTP server if needed
if [ ! -f "$JAVA_SERVER" ]; then
    echo "Compiling Java HTTP server..."
    javac "$SCRIPT_DIR/SimpleHttpServer.java"
    if [ $? -ne 0 ]; then
        echo "❌ Failed to compile SimpleHttpServer.java"
        exit 1
    fi
fi

# Use Java HTTP server (works in restricted environments)
echo "Starting frontend on http://localhost:3000"
echo "Serving from: $BUILD_DIR"
echo "Press Ctrl+C to stop"
cd "$SCRIPT_DIR"
java SimpleHttpServer 3000 "$BUILD_DIR"
