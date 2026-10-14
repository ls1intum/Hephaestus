---
"hephaestus": patch
---

GitLab issue parent links use the reported project and GitLab instance, even when another project or instance has the same issue number. If the reported parent is unavailable locally, sync preserves the stored link, continues through readable siblings, and reports an incomplete hierarchy read.
