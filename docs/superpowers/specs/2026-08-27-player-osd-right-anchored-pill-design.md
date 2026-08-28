# Right-anchored OSD state pill

The right state indicator uses its natural content width. Remove the fixed
width and retain the weighted spacer before it, so it stays aligned to the
right edge without affecting vertical layout or the left transport controls.

- Normal playback keeps the `NAŽIVO` pill.
- Switching replaces it with a small indeterminate spinner and no text.
- Automatic recovery or an unavailable programme replaces it with `↻` and no
  countdown text.

Verification: compile and install the debug APK, then inspect `NAŽIVO`, the
switching spinner, and the recovery icon on the TV.
