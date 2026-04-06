#!/usr/bin/env bash

NODE_ID="${1:?Usage: ./run_node.sh <nodeId> [configFile] [rabbitHost]}"
CONFIG="${2:-config.json}"
RABBIT="${3:-localhost}"
JAR="target/overlay-1.0.0-SNAPSHOT-runnable-physical.jar"

if [[ ! -f "$JAR" ]]; then
    echo "Error: JAR file not found at $JAR. Please build the project first."
    exit 1
fi

exec java -jar "$JAR" "$NODE_ID" "$CONFIG" "$RABBIT"