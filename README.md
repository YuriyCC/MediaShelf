# MediaShelf 0.3 — Android TV proof of concept

MediaShelf is a native Android app to search Jackett torrents, display posters where provided, show detail pages for supported sites, and play through TorrServer and an external player.

## Device configuration
- Jackett: `http://192.168.50.2:9117` while on the same home network
- Enter your **own** Jackett API key in MediaShelf settings; never publish it
- Default tracker: `xxxclub`; `all` can search multiple indexers
- TorrServer: specify the actual configured address; `127.0.0.1:8090` is only valid for a server running on the same Android device

## Signing — IMPORTANT
MediaShelf 0.1 and 0.2 were signed with distinct ephemeral debug keys. Android therefore requires a **one-time uninstall** before installing the newly signed 0.3. App preferences will be reset.

MediaShelf 0.3 is now signed with one **fixed, private test key**, kept OUTSIDE this public GitHub repository. It is preserved for the owner in the private `MediaShelf-0.3-RECOVERY-2026-11-22.zip` backup attached in the ChatGPT project conversation. Do not publish that archive, the keystore, or its password.

Future versions must keep the application ID `org.mediashelf.tv`, increase `versionCode`, and use the **same key from the private recovery archive**. Public GitHub Actions compiles an unsigned APK and provides Android build tools, while signing occurs privately after downloading the build. Never publish an ephemeral-signed debug build.

Certificate SHA-256 (public fingerprint only): `80:E4:EF:86:67:A9:C5:CF:7C:AF:B6:B4:94:82:29:A8:F9:5C:E5:81:A3:A3:F6:89:A2:6A:F1:3E:6D:15:46:DB`.

## Current prototype
- Categories are text searches, not fully normalized genres
- XXXClub detail-page enrichment can fetch poster, description and screenshots, but sites may block automated HTTP requests
- TorrServer video files and external video-player integration
- User device testing for poster reliability and playback is still in progress

Build scripts: `.github/workflows/build.yml`. The workflow does **not** publish installable APKs directly; installable signed versions are attached in the private ChatGPT conversation together with the recovery archive.
