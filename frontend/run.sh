#!/bin/bash
# Run React Frontend (node_modules already included)

cd "$(dirname "$0")"

# Set API URL if not already set
if [ ! -f .env ]; then
    echo "REACT_APP_API_URL=http://localhost:8080/api" > .env
fi

# Use node_modules directly (no npm install needed)
./node_modules/.bin/react-scripts start

