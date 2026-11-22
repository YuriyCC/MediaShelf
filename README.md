# MediaShelf 0.2 — Android TV test build

Standalone Android UI for browsing Jackett indexed content, showing posters when available, and streaming a selected torrent through a previously installed TorrServer and external player.

## Status
Test prototype; v0.2 uses optional XXXClub details-page parser for poster, description and screenshot enrichment. Sites may block direct requests. independent from Cinebox. Source is in the `MediaShelf/` directory on the dedicated `mediashelf-mvp` branch, with build workflow at repository root. It is not yet verified on physical devices.

## Connection
1. On first launch: Jackett URL `http://192.168.50.2:9117`.
2. Enter the Jackett API key from your own interface. **Never commit or send the key**. Stored only in app-private preferences.
3. Indexer defaults to `xxxclub`, switch to `all` or another indexed source in Settings.
4. TorrServer defaults to `http://127.0.0.1:8090` for a local Android service; change it to the server IP if TorrServer runs elsewhere.
5. Install a player accepting Android ACTION_VIEW video URLs.

The main-page prefetch is intentionally limited to 18 cards to avoid overloading XXXClub. Screenshots are fetched when opening details. The search module is separate from the UI and can be replaced by an on-device provider later. Categories are presently search keywords, not metadata genres. Latest sorts by date; Popular sorts by seed count. Poster coverage depends on the Jackett indexer; no TMDB is used.

## Build
Pushing to `mediashelf-mvp` runs the GitHub Actions workflow `MediaShelf APK (prototype)`. The downloadable artifact is named `MediaShelf-debug`.

## Caveats
GitHub Actions build and device behavior need verification. This does not include Jackett or TorrServer binaries. Use on networks you control; do not expose these services directly to the internet.
