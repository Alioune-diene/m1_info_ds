#!/bin/bash
# Lab2 RMI Hello - compile & launch helper
# Usage:
#   ./lab2-hello.sh compile     - compile all sources
#   ./lab2-hello.sh registry    - start rmiregistry (background)
#   ./lab2-hello.sh server      - start HelloServer
#   ./lab2-hello.sh client      - start HelloClient

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
CP="$PROJECT_DIR/target/classes"
PKG="fr.uga.im2ag.m1info.lab2.hello2"
PORT="${PORT:-1200}"
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

  registry)
    echo "==> Starting rmiregistry on port $PORT (background)..."
    cd "$CP" && rmiregistry "$PORT"
    ;;

  server)
    echo "==> Starting HelloServer (port $PORT)..."
    java -Djava.rmi.server.hostname=localhost -cp "$CP" "$PKG.HelloServer" "$PORT"
    ;;

  client)
    echo "==> Starting HelloClient (host=$HOST, port=$PORT)..."
    java -cp "$CP" "$PKG.HelloClient" "$HOST" "$PORT" "$PORT"
    ;;

  *)
    echo "Lab2 RMI Hello helper"
    echo ""
    echo "Usage: $0 {compile|registry|server|client}"
    echo ""
    echo "Steps:"
    echo "  1) $0 compile           # compile all sources"
    echo "  2) $0 registry          # start rmiregistry (background)"
    echo "  3) $0 server            # start HelloServer"
    echo "  4) $0 client            # start HelloClient (in another terminal)"
    echo ""
    echo "Environment variables:"
    echo "  PORT=1099   (default)    # rmiregistry / server port"
    echo "  HOST=localhost (default) # server host (for client)"
    ;;
esac
