#!/bin/bash
# TchatsApp RMI Chat - compile & launch helper
# Usage:
#   ./chatapp_launcher.py compile          - compile all sources
#   ./chatapp_launcher.py server           - start ChatServer
#   ./chatapp_launcher.py client <name>    - start ChatClient with given username

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
CP="$PROJECT_DIR/target/classes"
SERVER_PKG="fr.uga.im2ag.m1info.server.ChatServerMain"
CLIENT_PKG="fr.uga.im2ag.m1info.client.ChatClientMain"
HOST="${HOST:-localhost}"

case "$1" in
  compile)
    echo "==> Compiling..."
    mvn -f "$PROJECT_DIR/pom.xml" compile -q
    if [ $? -eq 0 ]; then
      echo "==> Compilation OK (classes in target/classes/)"
    else
      echo "==> Compilation FAILED"
      exit 1
    fi
    ;;

  server)
    SERVER_IP="${SERVER_IP:-$(hostname -I | awk '{print $1}')}"
    echo "==> Starting ChatServer on $SERVER_IP (registry on port 1099)..."
    java -Djava.rmi.server.hostname="$SERVER_IP" -cp "$CP" "$SERVER_PKG"
    ;;

  client)
    USERNAME="${2:-anonymous}"
    echo "==> Starting ChatClient as '$USERNAME' (host=$HOST)..."
    java -cp "$CP" "$CLIENT_PKG" "$HOST" "$USERNAME"
    ;;

  *)
    echo "TchatsApp RMI Chat helper"
    echo ""
    echo "Usage: $0 {compile|server|client <username>}"
    echo ""
    echo "Steps:"
    echo "  1) $0 compile                # compile all sources"
    echo "  2) $0 server                 # start ChatServer (includes registry)"
    echo "  3) $0 client Alice           # join as Alice (in another terminal)"
    echo "  4) $0 client Bob             # join as Bob (in another terminal)"
    echo ""
    echo "Environment variables:"
    echo "  HOST=localhost (default)     # server host (for client)"
    ;;
esac
