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

## 0.5 — selector and label fix
- Launcher/app name is permanently `MediaShelf`, not `MediaShelf 0.1`.
- Version is `0.5-test` (`versionCode 5`), without .4.1 style patch numbering.
- 'Выбрать сайт' never blocks when `/api/v2.0/indexers` fails; it always offers `Все сайты`, `XXXClub`, a manual source-ID entry, and a refresh action.
- Last successfully retrieved list of configured indexers is cached in app preferences.
- The all-sites search also collects source IDs and names from Jackett's `Indexers` report as a secondary list discovery mechanism.
- Status shows indexer-list errors instead of pretending all sites are working; sites remain unverified until queried.
- Install as an in-place update over MediaShelf 0.3 or 0.4 with the **same** signing certificate.

## 0.6 — source-specific category and cover diagnosis

**Why this release:** the phone showed `HTTP 400` from Jackett's global `/api/v2.0/indexers` endpoint even though indexed releases appeared. This prevented the category dialog for XXXClub and all selected sources. Many all-site results from torrent-pirat were also missing covers.

- Category dialog fetches `/api/v2.0/indexers/<selected-id>/results/torznab/api?t=caps&apikey=...` **per site** and never depends on the global source-list endpoint.
- Known XXXClub Torznab category mapping is bundled as fallback (Movies/DVD/WEB=6090, x264=6040, 4K=6045, SD=6080, Packs=6050, ImageSet=6060, VR/Other=6070).
- For torrent-pirat, Jackett maps its many site forums to one generalized category `XXX=6000`. The native site forums are **not** silently treated as separately selectable categories. Specific site-forum support would require another adapter.
- The site-aware HTML details adapter now supports XXXClub (real detail-page poster), torrent-pirat (phpBB post-body image candidates), and MyPornClub (post page image candidates). Other sources still show a clear no-cover placeholder until a dedicated parser is added.
- The app has per-source diagnostics for search response, category availability, poster URL coverage and actual HTTP image access. The "check all" function checks on the user's network and can be slow; status claims are limited to actual results.
- `MediaShelf` is still the fixed launcher name, and `versionCode` increases to 6 for upgrade over signed 0.5 with preserved certificate and preferences.

**Testing disclaimer:** compilation/Android signature are checked by CI and local signing tools. No direct access to the user's private Home Assistant/Jackett or TV is available, so site-specific network behaviour and image heuristics require user-side testing. A URL returning HTTP 200 does not guarantee a correct poster (it could be a thumbnail); the app now prioritizes XXXClub's detail-page poster.

## 0.7: first-page limitation and site navigation (experimental)

**Root cause:** Jackett's `xxxclub.yml` queries `torrents/browse/all` or `torrents/search/all/<keyword>` with a single `search.paths` entry and has no pagination rules. As a result, about 50 titles per query are returned. Filtering the first page by XXX Movies/DVD/WEB yields only a small subset (the user observed 12), which does *not* measure the full site category.

MediaShelf 0.7 introduces `app/src/main/java/org/mediashelf/tv/SitePages.java`:
- "Загрузить ещё с XXXClub" uses the site's own HTML, parses the result list, and follows actual pagination anchors. It does not invent generic offset requests or assume all indexers paginate alike.
- Native category mapping: `6000+` Torznab categories are translated to XXXClub's site IDs, e.g. 6090→3 (Movies/DVD/WEB). URL `/torrents/browse/3` is a best-effort initial category route, **not verified live**; the app detects HTML format and errors visibly.
- In a category, site-page discovery starts in the actual category context rather than filtering an arbitrary page of all-site releases; in search it uses the keyword URL. If site pagination is missing or blocked, the UI states that the full catalog could not be retrieved.
- Each page is merged into the existing catalog and deduplicated by details URL. Two pages may be read on first click if the initial site page duplicates all Jackett results. No silent maximum of 50 or 120; the grid renders up to 60 at a time with on-demand "Показать ещё" to avoid creating thousands of views.
- When clicking a release obtained directly from the website, `SiteDetails` resolves the magnet or torrent download link in the details page before enabling TorrServer playback.
- The site pager is modular and currently active only for XXXClub. Other indexers need their own supported pagination parser and must not be labelled complete.
- **Source of truth** for APK signing: use the same private signing key/fingerprint as 0.3–0.6; applicationId stays `org.mediashelf.tv`, versionCode 7. Never commit the key to the public GitHub repo.

The direct website may block or change its HTML, so runtime validation on the user's device is still required. This change deliberately keeps Jackett as the initial search fallback until direct pagination proves reliable.
