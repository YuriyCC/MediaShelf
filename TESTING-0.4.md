# MediaShelf 0.4 — device testing checklist

1. **Update in place:** install 0.4 over existing signed 0.3 without removing it. Verify address/API key stay saved (versionCode 4, package org.mediashelf.tv, same certificate).
2. **TV navigation:** D-pad focus in left rail, switch XXXClub → Все сайты → another site → back, focus card grid. Observe scroll and selection outlines.
3. **Phone layout:** source dropdown and menu on smaller screen; grid stays two columns.
4. **Dynamic list:** source count and names should match *configured* indexers in Jackett. If none appear, check message and Jackett address.
5. **Live site search:** XXXClub with 'Gangbang' should return names and posters (previously confirmed in 0.3); test another indexer individually.
6. **All sites:** search a term and read the server-reported count of successful and failed indexers; do not infer that all 32 work.
7. **Site categories:** choose a source then 'Категории сайта'; select an actual category to filter results. The 'all' source correctly explains that category trees differ.
8. **Diagnostics:** test an available source and one known unavailable source individually; 0 results from empty query must not be falsely labelled an outage. All-sites probe can take several minutes because it queries each site.
9. **Poster/details:** XXXClub poster should appear. Other sites may lack poster fields and require future adapters. Check details without concluding that absent images mean search failed.
10. **Playback:** configure TorrServer endpoint, test selecting the video file and opening with the external player. If mobile TorrServer runs locally, use 127.0.0.1; otherwise use LAN IP.

Known architecture: SourceRegistry.java enumerates Jackett configured indexers and their caps; MainActivity.java renders adaptive navigation/diagnostics/search; SiteDetails.java provides optional site enrichment (currently XXXClub).

**Security:** the private signing keystore is in the user-held recovery ZIP, not this repository. Never post the API key or signing password in issues.
