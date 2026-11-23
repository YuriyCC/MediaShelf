# План развития MediaShelf — модули сайтов и единое оформление

## Принципы
- Один APK на Android TV и телефон, единый медиакаталог с интерфейсом телевизионного приложения.
- Только тематические источники, которые прошли проверку качества и надёжности; список не равен всем индексаторам Jackett.
- Каждый сайт имеет независимый native-адаптер. Добавление в перечень не означает, что он готов.
- Сайт — источник данных, а не встроенный браузер с рекламой. Интерфейс, навигация, карточки, категории и управление пультом принадлежат MediaShelf.
- При изменении разметки исправляется отдельный модуль, остальные продолжают работать.
- Direct-поиск по сайту — основной метод. Jackett — временный необязательный мост для ещё не готовых модулей.
- Если для сайта нужна авторизация, отдельное безопасное хранение сессии и управление капчей, без публикации логинов/паролей в коде.
- Один TorrServer или системный плеер для просмотра, вне зависимости от трекера.
- Ключ подписи Android хранится только в приватном архиве восстановления; этот GitHub-репозиторий публичный.

## Модули
| ID | Статус 0.8 | Учётная запись |
|---|---|---|
| xxxclub | native тест; поисковая страница и постеры, пагинация неполная | Не требуется |
| pornolab | подготовлен ID и временный Jackett, прямой модуль не готов | Требуется |
| mypornclub | подготовлен ID и временный Jackett, прямой модуль не готов | Зависит от функций сайта |
| torrent-pirat | подготовлен ID и временный Jackett, прямой модуль не готов | Не требуется для публичного каталога |
| xxxtor | подготовлен ID и временный Jackett, прямой модуль не готов | Нужна проверка |

## Критерии готовности отдельного модуля
1. Реальный список категорий, без смешения категорий разных сайтов.
2. Поиск по нескольким запросам даёт ожидаемые результаты и не обрывается после первой страницы.
3. Работает переход между страницами/курсор/фильтры сортировки; указывается точный источник ограничений.
4. Постеры доступны, при ошибке есть понятная заглушка и диагностический код.
5. Карточка фильма показывает название, описание, параметры раздачи и источник.
6. Torrent/magnet получаются непосредственно и корректно передаются TorrServer.
7. Неудачи авторизации/блокировки сайта не ломают остальные модули.
8. Навигация телевизионным пультом и пальцем на телефоне.
9. Не сохраняются пароли в логах; сессии защищены и изолированы по источникам.
10. Документирована проверка на устройстве пользователя.

## Очерёдность
1. XXXClub — полностью восстановить переход по страницам и убедиться, что поиск не ограничен первыми 50.
2. PornoLab — категории, поиск, авторизация, постеры, пагинация; сначала допустить опциональный Jackett-мост.
3. MyPornClub и torrent-pirat — независимые модули и тесты.
4. Поиск новых авторитетных тематических трекеров по актуальной репутации и качеству раздач, без автоматического добавления в приложение.
5. Объединённый поиск только по готовым выбранным сайтам, с ярлыком источника и независимой диагностикой.
6. Финальная работа над стилем MediaShelf и выпуск стабильно подписанного APK.

## Как безопасно обновлять
- Не удалять существующий пакет: applicationId = org.mediashelf.tv.
- Новые версии: 0.9, 1.0, 1.1... (без 0.8.1), с последовательными versionCode.
- APK и ZIP восстановления в чат; ZIP содержит приватный ключ, не публикуется на GitHub.

## 0.9 — Cursor navigation
- Verified by user: on XXXClub first page about 51 items and a visible Next Page button pointing to /torrents/search/all/Gangbang/7296816424212307464.
- 0.9 expands the native adapter to find same-search cursors across the document and interpret Next Page, rather than numeric pages.
- Offline tests cover the real URL format. Live functionality still requires device verification.

