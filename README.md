# MediaShelf 0.4 — Android TV and phone catalog

Native Android app for search through existing Jackett indexers, poster and details enrichment, and local/LAN TorrServer playback.

## What's new in 0.4

- **TV-first design:** large focusable cards and a left source rail when screen width is 680dp or greater. Phone uses a compact source dropdown/menu and two-column grid.
- **All sites / one site:** choose `Все сайты` to query Jackett meta indexer `all`, or choose one of the **configured** sites read dynamically from `/api/v2.0/indexers?configured=true`. No hardcoded 32-ID list.
- **Real site categories:** for a selected site, category choices use the indexer's `caps` response and the request carries `Category[]`. For all-sites search, category taxonomies differ and are deliberately not mixed into one misleading list.
- **Diagnostics:** on-demand individual probes and all-sites probes show results, poster field coverage, and indexer errors. A configured source is *not* automatically considered operational. Zero results on an empty query are not called a failure.
- Search shortcuts, newest/popular sorting, TorrServer video file picker and external video playback remain.

## Verified versus unverified

Previous user test: searching XXXClub returned releases and poster images in MediaShelf 0.3. The 0.4 upgrade is compiled, but TV focus behavior, all 32 sites, site categories and player integration still require device/network testing. Other indexers are not falsely marked as tested. Per-site richer descriptions/screenshots require specific site parsers; currently XXXClub has the optional detail-page enrichment module.

Search for `Все сайты` uses Jackett `/api/v2.0/indexers/all/results`, and reads per-indexer status from `Indexers[]` where present. Selecting a site uses the same API with its actual indexer ID. Generic search categories are not invented for trackers that don't expose them.

## Local connection settings

- Jackett: `http://192.168.50.2:9117` for the user's existing home setup, or set another LAN address.
- API key entered within the app (locally stored; never commit or paste the key into public source).
- TorrServer: configure the actual service address. `127.0.0.1:8090` only works if TorrServer runs on the same Android device.

## Stable installation signing (CRITICAL)

Package name **`org.mediashelf.tv`** and fixed signing certificate SHA-256:

`80:E4:EF:86:67:A9:C5:CF:7C:AF:B6:B4:94:82:29:A8:F9:5C:E5:81:A3:A3:F6:89:A2:6A:F1:3E:6D:15:46:DB`.

Version 0.3 was first signed using the **private fixed test keystore** retained in the user's private recovery ZIP. Version 0.4 and all future releases must use that exact same key and increment `versionCode` (0.3 = 3; 0.4 = 4). **Do not generate another key.**

This public GitHub repo deliberately only compiles an unsigned APK. The signed APK and private recovery ZIP are delivered in the ChatGPT conversation. The recovery ZIP contains the private signing key and password and must never be uploaded to a public repository.

## Build workflow

GitHub Actions `.github/workflows/build.yml` uses Gradle 8.9, JDK17, Android API35; outputs `MediaShelf-0.4-unsigned.apk`, `MediaShelf-0.4-source.zip`, and Android `apksigner.jar`/zipalign. The final APK must be aligned, signed privately with the preserved key, and verified via `apksigner verify --verbose --print-certs`.

## Known limitations

- Site-specific image/description scraping can break if websites change their markup or block app requests.
- Some configured Jackett indexers can be unreachable; diagnostics explicitly distinguishes successful responses, no results and errors.
- Generic category buttons such as Gangbang are query presets, not standardized categories across trackers.
- This is an experimental standalone APK; do not expose Jackett/TorrServer to the public Internet.

## 0.4.1 patch
App name in the launcher, MIUI security scan, Android TV launcher and system settings is now always **MediaShelf**. Version numbers belong only in Android package metadata (`versionName`), never the app label. `versionCode` is 5 for an in-place upgrade from signed 0.4, and the release must use the same private signing certificate.
