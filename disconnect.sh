#!/usr/bin/env bash
set -euo pipefail

# Generate terminal ID the same way connect.sh does
TERMINAL_ID="${TMUX_PANE:-$$}"
TERMINAL_ID="${TERMINAL_ID#%}"

PIDFILE="/tmp/pixel-office-heartbeat-${TERMINAL_ID}.pid"

# Stop heartbeat
if [ -f "$PIDFILE" ]; then
    kill "$(cat "$PIDFILE")" 2>/dev/null || true
    rm -f "$PIDFILE"
    echo "Heartbeat stopped."
else
    echo "No heartbeat found for terminal $TERMINAL_ID."
fi

# Disconnect pipe-pane
if [ -n "${TMUX:-}" ]; then
    tmux pipe-pane
    echo "Disconnected from Pixel Office."
else
    echo "Not in a tmux session."
fi
