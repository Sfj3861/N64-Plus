# SNES library detection prototype

This branch adds a SAF tree scanner for SNES content and a UI list for selecting detected entries.

- Folder scanning via Android DocumentFile.
- Recognizes .sfc, .smc, .fig, .swc and .zip names.
- Traverses folders up to two directory levels below the selected root.
- Displays game-name candidates and retains each content URI.
- Launch is explicitly gated: this is not yet a running emulator. Libretro core loading, content URI bridging, video/audio/input loop and lifecycle are still unimplemented.
- Scanning is limited to the SNES-selected system in this prototype.

No ROMs are included. Users select folders containing their own files.
