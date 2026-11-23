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

## 0.8 — native-first module architecture

The long-term product is a **single TV-first Android APK** with a curated adult-only tracker library, **one independently tested module per tracker**, a consistent catalog/search/film-detail UI, and optional TorrServer playback. The user prefers to finish the first useful curated set of modules before installing broadly. This release is a development milestone, not the finished site catalog.

Implemented in this commit:
- `SiteCatalog.java`: curated adult-only site registry with truthful per-site implementation status. XXXClub has a direct adapter; PornoLab, MyPornClub, torrent-pirat and XXXTor are listed as planned modules and temporarily rely on Jackett.
- `NativeSource.java`: explicit native module contract with separate discovery, category support, search/page cursor, detail poster/torrent enrichment; `XxxClubSource` implementation.
- **XXXClub searches directly from the Android app, no Jackett API key or Home Assistant required**. Direct HTML request has a fallback to Jackett only when optional bridge settings are present and the direct request fails.
- "Все подключённые" initially searches the only available native adapter, XXXClub, until additional native site modules have been developed. It MUST NOT claim to search unavailable/unimplemented sites.
- Java UI source selection and TV side menu show a curated adult-only list rather than 32 arbitrary indexers. Unimplemented direct modules are labelled honestly. Source-specific categories and diagnostics; no forced Jackett credential dialog at launch.
- Small dark-theme refinements, focus border, corrected first-25 poster detail prefetch, adaptive mobile/TV layout retained.
- Existing signed APK package and fixed signing cert are preserved; versionCode=8.

### Current limitations and acceptance test
- XXXClub pagination is still **not verified complete**: on the user's device the older module found 51 search results and could not find a next link. No code should claim it is full-site search until this is resolved with actual pagination markup.
- Native login sessions for private trackers are **not yet implemented**; never ask users to paste passwords into public GitHub or conversation. Jackett may remain an optional bridge.
- Visual design is an early native implementation and will be further refined in the finalized product.
- Some sites return thumbnails rather than portrait posters and require dedicated detail parsers, not generic assumptions.
- No adult torrent indexer list is considered endorsed solely because it appears in the registry: reliability, moderation, quality of releases and live access must be checked independently.
- Each module must pass live search (query+category), full pagination, image, details, torrent URL and playback tests, plus a negative test when the site is unavailable. Tested per site, not inferred from Jackett discovery.

## 0.9 — XXXClub opaque-cursor Next Page repair

User confirmed: after the first-page search at https://xxxclub.to/torrents/search/all/Gangbang the website's Next Page button opens https://xxxclub.to/torrents/search/all/Gangbang/7296816424212307464. The suffix is a long numeric cursor, not a page number. The site's navigation has no numeric page links.

Bug in 0.8: SitePages.findNext scanned only CSS .pagination, .pager, .next and exact 'next' text, so an ordinary Next Page anchor was missed. MediaShelf incorrectly labeled the 51-result first page "last".

0.9 searches "Next Page" anchors anywhere in the HTML, along with data-href and onclick handlers, using same-listing cursor URLs, never manufacturing tokens. If no continuation found, it states that this does NOT prove the site's catalog ended. Nine offline regression tests reproduce the user's URL format; CI runs testDebugUnitTest before packaging.

The user-provided link proves the URL scheme, but the live HTML could not be retrieved in the build environment. This is NOT yet a live end-to-end verification of all pages, and there is no claim the full site catalog is indexed. The optional Jackett bridge is unchanged; applicationId and signing certificate remain stable; versionCode 9.

## 1.0 — reusable pagination controller and real XXXClub Next Page filtering

The user confirmed category pagination worked in 0.9 but XXXClub keyword search still stopped at 51 results. A captured, independently saved November 2026 HTML fixture for XXXClub search showed SIX off-screen fake Next Page links to a decoy cursor (CSS `position:absolute; left:-9999px;`) alongside ONE genuine, visible `data-no-instant` Next Page link pointing to a different cursor. The old engine picked the first fake.

