#!/bin/bash
# Run backend JAR from anywhere
# Usage: ./run-backend.sh

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JAR_FILE="$SCRIPT_DIR/backend/target/rag-api-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    echo "Building JAR..."
    cd "$SCRIPT_DIR/backend" || exit 1
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "❌ Build failed"
        exit 1
    fi
fi

echo "Starting backend..."
java -jar "$JAR_FILE"

