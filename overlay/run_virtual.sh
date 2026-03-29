#!/usr/bin/env bash

VIRTUAL_ID="${1:?Usage: ./run_virtual.sh <virtualId> <ringSize> [configFile] [rabbitHost]}"
RING_SIZE="${2:?ringSize requis}"
CONFIG="${3:-config.json}"
RABBIT="${4:-localhost}"
JAR="target/overlay-1.0.0-SNAPSHOT-runnable-virtual.jar"

if [[ ! -f "$JAR" ]]; then
    echo "Error: JAR file not found at $JAR. Please build the project first."
    exit 1
fi

exec java -jar "$JAR" "$VIRTUAL_ID" "$RING_SIZE" "$CONFIG" "$RABBIT"