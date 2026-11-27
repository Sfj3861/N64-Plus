# SNES runtime integration scaffold

This branch introduces a JNI shared library and a native dynamic-core probe, plus an Android runtime activity.

**This is not a playable emulator integration yet.** The app does not bundle a Libretro SNES core, and the frontend callbacks, content loading, frame loop, Android rendering, audio, input, and lifecycle integration remain unfinished. The probe checks core library presence and symbols only.

The expected core path is `app-private files/cores/libretro_snes.so`. Do not download or bundle copyrighted ROMs. The user must supply their own lawful game files.