MediaShelf 1.0 introduces `PageNavigation.java` as a **reusable page-navigation controller** that every new native source can use with a site-specific URL validator. The controller:
- excludes hidden/out-of-viewport links (including ancestors with hidden styles);
- ignores Previous Page links;
- gives preference to visible "Next Page" buttons and explicit `data-no-instant` navigation metadata;
- does not guess cursors, avoiding broken/looping categories;
- keeps XXXClub-specific URL-context checks inside its own `SitePages` adapter rather than making a universal assumption about categories;
- recognizes real cursor pages in both search and browse sections.

Unit regressions (15 cases) now include a snippet reproduced from the real search HTML where six fake hidden links and the actual visible link have the same "Next Page" label, plus **all eight** native XXXClub category paths (0 through 7). HD 720p and 1080p are distinct app-native categories; Jackett's Torznab 6040 had merged them.

`gradle testDebugUnitTest assembleRelease` runs before exporting this unsigned build. These are offline checks, not a live end-to-end claim for every page on the user's device. The site is allowed to change its HTML, in which case the site-specific adapter may need another update.

## MediaShelf 1.1 — TV-first visual design (versionName "1.1", no "-test")

This is the visual/interaction release intended for Android TV testing with XXXClub before adding more native trackers. The application label is **MediaShelf** on TV and phones, and the Android OS version label is **1.1** (no `-test` suffix). Same applicationId `org.mediashelf.tv` and private signing key as versions 0.3–1.0.

Features:
- Full dark native interface (no WebView), consistent dark-navy/blue palette and new MediaShelf custom vector launcher icon plus Android TV banner.
- Television-first 240dp side navigation, clear current-site emphasis, site statuses, separate diagnostics and connection settings, adapted phone header and source menu.
- Dynamic category filter chips for XXXClub's eight native sections, including 720p and 1080p separately; swipeable on phones and D-pad focusable on TV. No hardcoded English search terms pretending to be main categories.
- Real result posters with controlled gradients, 4K/FULL HD/HD badges and seed counts, animated remote focus, balanced adaptive grid at both TV and phone sizes.
- Featured hero for latest/popular unfiltered listings, with a prominent detail-card action and an image from the actual tracker when available. No fake titles or promotional images.
- Dedicated film details dialog, large poster, data facts, description, screenshot gallery, working playback button if the tracker provides torrent/magnet, external site fallback, remote navigation.
- Bitmap LRU cache limits redundant image requests and memory usage (24 MiB). Grids present 60 at first (and show more), preserve scroll and focus appropriately during pagination.
- Direct XXXClub search, site cursor pagination and the 15 automated regression tests from 1.0 remain; Jackett is an optional fallback, not required for the first site.

Validation in CI: `gradle testDebugUnitTest assembleRelease`. Actual remote-control behavior, site access, poster quality and TorrServer playback must be verified on a real Android TV. **Do not mark the XXXClub module fully certified until end-to-end testing.**

## MediaShelf 1.2 — ТВ-предпросмотр на телефоне

Доступен пункт `⋮ → Включить ТВ-режим (горизонтально)` в приложении на телефоне. Он сохраняет `tv_preview=true` локально, переводит экран в альбомную ориентацию и использует настоящее ТВ-меню MediaShelf вместо статического макета. Возврат к телефонному виду через кнопку `Режим телефона` внизу боковой панели. Переустановка не требуется для переключения; сохранены исходные настройки источников, поиск и TorrServer.

На небольшом горизонтальном экране предпросмотр автоматически уменьшает боковое меню, заголовки, поиск, фильтры, баннер и высоту постеров. На реальном Android TV этот телефонный режим не появляется и телевизионная разметка 1.1 остаётся прежней. Версия приложения называется `1.2`, без `-test`. Пакет и сертификат подписи прежние. После смены ориентации восстанавливаются слова поиска, сортировка и выбранная категория; выдача обновляется.

Проверка: `gradle testDebugUnitTest assembleRelease`, затем ручная проверка горизонтального телефона. Предпросмотр не заменяет отдельную проверку реальным пультом.

