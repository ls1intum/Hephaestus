---
"hephaestus": patch
---

On GitLab, a review summary that was already posted on an issue is now recognised as such. Previously
the check only ever looked at merge requests, so an issue whose summary had been posted just before a
restart could receive a second copy of the same comment. The same check on merge requests now starts
from the newest comment, so it finds a just-posted summary immediately instead of paging through a
long discussion and giving up.
