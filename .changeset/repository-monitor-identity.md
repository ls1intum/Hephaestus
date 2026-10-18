---
"hephaestus": patch
---

A GitHub workspace that monitored the same repository twice starts its sync on startup again and runs practice reviews again. The upgrade merges the two monitors into the earlier one. A repository renamed while its webhook was missed, or whose name changed only in letter case, keeps its monitor, synced data and review settings, and no longer stops the workspace's startup sync. If the two monitors have different repository paths or different review settings, the upgrade stops and names both, so no path or setting is discarded.
