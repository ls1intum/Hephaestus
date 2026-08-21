---
"hephaestus": patch
---

Practice reviews no longer fail on a deployment whose repository volume was created by an earlier release. The agent writes its evidence store under the git-checkout volume, and a volume created root-owned — or owned by the user id a previous image ran as — left that store unwritable, so the first review of an upgraded instance failed with a permission error instead of running. Ownership is now corrected before the application starts.
