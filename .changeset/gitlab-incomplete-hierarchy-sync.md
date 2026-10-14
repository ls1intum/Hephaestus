---
"hephaestus": patch
---

GitLab hierarchy syncs continue through readable items and later pages while preserving stored parent links for items whose hierarchy is unreadable. Stale parent links clear only after the full listing succeeds. Hierarchy syncs also retry the first page after a rate-limit wait. Scheduled and manual syncs report incomplete hierarchy reads as warnings.
