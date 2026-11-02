# Original profile media and desktop lifecycle

Windows observes monitor modes at startup and focus changes. It changes the physical
refresh rate only after an explicit minimum/maximum edit. It does not switch back
at exit. A user-requested physical mode switch can still briefly blank a monitor.
Mode enumeration is reused while the monitor and its current mode are unchanged.

A per-user file lock prevents duplicate application launches, including simultaneous
launches. A private filesystem mailbox forwards protocol arguments unchanged and
brings the existing window forward. The lock is released by the OS on a crash.
Native player/WebView warmup no longer runs at application startup. Profile GIFs
pause and release their decoder when the window is unfocused, retaining one still
frame; animation restarts on focus. The collection
HTTP bridge is explicitly stopped on window close. Player subprocesses during
playback can still be legitimate; their number alone is not a memory-leak diagnosis.

JPEG, PNG, WebP and GIF profile originals are imported and uploaded byte-for-byte,
including animation, transparency and metadata. Both avatar and background use the
same rule. No JPEG recompression or 512/1920-pixel normalization occurs. Images must
be decodable, at most 18,000,000 bytes and 32 megapixels. Other formats are rejected
with an explanation, not silently converted. Existing previously reduced images
cannot regain their original quality: reimport the original. The cloud protocol,
SHA-256 integrity verification and private account/profile isolation are unchanged.

TV avatars request their displayed pixel size and full colour instead of RGB565;
this reduces decoded memory without changing the stored/synchronized original.
Existing constrained-TV cache limits and bounded concurrent decoders remain active.
Display resampling is separate from preserving original file quality.

Cloudflare logout retains local state, pending media/catalogue checkpoints and
server data. Reconnecting the same account resumes reconciliation. This does not
promise that an offline edit appears on another device before it has been uploaded.
The catalogue protocol retains complete compatible definitions, order and categories,
up to its existing 4 MB snapshot limit; it cannot turn arbitrary third-party websites
into compatible catalogues or guarantee third-party availability/credentials.

Validation includes desktop original-byte round trips, Android 2048x1024 original
round trips plus animated GIFs, process-lock/launch forwarding tests, existing
pending-upload recreation/account-isolation tests and full catalogue fixture tests.
The Windows packaged-player check also exercises a second launch and normal exit.
These tests do not measure real TV frame rates or eliminate hardware-specific risks.
