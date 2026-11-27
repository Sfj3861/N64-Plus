# SNES Libretro frontend

## Implemented frontend

- Loads a compatible Android Libretro core from app-private storage.
- Checks the Libretro API version and required entry points.
- Initializes the core and loads the selected content by path.
- Executes retro_run on a dedicated, frame-paced thread.
- Renders XRGB8888 video frames to an Android Surface through ANativeWindow.
- Streams stereo signed 16-bit audio into Android AudioTrack.
- Maps on-screen SNES controls to the Libretro joypad device.
- Stops the run loop before unloading the game and core.
- Handles activity and Surface lifecycle transitions.

## Required external core

The app does not currently bundle a SNES core. Put a compatible ARM64 Android Libretro SNES core at:

`/data/data/com.sfj.n64plus/files/cores/libretro_snes.so`

The core must report Libretro API version 1 and accept XRGB8888 output. The selected ROM must be supported by that core. The app does not provide game ROMs.

## Known limitations

- No save-state UI or SRAM persistence yet.
- No Bluetooth/controller mapping yet.
- No aspect-ratio or shader settings yet.
- The current video frontend expects XRGB8888 output.
- Loading errors are shown in the player status text.
