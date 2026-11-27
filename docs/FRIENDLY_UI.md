# Friendly UI prototype

This branch replaces the placeholder Activity screen with a simple Android-native UI:
- system selection cards for Nintendo 64, SNES, Wii, and GameCube
- file selection using Android's Storage Access Framework
- selected-file display and launch action
- status messages that explain engine readiness

This is not yet a playable emulator frontend. The launch action intentionally reports that the backend is a stub. The selected file's read permission is retained when the provider supports it.

Next UI/backend work: filter formats per system, artwork and metadata, library scanning, error states, engine initialization/loading progress, settings, and actual native renderer/input integration.
