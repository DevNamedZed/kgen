#!/bin/sh
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

DOOM_WASM_VERSION="v0.1.0"
SDL2_VERSION="2.30.11"

echo "=== Wark Asset Downloader ==="
echo ""

# --- doom.wasm ---
if [ -f doom.wasm ]; then
    echo "[ok] doom.wasm already exists"
else
    echo "[downloading] doom.wasm ($DOOM_WASM_VERSION)..."
    curl -fSL -o doom.wasm \
        "https://github.com/jacobenget/doom.wasm/releases/download/$DOOM_WASM_VERSION/doom-$DOOM_WASM_VERSION.wasm"
    echo "[ok] doom.wasm"
fi

# --- doom1.wad (shareware) ---
if [ -f doom1.wad ]; then
    echo "[ok] doom1.wad already exists"
else
    echo "[downloading] doom1.wad (shareware)..."
    curl -fSL -o doom1.wad \
        "https://distro.ibiblio.org/slitaz/sources/packages/d/doom1.wad" \
        || curl -fSL -o doom1.wad \
            "https://archive.org/download/2020_03_22_DOOM/DOOM%20WADs/DOOM1.WAD"
    echo "[ok] doom1.wad"
fi

# --- SDL2 ---
OS="$(uname -s)"

case "$OS" in
    Linux*)
        if ldconfig -p 2>/dev/null | grep -q libSDL2; then
            echo "[ok] SDL2 already installed"
        else
            echo "[info] Install SDL2 via your package manager:"
            echo "  Ubuntu/Debian: sudo apt install libsdl2-dev"
            echo "  Fedora:        sudo dnf install SDL2-devel"
            echo "  Arch:          sudo pacman -S sdl2"
        fi
        ;;
    Darwin*)
        if [ -f /opt/homebrew/lib/libSDL2.dylib ] || [ -f /usr/local/lib/libSDL2.dylib ]; then
            echo "[ok] SDL2 already installed"
        else
            echo "[info] Install SDL2 via Homebrew:"
            echo "  brew install sdl2"
        fi
        ;;
    MINGW*|MSYS*|CYGWIN*)
        if [ -f SDL2.dll ]; then
            echo "[ok] SDL2.dll already exists"
        else
            echo "[downloading] SDL2 $SDL2_VERSION (Windows x64)..."
            curl -fSL -o sdl2-tmp.zip \
                "https://github.com/libsdl-org/SDL/releases/download/release-$SDL2_VERSION/SDL2-$SDL2_VERSION-win32-x64.zip"
            unzip -o sdl2-tmp.zip SDL2.dll -d "$DIR"
            rm -f sdl2-tmp.zip
            echo "[ok] SDL2.dll"
        fi
        ;;
    *)
        echo "[warn] Unknown OS: $OS — download SDL2 manually"
        ;;
esac

# --- WASM-4 Cartridges ---
echo ""
echo "--- WASM-4 games ---"
mkdir -p "$DIR/wasm4"

download_wasm4() {
    local name="$1"
    local file="$2"
    local url="$3"
    if [ -f "$DIR/wasm4/$file" ]; then
        echo "[ok] $name ($file) already exists"
    else
        echo "[downloading] $name..."
        curl -fSL -o "$DIR/wasm4/$file" "$url"
        echo "[ok] $file"
    fi
}

# Read from games.json if jq is available, otherwise hardcode
if command -v jq >/dev/null 2>&1 && [ -f "$DIR/wasm4/games.json" ]; then
    jq -r '.games[] | "\(.name)|\(.file)|\(.url)"' "$DIR/wasm4/games.json" | while IFS='|' read -r name file url; do
        download_wasm4 "$name" "$file" "$url"
    done
else
    download_wasm4 "Watris" "watris.wasm" "https://wasm4.org/carts/watris.wasm"
    download_wasm4 "Snake" "snake.wasm" "https://wasm4.org/carts/snake.wasm"
    download_wasm4 "Platformer" "platformer.wasm" "https://wasm4.org/carts/platformer.wasm"
fi

echo ""
echo "=== Done ==="
ls -lh "$DIR"/*.wasm "$DIR"/*.wad "$DIR"/SDL2.dll "$DIR"/libSDL2.* "$DIR"/wasm4/*.wasm 2>/dev/null || true
