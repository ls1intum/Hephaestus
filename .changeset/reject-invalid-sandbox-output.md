---
"hephaestus": patch
---

A practice review whose results cannot be read back is now reported as failed instead of as a review
that found nothing. Results are capped at 50 MiB in total, 10 MiB per file and 10,000 files, and a
result file's name is limited to 100 characters including its folder; a review that passes a cap, or
whose results come back damaged, fails rather than reporting a partial answer. A review interrupted
by the container host while its results were being read is retried, since that one can succeed on a
second attempt.

A symbolic link inside a repository under review is no longer followed when the work is handed to the
reviewing container, so a link is left out rather than pulling in whatever it points at.
