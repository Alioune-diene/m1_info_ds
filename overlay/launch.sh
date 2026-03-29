#!/usr/bin/env bash
# ==============================================================================
# Usage :
#   ./launch.sh [options]
#
# Options :
#   -r, --ring-size  <N>
#   -c, --config     <f>
#   -b, --rabbit     <h>
#   -d, --delay      <ms>
#       --no-virtual
#       --physical-only
#   -h, --help
#
# ==============================================================================

set -euo pipefail

CONFIG="config.json"
RABBIT="localhost"
RING_SIZE=""
DELAY_S=0.5
LAUNCH_VIRTUAL=true
JAR_PHYS="target/overlay-1.0.0-SNAPSHOT-runnable-physical.jar"
JAR_VIRT="target/overlay-1.0.0-SNAPSHOT-runnable-virtual.jar"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${CYAN}[launch]${RESET} $*"; }
success() { echo -e "${GREEN}[launch]${RESET} $*"; }
warn()    { echo -e "${YELLOW}[launch]${RESET} $*"; }
error()   { echo -e "${RED}[launch]${RESET} $*" >&2; }
die()     { error "$*"; exit 1; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        -r|--ring-size)   RING_SIZE="$2"; shift 2 ;;
        -c|--config)      CONFIG="$2";    shift 2 ;;
        -b|--rabbit)      RABBIT="$2";    shift 2 ;;
        -d|--delay)       DELAY_S="$2";   shift 2 ;;
        --no-virtual|--physical-only) LAUNCH_VIRTUAL=false; shift ;;
        -h|--help)
            sed -n '/^# Usage :/,/^# =/p' "$0" | sed 's/^# \?//'
            exit 0 ;;
        *) die "Unknow option : $1  (--help for usage)" ;;
    esac
done

[[ -f "$CONFIG" ]] || die "Config file not found : $CONFIG"

NODE_COUNT=$(grep '"nodeCount"' "$CONFIG" | grep -o '[0-9]*')
[[ -n "$NODE_COUNT" && "$NODE_COUNT" -gt 0 ]] || die "nodeCount must be a positive integer in $CONFIG"

[[ -z "$RING_SIZE" ]] && RING_SIZE="$NODE_COUNT"
[[ "$RING_SIZE" -ge 2 ]] || die "ring-size must be an integer ≥ 2"

detect_terminal() {
    for term in kitty konsole gnome-terminal xfce4-terminal xterm; do
        if command -v "$term" &>/dev/null; then
            echo "$term"
            return
        fi
    done
    echo ""
}

TERM_CMD=$(detect_terminal)
[[ -n "$TERM_CMD" ]] || die "No supported terminal emulator found (tried: kitty, konsole, gnome-terminal, xfce4-terminal, xterm)."
info "Using terminal emulator: $TERM_CMD"

open_terminal() {
    local title="$1"; shift
    local cmd="$*"

    case "$TERM_CMD" in
        kitty)
            kitty --title "$title" bash -c "$cmd; echo; read -p 'Tipe Enter to close...' " &
            ;;
        konsole)
            konsole --new-tab --title "$title" -e bash -c "$cmd; echo; read -p 'Tipe Enter to close...' " &
            ;;
        gnome-terminal)
            gnome-terminal --title="$title" -- bash -c "$cmd; echo; read -p 'Tipe Enter to close...' " &
            ;;
        xfce4-terminal)
            xfce4-terminal --title="$title" -e "bash -c \"$cmd; echo; read -p 'Tipe Enter to close...' " &
            ;;
        xterm)
            xterm -title "$title" -e bash -c "$cmd; echo; read -p 'Tipe Enter to close...' " &
            ;;
    esac
}

echo ""
echo -e "${BOLD}========================================${RESET}"
echo -e "  Physical config : ${CYAN}$CONFIG${RESET}"
echo -e "  Physical nodes  : ${CYAN}$NODE_COUNT${RESET}"
if $LAUNCH_VIRTUAL; then
    echo -e "  Virtual nodes   : ${CYAN}$RING_SIZE${RESET}  (ring)"
    echo -e "  Initial mapping :"
    for ((v=0; v<RING_SIZE; v++)); do
        p=$((v % NODE_COUNT))
        echo -e "    V${v} -> P${p}"
    done
fi
echo -e "  Broker RabbitMQ   : ${CYAN}$RABBIT${RESET}"
echo -e "  Terminal emulator : ${CYAN}$TERM_CMD${RESET}"
echo -e "${BOLD}========================================${RESET}"
echo ""

read -rp "$(echo -e ${YELLOW}Proceed? [Y/n] ${RESET})" confirm
confirm="${confirm:-Y}"
[[ "$confirm" =~ ^[Yy]$ ]] || { info "Canceled."; exit 0; }

echo ""

info "Launching $NODE_COUNT physical node(s)..."

for ((p=0; p<NODE_COUNT; p++)); do
    title="Physical P${p}"
    cmd="java -jar '$(pwd)/$JAR_PHYS' $p '$CONFIG' '$RABBIT'"
    info "  -> $title"
    open_terminal "$title" "$cmd"
    sleep $DELAY_S
done

success "$NODE_COUNT physical node(s) launched."

if $LAUNCH_VIRTUAL; then
    WAIT_S=5
    echo ""
    info "Waiting ${WAIT_S}s for spanning tree to stabilize..."
    sleep $WAIT_S
    echo ""
    info "Launching $RING_SIZE virtual node(s) in ring topology..."

    for ((v=0; v<RING_SIZE; v++)); do
        left=$(( (v - 1 + RING_SIZE) % RING_SIZE ))
        right=$(( (v + 1) % RING_SIZE ))
        title="Virtual V${v}  [<-V${left}  ->V${right}]"
        cmd="java -jar '$(pwd)/$JAR_VIRT' $v $RING_SIZE '$CONFIG' '$RABBIT'"
        info "  -> $title"
        open_terminal "$title" "$cmd"
        sleep $DELAY_S
    done

    success "$RING_SIZE virtual node(s) launched."
fi

success "All nodes launched successfully!"