## MediaShelf 1.3 — design iteration toward the approved TV mockup

The user compared a cinematic purple TV catalog mockup with MediaShelf 1.2 on a landscape phone and reported that the implemented interface looked significantly weaker. This iteration makes concrete improvements rather than showing another concept image.

Changes:
- Brand palette moves from muted blue to a deep navy/indigo and vivid violet accent, including the TV app icon/banner, sidebar focus, selected categories and action buttons.
- TV preview on a phone is immersive (hides status/navigation bars when preview is active; TV hardware is unchanged), retains an always available explicit `Режим телефона` exit button in the sidebar.
- Removes duplicated large title and status row in compact TV preview (still present on real TV). Search and category chips sit at the top like the design concept.
- Sidebar occupies ~20% of the logical phone width, with the catalog having priority.
- Shows the actual **featured hero with real tracker poster** at the top of latest/popular category views; resets list scrolling after a new query so the hero is not skipped due to stale ScrollView offset or focus.
- Compact-TV preview shows 4–6 thumbnails per row instead of three large portrait cards. TV hardware poster layout and normal portrait-phone layout remain unchanged.
- Shaves padding, headings and info labels in the small horizontal preview. Full site status and diagnostics are still available in the real TV layout and site diagnostics.
- Existing source modules, XXXClub pagination, TorrServer settings, signed package ID and TV/phone toggle logic remain intact.

Caution: The mockup used stylized scenic placeholder images. The actual application must display each tracker’s real image and release name, not fabricated content. Consequently the appearance can be brought close in spacing/color/composition, but the footage and artwork will differ.

Build checks: CI must run `testDebugUnitTest assembleRelease`. UI layout and focus behavior are still to be verified from real-device screenshots before being considered final.

## MediaShelf 1.4 — фильмовая карусель вместо случайного MegaPACK

По запросу пользователя большой баннер на главном экране заменён настоящей каруселью до пяти отдельных фильмов. **Карусель не берёт первый элемент общего каталога**: загружает отдельную страницу native-категории XXXClub № 3 (полные фильмы / DVD / WEB) и ранжирует реальные раздачи по разрешению в названии, числу раздающих и наличию обложки. MegaPACK/Pack/Collection/Сборник/SiteRip/фотосеты/VR исключаются, дубликаты ссылок устраняются. Отображаются только кандидаты с реальной обложкой и минимум одним сидом; если подходящих меньше пяти, показываются только найденные. При недоступности категории не подставляется случайная раздача; основной каталог остаётся доступным.

Управление: кнопки «‹» и «›» в баннере, индикатор текущего фильма, нажатие на баннер открывает карточку фильма, автоматическая смена каждые 7,5 секунды; если пользователь фокусируется на баннере или кнопках управления пультом, автоматическая смена пропускается. На паузе/выходе из приложения таймер останавливается; смена раздела/источника сбрасывает подборку и не допускает устаревших ответов. В горизонтальном ТВ-предпросмотре действует тот же функциональный механизм, но компактная высота баннера.

Критерии отбора — эвристики по названию и сидам, а не объективная независимая проверка художественного качества фильма. Подборка не добавляет выдуманных названий, обложек или внешней рекламы. Пользовательские предпочтения и персональный рейтинг пока не используются.

Новые файлы: `FeaturedSelector.java` и `FeaturedSelectorTest.java`; 9 независимых тестов исключения паков, пустых постеров, нулевых сидов, ранжирования и дублей. Старые 15 тестов пагинации продолжают выполняться.

Версия Android `1.4`, без `test`, код 14; package `org.mediashelf.tv`, подпись прежним личным сертификатом. Автоматическая сборка `testDebugUnitTest assembleRelease`; живую работу баннера и управление пультом необходимо дополнительно проверить на устройстве.

## MediaShelf 1.5 — compact TV interface, remote focus and configurable homepage banner

