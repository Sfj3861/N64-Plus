# SNES runtime integration scaffold

## Implemented in this iteration

- The selected SAF document URI, title and extension are passed to EmulatorActivity.
- The activity copies the selected content into an app-private cache directory before runtime setup.
- Empty files, unreadable document URIs and cache-directory creation failures are reported.
- The optional core loader checks the Libretro frontend entry points, closes stale handles before re-probing, and releases incompatible libraries.
- The activity closes the loaded library when it is destroyed.

## Current limitations

This is a runtime staging and compatibility-probe milestone, **not playable emulation**. The native layer does not yet call retro_set_environment, initialize the core, load staged content with retro_load_game, or execute retro_run. Video rendering, audio output, input mapping, save data and lifecycle pause/resume integration remain pending.

The expected core path is app-private files/cores/libretro_snes.so. The app does not bundle ROMs or third-party core binaries. Users must provide content they are legally entitled to use and a compatible core build.