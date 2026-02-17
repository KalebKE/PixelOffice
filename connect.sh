#!/usr/bin/env bash
set -euo pipefail

DEFAULT_PORT=9999
MULTICAST_GROUP="239.255.80.79"
MULTICAST_PORT=9998
DISCOVERY_TIMEOUT=5
HEARTBEAT_PORT=9997
HEARTBEAT_INTERVAL=30

# Generate unique terminal ID from tmux pane or PID
TERMINAL_ID="${TMUX_PANE:-$$}"
# Clean up tmux pane ID (remove % prefix)
TERMINAL_ID="${TERMINAL_ID#%}"

# --- Check prerequisites ---
if [ -z "${TMUX:-}" ]; then
    echo "Error: Not inside a tmux session."
    echo "Usage: Run this script from a tmux pane to connect it to Pixel Office."
    exit 1
fi

# --- Discover server via UDP multicast ---
echo "Discovering Pixel Office server on LAN..."
DISCOVERY=$(python3 -c "
import socket, struct, sys
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
sock.bind(('', $MULTICAST_PORT))
mreq = struct.pack('4sL', socket.inet_aton('$MULTICAST_GROUP'), socket.INADDR_ANY)
sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)
sock.settimeout($DISCOVERY_TIMEOUT)
try:
    data, addr = sock.recvfrom(256)
    msg = data.decode()
    if msg.startswith('PIXELOFFICE:'):
        print(addr[0] + ':' + msg.split(':',1)[1])
except socket.timeout:
    pass
finally:
    sock.close()
" 2>/dev/null || true)

if [ -n "$DISCOVERY" ]; then
    HOST="${DISCOVERY%%:*}"
    PORT="${DISCOVERY##*:}"
    echo "Found server at $HOST:$PORT"
else
    echo "No server found via discovery, falling back to localhost"
    HOST="localhost"
    PORT="$DEFAULT_PORT"
fi

# --- Check server is reachable ---
if ! nc -z "$HOST" "$PORT" 2>/dev/null; then
    echo "Error: Pixel Office is not running on $HOST:$PORT."
    echo "Start the game first with: ./start.sh"
    exit 1
fi

# --- Tmux mouse scroll fix ---
tmux set -g mouse on
tmux bind-key -T root WheelUpPane \
    if-shell -F '#{alternate_on}' 'send-keys -M' 'copy-mode -e; send-keys -M'
tmux bind-key -T root WheelDownPane \
    if-shell -F '#{alternate_on}' 'send-keys -M' 'send-keys -M'

# Send first heartbeat immediately (developer spawns before any data)
echo "HEARTBEAT:$TERMINAL_ID" | nc -u -w1 "$HOST" $HEARTBEAT_PORT 2>/dev/null || true

# --- Start heartbeat sender in background (detached) ---
PIDFILE="/tmp/pixel-office-heartbeat-${TERMINAL_ID}.pid"
(
    echo $BASHPID > "$PIDFILE"
    while true; do
        echo "HEARTBEAT:$TERMINAL_ID" | nc -u -w1 "$HOST" $HEARTBEAT_PORT 2>/dev/null || true
        sleep $HEARTBEAT_INTERVAL
    done
) &
disown

# --- Connect ---
tmux pipe-pane -o "nc $HOST $PORT"
echo "Connected this tmux pane to Pixel Office on $HOST:$PORT (terminal: $TERMINAL_ID)"
echo "To disconnect: pixel-office-disconnect (or tmux pipe-pane)"
