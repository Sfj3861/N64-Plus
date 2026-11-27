# Integration status

This branch introduces the Android application shell, console registry and engine lifecycle interfaces.

## Wiring status
- N64: Mupen64Plus-Next source imported; adapter API stub created. Native Libretro ABI, core build, renderer/audio/input callbacks and content loading are not implemented.
- SNES: bsnes-libretro source imported; adapter API stub created. Native build/ABI integration is not implemented.
- Wii/GameCube: Dolphin source imported. Dolphin's Android project is standalone and requires its own build configuration, JNI integration, surface/input/audio lifecycle and packaging work. No JNI bridge yet.

## Current limitation
The app is a scaffold and is **not a functioning emulator or APK release**. The stub adapters deliberately report failure instead of pretending to launch games.

## Next milestones
1. Establish Android NDK/CMake toolchain and native build environment.
2. Implement Libretro JNI host (callbacks, video/audio, input, lifecycle, content paths).
3. Build and load N64 core for supported Android ABIs; verify with homebrew/test content.
4. Repeat for SNES core, after licensing/build audit.
5. Integrate Dolphin native Android library/host surface; reconcile its existing Android app architecture with this app.
6. Add SAF storage, saves, permissions, controller mapping, diagnostics and compatibility tests.
7. Audit licenses/notices and packaging before distribution.

Imported source remains unchanged under its folders.