#!/usr/bin/env bash
set -euo pipefail

PORT=9999
TIMEOUT=30

script_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$script_dir"

# --- Tmux mouse scroll fix ---
apply_tmux_mouse_fix() {
    if [ -n "${TMUX:-}" ]; then
        tmux set -g mouse on
        tmux bind-key -T root WheelUpPane \
            if-shell -F '#{alternate_on}' 'send-keys -M' 'copy-mode -e; send-keys -M'
        tmux bind-key -T root WheelDownPane \
            if-shell -F '#{alternate_on}' 'send-keys -M' 'send-keys -M'
    fi
}

# --- Build and launch ---
GAME_LOG="$script_dir/.game.log"
echo "Building and launching Pixel Office (log: $GAME_LOG)..."
./gradlew desktop:run --console=plain > "$GAME_LOG" 2>&1 &
GAME_PID=$!

# Wait for the server to be ready
echo "Waiting for game server on port $PORT..."
elapsed=0
while ! (echo > /dev/tcp/localhost/$PORT) 2>/dev/null; do
    sleep 1
    elapsed=$((elapsed + 1))
    if [ $elapsed -ge $TIMEOUT ]; then
        echo "Error: Game server did not start within ${TIMEOUT}s"
        kill "$GAME_PID" 2>/dev/null || true
        exit 1
    fi
done
echo "Game server ready on port $PORT"

# --- Auto-connect tmux pane ---
if [ -n "${TMUX:-}" ]; then
    tmux pipe-pane -o "nc localhost $PORT"
    echo "Connected current tmux pane to Pixel Office"
    apply_tmux_mouse_fix
else
    echo "Not in tmux — connect manually with: pixel-office-connect (from a tmux pane)"
fi

# --- Wait for game; clean up on exit ---
cleanup() {
    if [ -n "${TMUX:-}" ]; then
        tmux pipe-pane
    fi
    kill "$GAME_PID" 2>/dev/null || true
    wait "$GAME_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "Game is running (PID $GAME_PID). Press Ctrl+C to stop."
wait "$GAME_PID" 2>/dev/null || true
