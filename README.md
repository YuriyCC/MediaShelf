# MediaShelf 0.3 — Android TV proof of concept

MediaShelf is an Android app to search Jackett torrents, display posters where provided, show detail pages for supported sites, and play through TorrServer and an external player.

## Install/test configuration
- Jackett: `http://192.168.50.2:9117` on the same home network
- Enter your **own** Jackett API key in MediaShelf settings; never share it
- The first tracker is `xxxclub`; other tracker IDs or `all` can be selected
- TorrServer: specify its actual local device/LAN address; `127.0.0.1:8090` only works if TorrServer is running on the same device

## Build and signing
**Important:** MediaShelf 0.1 and 0.2 were each signed by ephemeral GitHub Actions debug keys, so Android refuses to update either with a new build. One uninstall/reinstall is required when installing 0.3.

Starting with 0.3, the installation package is built with a fixed private test signing key stored **outside this public repository** in an isolated branch of a private GitHub repository, and its certificate fingerprint is verified before distributing the APK. Future signed builds with increasing `versionCode` can update 0.3 without uninstalling. **Never generate a new key again.**

The `.github/workflows/build.yml` workflow in this public repository is now **compile-only**. Its runner-generated APK is not distributed, to avoid conflicting signatures. APK signing is performed in private CI; its source and configuration are documented in the recovery ZIP. The signed APK is provided through ChatGPT, not this public GitHub release.

## Current prototype behaviour
- Search, newest/popular listings and category keyword shortcuts
- Grid cards with posters if the tracker supplies them; optional XXXClub HTML fallback
- XXXClub details-page description and screenshots
- TorrServer integration, separate video file selection and an external video player

The XXXClub website may block automated image/details requests, so device validation remains required. This prototype is not production-ready.
