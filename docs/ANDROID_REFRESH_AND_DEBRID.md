# Android refresh, debrid providers and sync scheduling

Android now reports Display.refreshRate, rather than the physical mode's nominal
rate. Android 15 rendering views receive the selected frame-rate vote recursively:
a ViewGroup vote is not inherited by its children. SurfaceView player surfaces
receive their own API 30+ frame-rate hint when created and when the selection
changes. Requests are updated after layout/foreground/display changes and removed
with the Activity lifecycle. Unsupported modes are not invented. Thermal/battery/
OEM restrictions still take precedence; a 24 fps video remains a 24 fps video.

Provider order is AllDebrid, Real-Debrid, Torbox, Premiumize on all three clients.
AllDebrid and Real-Debrid authenticate with a personal API key validated directly
against their official user endpoint. No Nuvio backend or bundled public client
secret is involved. Their torrent resolver supports configured client-resolve streams
and local torrent streams, with exact file/episode selection. AllDebrid uses the
magnet upload/files tree and link unlock endpoints; a pending download is reported
as not ready rather than given a fabricated playable URL. Unsupported cloud-library
and cache-check capabilities are not advertised. Keys remain in device storage.

Watch-state reconciliation now precedes third-party addon/code fetching.
Collections/layout follow addon installation so their identifiers are available;
all of these operations precede large profile images. TV still links profile identities before syncing
account state, but downloads photos afterward. This preserves the existing ownership,
revision/conflict and offline-outbox rules. The complete compatible catalogue format
and 4 MB limit are unchanged; arbitrary websites/formats require an importer.

Validation: shared API success/error/nested-file tests on each target, Android HTTP
integration test with an actual local server and credential/form checks, Android 15
nested-view frame-rate test, existing full catalogue fixtures, playback/history and
home tests, and the upstream-domain audit. Private paid accounts and physical high-
refresh displays require device/account validation; no premium subscription is supplied.

Official references:
- https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate
- https://developer.android.com/media/optimize/performance/frame-rate
- https://docs.alldebrid.com/
- https://api.real-debrid.com/
