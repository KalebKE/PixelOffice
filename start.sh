#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$script_dir"

echo "Building and launching Pixel Office..."
exec ./gradlew desktop:run --console=plain
