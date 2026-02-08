#!/usr/bin/env bash
set -euo pipefail

PORT=9999

# --- Check prerequisites ---
if [ -z "${TMUX:-}" ]; then
    echo "Error: Not inside a tmux session."
    echo "Usage: Run this script from a tmux pane to connect it to Pixel Office."
    exit 1
fi

if ! nc -z localhost "$PORT" 2>/dev/null; then
    echo "Error: Pixel Office is not running on port $PORT."
    echo "Start the game first with: ./start.sh"
    exit 1
fi

# --- Tmux mouse scroll fix ---
tmux set -g mouse on
tmux bind-key -T root WheelUpPane \
    if-shell -F '#{alternate_on}' 'send-keys -M' 'copy-mode -e; send-keys -M'
tmux bind-key -T root WheelDownPane \
    if-shell -F '#{alternate_on}' 'send-keys -M' 'send-keys -M'

# --- Connect ---
tmux pipe-pane -o "nc localhost $PORT"
echo "Connected this tmux pane to Pixel Office on port $PORT"
echo "To disconnect: tmux pipe-pane"
