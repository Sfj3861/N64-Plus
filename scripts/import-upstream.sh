#!/usr/bin/env bash
# Import pinned upstream trees into N64 Plus. Run from repository root.
set -euo pipefail

import_repo() {
  local url="$1" dest="$2" commit="$3" tmp
  tmp="$(mktemp -d)"
  git clone --filter=blob:none "$url" "$tmp/repo"
  git -C "$tmp/repo" checkout "$commit"
  rsync -a --exclude='.git' "$tmp/repo/" "$dest/"
  rm -rf "$tmp"
}

mkdir -p Wii-GC N64 SNES
import_repo https://github.com/dolphin-emu/dolphin.git Wii-GC 44a1e006d172b426c489581ee1c362b82438438d
import_repo https://github.com/libretro/mupen64plus-libretro-nx.git N64 8623e490a8773fcb6c10f424cebbdb0ac44e88ea
import_repo https://github.com/libretro/bsnes-libretro.git SNES f9344df74b3a6de49996cd4841038443ef595502

git add Wii-GC N64 SNES SOURCE_ORIGINS.md
git commit -m "Import pinned emulator source trees"