From an actual Android TV photo provided by the user and a comparison to Lampa's dense, laconic media catalog:
- **Removed oversized "Медиатека" title and full-width TV search input.** The TV now shows a compact MediaShelf top toolbar (site selector, search icon, banner settings icon, settings icon). A D-pad/remote-friendly search dialog provides keyword search and direct return to latest releases. The full search field remains on touch-phone layout.
- **Reduced TV sidebar and posters.** Sidebar ~176dp instead of 240dp, compact labels, 5–8 poster cards per row depending on actual dp width; titles limited to two lines on TV. Source menu, categories, playback and posters still work; responsive compact landscape-phone TV-preview retained.
- **Compact rotating carousel**: TV height 132dp instead of 228dp; no large call-to-action button. Manual arrows, five indicator dots and focus-aware 7.5s autoplay retained.
- **Banner category selection**: TV top star and sidebar "Настройка баннера" open a multi-select dialog for native site categories (novelties—all, film/DVD, 720p, 1080p, 4K, SD, packs, photo, VR). The default is "Новинки — все разделы" rather than "Фильмы".
- **Banner topic filter**: optional free-text query e.g. "Gangbang", combining category selection with the site's own exact native category search path. Independent per-source local preferences; no password or server requirement.
- **True-newest selection**: retains the native site's latest ordering, not an artificial ranking by seeders or 4K. Excludes dead seeds/posterless entries; by default filters MegaPACK and similar collections, but explicitly selected collection/photo/VR categories can populate the banner.
- **Remote up-navigation repaired**: grid rows use explicit focus IDs and DPAD-UP returns from first row to the banner or category chips; from banner up to chips, and from chips up to the top-bar search. Carousel controls also have up/down key routes.
- **No reuse of fabricated content:** posters and titles are real XXXClub site data only. Search and native cursor paging architecture unchanged.
- **Version**: 1.5 (no "-test"), versionCode 15, package org.mediashelf.tv and stable private signing certificate unchanged.

Validation: `gradle testDebugUnitTest assembleRelease` in GitHub Actions. Added BannerSettingsTest and additional FeaturedSelector tests. Native TV focus, source availability and video player require user device testing and cannot be claimed verified by CI alone.

## MediaShelf 1.6 — unified TV navigation rail

Based on the user's actual Android TV screenshots and comparative Lampa screenshot. Instead of copying a proprietary UI, version 1.6 applies proven lean-back navigation: focusable left rail/drawer, compact context breadcrumb, and more content visible per row.

- Collapsed navigation rail is ~54dp wide on TV (~46dp in phone TV preview) and remains available at all times. Expanded rail is 176dp on TV, labels visible; collapse default for installations without a prior preference. Persist `sidebar_hidden` preference.
- Search, Novelties (home), Popular, Categories, Sources, Banner setup, Connections/Settings and Diagnostics are all located inside the same side rail. The phone TV preview additionally offers an exit to touch-phone mode.
- On Android TV removes the duplicated upper header (MediaShelf/selector/search/settings row), the full-width search field, and the long horizontal filter strip. Only a tiny single-line source/section breadcrumb remains above the catalog. Portrait-touch phones keep their familiar search and controls.
- The native XXXClub search dialog, categories, site selection, TorrServer and user-configurable carousel continue unchanged.
- The actual card grid is recalculated to account for the ALWAYS-visible collapsed icon rail, rather than pretending the sidebar width is 0. Unit tests verify that the collapse yields additional cards per row and preserves safe card widths on TV and phone preview.
- D-pad LEFT from a first-column card focuses the rail Search button. D-pad RIGHT from the rail returns to featured banner or first film card; DPAD UP from top row → banner (if present) → side search. Menu remote key toggles collapsed state. Back from an expanded menu collapses it; Back again leaves the app.
- Layout changes do not refetch content, log out accounts, overwrite TorrServer settings or reset selected source; scroll offset and selected source remain.
- No copied code or assets from Kinopoisk/Lampa/Plex; source is native Android and uses MediaShelf branding and original functionality.

App display name and version: `MediaShelf`, `1.6` without `-test`, versionCode 16. Application ID `org.mediashelf.tv`, same private signing certificate. CI performs JVM unit tests before assembling release APK. Actual TV remote/device verification is still required.