## 1.0 — Универсальный контроллер страниц
- Кнопки переходов проверяются универсальным компонентом PageNavigation, но каждое расширение сайта определяет допустимые URL.
- Подтвержденная причина для XXXClub: подставные скрытые ссылки (left:-9999px) с фальшивыми курсорами и одна рабочая видимая ссылка.
- Добавлены тесты всех 8 категорий (0..7) и отдельный поиск по слову; группы 720p и 1080p разделены.
- Полнота живого каталога требует проверки на устройстве. Пока нет доказательств, что количество результатов равно полному индексу сайта.

## TV-first visual release 1.1
- Design system isolated in `MediaDesign.java`; all native Android layouts, no embedded websites.
- Main activity: new sidebar, source chips, hero, poster grid, details and navigation focus.
- Launcher icon `res/drawable/ic_mediashelf.xml`, TV banner `res/drawable/banner_mediashelf.xml`.
- User requirement: no `test` in visible app name or version; stable package and certificate are preserved; the private key's filename and alias must NOT be changed.
- TV acceptance: remote navigation across left menu / search / category chips / poster cards, back from details, poster loading, page 2+ in keyword search and categories, play a file on TorrServer.

## Версия 1.2: проверка дизайна телефона до Android TV
- Единая нативная ТВ-компоновка доступна на телефоне через `⋮ → Включить ТВ-режим (горизонтально)`.
- В режиме ТВ на телефоне можно выйти в обычную компоновку кнопкой `Режим телефона`.
- Компоненты масштабируются под горизонтальный экран телефона без выдачи имитации за реальное телевизионное разрешение.
- Поиск XXXClub, выбор разделов, постеры и переходы по страницам не меняются.

## 1.3 design iteration
- Screenshot feedback: 1.2 compact-TV preview was overloaded by duplicate top headings, had only 3 overlarge posters per row and showed the hero below the visible scroll area.
- Changed theme to purple/indigo, immersive preview, smaller 4–6 poster grid and top-of-catalog reset.
- Direct source and pager implementation unaffected.
- Real site images remain real source images; scenic placeholders in design mockup are NOT shipped as fake content.

## 1.4: Carousel
- Universal presentation layer; hero content is fetched from a dedicated XXXClub native Movies category (3), isolated from home latest listing.
- Ranked (poster + quality hint + seeder count) eligible actual torrents; excludes XXXClub pack/image/VR category groups and title patterns, deduplicates.
- Max five slides; automatically rotates every 7.5 seconds; TV remote clickable arrows and index dots.
- Does not run rotation in paused activity or while hero navigation has focus.
- Never substitutes first MegaPACK or arbitrary torrent if curation fails.

## MediaShelf 1.5 — интерфейс Lampa-подобной плотности, собственная логика
- Строка поиска на TV заменена кнопкой-иконкой и всплывающим полноценным поисковым диалогом. На телефоне строка осталась.
- Баннер 132dp; его источник управляется отдельно: разделы + ключевое слово. В браузере XXXClub это native browse/search с курсорной пагинацией.
- По умолчанию новинки со всего сайта; при желании «Gangbang» через пустую категорию all, либо заданные подкатегории.
- При выборе фото/VR/сборников допускаются соответствующие релизы в подборке, иначе MegaPACK остаётся вне баннера.
- DPAD-UP теперь имеет явную цепочку карточки→баннер→категории→поиск. Для остальных рядов назначен фокус вверх на карточку предыдущего ряда.
- Размеры 5–8 обложек на ТВ зависят от логической ширины/плотности экранного режима.
- Панель сайта и настройки компактны, как у современных телевизионных медиатек. Это не копия кода Lampa.

## 1.6 — unified television navigation rail
- The list of sources is now accessed through a single "Источники" entry rather than taking the entire TV sidebar.
- Compact icon rail 54dp is persistent. Expanded 176dp rail shows labels and does not overlay the cards.
- All global actions migrated to the rail. Touch UI retains original controls.
- D-pad navigation and poster grid geometry updated; `PosterGridTest` checks collapse effect.
