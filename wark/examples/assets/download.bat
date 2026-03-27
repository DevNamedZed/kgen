@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

set DOOM_WASM_VERSION=v0.1.0
set SDL2_VERSION=2.30.11

echo === Wark Asset Downloader ===
echo.

rem --- doom.wasm ---
if exist doom.wasm (
    echo [ok] doom.wasm already exists
) else (
    echo [downloading] doom.wasm (%DOOM_WASM_VERSION%)...
    curl -fSL -o doom.wasm "https://github.com/jacobenget/doom.wasm/releases/download/%DOOM_WASM_VERSION%/doom-%DOOM_WASM_VERSION%.wasm"
    if errorlevel 1 (
        echo [error] Failed to download doom.wasm
        echo         Visit: https://github.com/jacobenget/doom.wasm/releases
    ) else (
        echo [ok] doom.wasm
    )
)

rem --- doom1.wad (shareware) ---
if exist doom1.wad (
    echo [ok] doom1.wad already exists
) else (
    echo [downloading] doom1.wad ^(shareware^)...
    curl -fSL -o doom1.wad "https://distro.ibiblio.org/slitaz/sources/packages/d/doom1.wad"
    if errorlevel 1 (
        echo [fallback] Trying archive.org...
        curl -fSL -o doom1.wad "https://archive.org/download/2020_03_22_DOOM/DOOM%%20WADs/DOOM1.WAD"
        if errorlevel 1 (
            echo [error] Failed to download doom1.wad
            echo         Download DOOM1.WAD manually and place it here.
        ) else (
            echo [ok] doom1.wad
        )
    ) else (
        echo [ok] doom1.wad
    )
)

rem --- SDL2.dll (Windows x64) ---
if exist SDL2.dll (
    echo [ok] SDL2.dll already exists
) else (
    echo [downloading] SDL2 %SDL2_VERSION% ^(Windows x64^)...
    curl -fSL -o sdl2-tmp.zip "https://github.com/libsdl-org/SDL/releases/download/release-%SDL2_VERSION%/SDL2-%SDL2_VERSION%-win32-x64.zip"
    if errorlevel 1 (
        echo [error] Failed to download SDL2
        echo         Visit: https://github.com/libsdl-org/SDL/releases
    ) else (
        tar -xf sdl2-tmp.zip SDL2.dll 2>nul
        if not exist SDL2.dll (
            powershell -Command "Expand-Archive -Path sdl2-tmp.zip -DestinationPath sdl2-tmp -Force; Copy-Item sdl2-tmp\SDL2.dll ."
            rd /s /q sdl2-tmp 2>nul
        )
        del sdl2-tmp.zip 2>nul
        echo [ok] SDL2.dll
    )
)

echo.
echo === Done ===
dir /b *.wasm *.wad *.dll 2>nul
